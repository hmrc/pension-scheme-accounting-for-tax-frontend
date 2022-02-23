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
import controllers.financialOverview.{routes => financialOverview}
import controllers.financialStatement.paymentsAndCharges.routes.{PaymentsAndChargeDetailsController, PaymentsAndChargesInterestController}
import helpers.FormatHelper._
import models.ChargeDetailsFilter
import models.ChargeDetailsFilter.{Overdue, Upcoming}
import models.financialStatement.PaymentOrChargeType.{AccountingForTaxCharges, getPaymentOrChargeType}
import models.financialStatement.SchemeFSChargeType._
import models.financialStatement.{PaymentOrChargeType, SchemeFS, SchemeFSChargeType}
import models.viewModels.financialOverview.{PaymentsAndChargesDetails => FinancialPaymentAndChargesDetails}
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

  def getPaymentsAndCharges(srn: String, schemeFS: Seq[SchemeFS], chargeDetailsFilter: ChargeDetailsFilter, paymentOrChargeType: PaymentOrChargeType)
                           (implicit messages: Messages): Table = {

    val chargeRefs: Seq[String] = schemeFS.map(_.chargeReference)

    val seqPayments: Seq[PaymentsAndChargesDetails] = schemeFS.flatMap { paymentOrCharge =>
      paymentsAndChargesDetails(paymentOrCharge, srn, chargeRefs, chargeDetailsFilter, paymentOrChargeType)
    }

    mapToTable(seqPayments)
  }

  case class IndexRef(chargeType: String, chargeReference: String)

  def getPaymentsAndChargesNew(srn: String,
                               schemeFS: Seq[SchemeFS],
                               mapChargeTypeVersion: Map[SchemeFSChargeType, Option[Int]],
                               chargeDetailsFilter: ChargeDetailsFilter
                              )
                              (implicit messages: Messages, hc: HeaderCarrier, ec: ExecutionContext): Table = {
    val indexRefs: Seq[IndexRef] = schemeFS.map { x => IndexRef(x.chargeType.toString, x.chargeReference) }
    val chargeRefs: Seq[String] = schemeFS.map(_.chargeReference)
    val seqPayments: Seq[FinancialPaymentAndChargesDetails] = schemeFS.flatMap { paymentOrCharge =>
      paymentsAndChargesDetailsNew(paymentOrCharge, srn, indexRefs, chargeRefs, mapChargeTypeVersion, chargeDetailsFilter)
    }

    mapToNewTable(seqPayments)
  }

  val extractUpcomingCharges: Seq[SchemeFS] => Seq[SchemeFS] = schemeFS =>
    schemeFS.filter(charge => charge.dueDate.nonEmpty && !charge.dueDate.get.isBefore(DateHelper.today))

  def getOverdueCharges(schemeFS: Seq[SchemeFS]): Seq[SchemeFS] =
    schemeFS
      .filter(_.dueDate.nonEmpty)
      .filter(_.dueDate.get.isBefore(DateHelper.today))

  val isPaymentOverdue: SchemeFS => Boolean = data => data.amountDue > BigDecimal(0.00) && data.dueDate.exists(_.isBefore(DateHelper.today))

  private def paymentsAndChargesDetails(
                                         details: SchemeFS,
                                         srn: String,
                                         chargeRefs: Seq[String],
                                         chargeDetailsFilter: ChargeDetailsFilter,
                                         paymentOrChargeType: PaymentOrChargeType
                                       )(implicit messages: Messages): Seq[PaymentsAndChargesDetails] = {

    val onlyAFTAndOTCChargeTypes: Boolean =
      details.chargeType == PSS_AFT_RETURN || details.chargeType == PSS_OTC_AFT_RETURN

    val period: String = if (paymentOrChargeType == AccountingForTaxCharges) details.periodStartDate.toString else details.periodEndDate.getYear.toString

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

  //scalastyle:off parameter.number
  // scalastyle:off method.length
  //scalastyle:off cyclomatic.complexity
  private def paymentsAndChargesDetailsNew(
                                            details: SchemeFS,
                                            srn: String,
                                            indexRefs: Seq[IndexRef],
                                            chargeRefs: Seq[String],
                                            mapChargeTypeVersion: Map[SchemeFSChargeType, Option[Int]],
                                            chargeDetailsFilter: ChargeDetailsFilter
                                          )(implicit messages: Messages, hc: HeaderCarrier,
                                            ec: ExecutionContext): Seq[FinancialPaymentAndChargesDetails] = {

    val ref: Map[String, Seq[IndexRef]] = indexRefs.groupBy(_.chargeType)

    val chargeRefsForChargeTypes = ref.map{
      item =>
        val chargeRefs = item._2.map{
           yy =>
            yy.chargeReference
        }
        Tuple2(item._1, chargeRefs)
    }

    chargeRefsForChargeTypes

//
//    ref Map(Excess relief paid -> Vector(IndexRef(Excess relief paid, XA002610150203)), Interest on excess relief -> Vector (IndexRef(Interest on excess relief, XA002610150204)), Accounting
//    for Tax
//    return -> Vector (IndexRef(Accounting
//    for Tax
//    return
//    , XA10000000001
//    ), IndexRef(Accounting
//    for Tax
//    return
//    , XA10000000003
//    ), IndexRef(Accounting
//    for Tax
//    return
//    , XA10000000006
//    ) ), Overseas transfer charge -> Vector(IndexRef(Overseas transfer charge, XA10000000002))
//    )
//

    println("\n\n\n\n\n\n\nref " + ref)
    val index = ref.find(_._1 == details.chargeType.toString).map {
      x => x._2.find(_.chargeReference == details.chargeReference)
    }
    println("\n\n\n\n\n\n\nindex " + index)
    val chargeType = getPaymentOrChargeType(details.chargeType)
    val paymentOrChargeType = details.chargeType
    val onlyAFTAndOTCChargeTypes: Boolean =
      paymentOrChargeType == PSS_AFT_RETURN || paymentOrChargeType == PSS_OTC_AFT_RETURN
    val ifAFTAndOTCChargeTypes: Boolean =
      onlyAFTAndOTCChargeTypes || paymentOrChargeType == PSS_AFT_RETURN_INTEREST || paymentOrChargeType == PSS_OTC_AFT_RETURN_INTEREST

    val suffix: (Option[String], Option[Int]) = (ifAFTAndOTCChargeTypes, mapChargeTypeVersion.find(_._1 == paymentOrChargeType).flatMap(_._2)) match {
      case (true, Some(version)) => (Some(s" submission $version"), Some(version))
      case _ => (None, None)
    }

    val periodValue: String = if (chargeType.toString == "AccountingForTaxCharges") {
      details.periodStartDate.toString
    } else {
      details.periodEndDate.getYear.toString
    }

    val seqChargeRefs = chargeRefsForChargeTypes.find(_._1 == details.chargeType.toString)  match {
      case Some(found) => found._2
      case _ => Nil
    }


    seqChargeRefs.indexOf(details.chargeReference)

    val chargeDetailsItemWithStatus: PaymentAndChargeStatus => FinancialPaymentAndChargesDetails =
      status =>
        FinancialPaymentAndChargesDetails(
          chargeType = details.chargeType.toString + suffix._1.getOrElse(""),
          chargeReference = details.chargeReference,
          originalChargeAmount = s"${formatCurrencyAmountAsString(details.totalAmount)}",
          paymentDue = s"${formatCurrencyAmountAsString(details.amountDue)}",
          status = status,
          period = setPeriod(details.chargeType, details.periodStartDate, details.periodEndDate),
          redirectUrl = financialOverview.PaymentsAndChargeDetailsController.onPageLoad(
            srn, periodValue, chargeRefs.indexOf(details.chargeReference).toString, chargeType, suffix._2, chargeDetailsFilter).url,
          visuallyHiddenText = messages("paymentsAndCharges.visuallyHiddenText", details.chargeReference)
        )

    (onlyAFTAndOTCChargeTypes, details.amountDue > 0) match {
      case (true, true) if details.accruedInterestTotal > 0 =>
        val interestChargeType =
          if (details.chargeType == PSS_AFT_RETURN) PSS_AFT_RETURN_INTEREST else PSS_OTC_AFT_RETURN_INTEREST
        Seq(
          chargeDetailsItemWithStatus(PaymentOverdue),
          FinancialPaymentAndChargesDetails(
            chargeType = interestChargeType.toString + suffix._1.getOrElse(""),
            chargeReference = messages("paymentsAndCharges.chargeReference.toBeAssigned"),
            originalChargeAmount = "",
            paymentDue = s"${formatCurrencyAmountAsString(details.accruedInterestTotal)}",
            status = InterestIsAccruing,
            period = setPeriod(interestChargeType, details.periodStartDate, details.periodEndDate),
            redirectUrl = financialOverview.PaymentsAndChargesInterestController.onPageLoad(
              srn, periodValue, chargeRefs.indexOf(details.chargeReference).toString, chargeType, suffix._2, chargeDetailsFilter).url,
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

  private def setPeriod(chargeType: SchemeFSChargeType, periodStartDate: LocalDate, periodEndDate: LocalDate): String = {
    chargeType match {
      case PSS_AFT_RETURN | PSS_OTC_AFT_RETURN | PSS_AFT_RETURN_INTEREST | PSS_OTC_AFT_RETURN_INTEREST
           | AFT_MANUAL_ASST | AFT_MANUAL_ASST_INTEREST | OTC_MANUAL_ASST | OTC_MANUAL_ASST_INTEREST =>
        "Quarter: " + formatStartDate(periodStartDate) + " to " + formatDateDMY(periodEndDate)
      case PSS_CHARGE | PSS_CHARGE_INTEREST | CONTRACT_SETTLEMENT | CONTRACT_SETTLEMENT_INTEREST =>
        "Period: " + formatStartDate(periodStartDate) + " to " + formatDateDMY(periodEndDate)
      case EXCESS_RELIEF_PAID =>
        "Tax Year: " + formatDateDMY(periodStartDate) + " to " + formatDateDMY(periodEndDate)
      case EXCESS_RELIEF_INTEREST =>
        "Tax Period: " + formatDateDMY(periodStartDate) + " to " + formatDateDMY(periodEndDate)
      case _ => ""
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

  private def htmlStatusNew(data: FinancialPaymentAndChargesDetails)
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

  private def mapToNewTable(allPayments: Seq[FinancialPaymentAndChargesDetails])
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
        Cell(Literal(s"${data.chargeReference}")),
        Cell(Literal(data.originalChargeAmount)),
        Cell(Literal(data.paymentDue)),
        Cell(htmlStatusNew(data), classes = Nil)
      )
    }


    Table(
      head = head,
      rows = rows,
      attributes = Map("role" -> "table")
    )
  }

  def getChargeDetailsForSelectedCharge(schemeFS: SchemeFS)
                                       (implicit messages: Messages): Seq[SummaryList.Row] = {
    originalAmountChargeDetailsRow(schemeFS) ++ paymentsAndCreditsChargeDetailsRow(schemeFS) ++
      stoodOverAmountChargeDetailsRow(schemeFS) ++ totalAmountDueChargeDetailsRow(schemeFS)
  }

  private def originalAmountChargeDetailsRow(schemeFS: SchemeFS)
                                            (implicit messages: Messages): Seq[SummaryList.Row] = {
    val value = if (schemeFS.totalAmount < 0) {
      s"${formatCurrencyAmountAsString(schemeFS.totalAmount.abs)} ${messages("paymentsAndCharges.credit")}"
    } else {
      s"${formatCurrencyAmountAsString(schemeFS.totalAmount)}"
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
            if (schemeFS.totalAmount < 0) Nil else Seq("govuk-!-width-one-quarter", "govuk-table__cell--numeric")
        ),
        actions = Nil
      ))
  }

  private def paymentsAndCreditsChargeDetailsRow(schemeFS: SchemeFS): Seq[SummaryList.Row] =
    if (schemeFS.totalAmount - schemeFS.amountDue - schemeFS.stoodOverAmount > 0) {
      Seq(
        Row(
          key = Key(
            content = msg"paymentsAndCharges.chargeDetails.payments",
            classes = Seq("govuk-!-padding-left-0", "govuk-!-width-three-quarters")
          ),
          value = Value(
            content = Literal(s"-${
              formatCurrencyAmountAsString(
                schemeFS.totalAmount - schemeFS.amountDue - schemeFS.stoodOverAmount
              )
            }"),
            classes = Seq("govuk-!-width-one-quarter", "govuk-table__cell--numeric")
          ),
          actions = Nil
        ))
    } else {
      Nil
    }

  private def stoodOverAmountChargeDetailsRow(schemeFS: SchemeFS): Seq[SummaryList.Row] =
    if (schemeFS.stoodOverAmount > 0) {
      Seq(
        Row(
          key = Key(
            content = msg"paymentsAndCharges.chargeDetails.stoodOverAmount",
            classes = Seq("govuk-!-padding-left-0", "govuk-!-width-three-quarters")
          ),
          value = Value(
            content = Literal(s"-${formatCurrencyAmountAsString(schemeFS.stoodOverAmount)}"),
            classes = Seq("govuk-!-width-one-quarter", "govuk-table__cell--numeric")
          ),
          actions = Nil
        ))
    } else {
      Nil
    }

  private def totalAmountDueChargeDetailsRow(schemeFS: SchemeFS): Seq[SummaryList.Row] = {
    val amountDueKey: Content = (schemeFS.dueDate, schemeFS.amountDue > 0) match {
      case (Some(date), true) =>
        msg"paymentsAndCharges.chargeDetails.amountDue".withArgs(date.format(dateFormatterDMY))
      case _ =>
        msg"paymentsAndCharges.chargeDetails.noAmountDueDate"
    }
    if (schemeFS.totalAmount > 0) {
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
            content = Literal(s"${formatCurrencyAmountAsString(schemeFS.amountDue)}"),
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

  def getTypeParam(paymentType: PaymentOrChargeType)(implicit messages: Messages): String =
    if (paymentType == AccountingForTaxCharges) {
      messages(s"paymentOrChargeType.${paymentType.toString}")
    } else {
      messages(s"paymentOrChargeType.${paymentType.toString}").toLowerCase()
    }

}

import models.SchemeDetails

case class PaymentsCache(loggedInId: String, srn: String, schemeDetails: SchemeDetails, schemeFS: Seq[SchemeFS])

object PaymentsCache {
  implicit val format: OFormat[PaymentsCache] = Json.format[PaymentsCache]
}