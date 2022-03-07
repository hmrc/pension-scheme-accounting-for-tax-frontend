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

package controllers.financialStatement.penalties

import config.FrontendAppConfig
import connectors.FinancialStatementConnector
import controllers.actions._
import models.FeatureToggle.{Disabled, Enabled}
import models.FeatureToggleName.FinancialInformationAFT
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import play.twirl.api.Html
import renderer.Renderer
import services.{AFTPartialService, FeatureToggleService}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.NunjucksSupport

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class PenaltiesPartialController @Inject()(
                                                identify: IdentifierAction,
                                                override val messagesApi: MessagesApi,
                                                val controllerComponents: MessagesControllerComponents,
                                                fsConnector: FinancialStatementConnector,
                                                renderer: Renderer,
                                                aftPartialService: AFTPartialService,
                                                toggleService: FeatureToggleService
                                 )(implicit ec: ExecutionContext)
  extends FrontendBaseController
    with I18nSupport
    with NunjucksSupport {

  def penaltiesPartial() : Action[AnyContent] = identify.async { implicit request =>
    toggleService.get(FinancialInformationAFT).flatMap {
      case Enabled(FinancialInformationAFT) =>
        fsConnector.getPsaFS(request.psaIdOrException.id).flatMap { psaFS =>
          val result = if (psaFS.isEmpty) {
            Future.successful(Html(""))
          } else {
            val viewModel = aftPartialService.penaltiesAndCharges(psaFS)
            renderer.render(
              template = "partials/psaSchemeDashboardPartial.njk",
              ctx = Json.obj("cards" -> Json.toJson(viewModel))
            )
          }
          result.map(Ok(_))
        }

      case Disabled(FinancialInformationAFT) =>
        fsConnector.getPsaFS(request.psaIdOrException.id).flatMap { psaFS =>
          val result = if (psaFS.isEmpty) {
            Future.successful(Html(""))
          } else {
            val viewModel = aftPartialService.retrievePsaPenaltiesCardModel(psaFS)
            renderer.render(
              template = "partials/penalties.njk",
              ctx = Json.obj("viewModel" -> Json.toJson(viewModel))
            )
          }
          result.map(Ok(_))
        }
    }
  }
}
