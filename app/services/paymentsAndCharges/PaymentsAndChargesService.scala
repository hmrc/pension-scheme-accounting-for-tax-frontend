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
import uk.gov.hmrc.govukfrontend.views.viewmodels.summarylist.{Key, SummaryListRow, Value}
import uk.gov.hmrc.http.HeaderCarrier
import utils.DateHelper
import utils.DateHelper.dateFormatterDMY
import uk.gov.hmrc.govukfrontend.views.viewmodels.content.{Content, HtmlContent, Text}
import uk.gov.hmrc.govukfrontend.views.viewmodels.table.{HeadCell, Table, TableRow}

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
    schemeFSDetail.filter(charge => charge.dueDate.nonEmpty
      && (charge.dueDate.get.isEqual(DateHelper.today) || charge.dueDate.get.isAfter(DateHelper.today)))}

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

  //scalastyle:off parameter.number
  //scalastyle:off method.length
  //scalastyle:off cyclomatic.complexity
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
                        (implicit messages: Messages): HtmlContent = {
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
    HtmlContent(s"<span class='$classes'>$content</span>")
  }

  private def mapToTable(allPayments: Seq[PaymentsAndChargesDetails])
                        (implicit messages: Messages): Table = {

    val head = Seq(
      HeadCell(Text(messages("paymentsAndCharges.chargeType.table"))),
      HeadCell(Text(messages("paymentsAndCharges.totalDue.table")), classes = "govuk-!-font-weight-bold"),
      HeadCell(Text(messages("paymentsAndCharges.chargeReference.table")), classes = "govuk-!-font-weight-bold"),
      HeadCell(HtmlContent(
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

      val htmlChargeType = HtmlContent(
        s"<a id=$linkId class=govuk-link href=" +
          s"${data.redirectUrl}>" +
          s"${data.chargeType} " +
          s"<span class=govuk-visually-hidden>${data.visuallyHiddenText}</span> </a>")

      Seq(
        TableRow(htmlChargeType, classes = "govuk-!-padding-right-7"),
        TableRow(Text(data.amountDue), classes ="govuk-!-padding-right-7"),
        TableRow(Text(s"${data.chargeReference}"), classes = "govuk-!-padding-right-7"),
        TableRow(htmlStatus(data))
      )
    }


      Table(
        head = Some(head),
        rows = rows,
        attributes = Map("role" -> "table")
    )
  }

  def getChargeDetailsForSelectedCharge(schemeFSDetail: SchemeFSDetail)
                                       (implicit messages: Messages): Seq[SummaryListRow] = {
    originalAmountChargeDetailsRow(schemeFSDetail) ++ paymentsAndCreditsChargeDetailsRow(schemeFSDetail) ++
      stoodOverAmountChargeDetailsRow(schemeFSDetail) ++ totalAmountDueChargeDetailsRow(schemeFSDetail)
  }

  private def originalAmountChargeDetailsRow(schemeFSDetail: SchemeFSDetail)
                                            (implicit messages: Messages): Seq[SummaryListRow] = {
    val value = if (schemeFSDetail.totalAmount < 0) {
      s"${formatCurrencyAmountAsString(schemeFSDetail.totalAmount.abs)} ${messages("paymentsAndCharges.credit")}"
    } else {
      s"${formatCurrencyAmountAsString(schemeFSDetail.totalAmount)}"
    }
    Seq(
      SummaryListRow(
        key = Key(
          content = Text(messages("paymentsAndCharges.chargeDetails.originalChargeAmount")),
          classes = "govuk-!-padding-left-0 govuk-!-width-three-quarters"
        ),
        value = Value(
          content = Text(value),
          classes =
            if (schemeFSDetail.totalAmount < 0) "" else "govuk-!-width-one-quarter govuk-table__cell--numeric")
        )
      )
  }

  private def paymentsAndCreditsChargeDetailsRow(schemeFSDetail: SchemeFSDetail)(implicit messages: Messages): Seq[SummaryListRow] =
    if (schemeFSDetail.totalAmount - schemeFSDetail.amountDue - schemeFSDetail.stoodOverAmount > 0) {
      Seq(
        SummaryListRow(
          key = Key(
            content = Text(messages("paymentsAndCharges.chargeDetails.payments")),
            classes = "govuk-!-padding-left-0 govuk-!-width-three-quarters"
          ),
          value = Value(
            content = Text(s"-${
              formatCurrencyAmountAsString(
                schemeFSDetail.totalAmount - schemeFSDetail.amountDue - schemeFSDetail.stoodOverAmount
              )
            }"),
            classes = "govuk-!-width-one-quarter govuk-table__cell--numeric"
          )
        ))
    } else {
      Nil
    }

  private def stoodOverAmountChargeDetailsRow(schemeFSDetail: SchemeFSDetail)(implicit messages: Messages): Seq[SummaryListRow] =
    if (schemeFSDetail.stoodOverAmount > 0) {
      Seq(
        SummaryListRow(
          key = Key(
            content = Text(messages("paymentsAndCharges.chargeDetails.stoodOverAmount")),
            classes = "govuk-!-padding-left-0 govuk-!-width-three-quarters"
          ),
          value = Value(
            content = Text(s"-${formatCurrencyAmountAsString(schemeFSDetail.stoodOverAmount)}"),
            classes = "govuk-!-width-one-quarter govuk-table__cell--numeric"
          )
        ))
    } else {
      Nil
    }

  private def totalAmountDueChargeDetailsRow(schemeFSDetail: SchemeFSDetail)(implicit messages: Messages): Seq[SummaryListRow] = {
    val amountDueKey: Content = (schemeFSDetail.dueDate, schemeFSDetail.amountDue > 0) match {
      case (Some(date), true) =>
        Text(messages("paymentsAndCharges.chargeDetails.amountDue", date.format(dateFormatterDMY)))
      case _ =>
        Text(messages("paymentsAndCharges.chargeDetails.noAmountDueDate"))
    }
    if (schemeFSDetail.totalAmount > 0) {
      Seq(
        SummaryListRow(
          key = Key(
            content = amountDueKey,
            classes = "govuk-table__cell--numeric govuk-!-padding-right-0 govuk-!-width-three-quarters govuk-!-font-weight-bold"
          ),
          value = Value(
            content = Text(s"${formatCurrencyAmountAsString(schemeFSDetail.amountDue)}"),
            classes = "govuk-!-width-one-quarter govuk-table__cell--numeric govuk-!-font-weight-bold"
          )
        ))
    } else {
      Nil
    }
  }

  private def saveAndReturnPaymentsCache(loggedInId: String, srn: String)
                           (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[PaymentsCache] =
    for {
      schemeDetails <- schemeService.retrieveSchemeDetails(loggedInId, srn)
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