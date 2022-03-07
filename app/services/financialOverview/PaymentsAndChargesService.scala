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

import connectors.FinancialStatementConnector
import connectors.cache.FinancialInfoCacheConnector
import controllers.chargeB.{routes => _}
import controllers.financialOverview.{routes => financialOverview}
import helpers.FormatHelper._
import models.ChargeDetailsFilter
import models.ChargeDetailsFilter.{Overdue, Upcoming}
import models.financialStatement.PaymentOrChargeType.{AccountingForTaxCharges, getPaymentOrChargeType}
import models.financialStatement.SchemeFSChargeType._
import models.financialStatement.SchemeFSClearingReason._
import models.financialStatement.{DocumentLineItemDetail, SchemeFS, SchemeFSChargeType}
import models.viewModels.financialOverview.{PaymentsAndChargesDetails => FinancialPaymentAndChargesDetails}
import models.viewModels.paymentsAndCharges.PaymentAndChargeStatus
import models.viewModels.paymentsAndCharges.PaymentAndChargeStatus.{InterestIsAccruing, NoStatus, PaymentOverdue}
import play.api.i18n.Messages
import play.api.libs.json.{JsSuccess, Json, OFormat}
import services.SchemeService
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.viewmodels.SummaryList.{Key, Row, Value}
import uk.gov.hmrc.viewmodels.Text.Literal
import uk.gov.hmrc.viewmodels.{Content, Html, SummaryList, _}
import utils.DateHelper
import utils.DateHelper.{dateFormatterDMY, formatDateDMY, formatStartDate}
import viewmodels.Table
import viewmodels.Table.Cell

import java.time.LocalDate
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class PaymentsAndChargesService @Inject()(schemeService: SchemeService,
                                          fsConnector: FinancialStatementConnector,
                                          financialInfoCacheConnector: FinancialInfoCacheConnector
                                         ) {


  case class IndexRef(chargeType: String, chargeReference: String, period: String)

  def getPaymentsAndCharges(   srn: String,
                               pstr: String,
                               schemeFS: Seq[SchemeFS],
                               mapChargeTypesVersionAndDate: Map[SchemeFSChargeType, (Option[Int], Option[LocalDate])],
                               chargeDetailsFilter: ChargeDetailsFilter
                              )
                              (implicit messages: Messages, hc: HeaderCarrier, ec: ExecutionContext): Table = {

    val seqPayments: Seq[FinancialPaymentAndChargesDetails] = schemeFS.flatMap { paymentOrCharge =>
      paymentsAndChargesDetails(paymentOrCharge, srn, pstr, chargeRefs(schemeFS), mapChargeTypesVersionAndDate, chargeDetailsFilter)
    }

    mapToTable(seqPayments)
  }

  def chargeRefs (schemeFS: Seq[SchemeFS]) : Map[(String,String), Seq[String]] = {
    val indexRefs: Seq[IndexRef] = schemeFS.map { scheme =>
      val chargeType = getPaymentOrChargeType(scheme.chargeType).toString
      val period: String = if (chargeType == AccountingForTaxCharges.toString) {
        scheme.periodStartDate.toString
      } else {
        scheme.periodEndDate.getYear.toString
      }
      IndexRef(chargeType, scheme.chargeReference, period)
    }
    val ref: Map[(String, String), Seq[IndexRef]] = indexRefs.groupBy {
      x => (x.chargeType, x.period)
    }
    ref.map {
      item =>
        val chargeRef = item._2.map {
          value =>
            value.chargeReference
        }
        Tuple2(item._1, chargeRef)
    }
  }
  val extractUpcomingCharges: Seq[SchemeFS] => Seq[SchemeFS] = schemeFS =>
    schemeFS.filter(charge => charge.dueDate.nonEmpty
      && charge.dueDate.get.isAfter(DateHelper.today)
      && charge.amountDue > BigDecimal(0.00))

  def getOverdueCharges(schemeFS: Seq[SchemeFS]): Seq[SchemeFS] =
    schemeFS
      .filter(_.dueDate.nonEmpty)
      .filter(_.dueDate.get.isBefore(DateHelper.today))
      .filter(_.amountDue > BigDecimal(0.00))
  

  //scalastyle:off parameter.number
  // scalastyle:off method.length
  //scalastyle:off cyclomatic.complexity
  private def paymentsAndChargesDetails(
                                            details: SchemeFS,
                                            srn: String,
                                            pstr: String,
                                            chargeRefs: Map[(String,String), Seq[String]],
                                            mapChargeTypesVersionAndDate: Map[SchemeFSChargeType, (Option[Int], Option[LocalDate])],
                                            chargeDetailsFilter: ChargeDetailsFilter
                                          )(implicit messages: Messages, hc: HeaderCarrier,
                                            ec: ExecutionContext): Seq[FinancialPaymentAndChargesDetails] = {



    val chargeType = getPaymentOrChargeType(details.chargeType)
    val paymentOrChargeType = details.chargeType
    val onlyAFTAndOTCChargeTypes: Boolean =
      paymentOrChargeType == PSS_AFT_RETURN || paymentOrChargeType == PSS_OTC_AFT_RETURN
    val ifAFTAndOTCChargeTypes: Boolean =
      onlyAFTAndOTCChargeTypes || paymentOrChargeType == PSS_AFT_RETURN_INTEREST || paymentOrChargeType == PSS_OTC_AFT_RETURN_INTEREST

    val (suffix, version, submittedDate) = (ifAFTAndOTCChargeTypes, mapChargeTypesVersionAndDate.get(paymentOrChargeType)) match {
      case (true, Some(Tuple2(Some(version), Some(date)))) => (Some(s" submission $version"), Some(version), Some(formatDateDMY(date)))
      case _ => (None, None, None)
    }

    val periodValue: String = if (chargeType.toString == AccountingForTaxCharges.toString) {
      details.periodStartDate.toString
    } else {
      details.periodEndDate.getYear.toString
    }

    val seqChargeRefs = chargeRefs.find(_._1 == (chargeType.toString, periodValue))  match {
      case Some(found) => found._2
      case _ => Nil
    }

    val chargeDetailsItemWithStatus: PaymentAndChargeStatus => FinancialPaymentAndChargesDetails =
      status =>
        FinancialPaymentAndChargesDetails(
          chargeType = details.chargeType.toString + suffix.getOrElse(""),
          chargeReference = details.chargeReference,
          originalChargeAmount = s"${formatCurrencyAmountAsString(details.totalAmount)}",
          paymentDue = s"${formatCurrencyAmountAsString(details.amountDue)}",
          status = status,
          period = setPeriod(details.chargeType, details.periodStartDate, details.periodEndDate),
          redirectUrl = financialOverview.PaymentsAndChargeDetailsController.onPageLoad(
            srn, pstr, periodValue, seqChargeRefs.indexOf(details.chargeReference).toString, chargeType, version, submittedDate, chargeDetailsFilter).url,
          visuallyHiddenText = messages("paymentsAndCharges.visuallyHiddenText", details.chargeReference)
        )

    (onlyAFTAndOTCChargeTypes, details.amountDue > 0) match {
      case (true, true) if details.accruedInterestTotal > 0 =>
        val interestChargeType =
          if (details.chargeType == PSS_AFT_RETURN) PSS_AFT_RETURN_INTEREST else PSS_OTC_AFT_RETURN_INTEREST
        Seq(
          chargeDetailsItemWithStatus(PaymentOverdue),
          FinancialPaymentAndChargesDetails(
            chargeType = interestChargeType.toString + suffix.getOrElse(""),
            chargeReference = messages("paymentsAndCharges.chargeReference.toBeAssigned"),
            originalChargeAmount = "",
            paymentDue = s"${formatCurrencyAmountAsString(details.accruedInterestTotal)}",
            status = InterestIsAccruing,
            period = setPeriod(interestChargeType, details.periodStartDate, details.periodEndDate),
            redirectUrl = financialOverview.PaymentsAndChargesInterestController.onPageLoad(
              srn, pstr, periodValue, seqChargeRefs.indexOf(details.chargeReference).toString, chargeType, version, submittedDate, chargeDetailsFilter).url,
            visuallyHiddenText = messages("paymentsAndCharges.interest.visuallyHiddenText")
          )
        )
      case (true, _) if details.totalAmount < 0 =>
        Seq.empty
      case _ =>
        Seq(
          chargeDetailsItemWithStatus(NoStatus)
        )
    }
  }


  def setPeriod(chargeType: SchemeFSChargeType, periodStartDate: LocalDate, periodEndDate: LocalDate): String = {
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


  private def htmlStatus(data: FinancialPaymentAndChargesDetails)
                           (implicit messages: Messages): Html = {
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


  private def mapToTable(allPayments: Seq[FinancialPaymentAndChargesDetails])
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
          s"${data.period}</p>")

      Seq(
        Cell(htmlChargeType, classes = Seq("govuk-!-width-one-half")),
        Cell(Literal(s"${data.chargeReference}"), classes = Seq("govuk-!-width-one-quarter")),
        Cell(Literal(data.originalChargeAmount), classes = Seq("govuk-!-width-one-quarter")),
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

  def getChargeDetailsForSelectedCharge(schemeFS: SchemeFS, journeyType: ChargeDetailsFilter, submittedDate: Option[String])
                                       (implicit messages: Messages): Seq[SummaryList.Row] = {
    dateSubmittedRow(submittedDate) ++ chargeReferenceRow(schemeFS) ++ originalAmountChargeDetailsRow(schemeFS) ++
      clearingChargeDetailsRow(schemeFS.documentLineItemDetails) ++
      stoodOverAmountChargeDetailsRow(schemeFS) ++ totalAmountDueChargeDetailsRow(schemeFS, journeyType)
  }

  private def dateSubmittedRow(submittedDate: Option[String])
                                (implicit messages: Messages): Seq[SummaryList.Row] = {
    submittedDate match {
      case Some(date) =>
        Seq(
          Row(
            key = Key(
              content = msg"financialPaymentsAndCharges.dateSubmitted",
              classes = Seq("govuk-!-padding-left-0", "govuk-!-width-one-half")
            ),
            value = Value(
              content = Literal(date),
              classes =
                Seq("govuk-!-width-one-quarter")
            ),
            actions = Nil
          ))
      case _ =>
        Nil
    }

  }

  private def chargeReferenceRow(schemeFS: SchemeFS)
                                (implicit messages: Messages): Seq[SummaryList.Row] = {
    Seq(
      Row(
        key = Key(
          content = msg"financialPaymentsAndCharges.chargeReference",
          classes = Seq("govuk-!-padding-left-0", "govuk-!-width-one-half")
        ),
        value = Value(
          content = Literal(schemeFS.chargeReference),
          classes =
            Seq("govuk-!-width-one-quarter")
        ),
        actions = Nil
      ))
  }

  private def originalAmountChargeDetailsRow(schemeFS: SchemeFS)
                                            (implicit messages: Messages): Seq[SummaryList.Row] = {
    Seq(
      Row(
        key = Key(
          content = msg"paymentsAndCharges.chargeDetails.originalChargeAmount",
          classes = Seq("govuk-!-padding-left-0", "govuk-!-width-one-half")
        ),
        value = Value(
          content = Literal(s"${formatCurrencyAmountAsString(schemeFS.totalAmount)}"),
          classes =
            Seq("govuk-!-width-one-quarter")
        ),
        actions = Nil
      ))
  }

  private def stoodOverAmountChargeDetailsRow(schemeFS: SchemeFS): Seq[SummaryList.Row] =
    if (schemeFS.stoodOverAmount > 0) {
      Seq(
        Row(
          key = Key(
            content = msg"paymentsAndCharges.chargeDetails.stoodOverAmount",
            classes = Seq("govuk-!-padding-left-0", "govuk-!-width-one-half")
          ),
          value = Value(
            content = Literal(s"-${formatCurrencyAmountAsString(schemeFS.stoodOverAmount)}"),
            classes = Seq("govuk-!-width-one-quarter")
          ),
          actions = Nil
        ))
    } else {
      Nil
    }

  private def totalAmountDueChargeDetailsRow(schemeFS: SchemeFS, journeyType: ChargeDetailsFilter): Seq[SummaryList.Row] = {
    val amountDueKey: Content = (schemeFS.dueDate, schemeFS.amountDue > 0) match {
      case (Some(date), true) =>
        msg"financialPaymentsAndCharges.paymentDue.${journeyType.toString}.dueDate".withArgs(date.format(dateFormatterDMY))
      case _ =>
        msg"financialPaymentsAndCharges.paymentDue.noDueDate"
    }
    if (schemeFS.totalAmount > 0) {
      Seq(
        Row(
          key = Key(
            content = amountDueKey,
            classes = Seq("govuk-!-padding-left-0", "govuk-!-width-one-half")
          ),
          value = Value(
            content = Literal(s"${formatCurrencyAmountAsString(schemeFS.amountDue)}"),
            classes = Seq("govuk-!-padding-left-0", "govuk-!-width--one-half", "govuk-!-font-weight-bold")
          ),
          actions = Nil
        ))
    } else {
      Nil
    }
  }


  private def clearingChargeDetailsRow(documentLineItemDetails: Seq[DocumentLineItemDetail])
                                            (implicit messages: Messages): Seq[SummaryList.Row] = {

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
         }
          else
            Nil
      }
    }
  }


  private def saveAndReturnPaymentsCache(loggedInId: String, srn: String)
                                        (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[PaymentsCache] =
    for {
      schemeDetails <- schemeService.retrieveSchemeDetails(loggedInId, srn, "srn")
      schemeFS <- fsConnector.getSchemeFS(schemeDetails.pstr)
      paymentsCache = PaymentsCache(loggedInId, srn, schemeDetails, schemeFS)
      _ <- financialInfoCacheConnector.save(Json.toJson(paymentsCache))
    } yield paymentsCache

  private def getPaymentsFromCache(loggedInId: String, srn: String)(implicit ec: ExecutionContext, hc: HeaderCarrier): Future[PaymentsCache] =
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
      journeyType match {
        case Overdue => cache.copy(schemeFS = getOverdueCharges(cache.schemeFS))
        case Upcoming => cache.copy(schemeFS = extractUpcomingCharges(cache.schemeFS))
        case _ => cache
      }
    }

  private def getClearingDetailLabel(documentLineItemDetail: DocumentLineItemDetail)(implicit messages: Messages): Option[Text.Message] = {
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

}

import models.SchemeDetails

case class PaymentsCache(loggedInId: String, srn: String, schemeDetails: SchemeDetails, schemeFS: Seq[SchemeFS])

object PaymentsCache {
  implicit val format: OFormat[PaymentsCache] = Json.format[PaymentsCache]
}