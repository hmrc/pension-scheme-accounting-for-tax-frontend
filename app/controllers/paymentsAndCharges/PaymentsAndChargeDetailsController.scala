/*
 * Copyright 2020 HM Revenue & Customs
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

package controllers.paymentsAndCharges

import java.time.LocalDate

import config.FrontendAppConfig
import connectors.FinancialStatementConnector
import controllers.actions._
import helpers.FormatHelper
import javax.inject.Inject
import models.LocalDateBinder._
import models.financialStatement.SchemeFS
import models.financialStatement.SchemeFSChargeType.{PSS_AFT_RETURN, PSS_OTC_AFT_RETURN}
import play.api.Logger
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.libs.json.{JsObject, Json}
import play.api.mvc._
import renderer.Renderer
import services.SchemeService
import services.paymentsAndCharges.PaymentsAndChargesService
import uk.gov.hmrc.play.bootstrap.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.{Html, NunjucksSupport}
import utils.DateHelper.{dateFormatterDMY, dateFormatterStartDate}

import scala.concurrent.{ExecutionContext, Future}

class PaymentsAndChargeDetailsController @Inject()(override val messagesApi: MessagesApi,
                                                   identify: IdentifierAction,
                                                   val controllerComponents: MessagesControllerComponents,
                                                   config: FrontendAppConfig,
                                                   schemeService: SchemeService,
                                                   financialStatementConnector: FinancialStatementConnector,
                                                   paymentsAndChargesService: PaymentsAndChargesService,
                                                   renderer: Renderer)(implicit ec: ExecutionContext)
    extends FrontendBaseController
    with I18nSupport
    with NunjucksSupport {

  def onPageLoad(srn: String, startDate: LocalDate, chargeReference: String): Action[AnyContent] =
    identify.async { implicit request =>
      schemeService.retrieveSchemeDetails(request.psaId.id, srn).flatMap { schemeDetails =>
        financialStatementConnector.getSchemeFS(schemeDetails.pstr).flatMap { seqSchemeFS =>
          val filteredSchemeFs = seqSchemeFS.find(_.chargeReference == chargeReference)
          filteredSchemeFs match {
            case Some(schemeFs) =>
              renderer
                .render(template = "paymentsAndCharges/paymentsAndChargeDetails.njk", summaryListData(srn, schemeFs, schemeDetails.schemeName))
                .map(Ok(_))
            case _ =>
              Logger.warn(s"No Payments and Charge details found for the selected charge reference $chargeReference")
              Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad()))
          }
        }
      }
    }

  def summaryListData(srn: String, schemeFS: SchemeFS, schemeName: String)(implicit messages: Messages): JsObject = {
    val htmlInsetText = (schemeFS.dueDate, schemeFS.accruedInterestTotal > 0, schemeFS.amountDue > 0) match {
      case (Some(date), true, true) =>
        Html(
          s"<h2 class=govuk-heading-s>${messages("paymentsAndCharges.chargeDetails.interestAccruing")}</h2>" +
            s"<p class=govuk-body>${messages("paymentsAndCharges.chargeDetails.amount.not.paid.by.dueDate", date.format(dateFormatterDMY))}" +
            s"<span class=govuk-!-display-block><a class=govuk-link href=${controllers.paymentsAndCharges.routes.PaymentsAndChargesInterestController
              .onPageLoad(srn, schemeFS.periodStartDate, schemeFS.chargeReference)
              .url}>" +
            s"${messages("paymentsAndCharges.chargeDetails.interest.breakdown")}</a></span></p>"
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
      fields = "chargeDetailsList" -> paymentsAndChargesService.getChargeDetailsForSelectedCharge(schemeFS),
      "tableHeader" -> messages("paymentsAndCharges.caption",
                                schemeFS.periodStartDate.format(dateFormatterStartDate),
                                schemeFS.periodEndDate.format(dateFormatterDMY)),
      "schemeName" -> schemeName,
      "chargeType" -> schemeFS.chargeType.toString,
      "chargeReferenceTextMessage" -> (if (schemeFS.totalAmount < 0) {
                                         messages("paymentsAndCharges.credit.information",
                                                  s"${FormatHelper.formatCurrencyAmountAsString(schemeFS.totalAmount.abs)}")
                                       } else {
                                         messages("paymentsAndCharges.chargeDetails.chargeReference", schemeFS.chargeReference)
                                       }),
      "isPaymentOverdue" -> (schemeFS.amountDue > 0 && schemeFS.accruedInterestTotal > 0
        && (schemeFS.chargeType == PSS_AFT_RETURN || schemeFS.chargeType == PSS_OTC_AFT_RETURN)),
      "insetText" -> htmlInsetText,
      "interest" -> schemeFS.accruedInterestTotal,
      "returnUrl" -> config.managePensionsSchemeSummaryUrl.format(srn)
    )
  }

}