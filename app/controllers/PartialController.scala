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
import javax.inject.Inject
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import play.twirl.api.Html
import renderer.Renderer
import services.{AFTPartialService, SchemeService}
import uk.gov.hmrc.http.BadRequestException
import uk.gov.hmrc.play.bootstrap.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.NunjucksSupport

import scala.concurrent.{ExecutionContext, Future}

class PartialController @Inject()(
                                   identify: IdentifierAction,
                                   override val messagesApi: MessagesApi,
                                   val controllerComponents: MessagesControllerComponents,
                                   schemeService: SchemeService,
                                   financialStatementConnector: FinancialStatementConnector,
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
      userIdNumber = request.psaId.id
    ) flatMap { aftViewModels =>
      renderer.render(
        template = "partials/overview.njk",
        ctx = Json.obj("aftModels" -> Json.toJson(aftViewModels))).map(Ok(_)
      )
    }
  }

  def paymentsAndChargesPartial(srn: String): Action[AnyContent] = identify.async { implicit request =>
    schemeService.retrieveSchemeDetails(request.psaId.id, srn).flatMap { schemeDetails =>
      financialStatementConnector.getSchemeFS(schemeDetails.pstr).flatMap { schemeFs =>
        val futureHtml = if (schemeFs.isEmpty) {
          Future.successful(Html(""))
        }
        else {
          renderer.render(
            template = "partials/paymentsAndCharges.njk",
            Json.obj("redirectUrl" -> config.paymentsAndChargesUrl.format(srn, "2020"))
          )
        }
        futureHtml.map(Ok(_))
      }
    }
  }

  def pspDashboardAftCard: Action[AnyContent] = identify.async {
    implicit request =>
      val schemeIdNumber = request.headers.get("schemeIdNumber")
      val userIdNumber = request.headers.get("userIdNumber")

      println(s"\n\n\tpspDashboardAftCard\n\n")

      (schemeIdNumber, userIdNumber) match {
        case (Some(srn), Some(userNumber)) =>
          aftPartialService.retrieveOptionAFTViewModel(
            srn = srn,
            userIdNumber = userNumber
          ) flatMap {
            viewModels =>
              renderer.render(
                template = "partials/pspDashboardAftCard.njk",
                ctx = Json.obj("aftViewModels" -> Json.toJson(viewModels))).map(Ok(_)
              )
          }
        case _ =>
          Future.failed(
            new BadRequestException("Bad Request with missing parameters schemeIdNumber, userIdType or userIdNumber")
          )
      }
  }
}
