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

package controllers.financialOverview

import controllers.actions._
import models.LocalDateBinder._
import models.financialStatement.PaymentOrChargeType.{AccountingForTaxCharges, getPaymentOrChargeType}
import models.financialStatement.SchemeFSChargeType.{PSS_AFT_RETURN, PSS_AFT_RETURN_INTEREST, PSS_OTC_AFT_RETURN}
import models.financialStatement.{PaymentOrChargeType, SchemeFS}
import models.requests.IdentifierRequest
import models.{ChargeDetailsFilter, Submission}
import play.api.Logger
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.libs.json.{JsObject, Json}
import play.api.mvc._
import renderer.Renderer
import services.financialOverview.PaymentsAndChargesService
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.{Html, NunjucksSupport}
import utils.DateHelper.dateFormatterDMY

import java.time.LocalDate
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class PaymentsAndChargeDetailsController @Inject()(
                                                    override val messagesApi: MessagesApi,
                                                    identify: IdentifierAction,
                                                    allowAccess: AllowAccessActionProviderForIdentifierRequest,
                                                    val controllerComponents: MessagesControllerComponents,
                                                    paymentsAndChargesService: PaymentsAndChargesService,
                                                    renderer: Renderer
                                                  )(implicit ec: ExecutionContext)
  extends FrontendBaseController
    with I18nSupport
    with NunjucksSupport {

  private val logger = Logger(classOf[PaymentsAndChargeDetailsController])

  def onPageLoad(srn: String, pstr: String, period: String, index: String,
                 paymentOrChargeType: PaymentOrChargeType, version: Option[Int],
                 submittedDate: Option[String], journeyType: ChargeDetailsFilter): Action[AnyContent] =
    (identify andThen allowAccess()).async {
    implicit request =>
      paymentsAndChargesService.getPaymentsForJourney(request.idOrException, srn, journeyType).flatMap { paymentsCache =>
        val schemeFS: Seq[SchemeFS] = getFilteredPayments(paymentsCache.schemeFS, period, paymentOrChargeType)
        buildPage(schemeFS, period, index, paymentsCache.schemeDetails.schemeName, srn, pstr, paymentOrChargeType, journeyType, submittedDate, version)
      }
  }

  //scalastyle:off parameter.number
  private def buildPage(
                         filteredCharges: Seq[SchemeFS],
                         period: String,
                         index: String,
                         schemeName: String,
                         srn: String,
                         pstr: String,
                         paymentOrChargeType: PaymentOrChargeType,
                         journeyType: ChargeDetailsFilter,
                         submittedDate: Option[String],
                         version: Option[Int]
                       )(
                         implicit request: IdentifierRequest[AnyContent]
                       ): Future[Result] = {

    val chargeRefs: Seq[String] = filteredCharges.map(_.chargeReference)
    val interestUrl = controllers.financialOverview.routes.PaymentsAndChargesInterestController
        .onPageLoad(srn, pstr, period, index, paymentOrChargeType, version, submittedDate, journeyType).url
    if (chargeRefs.size > index.toInt) {
      filteredCharges.find(_.chargeReference == chargeRefs(index.toInt)) match {
        case Some(schemeFs) =>
          val returnUrl = routes.PaymentsAndChargesController.onPageLoad(srn, pstr, journeyType).url
          renderer.render(
            template = "financialOverview/paymentsAndChargeDetails.njk",
            ctx =
              summaryListData(srn, period, schemeFs, schemeName, returnUrl, paymentOrChargeType, interestUrl, version, submittedDate, journeyType)
          ).map(Ok(_))
        case _ =>
          logger.warn(
            s"No Payments and Charge details found for the " +
              s"selected charge reference ${chargeRefs(index.toInt)}"
          )
          Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad))
      }
    } else {
      logger.warn(
        s"[paymentsAndCharges.PaymentsAndChargeDetailsController][IndexOutOfBoundsException]:" +
          s"index $period/$index of attempted"
      )
      Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad))
    }
  }

  private def summaryListData(srn: String, period: String, schemeFS: SchemeFS, schemeName: String,
                              returnUrl: String, paymentOrChargeType: PaymentOrChargeType, interestUrl: String, version: Option[Int],
                              submittedDate: Option[String], journeyType: ChargeDetailsFilter)
                             (implicit messages: Messages): JsObject = {
    val htmlInsetText = (schemeFS.dueDate, schemeFS.accruedInterestTotal > 0, schemeFS.amountDue > 0) match {
      case (Some(date), true, true) =>
        Html(
          s"<h2 class=govuk-heading-s>${messages("paymentsAndCharges.chargeDetails.interestAccruing")}</h2>" +
            s"<p class=govuk-body>${messages("financialPaymentsAndCharges.chargeDetails.amount.not.paid.by.dueDate.line1")}" +
            s" <span class=govuk-!-font-weight-bold>${messages("financialPaymentsAndCharges.chargeDetails.amount.not.paid.by.dueDate.line2", schemeFS.accruedInterestTotal)}</span>"+
            s" <span>${messages("financialPaymentsAndCharges.chargeDetails.amount.not.paid.by.dueDate.line3", date.format(dateFormatterDMY))}<span>" +
            s"<p class=govuk-body><span><a id='breakdown' class=govuk-link href=$interestUrl>" +
            s" ${messages("paymentsAndCharges.chargeDetails.interest.paid")}</a></span></p>"
        )
      case _ =>
        Html("")
    }
    Json.obj(
      "chargeDetailsList" -> paymentsAndChargesService.getChargeDetailsForSelectedCharge(schemeFS, journeyType, submittedDate),
      "tableHeader" -> tableHeader(schemeFS),
      "schemeName" -> schemeName,
      "chargeType" ->  (version match {
        case Some(value) => schemeFS.chargeType.toString + s" submission $value"
        case _ => schemeFS.chargeType.toString
      }),
        "versionValue" -> (version match {
        case Some(value) => s" submission $value"
        case _ => Nil
      }),
      "isPaymentOverdue" -> isPaymentOverdue(schemeFS),
      "insetText" -> htmlInsetText,
      "interest" -> schemeFS.accruedInterestTotal,
      "returnLinkBasedOnJourney" -> msg"financialPaymentsAndCharges.returnLink.${journeyType.toString}",
      "returnUrl" -> returnUrl
    ) ++ returnHistoryUrl(srn, period, paymentOrChargeType, version.getOrElse(0)) ++ optHintText(schemeFS)
  }

  private def optHintText(schemeFS: SchemeFS)(implicit messages: Messages): JsObject =
    if (schemeFS.chargeType == PSS_AFT_RETURN_INTEREST && schemeFS.amountDue == BigDecimal(0.00)) {
      Json.obj("hintText" -> messages("paymentsAndCharges.interest.hint"))
    } else {
      Json.obj()
    }

  private def returnHistoryUrl(srn: String, period: String, paymentOrChargeType: PaymentOrChargeType, version:Int): JsObject =
    if(paymentOrChargeType == AccountingForTaxCharges) {
      Json.obj("returnHistoryURL" -> controllers.routes.AFTSummaryController.onPageLoad(srn, LocalDate.parse(period), Submission, version).url)
    } else {
      Json.obj()
    }

  private def isPaymentOverdue(schemeFS: SchemeFS): Boolean =
    (schemeFS.amountDue > 0 && schemeFS.accruedInterestTotal > 0
      && (schemeFS.chargeType == PSS_AFT_RETURN || schemeFS.chargeType == PSS_OTC_AFT_RETURN))

  private def tableHeader(schemeFS: SchemeFS): String =
    paymentsAndChargesService.setPeriod(
      schemeFS.chargeType,
      schemeFS.periodStartDate,
      schemeFS.periodEndDate
    )

  private def getFilteredPayments(payments: Seq[SchemeFS], period: String, paymentOrChargeType: PaymentOrChargeType): Seq[SchemeFS] =
    if(paymentOrChargeType == AccountingForTaxCharges) {
      val startDate: LocalDate = LocalDate.parse(period)
      payments.filter(p => getPaymentOrChargeType(p.chargeType) == AccountingForTaxCharges).filter(_.periodStartDate == startDate)
    } else {
        payments.filter(p => getPaymentOrChargeType(p.chargeType) == paymentOrChargeType).filter(_.periodEndDate.getYear == period.toInt)
    }

}