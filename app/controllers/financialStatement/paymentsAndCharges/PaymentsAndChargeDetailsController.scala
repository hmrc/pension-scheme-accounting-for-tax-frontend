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
import models.LocalDateBinder._
import models.financialStatement.SchemeFS
import models.financialStatement.SchemeFSChargeType.{PSS_AFT_RETURN_INTEREST, PSS_OTC_AFT_RETURN, PSS_AFT_RETURN}
import models.requests.IdentifierRequest
import play.api.Logger
import play.api.i18n.{MessagesApi, Messages, I18nSupport}
import play.api.libs.json.{JsObject, Json}
import play.api.mvc._
import renderer.Renderer
import services.paymentsAndCharges.PaymentsAndChargesService
import uk.gov.hmrc.domain.{PsaId, PspId}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.{Html, NunjucksSupport}
import utils.DateHelper.{dateFormatterStartDate, dateFormatterDMY}

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

  def onPageLoad(srn: String, startDate: LocalDate, index: String): Action[AnyContent] = (identify andThen allowAccess()).async {
    implicit request =>
      paymentsAndChargesService.getPaymentsFromCache(request.idOrException, srn).flatMap { paymentsCache =>

        val schemeFS = paymentsCache.schemeFS.filter(_.periodStartDate == startDate)

        buildPage(schemeFS, startDate, index, paymentsCache.schemeDetails.schemeName, srn)
      }
  }

  def onPageLoadUpcoming(srn: String, startDate: LocalDate, index: String): Action[AnyContent] = (identify andThen allowAccess()).async {
    implicit request =>
      paymentsAndChargesService.getPaymentsFromCache(request.idOrException, srn).flatMap { paymentsCache =>

        val schemeFS: Seq[SchemeFS] = paymentsCache.schemeFS.filter(_.periodStartDate == startDate)
        val upcomingCharges: Seq[SchemeFS] = paymentsAndChargesService.extractUpcomingCharges(schemeFS)

        buildPage(upcomingCharges, startDate, index, paymentsCache.schemeDetails.schemeName, srn)
      }
  }

  def onPageLoadOverdue(srn: String, startDate: LocalDate, index: String): Action[AnyContent] = (identify andThen allowAccess()).async {
    implicit request =>
      paymentsAndChargesService.getPaymentsFromCache(request.idOrException, srn).flatMap { paymentsCache =>

        val schemeFS: Seq[SchemeFS] = paymentsCache.schemeFS.filter(_.periodStartDate == startDate)
        val overdueCharges: Seq[SchemeFS] = paymentsAndChargesService.getOverdueCharges(schemeFS)

        buildPage(overdueCharges, startDate, index, paymentsCache.schemeDetails.schemeName, srn)
      }
  }

  private def buildPage(
                         filteredCharges: Seq[SchemeFS],
                         startDate: LocalDate,
                         index: String,
                         schemeName: String,
                         srn: String
                       )(
                         implicit request: IdentifierRequest[AnyContent]
                       ): Future[Result] = {

    val chargeRefs: Seq[String] = filteredCharges.map(_.chargeReference)

    if (chargeRefs.size > index.toInt) {
      filteredCharges.find(_.chargeReference == chargeRefs(index.toInt)) match {
        case Some(schemeFs) =>
          renderer.render(
            template = "financialStatement/paymentsAndCharges/paymentsAndChargeDetails.njk",
            ctx = summaryListData(srn, startDate, schemeFs, schemeName, index, request.psaId, request.pspId)
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
          s"index $startDate/$index of attempted"
      )
      Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad()))
    }
  }

  private def summaryListData(srn: String, startDate: LocalDate, schemeFS: SchemeFS, schemeName: String,
                              index: String, psaId: Option[PsaId], pspId: Option[PspId])
                             (implicit messages: Messages): JsObject = {
    val htmlInsetText = (schemeFS.dueDate, schemeFS.accruedInterestTotal > 0, schemeFS.amountDue > 0) match {
      case (Some(_), true, true) =>
        Html(
          s"<h2 class=govuk-heading-s>${messages("paymentsAndCharges.chargeDetails.interestAccruing")}</h2>" +
            s"<p class=govuk-body>${messages("paymentsAndCharges.chargeDetails.amount.not.paid.by.dueDate")}" +
            s" <span><a id='breakdown' class=govuk-link href=${
              controllers.financialStatement.paymentsAndCharges.routes.PaymentsAndChargesInterestController
                .onPageLoad(srn, schemeFS.periodStartDate, index)
                .url
            }>" +
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
      "returnUrl" -> config.schemeDashboardUrl(psaId, pspId).format(srn),
      "returnHistoryURL" -> controllers.amend.routes.ReturnHistoryController.onPageLoad(srn, startDate).url
    ) ++ optHintText(schemeFS)

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

  def isPaymentOverdue(schemeFS: SchemeFS): Boolean =
    (schemeFS.amountDue > 0 && schemeFS.accruedInterestTotal > 0
      && (schemeFS.chargeType == PSS_AFT_RETURN || schemeFS.chargeType == PSS_OTC_AFT_RETURN))

  def tableHeader(schemeFS: SchemeFS)(implicit messages: Messages): String =
    messages(
      "paymentsAndCharges.caption",
      schemeFS.periodStartDate.format(dateFormatterStartDate),
      schemeFS.periodEndDate.format(dateFormatterDMY)
    )

}
