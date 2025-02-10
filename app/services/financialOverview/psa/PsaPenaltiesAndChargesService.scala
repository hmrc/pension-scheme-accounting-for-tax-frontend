/*
 * Copyright 2024 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package services.financialOverview.psa

import config.FrontendAppConfig
import connectors.cache.FinancialInfoCacheConnector
import connectors.{FinancialStatementConnector, MinimalConnector}
import helpers.FormatHelper
import helpers.FormatHelper.formatCurrencyAmountAsString
import models.ChargeDetailsFilter
import models.ChargeDetailsFilter.{All, Overdue, Upcoming}
import models.financialStatement.FSClearingReason._
import models.financialStatement.PenaltyType.{AccountingForTaxPenalties, displayCharge, getPenaltyType}
import models.financialStatement.PsaFSChargeType._
import models.financialStatement.{DocumentLineItemDetail, PenaltyType, PsaFSChargeType, PsaFSDetail}
import models.viewModels.financialOverview.PsaPaymentsAndChargesDetails
import models.viewModels.paymentsAndCharges.PaymentAndChargeStatus
import models.viewModels.paymentsAndCharges.PaymentAndChargeStatus.{InterestIsAccruing, NoStatus, PaymentOverdue}
import play.api.i18n.Messages
import play.api.libs.json.{JsSuccess, Json, OFormat}
import services.SchemeService
import uk.gov.hmrc.govukfrontend.views.Aliases.{Key, Table, Text, Value}
import uk.gov.hmrc.govukfrontend.views.viewmodels.content.{Content, HtmlContent}
import uk.gov.hmrc.govukfrontend.views.viewmodels.summarylist.SummaryListRow
import uk.gov.hmrc.govukfrontend.views.viewmodels.table.{HeadCell, TableRow}
import uk.gov.hmrc.http.HeaderCarrier
import utils.DateHelper
import utils.DateHelper.{dateFormatterDMY, formatDateDMY, formatStartDate}

import java.time.LocalDate
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}


class PsaPenaltiesAndChargesService @Inject()(fsConnector: FinancialStatementConnector,
                                              financialInfoCacheConnector: FinancialInfoCacheConnector,
                                              schemeService: SchemeService,
                                              minimalConnector: MinimalConnector,
                                              config: FrontendAppConfig) {

  val isPaymentOverdue: PsaFSDetail => Boolean = data => data.amountDue > BigDecimal(0.00) && data.dueDate.exists(_.isBefore(LocalDate.now()))


  case class chargeAmount(upcomingCharge: String, overdueCharge: String, interestAccruing: String)

  def retrievePsaChargesAmount(psaFs: Seq[PsaFSDetail]): chargeAmount = {

    val upcomingCharges: Seq[PsaFSDetail] =
      psaFs.filter(_.dueDate.exists(!_.isBefore(DateHelper.today)))

    val overdueCharges: Seq[PsaFSDetail] =
      psaFs.filter(_.dueDate.exists(_.isBefore(DateHelper.today)))

    val totalUpcomingCharge: BigDecimal = upcomingCharges.map(_.amountDue).sum
    val totalOverdueCharge: BigDecimal = overdueCharges.map(_.amountDue).sum
    val totalInterestAccruing: BigDecimal = overdueCharges.map(_.accruedInterestTotal).sum

    val totalUpcomingChargeFormatted = s"${FormatHelper.formatCurrencyAmountAsString(totalUpcomingCharge)}"
    val totalOverdueChargeFormatted = s"${FormatHelper.formatCurrencyAmountAsString(totalOverdueCharge)}"
    val totalInterestAccruingFormatted = s"${FormatHelper.formatCurrencyAmountAsString(totalInterestAccruing)}"

    chargeAmount(totalUpcomingChargeFormatted, totalOverdueChargeFormatted, totalInterestAccruingFormatted)
  }

  //scalastyle:off parameter.number
  //scalastyle:off method.length
  //scalastyle:off cyclomatic.complexity
  def getPenaltiesAndCharges(
                              psaId: String,
                              penalties: Seq[PsaFSDetail],
                              journeyType: ChargeDetailsFilter,
                              config: FrontendAppConfig
                            )(implicit messages: Messages, hc: HeaderCarrier, ec: ExecutionContext): Future[Table] = {

    val seqPayments = penalties.filter({ penalty =>
      penalty.chargeType match {
        case x:PenaltyType => displayCharge(x)
        case _ => true
      }
    }).foldLeft[Seq[Future[Table]]](
      Nil) { (acc, detail) =>

      val tableRecords = getSchemeName(psaId, detail.pstr).map { schemeName =>

        val tableChargeType = if (detail.chargeType == CONTRACT_SETTLEMENT_INTEREST) INTEREST_ON_CONTRACT_SETTLEMENT else detail.chargeType
        val penaltyDetailsItemWithStatus: PaymentAndChargeStatus => PsaPaymentsAndChargesDetails =
          status =>
            PsaPaymentsAndChargesDetails(
              chargeType = tableChargeType,
              chargeReference = displayChargeReference(detail.chargeReference),
              originalChargeAmount = s"${formatCurrencyAmountAsString(detail.totalAmount)}",
              paymentDue = s"${formatCurrencyAmountAsString(detail.amountDue)}",
              accruedInterestTotal = detail.accruedInterestTotal,
              status = status,
              pstr = detail.pstr,
              period =
                if(config.podsNewFinancialCredits)
                  setPeriodNew(detail.chargeType, detail.periodStartDate, detail.periodEndDate)
                else
                  setPeriod(detail.chargeType, detail.periodStartDate, detail.periodEndDate),
              schemeName = schemeName,
              redirectUrl = controllers.financialOverview.psa.routes.PsaPenaltiesAndChargeDetailsController
                .onPageLoad(detail.pstr, detail.index.toString, journeyType)
                .url,
              visuallyHiddenText = messages("paymentsAndCharges.visuallyHiddenText", displayChargeReference(detail.chargeReference)),
              dueDate = Some(detail.dueDate.get.format(dateFormatterDMY))
            )

        val seqInterestCharge: Seq[PsaPaymentsAndChargesDetails] =
          if (detail.chargeType == CONTRACT_SETTLEMENT && detail.accruedInterestTotal > 0) {
            Seq(
              PsaPaymentsAndChargesDetails(
                chargeType = INTEREST_ON_CONTRACT_SETTLEMENT,
                chargeReference = messages("paymentsAndCharges.chargeReference.toBeAssigned"),
                originalChargeAmount = "",
                paymentDue = s"${formatCurrencyAmountAsString(detail.accruedInterestTotal)}",
                accruedInterestTotal = detail.accruedInterestTotal,
                status = InterestIsAccruing,
                pstr = detail.pstr,
                period =
                  if(config.podsNewFinancialCredits)
                    setPeriodNew(detail.chargeType, detail.periodStartDate, detail.periodEndDate)
                  else
                    setPeriod(detail.chargeType, detail.periodStartDate, detail.periodEndDate),
                schemeName = schemeName,
                redirectUrl = controllers.financialOverview.psa.routes.PsaPaymentsAndChargesInterestController
                  .onPageLoad(detail.pstr, detail.index.toString, journeyType).url,
                visuallyHiddenText = messages("paymentsAndCharges.interest.visuallyHiddenText"),
                dueDate = Some(detail.dueDate.get.format(dateFormatterDMY))
              ))
          } else {
            Nil
          }

        val seqForTable = if (isPaymentOverdue(detail)) {
          Seq(penaltyDetailsItemWithStatus(PaymentOverdue)) ++ seqInterestCharge
        } else {
          Seq(penaltyDetailsItemWithStatus(NoStatus)) ++ seqInterestCharge
        }

        if(config.podsNewFinancialCredits) {
          mapToTableNew(seqForTable, includeHeadings = true, journeyType)
        } else {
          mapToTable(seqForTable, includeHeadings = false, journeyType)
        }

      }
      acc :+ tableRecords
    }
    Future.sequence(seqPayments).map {
      x =>
        x.foldLeft(Table(
          head = if(config.podsNewFinancialCredits) Some(getHeadingNew()) else Some(getHeading()),
          rows = Nil
        )) { (acc, a) =>
          acc.copy(
            rows = acc.rows ++ a.rows
          )
        }
    }
  }

  //scalastyle:off parameter.number
  //scalastyle:off method.length
  //scalastyle:off cyclomatic.complexity
  def getAllPenaltiesAndCharges(
                                 psaId: String,
                                 penalties: Seq[PsaFSDetail],
                                 journeyType: ChargeDetailsFilter
                               )(implicit messages: Messages, hc: HeaderCarrier, ec: ExecutionContext): Future[Table] = {

    val seqPayments = penalties.foldLeft[Seq[Future[Table]]](
      Nil) { (acc, detail) =>

      val tableRecords = getSchemeName(psaId, detail.pstr).map { schemeName =>

        val tableChargeType = if (detail.chargeType == CONTRACT_SETTLEMENT_INTEREST) INTEREST_ON_CONTRACT_SETTLEMENT else detail.chargeType
        val penaltyDetailsItemWithStatus: PaymentAndChargeStatus => PsaPaymentsAndChargesDetails =
          status =>
            PsaPaymentsAndChargesDetails(
              chargeType = tableChargeType,
              chargeReference = if(detail.chargeReference == "") messages("paymentsAndCharges.chargeReference.toBeAssigned") else detail.chargeReference,
              originalChargeAmount = s"${formatCurrencyAmountAsString(detail.totalAmount)}",
              paymentDue = s"${formatCurrencyAmountAsString(detail.amountDue)}",
              accruedInterestTotal = detail.accruedInterestTotal,
              status = status,
              pstr = detail.pstr,
              period = setPeriod(detail.chargeType, detail.periodStartDate, detail.periodEndDate),
              schemeName = schemeName,
              redirectUrl = controllers.financialOverview.psa.routes.PsaPenaltiesAndChargeDetailsController
                .onPageLoad(detail.pstr, detail.index.toString, journeyType).url,
              visuallyHiddenText = messages("paymentsAndCharges.visuallyHiddenText", detail.chargeReference),
              dueDate = Some(detail.dueDate.get.format(dateFormatterDMY))
            )

        val seqInterestCharge: Seq[PsaPaymentsAndChargesDetails] =
          if (detail.chargeType == CONTRACT_SETTLEMENT && detail.accruedInterestTotal > 0) {
            Seq(
              PsaPaymentsAndChargesDetails(
                chargeType = INTEREST_ON_CONTRACT_SETTLEMENT,
                chargeReference = messages("paymentsAndCharges.chargeReference.toBeAssigned"),
                originalChargeAmount = "",
                paymentDue = s"${formatCurrencyAmountAsString(detail.accruedInterestTotal)}",
                accruedInterestTotal = detail.accruedInterestTotal,
                status = InterestIsAccruing,
                pstr = detail.pstr,
                period = setPeriod(detail.chargeType, detail.periodStartDate, detail.periodEndDate),
                schemeName = schemeName,
                redirectUrl = controllers.financialOverview.psa.routes.PsaPaymentsAndChargesInterestController
                  .onPageLoad(detail.pstr, detail.index.toString, journeyType).url,
                visuallyHiddenText = messages("paymentsAndCharges.interest.visuallyHiddenText"),
                dueDate = Some(detail.dueDate.get.format(dateFormatterDMY))
              ))
          } else {
            Nil
          }

        val seqForTable = if (isPaymentOverdue(detail)) {
          Seq(penaltyDetailsItemWithStatus(PaymentOverdue)) ++ seqInterestCharge
        } else {
          Seq(penaltyDetailsItemWithStatus(NoStatus)) ++ seqInterestCharge
        }
        mapToTable(seqForTable, includeHeadings = false, journeyType)

      }
      acc :+ tableRecords
    }
    Future.sequence(seqPayments).map {
      x =>
        x.foldLeft(Table(head = Some(getHeading()), rows = Nil)) { (acc, a) =>
          acc.copy(
            rows = acc.rows ++ a.rows
          )
        }
    }
  }

  def getClearedPenaltiesAndCharges(psaId: String, psaFs: Seq[PsaFSDetail])
                                   (implicit messages: Messages, hc: HeaderCarrier, ec: ExecutionContext): Future[Table] = {
    val clearedPenaltiesAndCharges = psaFs.filter(_.outstandingAmount <= 0)

    val tableHeader = {
      Seq(
        HeadCell(
          HtmlContent(
            s"<span class='govuk-visually-hidden'>${messages("psa.financial.overview.penaltyOrCharge")}</span>"
          )),
        HeadCell(Text(Messages("psa.financial.overview.datePaid")), classes = "govuk-!-font-weight-bold"),
        HeadCell(Text(Messages("psa.financial.overview.payment.charge.amount")), classes = "govuk-!-font-weight-bold")
      )
    }

    def getRows = clearedPenaltiesAndCharges.map(penaltyOrCharge => {
      val clearingDates = penaltyOrCharge.documentLineItemDetails.flatMap(documentLineItemDetail =>
        documentLineItemDetail.clearingDate)
      val latestClearingDate = clearingDates.max

      getSchemeName(psaId, penaltyOrCharge.pstr).map(schemeName =>
        Seq(
          TableRow(HtmlContent(
            s"<a id=${penaltyOrCharge.chargeReference} class=govuk-link href=/>" +
              penaltyOrCharge.chargeType + "</a></br>" +
              schemeName + "</br>" +
              penaltyOrCharge.chargeReference + "</br>" +
              formatStartDate(penaltyOrCharge.periodStartDate) + " to " + formatDateDMY(penaltyOrCharge.periodEndDate)
          ), classes = "govuk-!-width-one-half"),
          TableRow(HtmlContent(s"<p>${formatDateDMY(latestClearingDate)}</p>")),
          TableRow(HtmlContent(s"<p>£${penaltyOrCharge.documentLineItemDetails.map(_.clearedAmountItem).sum}</p>"))
        )
      )
    })

    val rows = Future.sequence(getRows)

    rows.map(tableRows =>
      Table(head = Some(tableHeader), rows = tableRows)
    )
  }

  private def getSchemeName(psaId: String, pstr: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[String] = {
    val res = for {
      schemeDetails <- schemeService.retrieveSchemeDetails(psaId, pstr, "pstr")
    } yield schemeDetails.schemeName
    res
  }

  private def getHeading()(implicit messages: Messages): Seq[HeadCell] = {
    if(config.podsNewFinancialCredits) {
      Seq(
        HeadCell(
          HtmlContent(
            s"<span class='govuk-visually-hidden'>${messages("psa.financial.overview.penalty")}</span>"
          )),
        HeadCell(Text(Messages("psa.financial.overview.payment.dueDate")), classes = "govuk-!-font-weight-bold"),
        HeadCell(Text(Messages("psa.financial.overview.payment.charge.amount")), classes = "govuk-!-font-weight-bold"),
        HeadCell(Text(Messages("psa.financial.overview.payment.due")), classes = "govuk-!-font-weight-bold"),
        HeadCell(Text(Messages("psa.financial.overview.payment.interest")), classes = "govuk-!-font-weight-bold")
      )
    } else  {
      Seq(
        HeadCell(Text(Messages("psa.financial.overview.penalty")), classes = "govuk-!-width-one-half"),
        HeadCell(Text(Messages("psa.financial.overview.charge.reference")), classes = "govuk-!-font-weight-bold"),
        HeadCell(Text(Messages("psa.financial.overview.payment.amount")), classes = "govuk-!-font-weight-bold"),
        HeadCell(Text(Messages("psa.financial.overview.payment.due")), classes = "govuk-!-font-weight-bold"),
        HeadCell(
          HtmlContent(
            s"<span class='govuk-visually-hidden'>${messages("psa.financial.overview.paymentStatus")}</span>"
          ))
      )
    }
  }

  private def getHeadingNew()(implicit messages: Messages): Seq[HeadCell] = {
    Seq(
      HeadCell(Text(Messages("")), classes = "govuk-!-width-one-half"),
      HeadCell(Text(Messages("psa.financial.overview.dueDate")), classes = "govuk-!-font-weight-bold"),
      HeadCell(Text(Messages("psa.financial.overview.payment.amount.new")), classes = "govuk-!-font-weight-bold"),
      HeadCell(Text(Messages("psa.financial.overview.payment.due")), classes = "govuk-!-font-weight-bold,table-nowrap"),
      HeadCell(
        HtmlContent(
          s"<span class='govuk-visually-hidden'>${messages("psa.financial.overview.paymentStatus")}</span>"
        ))
    )
  }

  private def mapToTable(allPayments: Seq[PsaPaymentsAndChargesDetails], includeHeadings: Boolean,
                         journeyType: ChargeDetailsFilter)(implicit messages: Messages): Table = {

    val head = if (includeHeadings) {
      getHeading()
    } else {
      Nil
    }

    val rows = allPayments.map { data =>
      val linkId =
        data.chargeReference match {
          case "To be assigned" => "to-be-assigned"
          case "None" => "none"
          case _ => data.chargeReference
        }

      val htmlChargeType = (journeyType, getPenaltyType(data.chargeType)) match {
        case (All, AccountingForTaxPenalties) =>
          HtmlContent(
            s"<a id=$linkId class=govuk-link href=" +
              s"${data.redirectUrl}>" +
              s"${data.chargeType} " +
              s"<span class=govuk-visually-hidden>${data.visuallyHiddenText}</span> </a>")
        case (All, _) =>
          HtmlContent(
            s"<a id=$linkId class=govuk-link href=" +
              s"${data.redirectUrl}>" +
              s"${data.chargeType} " +
              s"<span class=govuk-visually-hidden>${data.visuallyHiddenText}</span> </a>" +
              s"<p class=govuk-hint>" +
              s"${data.period} </br>")
        case _ =>
          HtmlContent(
            s"<a id=$linkId class=govuk-link href=" +
              s"${data.redirectUrl}>" +
              s"${data.chargeType} " +
              s"<span class=govuk-visually-hidden>${data.visuallyHiddenText}</span> </a>" +
              s"<p class=govuk-hint>" +
              s"${data.schemeName} </br>" +
              s"(${data.pstr})")
      }

      val htmlChargeTypeV2 = (journeyType, getPenaltyType(data.chargeType)) match {
        case (All, AccountingForTaxPenalties) =>
          HtmlContent(
            s"<a id=$linkId class=govuk-link href=" +
              s"${data.redirectUrl}>" +
              s"${data.chargeType} " +
              s"<span class=govuk-visually-hidden>${data.visuallyHiddenText}</span> </a>")
        case (All, _) =>
          HtmlContent(
            s"<a id=$linkId class=govuk-link href=" +
              s"${data.redirectUrl}>" +
              s"${data.chargeType} " +
              s"<span class=govuk-visually-hidden>${data.visuallyHiddenText}</span> </a>" +
              s"<p class=govuk-hint>" +
              s"${data.period} </br>")
        case _ =>
          HtmlContent(
            s"<a id=$linkId class=govuk-link href=" +
              s"${data.redirectUrl}>" +
              s"${data.chargeType} " +
              s"<span class=govuk-visually-hidden>${data.visuallyHiddenText}</span> </a></br>" +
              s"${data.schemeName} </br>" +
              s"${data.chargeReference} </br>" +
              s"${data.period}")
      }

      if(config.podsNewFinancialCredits) {
        Seq(
          TableRow(htmlChargeTypeV2, classes = "govuk-!-width-one-half"),
          TableRow(Text(s"${data.dueDate.get.format(dateFormatterDMY)}"), classes = "govuk-!-width-one-quarter"),
          if (data.originalChargeAmount.isEmpty) {
            TableRow(HtmlContent(s"""<span class=govuk-visually-hidden>${messages("paymentsAndCharges.chargeDetails.visuallyHiddenText")}</span>"""))
          } else {
            TableRow(Text(data.originalChargeAmount), classes = "govuk-!-width-one-quarter")
          },
          TableRow(Text(data.paymentDue), classes = "govuk-!-width-one-quarter"),
          if (data.status == InterestIsAccruing && data.accruedInterestTotal!=0) {
            TableRow(Text(data.accruedInterestTotal.toString()), classes = "govuk-!-width-one-quarter")
          } else {
            TableRow(HtmlContent(s"""<span class=govuk-visually-hidden>${messages("paymentsAndCharges.chargeDetails.visuallyHiddenText")}</span>"""))
          }
        )
      } else {
        Seq(
          TableRow(htmlChargeType, classes = "govuk-!-width-one-half"),
          TableRow(Text(s"${data.chargeReference}"), classes = "govuk-!-width-one-quarter"),
          if (data.originalChargeAmount.isEmpty) {
            TableRow(HtmlContent(s"""<span class=govuk-visually-hidden>${messages("paymentsAndCharges.chargeDetails.visuallyHiddenText")}</span>"""))
          } else {
            TableRow(Text(data.originalChargeAmount), classes = "govuk-!-width-one-quarter")
          },
          TableRow(Text(data.paymentDue), classes = "govuk-!-width-one-quarter"),
          TableRow(htmlStatus(data), classes = "")
        )
      }

    }

    Table(
      head = Some(head),
      rows = rows.map(_.toSeq),
      attributes = Map("role" -> "table")
    )
  }

  private def mapToTableNew(allPayments: Seq[PsaPaymentsAndChargesDetails], includeHeadings: Boolean,
                         journeyType: ChargeDetailsFilter)(implicit messages: Messages): Table = {

    val head = if (includeHeadings) {
      getHeadingNew()
    } else {
      Nil
    }

    val rows = allPayments.map { data =>
      val linkId =
        data.chargeReference match {
          case "To be assigned" => "to-be-assigned"
          case "None" => "none"
          case _ => data.chargeReference
        }

      val htmlChargeType = (journeyType, getPenaltyType(data.chargeType)) match {
        case (All, AccountingForTaxPenalties) =>
          HtmlContent(
            s"<a id=$linkId class=govuk-link href=" +
              s"${data.redirectUrl}>" +
              s"${data.chargeType} " +
              s"<span class=govuk-visually-hidden>${data.visuallyHiddenText}</span></a>" +
              s"${data.chargeReference}")
        case (All, _) =>
          HtmlContent(
            s"<a id=$linkId class=govuk-link href=" +
              s"${data.redirectUrl}>" +
              s"${data.chargeType} " +
              s"<span class=govuk-visually-hidden>${data.visuallyHiddenText}</span></a>" +
              s"<p class=govuk-hint>" +
              s"${data.chargeReference}</br>" +
              s"${data.period}</p>")
        case _ =>
          HtmlContent(
            s"<a id=$linkId class=govuk-link href=" +
              s"${data.redirectUrl}>" +
              s"${data.chargeType} " +
              s"<span class=govuk-visually-hidden>${data.visuallyHiddenText}</span></a>" +
              s"<p class=govuk-hint>" +
              s"${data.schemeName}</br>" +
              s"${data.chargeReference}</p>")
      }

      Seq(
        TableRow(htmlChargeType, classes = "govuk-!-width-one-half"),
        TableRow(Text(s"${data.dueDate.get.format(dateFormatterDMY)}"), classes = "govuk-!-width-one-quarter"),
        if (data.originalChargeAmount.isEmpty) {
          TableRow(HtmlContent(s"""<span class=govuk-visually-hidden>${messages("paymentsAndCharges.chargeDetails.visuallyHiddenText")}</span>"""))
        } else {
          TableRow(Text(data.originalChargeAmount), classes = "govuk-!-width-one-quarter")
        },
        TableRow(Text(data.paymentDue), classes = "govuk-!-width-one-quarter"),
        TableRow(htmlStatus(data), classes = "")
      )
    }

    Table(
      head = Some(head),
      rows = rows.map(_.toSeq),
      attributes = Map("role" -> "table")
    )
  }

  private def htmlStatus(data: PsaPaymentsAndChargesDetails)(implicit messages: Messages): HtmlContent = {
    val (classes, content) = (data.status, data.paymentDue) match {
      case (InterestIsAccruing, _) =>
        ("govuk-tag govuk-tag--blue", data.status.toString)
      case (PaymentOverdue, _) =>
        ("govuk-tag govuk-tag--red", data.status.toString)
      case (_, "£0.00") =>
        ("govuk-visually-hidden", messages("paymentsAndCharges.chargeDetails.visuallyHiddenText.noPaymentDue"))
      case _ =>
        ("govuk-visually-hidden", messages("paymentsAndCharges.chargeDetails.visuallyHiddenText.paymentIsDue"))
    }
    HtmlContent(s"<span class='$classes'>$content</span>")
  }

  def getTypeParam(penaltyType: PenaltyType)(implicit messages: Messages): String =
    if (penaltyType == AccountingForTaxPenalties) {
      messages(s"penaltyType.${penaltyType.toString}")
    } else {
      messages(s"penaltyType.${penaltyType.toString}").toLowerCase()
    }

  def getDueCharges(psaFSDetail: Seq[PsaFSDetail]): Seq[PsaFSDetail] =
    psaFSDetail.filter(_.dueDate.nonEmpty)

  def getInterestCharges(psaFSDetail: Seq[PsaFSDetail]): Seq[PsaFSDetail] =
    psaFSDetail
      .filter(_.accruedInterestTotal >= BigDecimal(0.00))

  def getPenaltiesForJourney(psaId: String, journeyType: ChargeDetailsFilter)
                            (implicit ec: ExecutionContext, hc: HeaderCarrier): Future[PenaltiesCache] =
    getPenaltiesFromCache(psaId).map { cache =>
      journeyType match {
        case Overdue => cache.copy(penalties = getOverdueCharges(cache.penalties))
        case Upcoming => cache.copy(penalties = extractUpcomingCharges(cache.penalties))
        case _ => cache
      }
    }

  def getPenaltiesFromCache(psaId: String)(implicit ec: ExecutionContext, hc: HeaderCarrier): Future[PenaltiesCache] =
    financialInfoCacheConnector.fetch flatMap {
      case Some(jsValue) =>
        jsValue.validate[PenaltiesCache] match {
          case JsSuccess(value, _) if value.psaId == psaId => Future.successful(value)
          case _ => saveAndReturnPenalties(psaId)
        }
      case _ => saveAndReturnPenalties(psaId)
    }

  def saveAndReturnPenalties(psaId: String)(implicit ec: ExecutionContext, hc: HeaderCarrier): Future[PenaltiesCache] =
    for {
      penalties <- fsConnector.getPsaFS(psaId)
      minimalDetails <- minimalConnector.getMinimalPsaDetails()
      _ <- financialInfoCacheConnector.save(Json.toJson(PenaltiesCache(psaId, minimalDetails.name, penalties.seqPsaFSDetail)))
    } yield PenaltiesCache(psaId, minimalDetails.name, penalties.seqPsaFSDetail)

  def getOverdueCharges(psaFS: Seq[PsaFSDetail]): Seq[PsaFSDetail] =
    psaFS
      .filter(_.dueDate.nonEmpty)
      .filter(_.dueDate.get.isBefore(DateHelper.today))
      .filter(_.amountDue > BigDecimal(0.00))

  def extractUpcomingCharges(psaFS: Seq[PsaFSDetail]): Seq[PsaFSDetail] =
    psaFS
      .filter(charge => charge.dueDate.nonEmpty && !charge.dueDate.get.isBefore(DateHelper.today))
      .filter(_.amountDue > BigDecimal(0.00))

  def chargeDetailsRows(data: PsaFSDetail, journeyType: ChargeDetailsFilter)(implicit messages: Messages): Seq[SummaryListRow] = {
    chargeReferenceRow(data) ++ penaltyAmountRow(data) ++ clearingChargeDetailsRow(data) ++
      stoodOverAmountChargeDetailsRow(data) ++ totalAmountDueChargeDetailsRow(data, journeyType)
  }

  def chargeHeaderDetailsRows(data: PsaFSDetail)(implicit messages: Messages): Seq[SummaryListRow] = {
    pstrRow(data) ++ chargeReferenceRowNew(data) ++ setTaxPeriod(data)
  }

  def getPaymentDueDate(data: PsaFSDetail): String = {
    data.dueDate match {
      case Some(date) =>
        date.format(dateFormatterDMY)
      case _ => ""
    }
  }

  private def chargeReferenceRow(data: PsaFSDetail)(implicit messages: Messages): Seq[SummaryListRow] = {
    Seq(
      SummaryListRow(
        key = Key(Text(Messages("psa.financial.overview.charge.reference")), classes = "govuk-!-padding-left-0 govuk-!-width-one-half"),
        value = Value(Text(s"${data.chargeReference}"), classes = "govuk-!-width-one-quarter")
      ))
  }

  private def chargeReferenceRowNew(data: PsaFSDetail)(implicit messages: Messages): Seq[SummaryListRow] = {
    Seq(
      SummaryListRow(
        key = Key(Text(Messages("psa.financial.overview.charge.reference")), classes = "govuk-!-padding-left-0 govuk-!-width-one-half"),
        value = Value(Text(s"${data.chargeReference}"), classes = "govuk-!-width-one-half")
      ))
  }

  private def pstrRow(data: PsaFSDetail)(implicit messages: Messages): Seq[SummaryListRow] = {
    Seq(
      SummaryListRow(
        key = Key(Text(Messages("psa.pension.scheme.tax.reference.new")), classes = "govuk-!-padding-left-0 govuk-!-width-one-half"),
        value = Value(Text(s"${data.pstr}"), classes = "govuk-!-width-one-half")
      ))
  }

  private def setTaxPeriod(data: PsaFSDetail)(implicit messages: Messages): Seq[SummaryListRow] = {
    Seq(
      SummaryListRow(
        key = Key(Text(Messages("psa.pension.scheme.tax.period.new")), classes = "govuk-!-padding-left-0 govuk-!-width-one-half"),
        value = Value(Text(s"${formatDateDMY(data.periodStartDate) + " to " + formatDateDMY(data.periodEndDate)}"), classes = "govuk-!-width-one-half")
      ))
  }

  private def penaltyAmountRow(data: PsaFSDetail)(implicit messages: Messages): Seq[SummaryListRow] = {

    Seq(
      SummaryListRow(
        key = Key(Text(Messages("psa.financial.overview.penaltyAmount")), classes = "govuk-!-padding-left-0 govuk-!-width-one-half"),
        value = Value(Text(s"${FormatHelper.formatCurrencyAmountAsString(data.totalAmount)}"),
          classes = "govuk-!-width-one-quarter")
      ))
  }

  def chargeAmountDetailsRows(data: PsaFSDetail)(implicit messages: Messages): Table = {

    val headRow = Seq(
      HeadCell(Text(Messages("psa.pension.scheme.chargeAmount.label.new"))),
      HeadCell(Text("")),
      HeadCell(Text(s"${FormatHelper.formatCurrencyAmountAsString(data.totalAmount)}"), classes = "govuk-!-font-weight-regular")
    )

    val rows = data.documentLineItemDetails.map { documentLineItemDetail =>
      if (documentLineItemDetail.clearedAmountItem > 0) {
        getClearingDetailLabelNew(documentLineItemDetail) match {
          case Some(clearingDetailsValue) =>
            Seq(
              TableRow(clearingDetailsValue, classes = "govuk-!-font-weight-bold"),
              TableRow(Text(getChargeDateNew(documentLineItemDetail))),
              TableRow(Text(s"${FormatHelper.formatCurrencyAmountAsString(documentLineItemDetail.clearedAmountItem)}"))
            )
          case _ =>  Seq()
        }
      } else {
        Seq()
      }
    }

    val stoodOverAmountRow = if (data.stoodOverAmount > 0) {
      Seq(Seq(
        TableRow(Text(Messages("paymentsAndCharges.chargeDetails.stoodOverAmount")), classes = "govuk-!-font-weight-bold"),
        TableRow(Text("")),
        TableRow(Text(s"${FormatHelper.formatCurrencyAmountAsString(data.stoodOverAmount)}"),
          classes = "govuk-!-font-weight-regular,govuk-!-text-align-left")
      ))
    } else {
      Seq(Seq())
    }

    Table(head = Some(headRow), rows = rows ++ stoodOverAmountRow, attributes = Map("role" -> "table"))
  }

  private def getClearingDetailLabelNew(documentLineItemDetail: DocumentLineItemDetail)(implicit messages: Messages): Option[Text] = {
    (documentLineItemDetail.clearingReason, documentLineItemDetail.paymDateOrCredDueDate, documentLineItemDetail.clearingDate) match {
      case (Some(clearingReason), _, _) =>
        clearingReason match {
          case CLEARED_WITH_PAYMENT => Some(Text(Messages("financialPaymentsAndCharges.clearingReason.c1.new")))
          case CLEARED_WITH_DELTA_CREDIT => Some(Text(Messages("financialPaymentsAndCharges.clearingReason.c2.new")))
          case REPAYMENT_TO_THE_CUSTOMER => Some(Text(Messages("financialPaymentsAndCharges.clearingReason.c3.new")))
          case WRITTEN_OFF => Some(Text(Messages("financialPaymentsAndCharges.clearingReason.c4.new")))
          case TRANSFERRED_TO_ANOTHER_ACCOUNT => Some(Text(Messages("financialPaymentsAndCharges.clearingReason.c5.new")))
          case OTHER_REASONS => Some(Text(Messages("financialPaymentsAndCharges.clearingReason.c6.new")))
        }
      case (Some(clearingReason), None, None) =>
        clearingReason match {
          case CLEARED_WITH_PAYMENT => Some(Text(Messages("financialPaymentsAndCharges.clearingReason.noClearingDate.c1")))
          case CLEARED_WITH_DELTA_CREDIT => Some(Text(Messages("financialPaymentsAndCharges.clearingReason.noClearingDate.c2")))
          case REPAYMENT_TO_THE_CUSTOMER => Some(Text(Messages("financialPaymentsAndCharges.clearingReason.noClearingDate.c3")))
          case WRITTEN_OFF => Some(Text(Messages("financialPaymentsAndCharges.clearingReason.noClearingDate.c4")))
          case TRANSFERRED_TO_ANOTHER_ACCOUNT => Some(Text(Messages("financialPaymentsAndCharges.noClearingDate.clearingReason.c5")))
          case OTHER_REASONS => Some(Text(Messages("financialPaymentsAndCharges.clearingReason.noClearingDate.c6")))
        }
      case _ => None
    }
  }

  private def getChargeDateNew(documentLineItemDetail: DocumentLineItemDetail): String = {
    (documentLineItemDetail.paymDateOrCredDueDate, documentLineItemDetail.clearingDate) match {
      case (Some(paymDateOrCredDueDate), _) =>
        formatDateDMY(paymDateOrCredDueDate)
      case (None, Some(clearingDate)) =>
        formatDateDMY(clearingDate)
      case _ => ""
    }
  }

  private def clearingChargeDetailsRow(data: PsaFSDetail)(implicit messages: Messages): Seq[SummaryListRow] = {

    data.documentLineItemDetails.flatMap { documentLineItemDetail =>
      if (documentLineItemDetail.clearedAmountItem > 0) {
        getClearingDetailLabel(documentLineItemDetail) match {
          case Some(clearingDetailsValue) =>
            Seq(
              SummaryListRow(
                key = Key(
                  content = clearingDetailsValue,
                  classes = "govuk-!-padding-left-0 govuk-!-width-one-half"
                ),
                value = Value(
                  content = Text(s"-${formatCurrencyAmountAsString(documentLineItemDetail.clearedAmountItem)}"),
                  classes = "govuk-!-width-one-quarter"
                ),
                actions = None
              ))
          case _ => Nil
        }
      } else {
        Nil
      }
    }
  }

  private def getClearingDetailLabel(documentLineItemDetail: DocumentLineItemDetail)(implicit messages: Messages): Option[Text] = {
    (documentLineItemDetail.clearingReason, documentLineItemDetail.paymDateOrCredDueDate, documentLineItemDetail.clearingDate) match {
      case (Some(clearingReason), Some(paymDateOrCredDueDate), _) =>
        clearingReason match {
          case CLEARED_WITH_PAYMENT => Some(Text(Messages("financialPaymentsAndCharges.clearingReason.c1",formatDateDMY(paymDateOrCredDueDate))))
          case CLEARED_WITH_DELTA_CREDIT => Some(Text(Messages("financialPaymentsAndCharges.clearingReason.c2",formatDateDMY(paymDateOrCredDueDate))))
          case REPAYMENT_TO_THE_CUSTOMER => Some(Text(Messages("financialPaymentsAndCharges.clearingReason.c3",formatDateDMY(paymDateOrCredDueDate))))
          case WRITTEN_OFF => Some(Text(Messages("financialPaymentsAndCharges.clearingReason.c4",formatDateDMY(paymDateOrCredDueDate))))
          case TRANSFERRED_TO_ANOTHER_ACCOUNT => Some(Text(Messages("financialPaymentsAndCharges.clearingReason.c5",formatDateDMY(paymDateOrCredDueDate))))
          case OTHER_REASONS => Some(Text(Messages("financialPaymentsAndCharges.clearingReason.c6",formatDateDMY(paymDateOrCredDueDate))))
        }
      case (Some(clearingReason), None, Some(clearingDate)) =>
        clearingReason match {
          case CLEARED_WITH_PAYMENT => Some(Text(Messages("financialPaymentsAndCharges.clearingReason.c1",formatDateDMY(clearingDate))))
          case CLEARED_WITH_DELTA_CREDIT => Some(Text(Messages("financialPaymentsAndCharges.clearingReason.c2",formatDateDMY(clearingDate))))
          case REPAYMENT_TO_THE_CUSTOMER => Some(Text(Messages("financialPaymentsAndCharges.clearingReason.c3",formatDateDMY(clearingDate))))
          case WRITTEN_OFF => Some(Text(Messages("financialPaymentsAndCharges.clearingReason.c4",formatDateDMY(clearingDate))))
          case TRANSFERRED_TO_ANOTHER_ACCOUNT => Some(Text(Messages("financialPaymentsAndCharges.clearingReason.c5",formatDateDMY(clearingDate))))
          case OTHER_REASONS => Some(Text(Messages("financialPaymentsAndCharges.clearingReason.c6",formatDateDMY(clearingDate))))
        }
      case (Some(clearingReason), None, None) =>
        clearingReason match {
          case CLEARED_WITH_PAYMENT => Some(Text(Messages("financialPaymentsAndCharges.clearingReason.noClearingDate.c1")))
          case CLEARED_WITH_DELTA_CREDIT => Some(Text(Messages("financialPaymentsAndCharges.clearingReason.noClearingDate.c2")))
          case REPAYMENT_TO_THE_CUSTOMER => Some(Text(Messages("financialPaymentsAndCharges.clearingReason.noClearingDate.c3")))
          case WRITTEN_OFF => Some(Text(Messages("financialPaymentsAndCharges.clearingReason.noClearingDate.c4")))
          case TRANSFERRED_TO_ANOTHER_ACCOUNT => Some(Text(Messages("financialPaymentsAndCharges.noClearingDate.clearingReason.c5")))
          case OTHER_REASONS => Some(Text(Messages("financialPaymentsAndCharges.clearingReason.noClearingDate.c6")))
        }
      case _ => None
    }
  }

  private def stoodOverAmountChargeDetailsRow(data: PsaFSDetail)(implicit messages: Messages): Seq[SummaryListRow] =
    if (data.stoodOverAmount > 0) {
      Seq(
        SummaryListRow(
          key = Key(
            content = Text(Messages("paymentsAndCharges.chargeDetails.stoodOverAmount")),
            classes = "govuk-!-padding-left-0 govuk-!-width-one-half"
          ),
          value = Value(
            content = Text(s"-${formatCurrencyAmountAsString(data.stoodOverAmount)}"),
            classes = "govuk-!-width-one-quarter"
          ),
          actions = None
        ))
    } else {
      Nil
    }

  private def totalAmountDueChargeDetailsRow(data: PsaFSDetail, journeyType: ChargeDetailsFilter)(implicit messages: Messages): Seq[SummaryListRow] = {
    val amountDueKey: Content = (data.dueDate, data.amountDue > 0) match {
      case (Some(date), true) =>
        Text(Messages(s"financialPaymentsAndCharges.paymentDue.${journeyType.toString}.dueDate", date.format(dateFormatterDMY)))
      case _ =>
        Text(Messages("financialPaymentsAndCharges.paymentDue.noDueDate"))
    }
    if (data.totalAmount > 0) {
      Seq(
        SummaryListRow(
          key = Key(
            content = amountDueKey,
            classes = "govuk-!-padding-left-0 govuk-!-width-one-half"
          ),
          value = Value(
            content = Text(s"${formatCurrencyAmountAsString(data.amountDue)}"),
            classes = "govuk-!-width-one-quarter govuk-!-font-weight-bold"
          ),
          actions = None
        ))
    } else {
      Nil
    }
  }

  def setPeriod(chargeType: PsaFSChargeType, periodStartDate: LocalDate, periodEndDate: LocalDate): String =
    chargeType match {
      case AFT_INITIAL_LFP | AFT_DAILY_LFP | AFT_30_DAY_LPP | AFT_6_MONTH_LPP
           | AFT_12_MONTH_LPP | OTC_30_DAY_LPP | OTC_6_MONTH_LPP | OTC_12_MONTH_LPP =>
        "Quarter: " + formatStartDate(periodStartDate) + " to " + formatDateDMY(periodEndDate)
      case PSS_PENALTY | PSS_INFO_NOTICE | CONTRACT_SETTLEMENT | CONTRACT_SETTLEMENT_INTEREST =>
        "Period: " + formatStartDate(periodStartDate) + " to " + formatDateDMY(periodEndDate)
      case _ => ""
    }

  def setPeriodNew(chargeType: PsaFSChargeType, periodStartDate: LocalDate, periodEndDate: LocalDate): String =
    chargeType match {
      case AFT_INITIAL_LFP | AFT_DAILY_LFP | AFT_30_DAY_LPP | AFT_6_MONTH_LPP
           | AFT_12_MONTH_LPP | OTC_30_DAY_LPP | OTC_6_MONTH_LPP | OTC_12_MONTH_LPP =>
        formatStartDate(periodStartDate) + " to " + formatDateDMY(periodEndDate)
      case PSS_PENALTY | PSS_INFO_NOTICE | CONTRACT_SETTLEMENT | CONTRACT_SETTLEMENT_INTEREST =>
        formatStartDate(periodStartDate) + " to " + formatDateDMY(periodEndDate)
      case _ => ""
    }

  private def totalInterestDueRow(data: PsaFSDetail)(implicit messages: Messages): Seq[SummaryListRow] = {
    val dateAsOf: String = LocalDate.now.format(dateFormatterDMY)
    Seq(SummaryListRow(
      key = Key(Text(Messages("psa.financial.overview.totalDueAsOf",dateAsOf)), classes = "govuk-!-width-two-quarters"),
      value = Value(Text(s"${FormatHelper.formatCurrencyAmountAsString(data.accruedInterestTotal)}"),
        classes = "govuk-!-width-one-quarter")
    ))
  }

  private def interestTaxPeriodRow(data: PsaFSDetail)(implicit messages: Messages): Seq[SummaryListRow] = {
    Seq(SummaryListRow(
      key = Key(Text(Messages("psa.pension.scheme.interest.tax.period.new")), classes = "govuk-!-width-one-half"),
      value = Value(Text(s"${formatDateDMY(data.periodStartDate) + " to " + formatDateDMY(data.periodEndDate)}"),
        classes = "govuk-!-width-one-half")
    ))
  }

  private def chargeReferenceInterestRow(implicit messages: Messages): Seq[SummaryListRow] = {

    Seq(
      SummaryListRow(
        key = Key(Text(Messages("psa.financial.overview.charge.reference")), classes = "govuk-!-width-two-quarters"),
        value = Value(Text(Messages("paymentsAndCharges.chargeReference.toBeAssigned")), classes = "govuk-!-width-one-quarter")
      ))
  }

  private def chargeReferenceInterestRowNew(implicit messages: Messages): Seq[SummaryListRow] = {
    Seq(
      SummaryListRow(
        key = Key(Text(Messages("psa.financial.overview.charge.reference")), classes = "govuk-!-width-one-half"),
        value = Value(Text(Messages("paymentsAndCharges.chargeReference.toBeAssigned")), classes = "govuk-!-width-one-half")
      ))
  }

  private def displayChargeReference(chargeReference: String)(implicit messages: Messages): String = {
    if (chargeReference == "") messages("paymentsAndCharges.chargeReference.toBeAssigned") else chargeReference
  }

  def interestRows(data: PsaFSDetail)(implicit messages: Messages): Seq[SummaryListRow] =
    chargeReferenceInterestRow ++ totalInterestDueRow(data)

  def interestRowsNew(data: PsaFSDetail)(implicit messages: Messages): Seq[SummaryListRow] =
    chargeReferenceInterestRowNew ++ interestTaxPeriodRow(data)

}

case class PenaltiesCache(psaId: String, psaName: String, penalties: Seq[PsaFSDetail])

object PenaltiesCache {
  implicit val format: OFormat[PenaltiesCache] = Json.format[PenaltiesCache]
}
