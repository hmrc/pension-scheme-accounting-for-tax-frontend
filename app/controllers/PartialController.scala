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

package controllers

import config.FrontendAppConfig
import connectors.{FinancialStatementConnector, SchemeDetailsConnector}
import controllers.actions._
import helpers.FormatHelper
import models.financialStatement.SchemeFS

import javax.inject.Inject
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import play.twirl.api.Html
import renderer.Renderer
import services.paymentsAndCharges.PaymentsAndChargesService
import services.{AFTPartialService, SchemeService}
import uk.gov.hmrc.http.BadRequestException
import uk.gov.hmrc.play.bootstrap.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.NunjucksSupport

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import scala.concurrent.{ExecutionContext, Future}

class PartialController @Inject()(
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

  def aftPartial(srn: String): Action[AnyContent] = identify.async { implicit request =>
    aftPartialService.retrieveOptionAFTViewModel(
      srn = srn,
      psaId = request.idOrException,
      schemeIdType = "srn"
    ) flatMap { aftViewModels =>
      renderer.render(
        template = "partials/overview.njk",
        ctx = Json.obj("aftModels" -> Json.toJson(aftViewModels))).map(Ok(_)
      )
    }
  }

  def paymentsAndChargesPartial(srn: String): Action[AnyContent] = identify.async { implicit request =>
    schemeService.retrieveSchemeDetails(
      psaId = request.idOrException,
      srn = srn,
      schemeIdType = "srn"
    ) flatMap { schemeDetails =>
      financialStatementConnector.getSchemeFS(schemeDetails.pstr).flatMap { schemeFs =>
        val futureHtml = if (schemeFs.isEmpty) {
          Future.successful(Html(""))
        } else {
          renderer.render(
            template = "partials/paymentsAndCharges.njk",
            Json.obj("redirectUrl" -> config.paymentsAndChargesUrl.format(srn, "2020"))
          )
        }
        futureHtml.map(Ok(_))
      }
    }
  }

  def pspDashboardAftReturnsPartial(): Action[AnyContent] = identify.async {
    implicit request =>
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
              ).map(Ok(_))
            }
          }
        case _ =>
          Future.failed(
            new BadRequestException("Bad Request with missing parameters idNumber, schemeIdType, psaId or authorisingPsaId")
          )
      }
  }

  def pspDashboardUpcomingAftChargesPartial(): Action[AnyContent] = identify.async {
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
              if (schemeFs.isEmpty) {
                Future.successful(Ok(Html("")))
              } else {
                val viewModel =
                  aftPartialService.retrievePspDashboardUpcomingAftChargesModel(schemeFs, srn)
                renderer.render(
                  template = "partials/pspDashboardUpcomingAftChargesCard.njk",
                  ctx = Json.obj("upcomingCharges" -> Json.toJson(viewModel))
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
