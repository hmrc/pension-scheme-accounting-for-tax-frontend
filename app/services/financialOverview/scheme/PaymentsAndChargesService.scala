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
import models.ChargeDetailsFilter.{All, Overdue, Upcoming}
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
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.viewmodels.SummaryList.{Key, Row, Value}
import uk.gov.hmrc.viewmodels.Text.Literal
import uk.gov.hmrc.viewmodels.{Content, Html, SummaryList, _}
import utils.DateHelper
import utils.DateHelper._
import viewmodels.Table
import viewmodels.Table.Cell

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

  private def htmlStatus(data: FinancialPaymentAndChargesDetails)(implicit messages: Messages): Html = {
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
    Html(s"<span class='$classes'>$content</span>")
  }


  private def mapToTableNew(allPayments: Seq[FinancialPaymentAndChargesDetails],
                         chargeDetailsFilter: ChargeDetailsFilter,
                         config: FrontendAppConfig
                        )(implicit messages: Messages): Table = {

    val head = Seq(
      Cell(msg"", classes = Seq("govuk-!-width-one-half")),
      Cell(msg"paymentsAndCharges.dateDue.table", classes = Seq("govuk-!-font-weight-bold table-nowrap")),
      Cell(msg"paymentsAndCharges.chargeDetails.originalChargeAmount.new", classes = Seq("govuk-!-font-weight-bold table-nowrap")),
      Cell(msg"paymentsAndCharges.paymentDue.table", classes = Seq("govuk-!-font-weight-bold table-nowrap")),
      Cell(msg"paymentsAndCharges.interestAccruing.table", classes = Seq("govuk-!-font-weight-bold table-nowrap"))
    )

    val rows = allPayments.map { data =>

      val htmlChargeType = (chargeDetailsFilter, data.submittedDate) match {
        case (All, Some(dateValue)) => Html(
          s"<a id=${data.id} class=govuk-link href=" +
            s"${data.redirectUrl}>" +
            s"${data.chargeType} " +
            s"<span class=govuk-visually-hidden>${data.visuallyHiddenText}</span></a>" +
            s"${data.chargeReference}</br>" +
            s"$dateValue"
          )
        case (All, None) => Html(
          s"<a id=${data.id} class=govuk-link href=" +
            s"${data.redirectUrl}>" +
            s"${data.chargeType} " +
            s"<span class=govuk-visually-hidden>${data.visuallyHiddenText}</span></a>" +
            s"${data.chargeReference}"
        )
        case _ =>
          Html(
            s"<a id=${data.id} class=govuk-link href=" +
              s"${data.redirectUrl}>" +
              s"${data.chargeType} " +
              s"<span class=govuk-visually-hidden>${data.visuallyHiddenText}</span></a>" +
              s"<p class=govuk-hint>${data.chargeReference}</br>" +
              s"${data.period}"
          )
      }

      Seq(
        Cell(htmlChargeType, classes = Seq("govuk-!-width-one-third")),
        Cell(Literal(s"${formatDateDMY(data.dateDue)}"), classes = Seq("govuk-!-padding-right-7", "table-nowrap")),
        if (data.originalChargeAmount.isEmpty) {
          Cell(Html(s"""<span class=govuk-visually-hidden>${messages("paymentsAndCharges.chargeDetails.visuallyHiddenText")}</span>"""))
        } else {
          Cell(Literal(data.originalChargeAmount), classes = Seq("govuk-table__cell", "govuk-!-padding-right-7", "table-nowrap"))
        },
        Cell(Literal(data.paymentDue), classes = Seq("govuk-table__cell", "govuk-!-padding-right-7", "table-nowrap")),
        Cell(Literal(s"${formatBigDecimal(data.accruedInterestTotal)}"), classes = Seq("govuk-table__cell", "govuk-table__cell--numeric", "table-nowrap"))
      )
    }

    Table(
      head = head,
      rows = rows,
      attributes = Map("role" -> "table")
    )
  }

  private def mapToTable(allPayments: Seq[FinancialPaymentAndChargesDetails], chargeDetailsFilter: ChargeDetailsFilter)
                        (implicit messages: Messages): Table = {

    val head = Seq(
      Cell(msg"paymentsAndCharges.chargeType.table", classes = Seq("govuk-!-width-one-half")),
      Cell(msg"paymentsAndCharges.chargeReference.table", classes = Seq("govuk-!-font-weight-bold")),
      Cell(msg"paymentsAndCharges.chargeDetails.originalChargeAmount", classes = Seq("govuk-!-font-weight-bold")),
      Cell(msg"paymentsAndCharges.paymentDue.table", classes = Seq("govuk-!-font-weight-bold")),
      Cell(Html(
        s"<span class='govuk-visually-hidden'>${messages("paymentsAndCharges.chargeDetails.paymentStatus")}</span>"
      ))
    )

    val rows = allPayments.map { data =>

      val htmlChargeType = (chargeDetailsFilter, data.submittedDate) match {
        case (All, Some(dateValue)) => Html(
          s"<a id=${data.id} class=govuk-link href=" +
            s"${data.redirectUrl}>" +
            s"${data.chargeType} " +
            s"<span class=govuk-visually-hidden>${data.visuallyHiddenText}</span> </a>" +
            s"<p class=govuk-hint>" +
            s"$dateValue</p>")
        case (All, None) => Html(
          s"<a id=${data.id} class=govuk-link href=" +
            s"${data.redirectUrl}>" +
            s"${data.chargeType} " +
            s"<span class=govuk-visually-hidden>${data.visuallyHiddenText}</span> </a>")
        case _ =>
          Html(
            s"<a id=${data.id} class=govuk-link href=" +
              s"${data.redirectUrl}>" +
              s"${data.chargeType} " +
              s"<span class=govuk-visually-hidden>${data.visuallyHiddenText}</span> </a>" +
              s"<p class=govuk-hint>" +
              s"${data.period}</p>")
      }

      Seq(
        Cell(htmlChargeType, classes = Seq("govuk-!-width-one-third")),
        Cell(Literal(s"${data.chargeReference}"), classes = Seq("govuk-!-padding-right-7")),
        if (data.originalChargeAmount.isEmpty) {
          Cell(Html(s"""<span class=govuk-visually-hidden>${messages("paymentsAndCharges.chargeDetails.visuallyHiddenText")}</span>"""))
        } else {
          Cell(Literal(data.originalChargeAmount), classes = Seq("govuk-!-padding-right-7 table-nowrap"))
        },
        Cell(Literal(data.paymentDue), classes = Seq("govuk-!-padding-right-5 table-nowrap")),
        Cell(htmlStatus(data), classes = Nil)
      )
    }


    Table(
      head = head,
      rows = rows,
      attributes = Map("role" -> "table")
    )
  }

  private def formatBigDecimal(value: BigDecimal): String = {
    if (value == 0) "" else f"$value%.2f"
  }

  def getChargeDetailsForSelectedCharge(schemeFSDetail: SchemeFSDetail, journeyType: ChargeDetailsFilter, submittedDate: Option[String])
  : Seq[SummaryList.Row] = {
    dateSubmittedRow(schemeFSDetail.chargeType, submittedDate) ++ chargeReferenceRow(schemeFSDetail) ++ originalAmountChargeDetailsRow(schemeFSDetail) ++
      clearingChargeDetailsRow(schemeFSDetail.documentLineItemDetails) ++
      stoodOverAmountChargeDetailsRow(schemeFSDetail) ++ totalAmountDueChargeDetailsRow(schemeFSDetail, journeyType)
  }

  def getChargeDetailsForSelectedChargeV2(schemeFSDetail: SchemeFSDetail, schemeDetails: SchemeDetails, journeyType: ChargeDetailsFilter, submittedDate: Option[String])
  : Seq[SummaryList.Row] = {
    pstrRow(schemeDetails) ++ chargeReferenceRow(schemeFSDetail) ++ getTaxPeriod(schemeFSDetail)
  }

  def chargeAmountDetailsRowsV2(schemeFSDetail: SchemeFSDetail): Table = {
    val headRow = scala.collection.immutable.Seq(
      Cell(msg"pension.scheme.chargeAmount.label.new"),
      Cell(Literal("")),
      Cell(Literal(s"${FormatHelper.formatCurrencyAmountAsString(schemeFSDetail.totalAmount)}"),
        classes = scala.collection.immutable.Seq("govuk-!-font-weight-regular", "govuk-!-text-align-right"))
    )

    val rows = schemeFSDetail.documentLineItemDetails map {
      documentLineItemDetail => {
        if (documentLineItemDetail.clearedAmountItem > 0) {
          getClearingDetailLabelV2(documentLineItemDetail) match {
            case Some(clearingDetailsValue) =>
              scala.collection.immutable.Seq(
                Cell(clearingDetailsValue, classes = scala.collection.immutable.Seq("govuk-!-font-weight-bold", "govuk-!-width-one-half")),
                Cell(Literal(getChargeDateV2(documentLineItemDetail))),
                Cell(Literal(s"${FormatHelper.formatCurrencyAmountAsString(documentLineItemDetail.clearedAmountItem)}"),
                  classes = scala.collection.immutable.Seq("govuk-!-font-weight-regular", "govuk-!-text-align-right"))
              )
            case _ => scala.collection.immutable.Seq()
          }
        } else {
          scala.collection.immutable.Seq()
        }
      }
    }

    val stoodOverAmountRow = if (schemeFSDetail.stoodOverAmount > 0) {
      scala.collection.immutable.Seq(scala.collection.immutable.Seq(
        Cell(msg"paymentsAndCharges.chargeDetails.stoodOverAmount", classes = scala.collection.immutable.Seq("govuk-!-font-weight-bold")),
        Cell(Literal("")),
        Cell(Literal(s"${FormatHelper.formatCurrencyAmountAsString(schemeFSDetail.stoodOverAmount)}"),
          classes = scala.collection.immutable.Seq("govuk-!-font-weight-regular", "govuk-!-text-align-right"))
      ))
    } else {
      scala.collection.immutable.Seq(scala.collection.immutable.Seq())
    }

    Table(head = headRow, rows = rows ++ stoodOverAmountRow, attributes = Map("role" -> "table"))
  }

  private def pstrRow(schemeDetails: SchemeDetails): Seq[SummaryList.Row] = {
    Seq(
      Row(
        key = Key(msg"pension.scheme.tax.reference.new", classes = Seq("govuk-!-padding-left-0", "govuk-!-width-one-half")),
        value = Value(Literal(s"${schemeDetails.pstr}"), classes = Seq("govuk-!-width-one-half"))
      ))
  }

  private def getTaxPeriod(schemeFSDetail: SchemeFSDetail): Seq[SummaryList.Row] = {
    Seq(
      Row(
        key = Key(msg"pension.scheme.interest.tax.period.new", classes = Seq("govuk-!-padding-left-0", "govuk-!-width-one-half")),
        value = Value(Literal(formatDateDMY(schemeFSDetail.periodStartDate) + " to " +
          formatDateDMY(schemeFSDetail.periodEndDate)), classes = Seq("govuk-!-width-one-half"))
      ))
  }

  private def dateSubmittedRow(chargeType: SchemeFSChargeType, submittedDate: Option[String]): Seq[SummaryList.Row] = {
    (chargeType, submittedDate) match {
      case (PSS_AFT_RETURN | PSS_OTC_AFT_RETURN, Some(date)) =>
        Seq(
          Row(
            key = Key(
              content = msg"financialPaymentsAndCharges.dateSubmitted",
              classes = Seq("govuk-!-padding-left-0", "govuk-!-width-one-half")
            ),
            value = Value(
              content = Literal(formatDateDMYString(date)),
              classes =
                Seq("govuk-!-width-one-quarter")
            ),
            actions = Nil
          ))
      case _ =>
        Nil
    }

  }

  private def chargeReferenceRow(schemeFSDetail: SchemeFSDetail): Seq[SummaryList.Row] = {
    Seq(
      Row(
        key = Key(
          content = msg"financialPaymentsAndCharges.chargeReference",
          classes = Seq("govuk-!-padding-left-0", "govuk-!-width-one-half")
        ),
        value = Value(
          content = Literal(schemeFSDetail.chargeReference),
          classes =
            Seq("govuk-!-width-one-quarter")
        ),
        actions = Nil
      ))
  }

  private def originalAmountChargeDetailsRow(schemeFSDetail: SchemeFSDetail): Seq[SummaryList.Row] = {
    Seq(
      Row(
        key = Key(
          content = msg"paymentsAndCharges.chargeDetails.originalChargeAmount",
          classes = Seq("govuk-!-padding-left-0", "govuk-!-width-one-half")
        ),
        value = Value(
          content = Literal(s"${formatCurrencyAmountAsString(schemeFSDetail.totalAmount)}"),
          classes =
            Seq("govuk-!-width-one-quarter")
        ),
        actions = Nil
      ))
  }

  private def stoodOverAmountChargeDetailsRow(schemeFSDetail: SchemeFSDetail): Seq[SummaryList.Row] =
    if (schemeFSDetail.stoodOverAmount > 0) {
      Seq(
        Row(
          key = Key(
            content = msg"paymentsAndCharges.chargeDetails.stoodOverAmount",
            classes = Seq("govuk-!-padding-left-0", "govuk-!-width-one-half")
          ),
          value = Value(
            content = Literal(s"-${formatCurrencyAmountAsString(schemeFSDetail.stoodOverAmount)}"),
            classes = Seq("govuk-!-width-one-quarter")
          ),
          actions = Nil
        ))
    } else {
      Nil
    }

  private def totalAmountDueChargeDetailsRow(schemeFSDetail: SchemeFSDetail, journeyType: ChargeDetailsFilter): Seq[SummaryList.Row] = {
    val amountDueKey: Content = (schemeFSDetail.dueDate, schemeFSDetail.amountDue > 0) match {
      case (Some(date), true) =>
        msg"financialPaymentsAndCharges.paymentDue.${journeyType.toString}.dueDate".withArgs(date.format(dateFormatterDMY))
      case _ =>
        msg"financialPaymentsAndCharges.paymentDue.noDueDate"
    }
    if (schemeFSDetail.totalAmount > 0) {
      Seq(
        Row(
          key = Key(
            content = amountDueKey,
            classes = Seq("govuk-!-padding-left-0", "govuk-!-width-one-half")
          ),
          value = Value(
            content = Literal(s"${formatCurrencyAmountAsString(schemeFSDetail.amountDue)}"),
            classes = Seq("govuk-!-padding-left-0", "govuk-!-width--one-half", "govuk-!-font-weight-bold")
          ),
          actions = Nil
        ))
    } else {
      Nil
    }
  }

  private def clearingChargeDetailsRow(documentLineItemDetails: Seq[DocumentLineItemDetail]): Seq[SummaryList.Row] = {
    documentLineItemDetails.flatMap {
      documentLineItemDetail => {
        if (documentLineItemDetail.clearedAmountItem > 0) {
          getClearingDetailLabel(documentLineItemDetail) match {
            case Some(clearingDetailsValue) =>
              Seq(
                Row(
                  key = Key(
                    content = clearingDetailsValue,
                    classes = Seq("govuk-!-padding-left-0", "govuk-!-width-one-half")
                  ),
                  value = Value(
                    content = Literal(s"-${formatCurrencyAmountAsString(documentLineItemDetail.clearedAmountItem)}"),
                    classes = Seq("govuk-!-width-one-quarter")
                  ),
                  actions = Nil
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
        case _ => cache
      }
    }

  private def getClearingDetailLabel(documentLineItemDetail: DocumentLineItemDetail): Option[Text.Message] = {
    (documentLineItemDetail.clearingReason, documentLineItemDetail.paymDateOrCredDueDate, documentLineItemDetail.clearingDate) match {
      case (Some(clearingReason), Some(paymDateOrCredDueDate), _) =>
        clearingReason match {
          case CLEARED_WITH_PAYMENT => Some(msg"financialPaymentsAndCharges.clearingReason.c1".withArgs(formatDateDMY(paymDateOrCredDueDate)))
          case CLEARED_WITH_DELTA_CREDIT => Some(msg"financialPaymentsAndCharges.clearingReason.c2".withArgs(formatDateDMY(paymDateOrCredDueDate)))
          case REPAYMENT_TO_THE_CUSTOMER => Some(msg"financialPaymentsAndCharges.clearingReason.c3".withArgs(formatDateDMY(paymDateOrCredDueDate)))
          case WRITTEN_OFF => Some(msg"financialPaymentsAndCharges.clearingReason.c4".withArgs(formatDateDMY(paymDateOrCredDueDate)))
          case TRANSFERRED_TO_ANOTHER_ACCOUNT => Some(msg"financialPaymentsAndCharges.clearingReason.c5".withArgs(formatDateDMY(paymDateOrCredDueDate)))
          case OTHER_REASONS => Some(msg"financialPaymentsAndCharges.clearingReason.c6".withArgs(formatDateDMY(paymDateOrCredDueDate)))
        }
      case (Some(clearingReason), None, Some(clearingDate)) =>
        clearingReason match {
          case CLEARED_WITH_PAYMENT => Some(msg"financialPaymentsAndCharges.clearingReason.c1".withArgs(formatDateDMY(clearingDate)))
          case CLEARED_WITH_DELTA_CREDIT => Some(msg"financialPaymentsAndCharges.clearingReason.c2".withArgs(formatDateDMY(clearingDate)))
          case REPAYMENT_TO_THE_CUSTOMER => Some(msg"financialPaymentsAndCharges.clearingReason.c3".withArgs(formatDateDMY(clearingDate)))
          case WRITTEN_OFF => Some(msg"financialPaymentsAndCharges.clearingReason.c4".withArgs(formatDateDMY(clearingDate)))
          case TRANSFERRED_TO_ANOTHER_ACCOUNT => Some(msg"financialPaymentsAndCharges.clearingReason.c5".withArgs(formatDateDMY(clearingDate)))
          case OTHER_REASONS => Some(msg"financialPaymentsAndCharges.clearingReason.c6".withArgs(formatDateDMY(clearingDate)))
        }
      case (Some(clearingReason), None, None) =>
        clearingReason match {
          case CLEARED_WITH_PAYMENT => Some(msg"financialPaymentsAndCharges.clearingReason.noClearingDate.c1")
          case CLEARED_WITH_DELTA_CREDIT => Some(msg"financialPaymentsAndCharges.clearingReason.noClearingDate.c2")
          case REPAYMENT_TO_THE_CUSTOMER => Some(msg"financialPaymentsAndCharges.clearingReason.noClearingDate.c3")
          case WRITTEN_OFF => Some(msg"financialPaymentsAndCharges.clearingReason.noClearingDate.c4")
          case TRANSFERRED_TO_ANOTHER_ACCOUNT => Some(msg"financialPaymentsAndCharges.noClearingDate.clearingReason.c5")
          case OTHER_REASONS => Some(msg"financialPaymentsAndCharges.clearingReason.noClearingDate.c6")
        }
      case _ => None
    }
  }

  private def getClearingDetailLabelV2(documentLineItemDetail: DocumentLineItemDetail): Option[Text.Message] = {
    (documentLineItemDetail.clearingReason, documentLineItemDetail.paymDateOrCredDueDate, documentLineItemDetail.clearingDate) match {
      case (Some(clearingReason), _, _) =>
        clearingReason match {
          case CLEARED_WITH_PAYMENT => Some(msg"pension.scheme.financialPaymentsAndCharges.clearingReason.c1.new")
          case CLEARED_WITH_DELTA_CREDIT => Some(msg"pension.scheme.financialPaymentsAndCharges.clearingReason.c2.new")
          case REPAYMENT_TO_THE_CUSTOMER => Some(msg"pension.scheme.financialPaymentsAndCharges.clearingReason.c3.new")
          case WRITTEN_OFF => Some(msg"pension.scheme.financialPaymentsAndCharges.clearingReason.c4.new")
          case TRANSFERRED_TO_ANOTHER_ACCOUNT => Some(msg"pension.scheme.financialPaymentsAndCharges.clearingReason.c5.new")
          case OTHER_REASONS => Some(msg"pension.scheme.financialPaymentsAndCharges.clearingReason.c6.new")
        }
      case (Some(clearingReason), None, None) =>
        clearingReason match {
          case CLEARED_WITH_PAYMENT => Some(msg"financialPaymentsAndCharges.clearingReason.noClearingDate.c1")
          case CLEARED_WITH_DELTA_CREDIT => Some(msg"financialPaymentsAndCharges.clearingReason.noClearingDate.c2")
          case REPAYMENT_TO_THE_CUSTOMER => Some(msg"financialPaymentsAndCharges.clearingReason.noClearingDate.c3")
          case WRITTEN_OFF => Some(msg"financialPaymentsAndCharges.clearingReason.noClearingDate.c4")
          case TRANSFERRED_TO_ANOTHER_ACCOUNT => Some(msg"financialPaymentsAndCharges.noClearingDate.clearingReason.c5")
          case OTHER_REASONS => Some(msg"financialPaymentsAndCharges.clearingReason.noClearingDate.c6")
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
