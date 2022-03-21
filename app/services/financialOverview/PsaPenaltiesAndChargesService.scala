/*
 * Copyright 2022 HM Revenue & Customs
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

package services.financialOverview

import connectors.cache.FinancialInfoCacheConnector
import connectors.{FinancialStatementConnector, MinimalConnector}
import helpers.FormatHelper
import helpers.FormatHelper.formatCurrencyAmountAsString
import models.ChargeDetailsFilter
import models.ChargeDetailsFilter.{Overdue, Upcoming}
import models.financialStatement.FSClearingReason.{CLEARED_WITH_DELTA_CREDIT, CLEARED_WITH_PAYMENT, OTHER_REASONS, REPAYMENT_TO_THE_CUSTOMER, TRANSFERRED_TO_ANOTHER_ACCOUNT, WRITTEN_OFF}
import models.financialStatement.PenaltyType.AccountingForTaxPenalties
import models.financialStatement.PsaFSChargeType.{AFT_12_MONTH_LPP, AFT_30_DAY_LPP, AFT_6_MONTH_LPP, AFT_DAILY_LFP, AFT_INITIAL_LFP, CONTRACT_SETTLEMENT, CONTRACT_SETTLEMENT_INTEREST, INTEREST_ON_CONTRACT_SETTLEMENT, OTC_12_MONTH_LPP, OTC_30_DAY_LPP, OTC_6_MONTH_LPP, PSS_INFO_NOTICE, PSS_PENALTY}
import models.financialStatement.{DocumentLineItemDetail, PenaltyType, PsaFSChargeType, PsaFSDetail}
import models.viewModels.financialOverview.PsaPaymentsAndChargesDetails
import models.viewModels.paymentsAndCharges.PaymentAndChargeStatus
import models.viewModels.paymentsAndCharges.PaymentAndChargeStatus.{InterestIsAccruing, PaymentOverdue}
import play.api.i18n.Messages
import play.api.libs.json.{JsSuccess, Json, OFormat}
import services.SchemeService
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.viewmodels.SummaryList.{Key, Row, Value}
import uk.gov.hmrc.viewmodels.Text.Literal
import uk.gov.hmrc.viewmodels.{Content, Html, SummaryList, Text}
import utils.DateHelper
import utils.DateHelper.{dateFormatterDMY, formatDateDMY, formatStartDate}
import viewmodels.Radios.MessageInterpolators
import viewmodels.Table
import viewmodels.Table.Cell

import java.time.LocalDate
import javax.inject.Inject
import scala.collection.Seq
import scala.concurrent.{ExecutionContext, Future}

class PsaPenaltiesAndChargesService @Inject()(fsConnector: FinancialStatementConnector,
                                              financialInfoCacheConnector: FinancialInfoCacheConnector,
                                              schemeService: SchemeService,
                                              minimalConnector: MinimalConnector) {

  val isPaymentOverdue: PsaFSDetail => Boolean = data => data.amountDue > BigDecimal(0.00) && data.dueDate.exists(_.isBefore(LocalDate.now()))

  def retrievePsaChargesAmount(psaFs: Seq[PsaFSDetail])(implicit messages: Messages): (String, String, String) = {

    val upcomingCharges: Seq[PsaFSDetail] =
      psaFs.filter(_.dueDate.exists(!_.isBefore(DateHelper.today)))

    val overdueCharges: Seq[PsaFSDetail] =
      psaFs.filter(charge => charge.dueDate.exists(_.isBefore(DateHelper.today)))

    val totalUpcomingCharge = upcomingCharges.map(_.amountDue).sum
    val totalOverdueCharge: BigDecimal = overdueCharges.map(_.amountDue).sum
    val totalInterestAccruing: BigDecimal = overdueCharges.map(_.accruedInterestTotal).sum

    val totalUpcomingChargeFormatted = s"${FormatHelper.formatCurrencyAmountAsString(totalUpcomingCharge)}"
    val totalOverdueChargeFormatted = s"${FormatHelper.formatCurrencyAmountAsString(totalOverdueCharge)}"
    val totalInterestAccruingFormatted = s"${FormatHelper.formatCurrencyAmountAsString(totalInterestAccruing)}"

    (totalUpcomingChargeFormatted, totalOverdueChargeFormatted, totalInterestAccruingFormatted)
  }

  //scalastyle:off parameter.number
  //scalastyle:off method.length
  //scalastyle:off cyclomatic.complexity
  def getAllPaymentsAndCharges(
      psaId: String,
      penalties: Seq[PsaFSDetail],
      chargeDetailsFilter: ChargeDetailsFilter)(implicit messages: Messages, hc: HeaderCarrier, ec: ExecutionContext): Future[Table] = {

    val seqPayments = penalties.foldLeft[Seq[Future[Table]]] (
      Nil) { (acc, details) =>

      val chargeRefsIndex: String => String = cr => penalties.map(_.chargeReference).indexOf(cr).toString

      val tableRecords = getSchemeName(psaId, details.pstr).map { schemeName =>

        val tableChargeType = if (details.chargeType == CONTRACT_SETTLEMENT_INTEREST) INTEREST_ON_CONTRACT_SETTLEMENT else details.chargeType
        val penaltyDetailsItemWithStatus: PaymentAndChargeStatus => PsaPaymentsAndChargesDetails =
          status =>
            PsaPaymentsAndChargesDetails(
              chargeType = tableChargeType.toString,
              chargeReference = details.chargeReference,
              originalChargeAmount = s"${formatCurrencyAmountAsString(details.totalAmount)}",
              paymentDue = s"${formatCurrencyAmountAsString(details.amountDue)}",
              status = status,
              pstr = details.pstr,
              schemeName = schemeName,
              redirectUrl = controllers.financialOverview.routes.PsaPenaltiesAndChargeDetailsController
                .onPageLoad(details.pstr, chargeRefsIndex(details.chargeReference), chargeDetailsFilter)
                .url,
              visuallyHiddenText = messages("paymentsAndCharges.visuallyHiddenText", details.chargeReference)
            )

        val seqInterestCharge: Seq[PsaPaymentsAndChargesDetails] =
          if (details.chargeType == CONTRACT_SETTLEMENT && details.accruedInterestTotal > 0) {
            Seq(
              PsaPaymentsAndChargesDetails(
                chargeType = INTEREST_ON_CONTRACT_SETTLEMENT.toString,
                chargeReference = messages("paymentsAndCharges.chargeReference.toBeAssigned"),
                originalChargeAmount = "",
                paymentDue = s"${formatCurrencyAmountAsString(details.accruedInterestTotal)}",
                status = InterestIsAccruing,
                pstr = details.pstr,
                schemeName = schemeName,
                redirectUrl = controllers.financialOverview.routes.PsaPaymentsAndChargesInterestController
                  .onPageLoad(details.pstr, chargeRefsIndex(details.chargeReference), chargeDetailsFilter).url,
                visuallyHiddenText = messages("paymentsAndCharges.interest.visuallyHiddenText")
              ))
          } else {
            Nil
          }

        val seqForTable = Seq(penaltyDetailsItemWithStatus(PaymentOverdue)) ++ seqInterestCharge
        mapToTable(seqForTable, includeHeadings = false)

      }
      acc :+ tableRecords
    }
    Future.sequence(seqPayments).map {
      x => x.foldLeft(Table(head = getHeading(), rows = Nil)) { (acc, a) =>
        acc.copy(
        rows = acc.rows ++ a.rows
        )
      }
    }
  }

  def getPenaltyChargeRefs(penalties: Seq[PsaFSDetail]): Map[(String, String), Seq[String]] = {
    val indexRefs: Seq[IndexRef] = penalties.map { penalty =>
      val chargeType = PenaltyType.getPenaltyType(penalty.chargeType).toString
      val period: String = if (chargeType == AccountingForTaxPenalties.toString) {
        penalty.periodStartDate.toString
      } else {
        penalty.periodEndDate.getYear.toString
      }
      IndexRef(chargeType, penalty.chargeReference, period)
    }

    val ref: Map[(String, String), Seq[IndexRef]] = indexRefs.groupBy { x =>
      (x.chargeType, x.period)
    }

    val chargeRefs = ref.map { item =>
      val chargeRef = item._2.map { value =>
        value.chargeReference
      }
      Tuple2(item._1, chargeRef)
    }
    chargeRefs
  }

  private def getSchemeName(psaId: String, pstr: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[String] = {
    val res = for {
      schemeDetails <- schemeService.retrieveSchemeDetails(psaId, pstr, "pstr")
    } yield schemeDetails.schemeName
    res
  }

  private def getHeading() (implicit messages: Messages): Seq[Cell] = {
   Seq(
      Cell(msg"psa.financial.overview.penalty", classes = Seq("govuk-!-width-one-half")),
      Cell(msg"psa.financial.overview.charge.reference", classes = Seq("govuk-!-font-weight-bold")),
      Cell(msg"psa.financial.overview.payment.amount", classes = Seq("govuk-!-font-weight-bold")),
      Cell(msg"psa.financial.overview.payment.due", classes = Seq("govuk-!-font-weight-bold")),
      Cell(
        Html(
          s"<span class='govuk-visually-hidden'>${messages("psa.financial.overview.paymentStatus")}</span>"
        ))
    )
  }

  private def mapToTable(allPayments: Seq[PsaPaymentsAndChargesDetails], includeHeadings: Boolean = true)(implicit messages: Messages): Table = {

    val head = if (includeHeadings) {
      getHeading()
    } else {
      Nil
    }

    val rows = allPayments.map { data =>
      val linkId =
        data.chargeReference match {
          case "To be assigned" => "to-be-assigned"
          case "None"           => "none"
          case _                => data.chargeReference
        }

      val htmlChargeType = Html(
        s"<a id=$linkId class=govuk-link href=" +
          s"${data.redirectUrl}>" +
          s"${data.chargeType} " +
          s"<span class=govuk-visually-hidden>${data.visuallyHiddenText}</span> </a>" +
          s"<p class=govuk-hint>" +
          s"${data.schemeName} </br>" +
          s"(${data.pstr})")

      Seq(
        Cell(htmlChargeType, classes = Seq("govuk-!-width-one-half")),
        Cell(Literal(s"${data.chargeReference}"), classes = Seq("govuk-!-width-one-quarter")),
        if (data.originalChargeAmount.isEmpty) {
          Cell(Html(s"""<span class=govuk-visually-hidden>${messages("paymentsAndCharges.chargeDetails.visuallyHiddenText")}</span>"""))
        } else {
          Cell(Literal(data.originalChargeAmount), classes = Seq("govuk-!-width-one-quarter"))
        },
        Cell(Literal(data.paymentDue), classes = Seq("govuk-!-width-one-quarter")),
        Cell(htmlStatus(data), classes = Nil)
      )
    }

    Table(
      head = head,
      rows = rows,
      attributes = Map("role" -> "table")
    )
  }

  private def htmlStatus(data: PsaPaymentsAndChargesDetails)(implicit messages: Messages): Html = {
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

  def getPenaltiesForJourney(psaId: String, journeyType: ChargeDetailsFilter)
                            (implicit ec: ExecutionContext, hc: HeaderCarrier): Future[PenaltiesCache] =
    getPenaltiesFromCache(psaId).map { cache =>
      journeyType match {
        case Overdue  => cache.copy(penalties = getOverdueCharges(cache.penalties))
        case Upcoming => cache.copy(penalties = extractUpcomingCharges(cache.penalties))
        case _        => cache
      }
    }

  def getPenaltiesFromCache(psaId: String)(implicit ec: ExecutionContext, hc: HeaderCarrier): Future[PenaltiesCache] =
    financialInfoCacheConnector.fetch flatMap {
      case Some(jsValue) =>
        jsValue.validate[PenaltiesCache] match {
          case JsSuccess(value, _) if value.psaId == psaId => Future.successful(value)
          case _                                           => saveAndReturnPenalties(psaId)
        }
      case _ => saveAndReturnPenalties(psaId)
    }

  def saveAndReturnPenalties(psaId: String)(implicit ec: ExecutionContext, hc: HeaderCarrier): Future[PenaltiesCache] =
    for {
      penalties <- fsConnector.getPsaFS(psaId)
      minimalDetails <- minimalConnector.getMinimalPsaDetails(psaId)
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

  def chargeDetailsRows(data: PsaFSDetail, journeyType: ChargeDetailsFilter): Seq[SummaryList.Row] = {
    chargeReferenceRow(data) ++ penaltyAmountRow(data) ++ clearingChargeDetailsRow(data) ++
      stoodOverAmountChargeDetailsRow(data) ++ totalAmountDueChargeDetailsRow(data, journeyType)
  }

  private def chargeReferenceRow(data: PsaFSDetail): Seq[SummaryList.Row] = {

    Seq(
      Row(
        key = Key(msg"psa.financial.overview.charge.reference", classes = Seq("govuk-!-padding-left-0", "govuk-!-width-one-half")),
        value = Value(Literal(s"${data.chargeReference}"), classes = Seq("govuk-!-width-one-quarter"))
      ))
  }

  private def penaltyAmountRow(data: PsaFSDetail): Seq[SummaryList.Row] = {

    Seq(
      Row(
        key = Key(msg"psa.financial.overview.penaltyAmount", classes = Seq("govuk-!-padding-left-0", "govuk-!-width-one-half")),
        value = Value(Literal(s"${FormatHelper.formatCurrencyAmountAsString(data.totalAmount)}"),
                      classes = Seq("govuk-!-width-one-quarter"))
      ))
  }

  private def clearingChargeDetailsRow(data: PsaFSDetail): Seq[SummaryList.Row] = {

    data.documentLineItemDetails.flatMap { documentLineItemDetail =>
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

  private def getClearingDetailLabel(documentLineItemDetail: DocumentLineItemDetail): Option[Text.Message] = {
    (documentLineItemDetail.clearingReason, documentLineItemDetail.clearingDate) match {
      case (Some(clearingReason), Some(clearingDate)) =>
        clearingReason match {
          case CLEARED_WITH_PAYMENT           => Some(msg"financialPaymentsAndCharges.clearingReason.c1".withArgs(formatDateDMY(clearingDate)))
          case CLEARED_WITH_DELTA_CREDIT      => Some(msg"financialPaymentsAndCharges.clearingReason.c2".withArgs(formatDateDMY(clearingDate)))
          case REPAYMENT_TO_THE_CUSTOMER      => Some(msg"financialPaymentsAndCharges.clearingReason.c3".withArgs(formatDateDMY(clearingDate)))
          case WRITTEN_OFF                    => Some(msg"financialPaymentsAndCharges.clearingReason.c4".withArgs(formatDateDMY(clearingDate)))
          case TRANSFERRED_TO_ANOTHER_ACCOUNT => Some(msg"financialPaymentsAndCharges.clearingReason.c5".withArgs(formatDateDMY(clearingDate)))
          case OTHER_REASONS                  => Some(msg"financialPaymentsAndCharges.clearingReason.c6".withArgs(formatDateDMY(clearingDate)))
        }
      case (Some(clearingReason), None) =>
        clearingReason match {
          case CLEARED_WITH_PAYMENT           => Some(msg"financialPaymentsAndCharges.clearingReason.noClearingDate.c1")
          case CLEARED_WITH_DELTA_CREDIT      => Some(msg"financialPaymentsAndCharges.clearingReason.noClearingDate.c2")
          case REPAYMENT_TO_THE_CUSTOMER      => Some(msg"financialPaymentsAndCharges.clearingReason.noClearingDate.c3")
          case WRITTEN_OFF                    => Some(msg"financialPaymentsAndCharges.clearingReason.noClearingDate.c4")
          case TRANSFERRED_TO_ANOTHER_ACCOUNT => Some(msg"financialPaymentsAndCharges.noClearingDate.clearingReason.c5")
          case OTHER_REASONS                  => Some(msg"financialPaymentsAndCharges.clearingReason.noClearingDate.c6")
        }
      case _ => None
    }
  }

  private def stoodOverAmountChargeDetailsRow(data: PsaFSDetail): Seq[SummaryList.Row] =
    if (data.stoodOverAmount > 0) {
      Seq(
        Row(
          key = Key(
            content = msg"paymentsAndCharges.chargeDetails.stoodOverAmount",
            classes = Seq("govuk-!-padding-left-0", "govuk-!-width-one-half")
          ),
          value = Value(
            content = Literal(s"-${formatCurrencyAmountAsString(data.stoodOverAmount)}"),
            classes = Seq("govuk-!-width-one-quarter")
          ),
          actions = Nil
        ))
    } else {
      Nil
    }

  private def totalAmountDueChargeDetailsRow(data: PsaFSDetail, journeyType: ChargeDetailsFilter): Seq[SummaryList.Row] = {
    val amountDueKey: Content = (data.dueDate, data.amountDue > 0) match {
      case (Some(date), true) =>
        msg"financialPaymentsAndCharges.paymentDue.${journeyType.toString}.dueDate".withArgs(date.format(dateFormatterDMY))
      case _ =>
        msg"financialPaymentsAndCharges.paymentDue.noDueDate"
    }
    if (data.totalAmount > 0) {
      Seq(
        Row(
          key = Key(
            content = amountDueKey,
            classes = Seq("govuk-!-padding-left-0", "govuk-!-width-one-half")
          ),
          value = Value(
            content = Literal(s"${formatCurrencyAmountAsString(data.amountDue)}"),
            classes = Seq("govuk-!-width-one-quarter","govuk-!-font-weight-bold")
          ),
          actions = Nil
        ))
    } else {
      Nil
    }
  }

  case class IndexRef(chargeType: String, chargeReference: String, period: String)

  def setPeriod(chargeType: PsaFSChargeType, periodStartDate: LocalDate, periodEndDate: LocalDate): String = {
    "Tax period: " + formatDateDMY(periodStartDate) + " to " + formatDateDMY(periodEndDate)
    chargeType match {
      case AFT_INITIAL_LFP | AFT_DAILY_LFP | AFT_30_DAY_LPP | AFT_6_MONTH_LPP
           | AFT_12_MONTH_LPP | OTC_30_DAY_LPP | OTC_6_MONTH_LPP | OTC_12_MONTH_LPP =>
        "Quarter: " + formatStartDate(periodStartDate) + " to " + formatDateDMY(periodEndDate)
      case PSS_PENALTY | PSS_INFO_NOTICE | CONTRACT_SETTLEMENT | CONTRACT_SETTLEMENT_INTEREST =>
        "Period: " + formatStartDate(periodStartDate) + " to " + formatDateDMY(periodEndDate)
      case _ => ""
    }
  }

  private def totalInterestDueRow(data: PsaFSDetail): Seq[SummaryList.Row] = {
    val dateAsOf: String = LocalDate.now.format(dateFormatterDMY)
    Seq(Row(
      key = Key(msg"psa.financial.overview.totalDueAsOf".withArgs(dateAsOf), classes = Seq("govuk-!-width-two-quarters")),
      value = Value(Literal(s"${FormatHelper.formatCurrencyAmountAsString(data.accruedInterestTotal)}"),
        classes = Seq("govuk-!-width-one-quarter"))
    ))
  }

  private def chargeReferenceInterestRow(data: PsaFSDetail): Seq[SummaryList.Row] = {

    Seq(
      Row(
        key = Key(msg"psa.financial.overview.charge.reference", classes = Seq("govuk-!-width-two-quarters")),
        value = Value(msg"paymentsAndCharges.chargeReference.toBeAssigned", classes = Seq("govuk-!-width-one-quarter"))
      ))
  }

  def interestRows(data: PsaFSDetail): Seq[SummaryList.Row] =
    chargeReferenceInterestRow(data) ++ totalInterestDueRow(data)

}

case class PenaltiesCache(psaId: String, psaName: String, penalties: Seq[PsaFSDetail])
object PenaltiesCache {
  implicit val format: OFormat[PenaltiesCache] = Json.format[PenaltiesCache]
}
