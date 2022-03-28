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

import config.FrontendAppConfig
import connectors.FinancialStatementConnector
import connectors.cache.FinancialInfoCacheConnector
import controllers.chargeB.{routes => _}
import controllers.financialOverview.routes
import helpers.FormatHelper._
import models.ChargeDetailsFilter
import models.ChargeDetailsFilter.{All, Overdue, Upcoming}
import models.financialStatement.FSClearingReason._
import models.financialStatement.PaymentOrChargeType.{AccountingForTaxCharges, getPaymentOrChargeType}
import models.financialStatement.SchemeFSChargeType._
import models.financialStatement.{DocumentLineItemDetail, PaymentOrChargeType, SchemeFSChargeType, SchemeFSDetail}
import models.viewModels.financialOverview.{PaymentsAndChargesDetails => FinancialPaymentAndChargesDetails}
import models.viewModels.paymentsAndCharges.PaymentAndChargeStatus
import models.viewModels.paymentsAndCharges.PaymentAndChargeStatus.{InterestIsAccruing, NoStatus, PaymentOverdue}
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


  case class IndexRef(chargeType: String, chargeReference: String, period: String)

  def getPaymentsAndCharges(srn: String,
                            pstr: String,
                            schemeFSDetail: Seq[SchemeFSDetail],
                            mapChargeTypesVersionAndDate: Map[SchemeFSChargeType, (Option[Int], Option[LocalDate])],
                            chargeDetailsFilter: ChargeDetailsFilter
                              )
                              (implicit messages: Messages): Table = {

    val chargeRefForAll: Seq[String] = schemeFSDetail.map(_.chargeReference)

    val seqPayments: Seq[FinancialPaymentAndChargesDetails] = schemeFSDetail.flatMap { paymentOrCharge =>
      paymentsAndChargesDetails(paymentOrCharge, srn, pstr, chargeRefForAll, chargeRefs(schemeFSDetail), mapChargeTypesVersionAndDate, chargeDetailsFilter)
    }

    mapToTable(seqPayments, chargeDetailsFilter)
  }


  def chargeRefs (schemeFSDetail: Seq[SchemeFSDetail]) : Map[(String,String), Seq[String]] = {
    val indexRefs: Seq[IndexRef] = schemeFSDetail.map { scheme =>
      val chargeType = getPaymentOrChargeType(scheme.chargeType).toString
      val period: String = if (chargeType == AccountingForTaxCharges.toString) {
        scheme.periodStartDate.map(_.toString).getOrElse("")
      } else {
        scheme.periodEndDate.map(_.getYear.toString).getOrElse("")
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

  val isPaymentOverdue: SchemeFSDetail => Boolean = data => data.amountDue > BigDecimal(0.00) && data.dueDate.exists(_.isBefore(DateHelper.today))

  val extractUpcomingCharges: Seq[SchemeFSDetail] => Seq[SchemeFSDetail] = schemeFSDetail =>
    schemeFSDetail.filter(charge => charge.dueDate.nonEmpty
      && charge.dueDate.get.isAfter(DateHelper.today)
      && charge.amountDue > BigDecimal(0.00))

  def getOverdueCharges(schemeFSDetail: Seq[SchemeFSDetail]): Seq[SchemeFSDetail] =
    schemeFSDetail
      .filter(_.dueDate.nonEmpty)
      .filter(_.dueDate.get.isBefore(DateHelper.today))
      .filter(_.amountDue > BigDecimal(0.00))

  def getDueCharges(schemeFSDetail: Seq[SchemeFSDetail]): Seq[SchemeFSDetail] =
    schemeFSDetail
      .filter(_.amountDue >= BigDecimal(0.00))

  def getInterestCharges(schemeFSDetail: Seq[SchemeFSDetail]): Seq[SchemeFSDetail] =
    schemeFSDetail
      .filter(_.accruedInterestTotal >= BigDecimal(0.00))

  private def setSubmittedDate(submittedDate: Option[String], chargeType: SchemeFSChargeType)
                              (implicit messages: Messages): Option[String] = {
    (chargeType, submittedDate) match {
      case (PSS_AFT_RETURN  | PSS_OTC_AFT_RETURN, Some(value)) =>
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
  def getReturnUrl(srn: String, pstr: String, psaId: Option[PsaId], pspId: Option[PspId], config: FrontendAppConfig,
                   journeyType: ChargeDetailsFilter): String = {
    journeyType match {
      case All => config.schemeDashboardUrl(psaId, pspId).format(srn)
      case _ => routes.PaymentsAndChargesController.onPageLoad(srn, pstr, journeyType).url
    }
  }

  //scalastyle:off parameter.number
  // scalastyle:off method.length
  //scalastyle:off cyclomatic.complexity
  private def paymentsAndChargesDetails(
                                         details: SchemeFSDetail,
                                         srn: String,
                                         pstr: String,
                                         chargeRefForAll: Seq[String],
                                         chargeRefs: Map[(String,String), Seq[String]],
                                         mapChargeTypesVersionAndDate: Map[SchemeFSChargeType, (Option[Int], Option[LocalDate])],
                                         chargeDetailsFilter: ChargeDetailsFilter
                                          )(implicit messages: Messages): Seq[FinancialPaymentAndChargesDetails] = {



    val chargeType = getPaymentOrChargeType(details.chargeType)
    val paymentOrChargeType = details.chargeType
    val onlyAFTAndOTCChargeTypes: Boolean =
      paymentOrChargeType == PSS_AFT_RETURN || paymentOrChargeType == PSS_OTC_AFT_RETURN
    val ifAFTAndOTCChargeTypes: Boolean =
      onlyAFTAndOTCChargeTypes || paymentOrChargeType == PSS_AFT_RETURN_INTEREST || paymentOrChargeType == PSS_OTC_AFT_RETURN_INTEREST

    val (suffix, version, submittedDate) = (ifAFTAndOTCChargeTypes, mapChargeTypesVersionAndDate.get(paymentOrChargeType)) match {
      case (true, Some(Tuple2(Some(version), Some(date)))) => (Some(s" submission $version"), Some(version), Some(formatDateYMD(date)))
      case _ => (None, None, None)
    }

    val periodValue: String = if (chargeType.toString == AccountingForTaxCharges.toString) {
      details.periodStartDate.map(_.toString).getOrElse("")
    } else {
      details.periodEndDate.map(_.getYear.toString).getOrElse("")
    }

    val seqChargeRefs = chargeRefs.find(_._1 == (chargeType.toString, periodValue))  match {
      case Some(found) => found._2
      case _ => Nil
    }

    val index : String = {
      chargeDetailsFilter match {
        case All => chargeRefForAll.indexOf(details.chargeReference).toString
        case _ => seqChargeRefs.indexOf(details.chargeReference).toString
      }
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
          submittedDate = setSubmittedDate(submittedDate, details.chargeType),
          redirectUrl = routes.PaymentsAndChargeDetailsController.onPageLoad(
            srn, pstr, periodValue, index, chargeType, version, submittedDate, chargeDetailsFilter).url,
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
            redirectUrl = routes.PaymentsAndChargesInterestController.onPageLoad(
              srn, pstr, periodValue, index, chargeType, version, submittedDate, chargeDetailsFilter).url,
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

  def getTypeParam(paymentType: PaymentOrChargeType)(implicit messages: Messages): String =
    if (paymentType == AccountingForTaxCharges) {
      messages(s"paymentOrChargeType.${paymentType.toString}")
    } else {
      messages(s"paymentOrChargeType.${paymentType.toString}").toLowerCase()
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
      val linkId =
        data.chargeReference match {
          case "To be assigned" => "to-be-assigned"
          case "None" => "none"
          case _ => data.chargeReference
        }

      val htmlChargeType = (chargeDetailsFilter, data.submittedDate) match {
        case (All, Some(dateValue)) => Html(
          s"<a id=$linkId class=govuk-link href=" +
            s"${data.redirectUrl}>" +
            s"${data.chargeType} " +
            s"<span class=govuk-visually-hidden>${data.visuallyHiddenText}</span> </a>" +
            s"<p class=govuk-hint>" +
            s"$dateValue</p>")
        case (All, None) => Html(
          s"<a id=$linkId class=govuk-link href=" +
            s"${data.redirectUrl}>" +
            s"${data.chargeType} " +
            s"<span class=govuk-visually-hidden>${data.visuallyHiddenText}</span> </a>")
        case _ =>
          Html(
            s"<a id=$linkId class=govuk-link href=" +
              s"${data.redirectUrl}>" +
              s"${data.chargeType} " +
              s"<span class=govuk-visually-hidden>${data.visuallyHiddenText}</span> </a>" +
              s"<p class=govuk-hint>" +
              s"${data.period}</p>")
      }



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

  def getChargeDetailsForSelectedCharge(schemeFSDetail: SchemeFSDetail, journeyType: ChargeDetailsFilter, submittedDate: Option[String])
                                       : Seq[SummaryList.Row] = {
    dateSubmittedRow(schemeFSDetail.chargeType, submittedDate) ++ chargeReferenceRow(schemeFSDetail) ++ originalAmountChargeDetailsRow(schemeFSDetail) ++
      clearingChargeDetailsRow(schemeFSDetail.documentLineItemDetails) ++
      stoodOverAmountChargeDetailsRow(schemeFSDetail) ++ totalAmountDueChargeDetailsRow(schemeFSDetail, journeyType)
  }

  private def dateSubmittedRow(chargeType: SchemeFSChargeType, submittedDate: Option[String]): Seq[SummaryList.Row] = {
    (chargeType, submittedDate) match {
      case (PSS_AFT_RETURN | PSS_OTC_AFT_RETURN , Some(date)) =>
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
        if(documentLineItemDetail.clearedAmountItem > 0) {
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
      schemeFSDetail <- fsConnector.getSchemeFS(schemeDetails.pstr)
      paymentsCache = PaymentsCache(loggedInId, srn, schemeDetails, schemeFSDetail.seqSchemeFSDetail)
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
        case Overdue => cache.copy(schemeFSDetail = getOverdueCharges(cache.schemeFSDetail))
        case Upcoming => cache.copy(schemeFSDetail = extractUpcomingCharges(cache.schemeFSDetail))
        case _ => cache
      }
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

}

import models.SchemeDetails

case class PaymentsCache(loggedInId: String, srn: String, schemeDetails: SchemeDetails, schemeFSDetail: Seq[SchemeFSDetail])

object PaymentsCache {
  implicit val format: OFormat[PaymentsCache] = Json.format[PaymentsCache]
}