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

package controllers.partials

import config.FrontendAppConfig
import connectors.FinancialStatementConnector
import controllers.actions._
import javax.inject.Inject
import models.Enumerable
import models.requests.IdentifierRequest
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.{JsString, JsArray, Writes, Json}
import play.api.mvc.{Result, AnyContent, MessagesControllerComponents, Action}
import play.twirl.api.{Html, HtmlFormat}
import renderer.Renderer
import services.paymentsAndCharges.PaymentsAndChargesService
import services.{AFTPartialService, SchemeService}
import uk.gov.hmrc.http.{BadRequestException, HeaderCarrier}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.NunjucksSupport

import scala.concurrent.{Future, ExecutionContext}

class PspSchemeDashboardPartialsController @Inject()(
                                   identify: IdentifierAction,
                                   override val messagesApi: MessagesApi,
                                   val controllerComponents: MessagesControllerComponents,
                                   schemeService: SchemeService,
                                   financialStatementConnector: FinancialStatementConnector,
                                   paymentsAndChargesService: PaymentsAndChargesService,
                                   aftPartialService: AFTPartialService,
                                   renderer: Renderer,
                                   config: FrontendAppConfig
                                 )(implicit ec: ExecutionContext)
  extends FrontendBaseController
    with I18nSupport
    with NunjucksSupport {

  def pspDashboardAllTilesPartial(): Action[AnyContent] = identify.async {
    implicit request =>
      val allResults = for {
        aftReturnsHtml <- pspDashboardAftReturnsPartial
        upcomingAftChargesHtml <- pspDashboardUpcomingAftChargesPartial
      } yield {
        scala.collection.immutable.Seq(aftReturnsHtml, upcomingAftChargesHtml)
      }
      allResults.map(HtmlFormat.fill).map(Ok(_))
  }

  def pspDashboardAftReturnsPartial(implicit request: IdentifierRequest[AnyContent], hc: HeaderCarrier):Future[Html] = {
      val idNumber = request.headers.get("idNumber")
      val schemeIdType = request.headers.get("schemeIdType")
      val authorisingPsaId = request.headers.get("authorisingPsaId")

      (idNumber, schemeIdType, authorisingPsaId) match {
        case (Some(srn), Some(idType), Some(psaId)) =>
          aftPartialService.retrievePspDashboardAftReturnsModel(
            srn = srn,
            pspId = request.idOrException,
            schemeIdType = idType,
            authorisingPsaId = psaId
          ) flatMap {
            viewModel => {
              renderer.render(
                template = "partials/pspDashboardAftReturnsCard.njk",
                ctx = Json.obj("aft" -> Json.toJson(viewModel))
              )
            }
          }
        case _ =>
          Future.failed(
            new BadRequestException("Bad Request with missing parameters idNumber, schemeIdType, psaId or authorisingPsaId")
          )
      }
  }

  def pspDashboardUpcomingAftChargesPartial(implicit request: IdentifierRequest[AnyContent], hc: HeaderCarrier):Future[Html] = {
      val idNumber = request.headers.get("idNumber")

      idNumber match {
        case Some(srn) =>
          schemeService.retrieveSchemeDetails(
            psaId = request.idOrException,
            srn = srn,
            schemeIdType = "srn"
          ) flatMap { schemeDetails =>
            financialStatementConnector.getSchemeFS(
              pstr = schemeDetails.pstr
            ) flatMap { schemeFs =>
              if (schemeFs.isEmpty) {
                Future.successful(Html(""))
              } else {
                val viewModel =
                  aftPartialService.retrievePspDashboardUpcomingAftChargesModel(schemeFs, srn)
                renderer.render(
                  template = "partials/pspDashboardUpcomingAftChargesCard.njk",
                  ctx = Json.obj("upcomingCharges" -> Json.toJson(viewModel))
                )
              }
            }
          }
        case _ =>
          Future.failed(
            new BadRequestException("Bad Request with missing parameters idNumber")
          )
      }
  }

  def pspDashboardOverdueAftChargesPartial(): Action[AnyContent] = identify.async {
    implicit request =>
      val idNumber = request.headers.get("idNumber")

      idNumber match {
        case Some(srn) =>
          schemeService.retrieveSchemeDetails(
            psaId = request.idOrException,
            srn = srn,
            schemeIdType = "srn"
          ) flatMap { schemeDetails =>
            financialStatementConnector.getSchemeFS(
              pstr = schemeDetails.pstr
            ) flatMap { schemeFs =>
              val overdueCharges =
                paymentsAndChargesService.getOverdueCharges(schemeFs)
              if (overdueCharges.isEmpty) {
                Future.successful(Ok(Html("")))
              } else {
                val viewModel =
                  aftPartialService.retrievePspDashboardOverdueAftChargesModel(overdueCharges, srn)
                renderer.render(
                  template = "partials/pspDashboardOverdueAftChargesCard.njk",
                  ctx = Json.obj("overdueCharges" -> Json.toJson(viewModel))
                ).map(Ok(_))
              }
            }
          }
        case _ =>
          Future.failed(
            new BadRequestException("Bad Request with missing parameters idNumber")
          )
      }
  }
}
