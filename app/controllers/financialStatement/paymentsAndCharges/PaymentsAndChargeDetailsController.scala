/*
 * Copyright 2021 HM Revenue & Customs
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

package controllers.financialStatement.paymentsAndCharges

import config.FrontendAppConfig
import controllers.actions._
import helpers.FormatHelper
import models.ChargeDetailsFilter
import models.LocalDateBinder._
import models.financialStatement.PaymentOrChargeType.{AccountingForTaxCharges, getPaymentOrChargeType}
import models.financialStatement.SchemeFSChargeType.{PSS_AFT_RETURN, PSS_AFT_RETURN_INTEREST, PSS_OTC_AFT_RETURN}
import models.financialStatement.{PaymentOrChargeType, SchemeFS}
import models.requests.IdentifierRequest
import play.api.Logger
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.libs.json.{JsObject, Json}
import play.api.mvc._
import renderer.Renderer
import services.paymentsAndCharges.PaymentsAndChargesService
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.{Html, NunjucksSupport}
import utils.DateHelper.{dateFormatterDMY, dateFormatterStartDate}

import java.time.LocalDate
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class PaymentsAndChargeDetailsController @Inject()(
                                                    override val messagesApi: MessagesApi,
                                                    identify: IdentifierAction,
                                                    allowAccess: AllowAccessActionProviderForIdentifierRequest,
                                                    val controllerComponents: MessagesControllerComponents,
                                                    config: FrontendAppConfig,
                                                    paymentsAndChargesService: PaymentsAndChargesService,
                                                    renderer: Renderer
                                                  )(implicit ec: ExecutionContext)
  extends FrontendBaseController
    with I18nSupport
    with NunjucksSupport {

  private val logger = Logger(classOf[PaymentsAndChargeDetailsController])

  def onPageLoad(srn: String, period: String, index: String,
                 paymentOrChargeType: PaymentOrChargeType, journeyType: ChargeDetailsFilter): Action[AnyContent] =
    (identify andThen allowAccess()).async {
    implicit request =>
      paymentsAndChargesService.getPaymentsForJourney(request.idOrException, srn, journeyType).flatMap { paymentsCache =>

        val schemeFS: Seq[SchemeFS] = getFilteredPayments(paymentsCache.schemeFS, period, paymentOrChargeType)
        buildPage(schemeFS, period, index, paymentsCache.schemeDetails.schemeName, srn, paymentOrChargeType, journeyType)
      }
  }

  private def buildPage(
                         filteredCharges: Seq[SchemeFS],
                         period: String,
                         index: String,
                         schemeName: String,
                         srn: String,
                         paymentOrChargeType: PaymentOrChargeType,
                         journeyType: ChargeDetailsFilter
                       )(
                         implicit request: IdentifierRequest[AnyContent]
                       ): Future[Result] = {

    val chargeRefs: Seq[String] = filteredCharges.map(_.chargeReference)
    val interestUrl = controllers.financialStatement.paymentsAndCharges.routes.PaymentsAndChargesInterestController
        .onPageLoad(srn, period, index, paymentOrChargeType, journeyType).url
    if (chargeRefs.size > index.toInt) {
      filteredCharges.find(_.chargeReference == chargeRefs(index.toInt)) match {
        case Some(schemeFs) =>
          val returnUrl = config.schemeDashboardUrl(request.psaId, request.pspId).format(srn)
          renderer.render(
            template = "financialStatement/paymentsAndCharges/paymentsAndChargeDetails.njk",
            ctx = summaryListData(srn, period, schemeFs, schemeName, returnUrl, paymentOrChargeType, interestUrl)
          ).map(Ok(_))
        case _ =>
          logger.warn(
            s"No Payments and Charge details found for the " +
              s"selected charge reference ${chargeRefs(index.toInt)}"
          )
          Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad()))
      }
    } else {
      logger.warn(
        s"[paymentsAndCharges.PaymentsAndChargeDetailsController][IndexOutOfBoundsException]:" +
          s"index $period/$index of attempted"
      )
      Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad()))
    }
  }

  private def summaryListData(srn: String, period: String, schemeFS: SchemeFS, schemeName: String,
                              returnUrl: String, paymentOrChargeType: PaymentOrChargeType, interestUrl: String)
                             (implicit messages: Messages): JsObject = {
    val htmlInsetText = (schemeFS.dueDate, schemeFS.accruedInterestTotal > 0, schemeFS.amountDue > 0) match {
      case (Some(_), true, true) =>
        Html(
          s"<h2 class=govuk-heading-s>${messages("paymentsAndCharges.chargeDetails.interestAccruing")}</h2>" +
            s"<p class=govuk-body>${messages("paymentsAndCharges.chargeDetails.amount.not.paid.by.dueDate")}" +
            s" <span><a id='breakdown' class=govuk-link href=$interestUrl>" +
            s"${messages("paymentsAndCharges.chargeDetails.interest.paid")}</a></span></p>"
        )
      case (Some(date), true, false) =>
        Html(
          s"<h2 class=govuk-heading-s>${messages("paymentsAndCharges.chargeDetails.interestAccrued")}</h2>" +
            s"<p class=govuk-body>${messages("paymentsAndCharges.chargeDetails.amount.paid.after.dueDate", date.format(dateFormatterDMY))}</p>"
        )
      case _ =>
        Html("")
    }

    Json.obj(
      "chargeDetailsList" -> paymentsAndChargesService.getChargeDetailsForSelectedCharge(schemeFS),
      "tableHeader" -> tableHeader(schemeFS),
      "schemeName" -> schemeName,
      "chargeType" -> schemeFS.chargeType.toString,
      "chargeReferenceTextMessage" -> chargeReferenceTextMessage(schemeFS),
      "isPaymentOverdue" -> isPaymentOverdue(schemeFS),
      "insetText" -> htmlInsetText,
      "interest" -> schemeFS.accruedInterestTotal,
      "returnUrl" -> returnUrl
    ) ++ returnHistoryUrl(srn, period, paymentOrChargeType) ++ optHintText(schemeFS)

  }

  def chargeReferenceTextMessage(schemeFS: SchemeFS)(implicit messages: Messages): String =
    if (schemeFS.totalAmount < 0) {
      messages("paymentsAndCharges.credit.information",
        s"${FormatHelper.formatCurrencyAmountAsString(schemeFS.totalAmount.abs)}")
    } else {
      messages("paymentsAndCharges.chargeDetails.chargeReference", schemeFS.chargeReference)
    }

  def optHintText(schemeFS: SchemeFS)(implicit messages: Messages): JsObject =
    if (schemeFS.chargeType == PSS_AFT_RETURN_INTEREST && schemeFS.amountDue == BigDecimal(0.00)) {
      Json.obj("hintText" -> messages("paymentsAndCharges.interest.hint"))
    } else {
      Json.obj()
    }

  def returnHistoryUrl(srn: String, period: String, paymentOrChargeType: PaymentOrChargeType): JsObject =
    if(paymentOrChargeType == AccountingForTaxCharges) {
      Json.obj("returnHistoryURL" -> controllers.amend.routes.ReturnHistoryController.onPageLoad(srn, LocalDate.parse(period)).url)
    } else {
      Json.obj()
    }

  def isPaymentOverdue(schemeFS: SchemeFS): Boolean =
    (schemeFS.amountDue > 0 && schemeFS.accruedInterestTotal > 0
      && (schemeFS.chargeType == PSS_AFT_RETURN || schemeFS.chargeType == PSS_OTC_AFT_RETURN))

  def tableHeader(schemeFS: SchemeFS)(implicit messages: Messages): String =
    messages(
      "paymentsAndCharges.caption",
      schemeFS.periodStartDate.format(dateFormatterStartDate),
      schemeFS.periodEndDate.format(dateFormatterDMY)
    )

  private def getFilteredPayments(payments: Seq[SchemeFS], period: String, paymentOrChargeType: PaymentOrChargeType): Seq[SchemeFS] =
    if(paymentOrChargeType == AccountingForTaxCharges) {
      val startDate: LocalDate = LocalDate.parse(period)
      payments.filter(p => getPaymentOrChargeType(p.chargeType) == AccountingForTaxCharges).filter(_.periodStartDate == startDate)
    } else {
        payments.filter(p => getPaymentOrChargeType(p.chargeType) == paymentOrChargeType).filter(_.periodEndDate.getYear == period.toInt)
    }

}
