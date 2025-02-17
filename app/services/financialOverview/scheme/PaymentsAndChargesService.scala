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

package services.financialOverview.scheme

import config.FrontendAppConfig
import connectors.FinancialStatementConnector
import connectors.cache.FinancialInfoCacheConnector
import controllers.chargeB.{routes => _}
import helpers.FormatHelper
import helpers.FormatHelper._
import models.ChargeDetailsFilter.{All, History, Overdue, Upcoming}
import models.financialStatement.FSClearingReason._
import models.financialStatement.PaymentOrChargeType.{AccountingForTaxCharges, getPaymentOrChargeType}
import models.financialStatement.SchemeFSChargeType._
import models.financialStatement._
import models.viewModels.financialOverview.{PaymentsAndChargesDetails => FinancialPaymentAndChargesDetails}
import models.viewModels.paymentsAndCharges.PaymentAndChargeStatus
import models.viewModels.paymentsAndCharges.PaymentAndChargeStatus.{InterestIsAccruing, NoStatus, PaymentOverdue}
import models.{ChargeDetailsFilter, SchemeDetails}
import play.api.Logger
import play.api.i18n.Messages
import play.api.libs.json.{JsSuccess, Json, OFormat}
import services.SchemeService
import uk.gov.hmrc.domain.{PsaId, PspId}
import uk.gov.hmrc.govukfrontend.views.Aliases.{Key, Table, Text, Value}
import uk.gov.hmrc.govukfrontend.views.viewmodels.content.{Content, HtmlContent}
import uk.gov.hmrc.govukfrontend.views.viewmodels.summarylist.SummaryListRow
import uk.gov.hmrc.govukfrontend.views.viewmodels.table.{HeadCell, TableRow}
import uk.gov.hmrc.http.HeaderCarrier
import utils.DateHelper
import utils.DateHelper._

import java.time.LocalDate
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class PaymentsAndChargesService @Inject()(schemeService: SchemeService,
                                          fsConnector: FinancialStatementConnector,
                                          financialInfoCacheConnector: FinancialInfoCacheConnector
                                         ) {

  private val logger = Logger(classOf[PaymentsAndChargesService])

  case class IndexRef(chargeType: String, chargeReference: String, period: String)

  def getPaymentsAndCharges(srn: String,
                            schemeFSDetail: Seq[SchemeFSDetail],
                            chargeDetailsFilter: ChargeDetailsFilter,
                            config: FrontendAppConfig
                           )(implicit messages: Messages): Table = {
    val filteredSchemeFSDetail = filteredSeqSchemeFsDetailNoCredits(schemeFSDetail)

    val seqPayments: Seq[FinancialPaymentAndChargesDetails] = filteredSchemeFSDetail.flatMap { paymentOrCharge =>
      def data: Seq[FinancialPaymentAndChargesDetails] = paymentsAndChargesDetails(paymentOrCharge, srn, chargeDetailsFilter, config)
      paymentOrCharge.chargeType match {
        case x:PenaltyType if !PenaltyType.displayCharge(x) => Seq.empty
        case _ => data
      }
    }

    if (config.podsNewFinancialCredits) {
      mapToTableNew(seqPayments, chargeDetailsFilter, config)
    } else {
      mapToTable(seqPayments, chargeDetailsFilter)
    }

  }

  val isPaymentOverdue: SchemeFSDetail => Boolean =
    data => data.amountDue > BigDecimal(0.00) && data.dueDate.exists(_.isBefore(DateHelper.today))

  val extractUpcomingCharges: Seq[SchemeFSDetail] => Seq[SchemeFSDetail] = schemeFSDetail =>
    schemeFSDetail.filter(charge => charge.dueDate.nonEmpty
      && (charge.dueDate.get.isEqual(DateHelper.today) || charge.dueDate.get.isAfter(DateHelper.today))
      && charge.amountDue > BigDecimal(0.00))

  def getClearedCharges(schemeFSDetail: Seq[SchemeFSDetail]): Seq[SchemeFSDetail] = {
    schemeFSDetail.filter(_.outstandingAmount <= 0)
  }

  def getOverdueCharges(schemeFSDetail: Seq[SchemeFSDetail]): Seq[SchemeFSDetail] = {
    val withDueDate = schemeFSDetail.filter(_.dueDate.nonEmpty)
    logger.warn(s"After filtering non-empty due dates, ${withDueDate.size} items remain")

    val overdue = withDueDate.filter(_.dueDate.get.isBefore(DateHelper.today))
    logger.warn(s"After filtering overdue dates, ${overdue.size} items remain")

    val withPositiveAmountDue = overdue.filter(_.amountDue > BigDecimal(0.00))
    logger.warn(s"After filtering positive amount due, ${withPositiveAmountDue.size} items remain")

    withPositiveAmountDue
  }

  def isOverdueChargeAvailable(schemeFSDetail: Seq[SchemeFSDetail]): Boolean = schemeFSDetail.exists(isPaymentOverdue)

  def getDueCharges(schemeFSDetail: Seq[SchemeFSDetail]): Seq[SchemeFSDetail] =
    schemeFSDetail
      .filter(_.dueDate.nonEmpty)
      .filter(_.amountDue >= BigDecimal(0.00))

  def getInterestCharges(schemeFSDetail: Seq[SchemeFSDetail]): Seq[SchemeFSDetail] =
    schemeFSDetail
      .filter(_.accruedInterestTotal >= BigDecimal(0.00))

  private def setSubmittedDate(submittedDate: Option[String], chargeType: SchemeFSChargeType)
                              (implicit messages: Messages): Option[String] = {
    (chargeType, submittedDate) match {
      case (PSS_AFT_RETURN | PSS_OTC_AFT_RETURN, Some(value)) =>
        Some(messages("returnHistory.submittedOn", formatDateDMYString(value)))
      case _ => None
    }
  }

  def getReturnLinkBasedOnJourney(journeyType: ChargeDetailsFilter, schemeName: String)
                                 (implicit messages: Messages): String = {
    journeyType match {
      case All => schemeName
      case _ => messages("financialPaymentsAndCharges.returnLink." +
        s"${journeyType.toString}")
    }
  }

  def getReturnUrl(srn: String, psaId: Option[PsaId], pspId: Option[PspId], config: FrontendAppConfig,
                   journeyType: ChargeDetailsFilter): String = {
    journeyType match {
      case All => config.schemeDashboardUrl(psaId, pspId).format(srn)
      case _ => controllers.financialOverview.scheme.routes.PaymentsAndChargesController.onPageLoad(srn, journeyType).url
    }
  }

  // scalastyle:off method.length
  private def paymentsAndChargesDetails(
                                         details: SchemeFSDetail,
                                         srn: String,
                                         chargeDetailsFilter: ChargeDetailsFilter,
                                         config: FrontendAppConfig
                                       )(implicit messages: Messages): Seq[FinancialPaymentAndChargesDetails] = {
    val chargeType = getPaymentOrChargeType(details.chargeType)
    val (version, receiptDate) = (details.version, details.receiptDate)
    val suffix = version.map(v => s" submission $v")
    val submittedDate = receiptDate.map(formatDateYMD)
    val index = details.index.toString
    val periodValue: String = if (chargeType.toString == AccountingForTaxCharges.toString) {
      details.periodStartDate.map(_.toString).getOrElse("")
    } else {
      details.periodEndDate.map(_.getYear.toString).getOrElse("")
    }

    val chargeDetailsItemWithStatus: PaymentAndChargeStatus => FinancialPaymentAndChargesDetails =
      status =>
        FinancialPaymentAndChargesDetails(
          chargeType = details.chargeType.toString + suffix.getOrElse(""),
          chargeReference = displayChargeReference(details.chargeReference),
          originalChargeAmount = s"${formatCurrencyAmountAsString(details.totalAmount)}",
          paymentDue = s"${formatCurrencyAmountAsString(details.amountDue)}",
          status = status,
          period = if(config.podsNewFinancialCredits) {
            setPeriodNew(details.chargeType, details.periodStartDate, details.periodEndDate)
          } else {
            setPeriod(details.chargeType, details.periodStartDate, details.periodEndDate)
          },
          submittedDate = setSubmittedDate(submittedDate, details.chargeType),
          redirectUrl = controllers.financialOverview.scheme.routes.PaymentsAndChargeDetailsController.onPageLoad(
            srn, periodValue, index, chargeType, version, submittedDate, chargeDetailsFilter).url,
          visuallyHiddenText = messages("paymentsAndCharges.visuallyHiddenText", displayChargeReference(details.chargeReference)),
          id = displayChargeReference(details.chargeReference),
          dateDue = details.dueDate,
          accruedInterestTotal = details.accruedInterestTotal
        )

    (isDisplayInterestChargeType(details.chargeType), details.amountDue > 0) match {
      case (true, true) if details.accruedInterestTotal > 0 =>
        val interestChargeType =
          if (details.chargeType == PSS_AFT_RETURN) PSS_AFT_RETURN_INTEREST else PSS_OTC_AFT_RETURN_INTEREST

        Seq(
          chargeDetailsItemWithStatus(PaymentOverdue),
          FinancialPaymentAndChargesDetails(
            chargeType = getInterestChargeTypeText(details.chargeType) + suffix.getOrElse(""),
            chargeReference = messages("paymentsAndCharges.chargeReference.toBeAssigned"),
            originalChargeAmount = "",
            paymentDue = s"${formatCurrencyAmountAsString(details.accruedInterestTotal)}",
            status = InterestIsAccruing,
            period = setPeriod(interestChargeType, details.periodStartDate, details.periodEndDate),
            redirectUrl = controllers.financialOverview.scheme.routes.PaymentsAndChargesInterestController.onPageLoad(
              srn, periodValue, index, chargeType, version, submittedDate, chargeDetailsFilter).url,
            visuallyHiddenText = messages("paymentsAndCharges.interest.visuallyHiddenText"),
            id = s"${details.chargeReference}-interest",
            dateDue = details.dueDate,
            accruedInterestTotal = details.accruedInterestTotal
          )
        )
      case (_, true) if details.dueDate.exists(_.isBefore(LocalDate.now())) =>
        Seq(chargeDetailsItemWithStatus(PaymentOverdue))
      case (true, _) if details.totalAmount < 0 =>
        Seq.empty
      case _ =>
        Seq(
          chargeDetailsItemWithStatus(NoStatus)
        )
    }
  }

  def getTypeParam(paymentType: PaymentOrChargeType)(implicit messages: Messages): String =
    if (paymentType == AccountingForTaxCharges) {
      messages(s"paymentOrChargeType.${paymentType.toString}")
    } else {
      messages(s"paymentOrChargeType.${paymentType.toString}").toLowerCase()
    }

  def setPeriodNew(chargeType: SchemeFSChargeType, periodStartDate: Option[LocalDate], periodEndDate: Option[LocalDate]): String = {
    chargeType match {
      case PSS_AFT_RETURN | PSS_OTC_AFT_RETURN | PSS_AFT_RETURN_INTEREST | PSS_OTC_AFT_RETURN_INTEREST
           | AFT_MANUAL_ASST | AFT_MANUAL_ASST_INTEREST | OTC_MANUAL_ASST | OTC_MANUAL_ASST_INTEREST =>
        formatStartDate(periodStartDate) + " to " + formatDateDMY(periodEndDate)
      case PSS_CHARGE | PSS_CHARGE_INTEREST | CONTRACT_SETTLEMENT | CONTRACT_SETTLEMENT_INTEREST =>
        formatStartDate(periodStartDate) + " to " + formatDateDMY(periodEndDate)
      case EXCESS_RELIEF_PAID =>
        formatDateDMY(periodStartDate) + " to " + formatDateDMY(periodEndDate)
      case EXCESS_RELIEF_INTEREST =>
        formatDateDMY(periodStartDate) + " to " + formatDateDMY(periodEndDate)
      case _ => ""
    }
  }

  def setPeriod(chargeType: SchemeFSChargeType, periodStartDate: Option[LocalDate], periodEndDate: Option[LocalDate]): String = {
    chargeType match {
      case PSS_AFT_RETURN | PSS_OTC_AFT_RETURN | PSS_AFT_RETURN_INTEREST | PSS_OTC_AFT_RETURN_INTEREST
           | AFT_MANUAL_ASST | AFT_MANUAL_ASST_INTEREST | OTC_MANUAL_ASST | OTC_MANUAL_ASST_INTEREST =>
        "Quarter: " + formatStartDate(periodStartDate) + " to " + formatDateDMY(periodEndDate)
      case PSS_CHARGE | PSS_CHARGE_INTEREST | CONTRACT_SETTLEMENT | CONTRACT_SETTLEMENT_INTEREST =>
        "Period: " + formatStartDate(periodStartDate) + " to " + formatDateDMY(periodEndDate)
      case EXCESS_RELIEF_PAID =>
        "Tax year: " + formatDateDMY(periodStartDate) + " to " + formatDateDMY(periodEndDate)
      case EXCESS_RELIEF_INTEREST =>
        "Tax period: " + formatDateDMY(periodStartDate) + " to " + formatDateDMY(periodEndDate)
      case _ => ""
    }
  }

  private def htmlStatus(data: FinancialPaymentAndChargesDetails)(implicit messages: Messages): HtmlContent = {
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


  private def mapToTableNew(allPayments: Seq[FinancialPaymentAndChargesDetails],
                         chargeDetailsFilter: ChargeDetailsFilter,
                         config: FrontendAppConfig
                        )(implicit messages: Messages): Table = {

    val head = Seq(
      HeadCell(Text(Messages("")), classes = "govuk-!-width-one-half"),
      HeadCell(Text(Messages("paymentsAndCharges.dateDue.table")), classes = "govuk-!-font-weight-bold table-nowrap"),
      HeadCell(Text(Messages("paymentsAndCharges.chargeDetails.originalChargeAmount.new")), classes = "govuk-!-font-weight-bold table-nowrap"),
      HeadCell(Text(Messages("paymentsAndCharges.paymentDue.table")), classes = "govuk-!-font-weight-bold table-nowrap"),
      HeadCell(Text(Messages("paymentsAndCharges.interestAccruing.table")), classes = "govuk-!-font-weight-bold table-nowrap")
    )

    val rows = allPayments.map { data =>

      val htmlChargeType = (chargeDetailsFilter, data.submittedDate) match {
        case (All, Some(dateValue)) => HtmlContent(
          s"<a id=${data.id} class=govuk-link href=" +
            s"${data.redirectUrl}>" +
            s"${data.chargeType} " +
            s"<span class=govuk-visually-hidden>${data.visuallyHiddenText}</span></a>" +
            s"${data.chargeReference}</br>" +
            s"$dateValue"
          )
        case (All, None) => HtmlContent(
          s"<a id=${data.id} class=govuk-link href=" +
            s"${data.redirectUrl}>" +
            s"${data.chargeType} " +
            s"<span class=govuk-visually-hidden>${data.visuallyHiddenText}</span></a>" +
            s"${data.chargeReference}"
        )
        case _ =>
          HtmlContent(
            s"<a id=${data.id} class=govuk-link href=" +
              s"${data.redirectUrl}>" +
              s"${data.chargeType} " +
              s"<span class=govuk-visually-hidden>${data.visuallyHiddenText}</span></a>" +
              s"<p class=govuk-hint>${data.chargeReference}</br>" +
              s"${data.period}"
          )
      }

      Seq(
        TableRow(htmlChargeType, classes = "govuk-!-width-one-third"),
        TableRow(Text(s"${formatDateDMY(data.dateDue)}"), classes = "govuk-!-padding-right-7, table-nowrap"),
        if (data.originalChargeAmount.isEmpty) {
          TableRow(HtmlContent(s"""<span class=govuk-visually-hidden>${messages("paymentsAndCharges.chargeDetails.visuallyHiddenText")}</span>"""))
        } else {
          TableRow(Text(data.originalChargeAmount), classes = "govuk-table__cell govuk-!-padding-right-7 table-nowrap")
        },
        TableRow(Text(data.paymentDue), classes = "govuk-table__cell govuk-!-padding-right-7 table-nowrap"),
        TableRow(Text(s"${formatBigDecimal(data.accruedInterestTotal)}"), classes = "govuk-table__cell govuk-table__cell--numeric table-nowrap")
      )
    }

    Table(
      head = Some(head),
      rows = rows.map(_.toSeq),
      attributes = Map("role" -> "table")
    )
  }

  private def mapToTable(allPayments: Seq[FinancialPaymentAndChargesDetails], chargeDetailsFilter: ChargeDetailsFilter)
                        (implicit messages: Messages): Table = {

    val head = Seq(
      HeadCell(Text(Messages("paymentsAndCharges.chargeType.table")), classes = "govuk-!-width-one-half"),
      HeadCell(Text(Messages("paymentsAndCharges.chargeReference.table")), classes = "govuk-!-font-weight-bold"),
      HeadCell(Text(Messages("paymentsAndCharges.chargeDetails.originalChargeAmount")), classes = "govuk-!-font-weight-bold"),
      HeadCell(Text(Messages("paymentsAndCharges.paymentDue.table")), classes = "govuk-!-font-weight-bold"),
      HeadCell(HtmlContent(
        s"<span class='govuk-visually-hidden'>${messages("paymentsAndCharges.chargeDetails.paymentStatus")}</span>"
      ))
    )

    val rows = allPayments.map { data =>

      val htmlChargeType = (chargeDetailsFilter, data.submittedDate) match {
        case (All, Some(dateValue)) => HtmlContent(
          s"<a id=${data.id} class=govuk-link href=" +
            s"${data.redirectUrl}>" +
            s"${data.chargeType} " +
            s"<span class=govuk-visually-hidden>${data.visuallyHiddenText}</span> </a>" +
            s"<p class=govuk-hint>" +
            s"$dateValue</p>")
        case (All, None) => HtmlContent(
          s"<a id=${data.id} class=govuk-link href=" +
            s"${data.redirectUrl}>" +
            s"${data.chargeType} " +
            s"<span class=govuk-visually-hidden>${data.visuallyHiddenText}</span> </a>")
        case _ =>
          HtmlContent(
            s"<a id=${data.id} class=govuk-link href=" +
              s"${data.redirectUrl}>" +
              s"${data.chargeType} " +
              s"<span class=govuk-visually-hidden>${data.visuallyHiddenText}</span> </a>" +
              s"<p class=govuk-hint>" +
              s"${data.period}</p>")
      }

      Seq(
        TableRow(htmlChargeType, classes = "govuk-!-width-one-third"),
        TableRow(Text(s"${data.chargeReference}"), classes = "govuk-!-padding-right-7"),
        if (data.originalChargeAmount.isEmpty) {
          TableRow(HtmlContent(s"""<span class=govuk-visually-hidden>${messages("paymentsAndCharges.chargeDetails.visuallyHiddenText")}</span>"""))
        } else {
          TableRow(Text(data.originalChargeAmount), classes = "govuk-!-padding-right-7 table-nowrap")
        },
        TableRow(Text(data.paymentDue), classes = "govuk-!-padding-right-5 table-nowrap"),
        TableRow(htmlStatus(data), classes = "")
      )
    }


    Table(
      head = Some(head),
      rows = rows.map(_.toSeq),
      attributes = Map("role" -> "table")
    )
  }

  private def formatBigDecimal(value: BigDecimal): String = {
    if (value == 0) "" else f"$value%.2f"
  }

  def getChargeDetailsForSelectedCharge(schemeFSDetail: SchemeFSDetail, journeyType: ChargeDetailsFilter, submittedDate: Option[String])
                                       (implicit messages: Messages): Seq[SummaryListRow] = {
    dateSubmittedRow(schemeFSDetail.chargeType, submittedDate) ++ chargeReferenceRow(schemeFSDetail) ++ originalAmountChargeDetailsRow(schemeFSDetail) ++
      clearingChargeDetailsRow(schemeFSDetail.documentLineItemDetails) ++
      stoodOverAmountChargeDetailsRow(schemeFSDetail) ++ totalAmountDueChargeDetailsRow(schemeFSDetail, journeyType)
  }

  def getChargeDetailsForSelectedChargeV2(schemeFSDetail: SchemeFSDetail, schemeDetails: SchemeDetails, isClearedCharge: Boolean = false)
                                         (implicit messages: Messages): Seq[SummaryListRow] = {
    pstrRow(schemeDetails) ++ chargeReferenceRow(schemeFSDetail) ++ getTaxPeriod(schemeFSDetail, isClearedCharge)
  }

  def chargeAmountDetailsRowsV2(schemeFSDetail: SchemeFSDetail)(implicit messages: Messages): Table = {

    val headRow = scala.collection.immutable.Seq(
      HeadCell(Text(Messages("pension.scheme.chargeAmount.label.new"))),
      HeadCell(Text("")),
      HeadCell(Text(s"${FormatHelper.formatCurrencyAmountAsString(schemeFSDetail.totalAmount)}"), classes = "govuk-!-font-weight-regular govuk-!-text-align-right")
    )

    val rows = schemeFSDetail.documentLineItemDetails.map { documentLineItemDetail => {
        if (documentLineItemDetail.clearedAmountItem > 0) {
          getClearingDetailLabelV2(documentLineItemDetail) match {
            case Some(clearingDetailsValue) =>
              scala.collection.immutable.Seq(
                TableRow(clearingDetailsValue, classes = "govuk-!-font-weight-bold govuk-!-width-one-half"),
                TableRow(Text(getChargeDateV2(documentLineItemDetail))),
                TableRow(Text(s"${FormatHelper.formatCurrencyAmountAsString(documentLineItemDetail.clearedAmountItem)}"),
                  classes = "govuk-!-font-weight-regular govuk-!-text-align-right")
              )
            case _ => Seq()
          }
        } else {
          Seq()
        }
      }
    }

    val stoodOverAmountRow = if (schemeFSDetail.stoodOverAmount > 0) {
      scala.collection.immutable.Seq(scala.collection.immutable.Seq(
        TableRow(Text(Messages("paymentsAndCharges.chargeDetails.stoodOverAmount")), classes = "govuk-!-font-weight-bold"),
        TableRow(Text("")),
        TableRow(Text(s"${FormatHelper.formatCurrencyAmountAsString(schemeFSDetail.stoodOverAmount)}"),
          classes = "govuk-!-font-weight-regular govuk-!-text-align-right"))
      )
    } else {
      Seq(Seq())
    }

    Table(head = Some(headRow), rows = rows ++ stoodOverAmountRow, attributes = Map("role" -> "table"))
  }

  private def pstrRow(schemeDetails: SchemeDetails)(implicit messages: Messages): Seq[SummaryListRow] = {
    Seq(
      SummaryListRow(
        key = Key(Text(Messages("pension.scheme.tax.reference.new")), classes = "govuk-!-padding-left-0 govuk-!-width-one-half"),
        value = Value(Text(s"${schemeDetails.pstr}"), classes = "govuk-!-width-one-half")
      ))
  }

  private def getTaxPeriod(schemeFSDetail: SchemeFSDetail, isClearedCharge: Boolean = false)(implicit messages: Messages): Seq[SummaryListRow] = {
    val formattedStartDate = if (isClearedCharge) {
      formatStartDate(schemeFSDetail.periodStartDate)
    } else {
      formatDateDMY(schemeFSDetail.periodStartDate)
    }

    Seq(
      SummaryListRow(
        key = Key(Text(Messages("pension.scheme.interest.tax.period.new")), classes = "govuk-!-padding-left-0 govuk-!-width-one-half"),
        value = Value(Text(formattedStartDate + " to " +
          formatDateDMY(schemeFSDetail.periodEndDate)), classes = "govuk-!-width-one-half")
      ))
  }

  private def dateSubmittedRow(chargeType: SchemeFSChargeType, submittedDate: Option[String])(implicit messages: Messages): Seq[SummaryListRow] = {
    (chargeType, submittedDate) match {
      case (PSS_AFT_RETURN | PSS_OTC_AFT_RETURN, Some(date)) =>
        Seq(
          SummaryListRow(
            key = Key(Text(Messages("financialPaymentsAndCharges.dateSubmitted")), classes = "govuk-!-padding-left-0 govuk-!-width-one-half"),
            value = Value(Text(formatDateDMYString(date)), classes = "govuk-!-width-one-quarter")
          ))
      case _ =>
        Nil
    }

  }

  private def chargeReferenceRow(schemeFSDetail: SchemeFSDetail)(implicit messages: Messages): Seq[SummaryListRow] = {
    Seq(
      SummaryListRow(
        key = Key(Text(Messages("financialPaymentsAndCharges.chargeReference")), classes = "govuk-!-padding-left-0 govuk-!-width-one-half"),
        value = Value(Text(schemeFSDetail.chargeReference), classes = "govuk-!-width-one-quarter")
      ))
  }

  private def originalAmountChargeDetailsRow(schemeFSDetail: SchemeFSDetail)(implicit messages: Messages): Seq[SummaryListRow] = {
    Seq(
      SummaryListRow(
        key = Key(Text(Messages("paymentsAndCharges.chargeDetails.originalChargeAmount")), classes = "govuk-!-padding-left-0 govuk-!-width-one-half"),
        value = Value(Text(s"${formatCurrencyAmountAsString(schemeFSDetail.totalAmount)}"), classes = "govuk-!-width-one-quarter")
      ))
  }

  private def stoodOverAmountChargeDetailsRow(schemeFSDetail: SchemeFSDetail)(implicit messages: Messages): Seq[SummaryListRow] = {
    if (schemeFSDetail.stoodOverAmount > 0) {
      Seq(
        SummaryListRow(
          key = Key(Text(Messages("paymentsAndCharges.chargeDetails.stoodOverAmount")), classes = "govuk-!-padding-left-0 govuk-!-width-one-half"),
          value = Value(Text(s"-${formatCurrencyAmountAsString(schemeFSDetail.stoodOverAmount)}"), classes = "govuk-!-width-one-quarter")
        ))
    } else {
      Nil
    }
  }

  private def totalAmountDueChargeDetailsRow(schemeFSDetail: SchemeFSDetail, journeyType: ChargeDetailsFilter)(implicit messages: Messages): Seq[SummaryListRow] = {
    val amountDueKey: Content = (schemeFSDetail.dueDate, schemeFSDetail.amountDue > 0) match {
      case (Some(date), true) =>
        Text(Messages(s"financialPaymentsAndCharges.paymentDue.${journeyType.toString}.dueDate", date.format(dateFormatterDMY)))
      case _ =>
        Text(Messages("financialPaymentsAndCharges.paymentDue.noDueDate"))
    }
    if (schemeFSDetail.totalAmount > 0) {
      Seq(
        SummaryListRow(
          key = Key(
            content = amountDueKey,
            classes = "govuk-!-padding-left-0 govuk-!-width-one-half"
          ),
          value = Value(
            content = Text(s"${formatCurrencyAmountAsString(schemeFSDetail.amountDue)}"),
            classes = "govuk-!-padding-left-0 govuk-!-width--one-half govuk-!-font-weight-bold"
          ),
          actions = None
        ))
    } else {
      Nil
    }
  }

  private def clearingChargeDetailsRow(documentLineItemDetails: Seq[DocumentLineItemDetail])(implicit messages: Messages): Seq[SummaryListRow] = {
    documentLineItemDetails.flatMap {
      documentLineItemDetail => {
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
  }

  private def saveAndReturnPaymentsCache(loggedInId: String, srn: String)
                                        (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[PaymentsCache] =
    for {
      schemeDetails <- schemeService.retrieveSchemeDetails(loggedInId, srn, "srn")
      schemeFSDetail <- fsConnector.getSchemeFS(schemeDetails.pstr)
      paymentsCache = PaymentsCache(loggedInId, srn, schemeDetails, schemeFSDetail.seqSchemeFSDetail,schemeFSDetail.inhibitRefundSignal)
      _ <- financialInfoCacheConnector.save(Json.toJson(paymentsCache))
    } yield paymentsCache

  def getPaymentsFromCache(loggedInId: String, srn: String)(implicit ec: ExecutionContext, hc: HeaderCarrier): Future[PaymentsCache] =
    financialInfoCacheConnector.fetch flatMap {
      case Some(jsValue) =>
        val cacheAuthenticated: PaymentsCache => Boolean = value => value.loggedInId == loggedInId && value.srn == srn
        jsValue.validate[PaymentsCache] match {
          case JsSuccess(value, _) if cacheAuthenticated(value) => Future.successful(value)
          case _ => saveAndReturnPaymentsCache(loggedInId, srn)
        }
      case _ => saveAndReturnPaymentsCache(loggedInId, srn)
    }

  def getPaymentsForJourney(loggedInId: String, srn: String, journeyType: ChargeDetailsFilter)
                           (implicit ec: ExecutionContext, hc: HeaderCarrier): Future[PaymentsCache] =
    getPaymentsFromCache(loggedInId, srn).map { cache =>

      logger.warn(s"schemeFSDetail had ${cache.schemeFSDetail.length} values. For journey type: ${journeyType}")
      journeyType match {
        case Overdue => cache.copy(schemeFSDetail = getOverdueCharges(cache.schemeFSDetail))
        case Upcoming => cache.copy(schemeFSDetail = extractUpcomingCharges(cache.schemeFSDetail))
        case History => cache.copy(schemeFSDetail = getClearedCharges(cache.schemeFSDetail))
        case _ => cache
      }
    }

  private def getClearingDetailLabel(documentLineItemDetail: DocumentLineItemDetail)(implicit messages: Messages): Option[Text] = {
    (documentLineItemDetail.clearingReason, documentLineItemDetail.paymDateOrCredDueDate, documentLineItemDetail.clearingDate) match {
      case (Some(clearingReason), Some(paymDateOrCredDueDate), _) =>
        clearingReason match {
          case CLEARED_WITH_PAYMENT => Some(Text(Messages("financialPaymentsAndCharges.clearingReason.c1", formatDateDMY(paymDateOrCredDueDate))))
          case CLEARED_WITH_DELTA_CREDIT => Some(Text(Messages("financialPaymentsAndCharges.clearingReason.c2", formatDateDMY(paymDateOrCredDueDate))))
          case REPAYMENT_TO_THE_CUSTOMER => Some(Text(Messages("financialPaymentsAndCharges.clearingReason.c3", formatDateDMY(paymDateOrCredDueDate))))
          case WRITTEN_OFF => Some(Text(Messages("financialPaymentsAndCharges.clearingReason.c4", formatDateDMY(paymDateOrCredDueDate))))
          case TRANSFERRED_TO_ANOTHER_ACCOUNT => Some(Text(Messages("financialPaymentsAndCharges.clearingReason.c5", formatDateDMY(paymDateOrCredDueDate))))
          case OTHER_REASONS => Some(Text(Messages("financialPaymentsAndCharges.clearingReason.c6", formatDateDMY(paymDateOrCredDueDate))))
        }
      case (Some(clearingReason), None, Some(clearingDate)) =>
        clearingReason match {
          case CLEARED_WITH_PAYMENT => Some(Text(Messages("financialPaymentsAndCharges.clearingReason.c1", formatDateDMY(clearingDate))))
          case CLEARED_WITH_DELTA_CREDIT => Some(Text(Messages("financialPaymentsAndCharges.clearingReason.c2", formatDateDMY(clearingDate))))
          case REPAYMENT_TO_THE_CUSTOMER => Some(Text(Messages("financialPaymentsAndCharges.clearingReason.c3", formatDateDMY(clearingDate))))
          case WRITTEN_OFF => Some(Text(Messages("financialPaymentsAndCharges.clearingReason.c4", formatDateDMY(clearingDate))))
          case TRANSFERRED_TO_ANOTHER_ACCOUNT => Some(Text(Messages("financialPaymentsAndCharges.clearingReason.c5", formatDateDMY(clearingDate))))
          case OTHER_REASONS => Some(Text(Messages("financialPaymentsAndCharges.clearingReason.c6", formatDateDMY(clearingDate))))
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

  private def getClearingDetailLabelV2(documentLineItemDetail: DocumentLineItemDetail)(implicit messages: Messages): Option[Text] = {
    (documentLineItemDetail.clearingReason, documentLineItemDetail.paymDateOrCredDueDate, documentLineItemDetail.clearingDate) match {
      case (Some(clearingReason), _, _) =>
        clearingReason match {
          case CLEARED_WITH_PAYMENT => Some(Text(Messages("pension.scheme.financialPaymentsAndCharges.clearingReason.c1.new")))
          case CLEARED_WITH_DELTA_CREDIT => Some(Text(Messages("pension.scheme.financialPaymentsAndCharges.clearingReason.c2.new")))
          case REPAYMENT_TO_THE_CUSTOMER => Some(Text(Messages("pension.scheme.financialPaymentsAndCharges.clearingReason.c3.new")))
          case WRITTEN_OFF => Some(Text(Messages("pension.scheme.financialPaymentsAndCharges.clearingReason.c4.new")))
          case TRANSFERRED_TO_ANOTHER_ACCOUNT => Some(Text(Messages("pension.scheme.financialPaymentsAndCharges.clearingReason.c5.new")))
          case OTHER_REASONS => Some(Text(Messages("pension.scheme.financialPaymentsAndCharges.clearingReason.c6.new")))
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

  private def getChargeDateV2(documentLineItemDetail: DocumentLineItemDetail): String = {
    (documentLineItemDetail.paymDateOrCredDueDate, documentLineItemDetail.clearingDate) match {
      case (Some(paymDateOrCredDueDate), _) =>
        formatDateDMY(paymDateOrCredDueDate)
      case (None, Some(clearingDate)) =>
        formatDateDMY(clearingDate)
      case _ => ""
    }
  }

  private def filteredSeqSchemeFsDetailNoCredits(seqSchemeFSDetail: Seq[SchemeFSDetail]): Seq[SchemeFSDetail]= {
    seqSchemeFSDetail.filter { schemeFSDetail =>
      !isCreditChargeType(schemeFSDetail.chargeType)
    }
  }

  private def displayChargeReference(chargeReference: String)(implicit messages: Messages): String = {
    if(chargeReference == "") messages("paymentsAndCharges.chargeReference.toBeAssigned") else chargeReference
  }
}

case class PaymentsCache(loggedInId: String,
                         srn: String,
                         schemeDetails: SchemeDetails,
                         schemeFSDetail: Seq[SchemeFSDetail],
                         inhibitRefundSignal: Boolean = false)

object PaymentsCache {
  implicit val format: OFormat[PaymentsCache] = Json.format[PaymentsCache]
}
