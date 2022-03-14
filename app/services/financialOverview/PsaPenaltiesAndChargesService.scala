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
import models.financialStatement.PenaltyType.{AccountingForTaxPenalties, getPenaltyType}
import models.financialStatement.PsaFSChargeType.{AFT_12_MONTH_LPP, AFT_30_DAY_LPP, AFT_6_MONTH_LPP, AFT_DAILY_LFP, AFT_INITIAL_LFP, CONTRACT_SETTLEMENT, CONTRACT_SETTLEMENT_INTEREST, OTC_12_MONTH_LPP, OTC_30_DAY_LPP, OTC_6_MONTH_LPP, PSS_INFO_NOTICE, PSS_PENALTY}
import models.financialStatement.SchemeFSClearingReason.{CLEARED_WITH_DELTA_CREDIT, CLEARED_WITH_PAYMENT, OTHER_REASONS, REPAYMENT_TO_THE_CUSTOMER, TRANSFERRED_TO_ANOTHER_ACCOUNT, WRITTEN_OFF}
import models.financialStatement.{DocumentLineItemDetail, PenaltyType, PsaFS, PsaFSChargeType}
import models.viewModels.financialOverview.PsaPaymentsAndChargesDetails
import models.viewModels.paymentsAndCharges.PaymentAndChargeStatus
import models.viewModels.paymentsAndCharges.PaymentAndChargeStatus.{InterestIsAccruing, PaymentOverdue}
import play.api.Logger
import play.api.i18n.Messages
import play.api.libs.json.{JsObject, JsSuccess, Json}
import services.{PenaltiesCache, PenaltiesService, SchemeService}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.viewmodels.SummaryList.{Key, Row, Value}
import uk.gov.hmrc.viewmodels.Text.{Literal, Message}
import uk.gov.hmrc.viewmodels.{Content, Html, SummaryList, Text}
import utils.DateHelper
import utils.DateHelper.{dateFormatterDMY, formatDateDMY, formatStartDate}
import viewmodels.Radios.MessageInterpolators
import viewmodels.Table
import viewmodels.Table.Cell

import java.time.LocalDate
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class PsaPenaltiesAndChargesService @Inject()(penaltiesService: PenaltiesService,
                                              fsConnector: FinancialStatementConnector,
                                              financialInfoCacheConnector: FinancialInfoCacheConnector,
                                              schemeService: SchemeService,
                                              paymentsAndChargesSerice: PaymentsAndChargesService,
                                              minimalConnector: MinimalConnector
                                         ) {

  case class IndexRef(chargeType: String, chargeReference: String, period: String)

  private val logger = Logger(classOf[PsaPenaltiesAndChargesService])

  val isPaymentOverdue: PsaFS => Boolean = data => data.amountDue > BigDecimal(0.00) && data.dueDate.exists(_.isBefore(LocalDate.now()))

  def retrievePsaChargesAmount(psaFs: Seq[PsaFS])
                              (implicit messages: Messages): (String,String,String) = {

    val upcomingCharges: Seq[PsaFS] =
      psaFs.filter(_.dueDate.exists(!_.isBefore(DateHelper.today)))

    val overdueCharges: Seq[PsaFS] =
      psaFs.filter(charge =>  charge.dueDate.exists(_.isBefore(DateHelper.today)))

    val totalUpcomingCharge = upcomingCharges.map(_.amountDue).sum
    val totalOverdueCharge: BigDecimal = overdueCharges.map(_.amountDue).sum
    val totalInterestAccruing: BigDecimal = overdueCharges.map(_.accruedInterestTotal).sum

    val totalUpcomingChargeFormatted= s"${FormatHelper.formatCurrencyAmountAsString(totalUpcomingCharge)}"
    val totalOverdueChargeFormatted= s"${FormatHelper.formatCurrencyAmountAsString(totalOverdueCharge)}"
    val totalInterestAccruingFormatted= s"${FormatHelper.formatCurrencyAmountAsString(totalInterestAccruing)}"


    (totalUpcomingChargeFormatted,totalOverdueChargeFormatted,totalInterestAccruingFormatted)
  }

  //scalastyle:off parameter.number
  // scalastyle:off method.length
  //scalastyle:off cyclomatic.complexity
  def getAllPaymentsAndCharges(psaId: String,
                               srn: String,
                               chargeRefsIndex: String => String,
                               penalties: Seq[PsaFS],
                               chargeDetailsFilter: ChargeDetailsFilter)
                              (implicit messages: Messages, hc: HeaderCarrier, ec: ExecutionContext): Table = {

    val seqPayments: Seq[PsaPaymentsAndChargesDetails] = penalties flatMap { details =>
    val penaltyType = getPenaltyType(details.chargeType)

    val periodValue: String = if (penaltyType.toString == AccountingForTaxPenalties.toString) {
      details.periodStartDate.toString
    } else {
      details.periodEndDate.getYear.toString
    }

    val seqChargeRefs = getPenaltyChargeRefs(penalties).find(_._1 == (penaltyType.toString, periodValue)) match {
      case Some(found) => found._2
      case _ => Nil
    }

    val schemeName1 = getSchemeDetails(psaId, details.pstr)

    val originalChargeRefsIndex: String => String = cr => penalties.map(_.chargeReference).indexOf(cr).toString

    val penaltyDetailsItemWithStatus: PaymentAndChargeStatus => PsaPaymentsAndChargesDetails =
      status =>
        PsaPaymentsAndChargesDetails(
          chargeType = details.chargeType.toString,
          chargeReference = details.chargeReference,
          originalChargeAmount = s"${formatCurrencyAmountAsString(details.totalAmount)}",
          paymentDue = s"${formatCurrencyAmountAsString(details.amountDue)}",
          status = status,
          pstr = details.pstr,
          schemeName = "",//schemeName1,
          period = "",//setPeriod(details.chargeType, details.periodStartDate, details.periodEndDate),
          redirectUrl = controllers.financialOverview.routes.PsaPenaltiesAndChargeDetailsController.onPageLoad(
            details.pstr, originalChargeRefsIndex(details.chargeReference).toString).url,
          visuallyHiddenText = messages("paymentsAndCharges.visuallyHiddenText", details.chargeReference)
        )

    val seqInterestCharge: Seq[PsaPaymentsAndChargesDetails] = if(details.chargeType == CONTRACT_SETTLEMENT && details.accruedInterestTotal > 0) {
      Seq(PsaPaymentsAndChargesDetails(
        chargeType = CONTRACT_SETTLEMENT_INTEREST.toString, //.toString + suffix.getOrElse(""),
        chargeReference = messages("paymentsAndCharges.chargeReference.toBeAssigned"),
        originalChargeAmount = "",
        paymentDue = s"${formatCurrencyAmountAsString(details.accruedInterestTotal)}",
        status = InterestIsAccruing,
        pstr = details.pstr,
        schemeName = "",
        period = "", //setPeriod(interestChargeType, details.periodStartDate, details.periodEndDate),
        redirectUrl = "" /*financialOverview.PaymentsAndChargesInterestController.onPageLoad(
        srn, pstr, periodValue, seqChargeRefs.indexOf(details.chargeReference).toString, chargeType, version, submittedDate, chargeDetailsFilter).url*/ ,
        visuallyHiddenText = messages("paymentsAndCharges.interest.visuallyHiddenText")
      ))
      } else {
        Nil
      }
      Seq(penaltyDetailsItemWithStatus(PaymentOverdue)) ++ seqInterestCharge
    }
    mapToTable(seqPayments)
  }

  def getPenaltyChargeRefs(penalties: Seq[PsaFS]): Map[(String,String), Seq[String]] = {
    val indexRefs: Seq[IndexRef] = penalties.map { penalty =>
      val chargeType = PenaltyType.getPenaltyType(penalty.chargeType).toString
      val period: String = if (chargeType == AccountingForTaxPenalties.toString) {
        penalty.periodStartDate.toString
      } else {
        penalty.periodEndDate.getYear.toString
      }
      IndexRef(chargeType, penalty.chargeReference, period) }

    val ref: Map[(String, String), Seq[IndexRef]] = indexRefs.groupBy{
      x => (x.chargeType, x.period)
    }

    val chargeRefs = ref.map{
      item =>
        val chargeRef = item._2.map{
          value =>
            value.chargeReference
        }
        Tuple2(item._1, chargeRef)
    }
    chargeRefs
  }

  private def getSchemeDetails(psaId: String, pstr: String)
                         (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[String] = {
  val res = for {
      schemeDetails <- schemeService.retrieveSchemeDetails(psaId, pstr, "pstr")
    } yield schemeDetails.schemeName
    res
  }

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

  private def mapToTable(allPayments: Seq[PsaPaymentsAndChargesDetails])
                        (implicit messages: Messages): Table = {

    val head = Seq(
      Cell(msg"psa.financial.overview.penalty", classes = Seq("govuk-!-width-one-half")),
      Cell(msg"psa.financial.overview.charge.reference", classes = Seq("govuk-!-font-weight-bold")),
      Cell(msg"psa.financial.overview.payment.amount", classes = Seq("govuk-!-font-weight-bold")),
      Cell(msg"psa.financial.overview.payment.due", classes = Seq("govuk-!-font-weight-bold")),
      Cell(Html(
        s"<span class='govuk-visually-hidden'>${messages("psa.financial.overview.paymentStatus")}</span>"
      ))
    )

    val rows = allPayments.map { data =>
      val linkId =
        data.chargeReference match {
          case "To be assigned" => "to-be-assigned"
          case "None" => "none"
          case _ => data.chargeReference
        }

      val htmlChargeType = Html(
        s"<a id=$linkId class=govuk-link href=" +
          s"${data.redirectUrl}>" +
          s"${data.chargeType} " +
          s"<span class=govuk-visually-hidden>${data.visuallyHiddenText}</span> </a>" +
          s"<p class=govuk-hint>" +
          s"${data.schemeName}" +
          s"${data.pstr} ")

      println("data ------> " + data.status)
      Seq(
        Cell(htmlChargeType, classes = Seq("govuk-!-width-one-half")),
        Cell(Literal(s"${data.chargeReference}")),
        Cell(Literal(data.originalChargeAmount)),
        Cell(Literal(data.paymentDue)),
        Cell(htmlStatus(data), classes = Nil)
      )
    }

    Table(
      head = head,
      rows = rows,
      attributes = Map("role" -> "table")
    )
  }

  private def htmlStatus(data: PsaPaymentsAndChargesDetails)
                        (implicit messages: Messages): Html = {
    println("In html status ------------" + data + "   status   " + data.status.toString)
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

  def getPsaFsJson(penalties: Seq[PsaFS], identifier: String, chargeRefsIndex: String => String, penaltyType: PenaltyType, journeyType: ChargeDetailsFilter)
                  (implicit messages: Messages): JsObject = {

    val head: Seq[Cell] = Seq(
      //Cell(msg"penalties.column.${if(penaltyType == ContractSettlementCharges) "chargeType" else "penaltyType"}"),
      Cell(msg"psa.financial.overview.penalty"),
      Cell(msg"psa.financial.overview.charge.reference"),
      Cell(msg"psa.financial.overview.payment.amount"),
      Cell(msg"psa.financial.overview.payment.due"),
      Cell(Html(s"<span class='govuk-visually-hidden'>${messages("penalties.column.paymentStatus")}</span>"))
    )

    val rows = penalties.flatMap { data =>
      val content = chargeTypeLink(identifier, data, chargeRefsIndex(data.chargeReference), journeyType)
      val charge = Seq(
        Cell(content, classes = Seq("govuk-!-width-two-thirds-quarter")),
        Cell(Literal(s"${FormatHelper.formatCurrencyAmountAsString(data.amountDue)}"),
          classes = Seq("govuk-!-width-one-quarter")),
        Cell(Literal(data.chargeReference), classes = Seq("govuk-!-width-one-quarter")),
        statusCell(data)
      )

      val interest = if (data.chargeType == CONTRACT_SETTLEMENT && data.accruedInterestTotal > 0) {
        val content = accruedInterestLink(identifier, data, chargeRefsIndex(data.chargeReference))
        Seq(
          Cell(content, classes = Seq("govuk-!-width-two-thirds-quarter")),
          Cell(Literal(s"${FormatHelper.formatCurrencyAmountAsString(data.accruedInterestTotal)}"),
            classes = Seq("govuk-!-width-one-quarter")),
          Cell(Message("penalties.column.chargeReference.toBeAssigned"), classes = Seq("govuk-!-width-one-quarter")),
          Cell(Html(s"<span class='govuk-body govuk-tag govuk-tag--blue'>${messages("penalties.status.interestAccruing").toUpperCase}</span>"))
        )
      } else {
        Nil
      }

      (Seq(charge) ++ Seq(interest)).filterNot(_.isEmpty)
    }
    Json.obj(
      "penaltyTable" -> Table(head = head, rows = rows, attributes = Map("role" -> "table"))
    )
  }

  private def chargeTypeLink(identifier: String, data: PsaFS, chargeRefsIndex: String, journeyType: ChargeDetailsFilter)
                            (implicit messages: Messages): Html =
    Html(s"<a id=${data.chargeReference} " +
      s"class=govuk-link href=${controllers.financialStatement.penalties.routes
        .ChargeDetailsController.onPageLoad(identifier, chargeRefsIndex, journeyType)}>" +
      s"${messages(data.chargeType.toString)}" +
      s"<span class=govuk-visually-hidden>${messages(s"penalties.visuallyHiddenText", data.chargeReference)}</span> </a>")

  private def statusCell(data: PsaFS)(implicit messages: Messages): Cell = {
    val (classes, content) = (isPaymentOverdue(data), data.amountDue) match {
      case (true, _) => ("govuk-tag govuk-tag--red", messages("penalties.status.paymentOverdue"))
      case (_, amount) if amount == BigDecimal(0.00) =>
        ("govuk-visually-hidden", messages("penalties.status.visuallyHiddenText.noPaymentDue"))
      case _ => ("govuk-visually-hidden", messages("penalties.status.visuallyHiddenText.paymentIsDue"))
    }
    Cell(Html(s"""<span class='$classes'>$content</span>"""))
  }

  private def accruedInterestLink(identifier: String, data: PsaFS, chargeRefsIndex: String)
                                 (implicit messages: Messages): Html =
    Html(s"<a id=${data.chargeReference}-interest " +
      s"class=govuk-link href=${controllers.financialStatement.penalties.routes
        .InterestController.onPageLoad(identifier, chargeRefsIndex)}>" +
      s"${messages("penalties.column.chargeType.interestOn", messages(data.chargeType.toString.toLowerCase))}" +
      s"<span class=govuk-visually-hidden>${messages(s"penalties.column.chargeType.interestToCome")}</span> </a>")


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
      minimalDetails <- minimalConnector.getMinimalPsaDetails(psaId)
      _ <- financialInfoCacheConnector.save(Json.toJson(PenaltiesCache(psaId, minimalDetails.name, penalties)))
    } yield PenaltiesCache(psaId, minimalDetails.name, penalties)


  val extractUpcomingCharges: Seq[PsaFS] => Seq[PsaFS] = psaFS =>
    psaFS.filter(charge => charge.dueDate.nonEmpty && !charge.dueDate.get.isBefore(DateHelper.today))
      .filter(_.amountDue > BigDecimal(0.00))

  def getOverdueCharges(psaFS: Seq[PsaFS]): Seq[PsaFS] =
    psaFS
      .filter(_.dueDate.nonEmpty)
      .filter(_.dueDate.get.isBefore(DateHelper.today))
      .filter(_.amountDue > BigDecimal(0.00))

  def chargeDetailsRows(data: PsaFS): Seq[SummaryList.Row] = {
    chargeReferenceRow(data) ++ penaltyAmountRow(data) ++ clearingChargeDetailsRow(data) ++
      stoodOverAmountChargeDetailsRow(data) ++  totalAmountDueChargeDetailsRow(data, "overdue")
  }

  private def chargeReferenceRow(data: PsaFS): Seq[SummaryList.Row] = {

    Seq(Row(
      key = Key(msg"psa.financial.overview.charge.reference", classes = Seq("govuk-!-width-two-quarters")),
      value = Value(Literal(s"${data.chargeReference}"),
        classes = Seq("govuk-!-width-one-quarter", "govuk-table__cell--numeric"))
    ))
  }

  private def penaltyAmountRow(data: PsaFS): Seq[SummaryList.Row] = {

    Seq(Row(
      key = Key(msg"psa.financial.overview.penaltyAmount", classes = Seq("govuk-!-width-three-quarters")),
      value = Value(Literal(s"${FormatHelper.formatCurrencyAmountAsString(data.totalAmount)}"),
        classes = Seq("govuk-!-width-one-quarter", "govuk-table__cell--numeric"))
    ))
  }

  private def getClearingDetailLabel(documentLineItemDetail: DocumentLineItemDetail): Option[Text.Message] = {
    (documentLineItemDetail.clearingReason, documentLineItemDetail.clearingDate) match {
      case (Some(clearingReason), Some(clearingDate)) =>
        clearingReason match {
          case CLEARED_WITH_PAYMENT => Some(msg"financialPaymentsAndCharges.clearingReason.c1".withArgs(formatDateDMY(clearingDate)))
          case CLEARED_WITH_DELTA_CREDIT => Some(msg"financialPaymentsAndCharges.clearingReason.c2".withArgs(formatDateDMY(clearingDate)))
          case REPAYMENT_TO_THE_CUSTOMER => Some(msg"financialPaymentsAndCharges.clearingReason.c3".withArgs(formatDateDMY(clearingDate)))
          case WRITTEN_OFF => Some(msg"financialPaymentsAndCharges.clearingReason.c4".withArgs(formatDateDMY(clearingDate)))
          case TRANSFERRED_TO_ANOTHER_ACCOUNT => Some(msg"financialPaymentsAndCharges.clearingReason.c5".withArgs(formatDateDMY(clearingDate)))
          case OTHER_REASONS => Some(msg"financialPaymentsAndCharges.clearingReason.c6".withArgs(formatDateDMY(clearingDate)))
        }
      case (Some(clearingReason),None) =>
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

  private def clearingChargeDetailsRow(data: PsaFS): Seq[SummaryList.Row] = {

    data.documentLineItemDetails.flatMap {
      documentLineItemDetail => {
        if(documentLineItemDetail.clearedAmountItem > 0) {
          getClearingDetailLabel(documentLineItemDetail) match {
            case Some(clearingDetailsValue) =>
              Seq(
                Row(
                  key = Key(
                    content = clearingDetailsValue,
                    classes = Seq("govuk-!-width-three-quarters")
                  ),
                  value = Value(
                    content = Literal(s"-${formatCurrencyAmountAsString(documentLineItemDetail.clearedAmountItem)}"),
                    classes = Seq("govuk-!-width-one-quarter", "govuk-table__cell--numeric")
                  ),
                  actions = Nil
                ))
            case _ => Nil
          }
        }
        else
          Nil
      }
    }
  }

  private def stoodOverAmountChargeDetailsRow(data: PsaFS): Seq[SummaryList.Row] =
    if (data.stoodOverAmount > 0) {
      Seq(
        Row(
          key = Key(
            content = msg"paymentsAndCharges.chargeDetails.stoodOverAmount",
            classes = Seq("govuk-!-width-three-quarters")
          ),
          value = Value(
            content = Literal(s"-${formatCurrencyAmountAsString(data.stoodOverAmount)}"),
            classes = Seq("govuk-!-width-one-quarter", "govuk-table__cell--numeric")
          ),
          actions = Nil
        ))
    } else {
      Nil
    }

  private def totalAmountDueChargeDetailsRow(data: PsaFS, journeyType: ChargeDetailsFilter): Seq[SummaryList.Row] = {
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
            classes = Seq("govuk-!-width-three-quarters")
          ),
          value = Value(
            content = Literal(s"${formatCurrencyAmountAsString(data.amountDue)}"),
            classes = Seq("govuk-!-width-one-quarter", "govuk-table__cell--numeric")
          ),
          actions = Nil
        ))
    } else {
      Nil
    }
  }
}

