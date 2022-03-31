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

package services.paymentsAndCharges

import connectors.FinancialStatementConnector
import connectors.cache.FinancialInfoCacheConnector
import controllers.chargeB.{routes => _}
import controllers.financialStatement.paymentsAndCharges.routes.{PaymentsAndChargeDetailsController, PaymentsAndChargesInterestController}
import helpers.FormatHelper._
import models.ChargeDetailsFilter
import models.ChargeDetailsFilter.{Overdue, Upcoming}
import models.financialStatement.PaymentOrChargeType.AccountingForTaxCharges
import models.financialStatement.SchemeFSChargeType._
import models.financialStatement.{PaymentOrChargeType, SchemeFSDetail}
import models.viewModels.paymentsAndCharges.PaymentAndChargeStatus.{InterestIsAccruing, NoStatus, PaymentOverdue}
import models.viewModels.paymentsAndCharges.{PaymentAndChargeStatus, PaymentsAndChargesDetails}
import play.api.i18n.Messages
import play.api.libs.json.{JsSuccess, Json, OFormat}
import services.SchemeService
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.viewmodels.SummaryList.{Key, Row, Value}
import uk.gov.hmrc.viewmodels.Text.Literal
import uk.gov.hmrc.viewmodels.{Content, Html, SummaryList, _}
import utils.DateHelper
import utils.DateHelper.dateFormatterDMY
import viewmodels.Table
import viewmodels.Table.Cell

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class PaymentsAndChargesService @Inject()(schemeService: SchemeService,
                                          fsConnector: FinancialStatementConnector,
                                          financialInfoCacheConnector: FinancialInfoCacheConnector
                                         ) {

  def getPaymentsAndCharges(srn: String, schemeFSDetail: Seq[SchemeFSDetail], chargeDetailsFilter: ChargeDetailsFilter,
                            paymentOrChargeType: PaymentOrChargeType)
                           (implicit messages: Messages): Table = {

        val chargeRefs: Seq[String] = schemeFSDetail.map(_.chargeReference)

        val seqPayments: Seq[PaymentsAndChargesDetails] = schemeFSDetail.flatMap { paymentOrCharge =>
            paymentsAndChargesDetails(paymentOrCharge, srn, chargeRefs, chargeDetailsFilter, paymentOrChargeType)
          }

        mapToTable(seqPayments)
    }

  val extractUpcomingCharges: Seq[SchemeFSDetail] => Seq[SchemeFSDetail] = schemeFSDetail =>{
    schemeFSDetail.filter(charge => charge.dueDate.nonEmpty && !charge.dueDate.get.isBefore(DateHelper.today))}

  def getOverdueCharges(schemeFSDetail: Seq[SchemeFSDetail]): Seq[SchemeFSDetail] =
    schemeFSDetail
      .filter(_.dueDate.nonEmpty)
      .filter(_.dueDate.get.isBefore(DateHelper.today))

  def getDueCharges(schemeFSDetail: Seq[SchemeFSDetail]): Seq[SchemeFSDetail] =
    schemeFSDetail.filter(_.dueDate.nonEmpty)

  val isPaymentOverdue: SchemeFSDetail => Boolean = data => data.amountDue > BigDecimal(0.00) && data.dueDate.exists(_.isBefore(DateHelper.today))

  def getInterestCharges(schemeFSDetail: Seq[SchemeFSDetail]): Seq[SchemeFSDetail] =
    schemeFSDetail
      .filter(_.accruedInterestTotal >= BigDecimal(0.00))

  private def paymentsAndChargesDetails(
                                         details: SchemeFSDetail,
                                         srn: String,
                                         chargeRefs: Seq[String],
                                         chargeDetailsFilter: ChargeDetailsFilter,
                                         paymentOrChargeType: PaymentOrChargeType
                                       )(implicit messages: Messages): Seq[PaymentsAndChargesDetails] = {

    val onlyAFTAndOTCChargeTypes: Boolean =
    details.chargeType == PSS_AFT_RETURN || details.chargeType == PSS_OTC_AFT_RETURN

    val period: String = if (paymentOrChargeType == AccountingForTaxCharges) {
      details.periodStartDate.map(_.toString).getOrElse("")
    } else {
      details.periodEndDate.map(_.getYear.toString).getOrElse("")
    }

    val chargeDetailsItemWithStatus: PaymentAndChargeStatus => PaymentsAndChargesDetails =
      status => PaymentsAndChargesDetails(
        chargeType = details.chargeType.toString,
        chargeReference = details.chargeReference,
        amountDue = s"${formatCurrencyAmountAsString(details.amountDue)}",
        status = status,
        redirectUrl = PaymentsAndChargeDetailsController.onPageLoad(
          srn, period, chargeRefs.indexOf(details.chargeReference).toString, paymentOrChargeType, chargeDetailsFilter).url,
        visuallyHiddenText = messages("paymentsAndCharges.visuallyHiddenText", details.chargeReference)
      )

    (onlyAFTAndOTCChargeTypes, details.amountDue > 0) match {

      case (true, true) if details.accruedInterestTotal > 0 =>
        val interestChargeType =
          if (details.chargeType == PSS_AFT_RETURN) PSS_AFT_RETURN_INTEREST else PSS_OTC_AFT_RETURN_INTEREST

        Seq(
          chargeDetailsItemWithStatus(PaymentOverdue),
          PaymentsAndChargesDetails(
            chargeType = interestChargeType.toString,
            chargeReference = messages("paymentsAndCharges.chargeReference.toBeAssigned"),
            amountDue = s"${formatCurrencyAmountAsString(details.accruedInterestTotal)}",
            status = InterestIsAccruing,
            redirectUrl = PaymentsAndChargesInterestController.onPageLoad(
              srn, period, chargeRefs.indexOf(details.chargeReference).toString, paymentOrChargeType, chargeDetailsFilter).url,
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

  private def htmlStatus(data: PaymentsAndChargesDetails)
                        (implicit messages: Messages): Html = {
    val (classes, content) = (data.status, data.amountDue) match {
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

  private def mapToTable(allPayments: Seq[PaymentsAndChargesDetails])
                        (implicit messages: Messages): Table = {

    val head = Seq(
      Cell(msg"paymentsAndCharges.chargeType.table"),
      Cell(msg"paymentsAndCharges.totalDue.table", classes = Seq("govuk-!-font-weight-bold")),
      Cell(msg"paymentsAndCharges.chargeReference.table", classes = Seq("govuk-!-font-weight-bold")),
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
          s"<span class=govuk-visually-hidden>${data.visuallyHiddenText}</span> </a>")

      Seq(
        Cell(htmlChargeType, classes = Seq("govuk-!-width-two-thirds-quarter")),
        Cell(Literal(data.amountDue), classes = Seq("govuk-!-width-one-quarter")),
        Cell(Literal(s"${data.chargeReference}"), classes = Seq("govuk-!-width-one-quarter")),
        Cell(htmlStatus(data), classes = Nil)
      )
    }


      Table(
        head = head,
        rows = rows,
        attributes = Map("role" -> "table")
    )
  }

  def getChargeDetailsForSelectedCharge(schemeFSDetail: SchemeFSDetail)
                                       (implicit messages: Messages): Seq[SummaryList.Row] = {
    originalAmountChargeDetailsRow(schemeFSDetail) ++ paymentsAndCreditsChargeDetailsRow(schemeFSDetail) ++
      stoodOverAmountChargeDetailsRow(schemeFSDetail) ++ totalAmountDueChargeDetailsRow(schemeFSDetail)
  }

  private def originalAmountChargeDetailsRow(schemeFSDetail: SchemeFSDetail)
                                            (implicit messages: Messages): Seq[SummaryList.Row] = {
    val value = if (schemeFSDetail.totalAmount < 0) {
      s"${formatCurrencyAmountAsString(schemeFSDetail.totalAmount.abs)} ${messages("paymentsAndCharges.credit")}"
    } else {
      s"${formatCurrencyAmountAsString(schemeFSDetail.totalAmount)}"
    }
    Seq(
      Row(
        key = Key(
          content = msg"paymentsAndCharges.chargeDetails.originalChargeAmount",
          classes = Seq("govuk-!-padding-left-0", "govuk-!-width-three-quarters")
        ),
        value = Value(
          content = Literal(value),
          classes =
            if (schemeFSDetail.totalAmount < 0) Nil else Seq("govuk-!-width-one-quarter", "govuk-table__cell--numeric")
        ),
        actions = Nil
      ))
  }

  private def paymentsAndCreditsChargeDetailsRow(schemeFSDetail: SchemeFSDetail): Seq[SummaryList.Row] =
    if (schemeFSDetail.totalAmount - schemeFSDetail.amountDue - schemeFSDetail.stoodOverAmount > 0) {
      Seq(
        Row(
          key = Key(
            content = msg"paymentsAndCharges.chargeDetails.payments",
            classes = Seq("govuk-!-padding-left-0", "govuk-!-width-three-quarters")
          ),
          value = Value(
            content = Literal(s"-${
              formatCurrencyAmountAsString(
                schemeFSDetail.totalAmount - schemeFSDetail.amountDue - schemeFSDetail.stoodOverAmount
              )
            }"),
            classes = Seq("govuk-!-width-one-quarter", "govuk-table__cell--numeric")
          ),
          actions = Nil
        ))
    } else {
      Nil
    }

  private def stoodOverAmountChargeDetailsRow(schemeFSDetail: SchemeFSDetail): Seq[SummaryList.Row] =
    if (schemeFSDetail.stoodOverAmount > 0) {
      Seq(
        Row(
          key = Key(
            content = msg"paymentsAndCharges.chargeDetails.stoodOverAmount",
            classes = Seq("govuk-!-padding-left-0", "govuk-!-width-three-quarters")
          ),
          value = Value(
            content = Literal(s"-${formatCurrencyAmountAsString(schemeFSDetail.stoodOverAmount)}"),
            classes = Seq("govuk-!-width-one-quarter", "govuk-table__cell--numeric")
          ),
          actions = Nil
        ))
    } else {
      Nil
    }

  private def totalAmountDueChargeDetailsRow(schemeFSDetail: SchemeFSDetail): Seq[SummaryList.Row] = {
    val amountDueKey: Content = (schemeFSDetail.dueDate, schemeFSDetail.amountDue > 0) match {
      case (Some(date), true) =>
        msg"paymentsAndCharges.chargeDetails.amountDue".withArgs(date.format(dateFormatterDMY))
      case _ =>
        msg"paymentsAndCharges.chargeDetails.noAmountDueDate"
    }
    if (schemeFSDetail.totalAmount > 0) {
      Seq(
        Row(
          key = Key(
            content = amountDueKey,
            classes = Seq(
              "govuk-table__cell--numeric",
              "govuk-!-padding-right-0",
              "govuk-!-width-three-quarters",
              "govuk-!-font-weight-bold"
            )
          ),
          value = Value(
            content = Literal(s"${formatCurrencyAmountAsString(schemeFSDetail.amountDue)}"),
            classes = Seq("govuk-!-width-one-quarter", "govuk-table__cell--numeric", "govuk-!-font-weight-bold")
          ),
          actions = Nil
        ))
    } else {
      Nil
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

  def getTypeParam(paymentType: PaymentOrChargeType)(implicit messages: Messages): String =
    if (paymentType == AccountingForTaxCharges) {
      messages(s"paymentOrChargeType.${paymentType.toString}")
    } else {
      messages(s"paymentOrChargeType.${paymentType.toString}").toLowerCase()
    }

}

import models.SchemeDetails

case class PaymentsCache(loggedInId: String, srn: String, schemeDetails: SchemeDetails, schemeFSDetail: Seq[SchemeFSDetail])
object PaymentsCache {
  implicit val format: OFormat[PaymentsCache] = Json.format[PaymentsCache]
}