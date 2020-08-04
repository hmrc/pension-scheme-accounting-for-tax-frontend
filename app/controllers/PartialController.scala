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
import controllers.actions._
import javax.inject.Inject
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import renderer.Renderer
import services.AFTPartialService
import uk.gov.hmrc.play.bootstrap.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.NunjucksSupport

import scala.concurrent.ExecutionContext

class PartialController @Inject()(
                                    identify: IdentifierAction,
                                    override val messagesApi: MessagesApi,
                                    val controllerComponents: MessagesControllerComponents,
                                    aftPartialService: AFTPartialService,
                                    renderer: Renderer,
                                    config: FrontendAppConfig
                                  )(implicit ec: ExecutionContext)
  extends FrontendBaseController
    with I18nSupport
    with NunjucksSupport {

  def aftPartial(srn: String): Action[AnyContent] = identify.async { implicit request =>
    aftPartialService.retrieveOptionAFTViewModel(srn, request.psaId.id).flatMap { aftPartial =>
      renderer.render(
        template = "partials/overview.njk",
        ctx = Json.obj("aftModels" -> Json.toJson(aftPartial))).map(Ok(_)
      )
    }
  }

  def paymentsAndChargesPartial(srn: String): Action[AnyContent] = identify.async { implicit request =>
    renderer.render(template = "partials/paymentsAndCharges.njk", Json.obj("redirectUrl" ->
      config.paymentsAndChargesUrl.format(srn, "2020"))).map(Ok(_))
  }
}
