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

package controllers

import config.FrontendAppConfig
import connectors.cache.UserAnswersCacheConnector
import controllers.actions.{IdentifierAction, DataRetrievalAction}
import play.api.i18n.{MessagesApi, I18nSupport}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import renderer.Renderer
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import models.LocalDateBinder._

import java.time.LocalDate
import javax.inject.Inject
import scala.concurrent.ExecutionContext

class CannotSubmitAFTController @Inject()(appConfig: FrontendAppConfig,
                                                    override val messagesApi: MessagesApi,
                                                    val controllerComponents: MessagesControllerComponents,
                                                    userAnswersCacheConnector: UserAnswersCacheConnector,
                                                    identify: IdentifierAction,
                                                    getData: DataRetrievalAction,
                                                    renderer: Renderer
                                                   )(implicit val executionContext: ExecutionContext)
  extends FrontendBaseController
    with I18nSupport {

  def onPageLoad(srn: String, startDate: LocalDate): Action[AnyContent] =
    (identify andThen getData(srn, startDate)).async {
      implicit request =>
        val json = Json.obj(
          "returnUrl" -> controllers.routes.CannotSubmitAFTController.onClick(srn, startDate).url
        )
        renderer.render("cannotSubmitAFT.njk", json).map(Ok(_))
    }

  def onClick(srn: String, startDate: LocalDate): Action[AnyContent] =
    (identify andThen getData(srn, startDate)).async {
      implicit request =>
        userAnswersCacheConnector.removeAll(request.internalId).map { _ =>
          Redirect(appConfig.managePensionsSchemeSummaryUrl.format(srn))
        }
    }
}
