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

import audit.AuditService
import config.FrontendAppConfig
import connectors.cache.UserAnswersCacheConnector
import controllers.actions._
import javax.inject.Inject
import models.LocalDateBinder._
import models.{Quarters, StartYears}
import navigators.CompoundNavigator
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import renderer.Renderer
import services.{AFTPartialService, AFTService, AllowAccessService, QuartersService, SchemeService}
import uk.gov.hmrc.play.bootstrap.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.NunjucksSupport
import viewmodels.{AFTViewModel, Link}

import scala.concurrent.{ExecutionContext, Future}

class ManageFrontendPartialController @Inject()(
                                    identify: IdentifierAction,
                                    override val messagesApi: MessagesApi,
                                    val controllerComponents: MessagesControllerComponents,
                                    aftPartialService: AFTPartialService,
                                    config: FrontendAppConfig
                                  )(implicit ec: ExecutionContext)
  extends FrontendBaseController
    with I18nSupport
    with NunjucksSupport {

  def aftPartial(srn: String): Action[AnyContent] = identify.async { implicit request =>

    println(s"\n\n\n>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>> \nENTERED CONTROLLER ")
    aftPartialService.retrieveOptionAFTViewModel(srn, "A2100005").map { aftPartial =>
      val t = Json.toJson(aftPartial)
      println(s"\n\n\n>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>> \n $t")
      Ok(t)
    }
  }



}
