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
import controllers.actions.{IdentifierAction, DataRetrievalAction, DataRequiredAction}
import play.api.i18n.{MessagesApi, I18nSupport}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import renderer.Renderer
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController

import java.time.LocalDate
import javax.inject.Inject
import scala.concurrent.ExecutionContext

class YourActionWasNotProcessedController @Inject()(appConfig: FrontendAppConfig,
                                                    override val messagesApi: MessagesApi,
                                                    val controllerComponents: MessagesControllerComponents,
                                                    identify: IdentifierAction,
                                                    getData: DataRetrievalAction,
                                                    requireData: DataRequiredAction,
                                                    renderer: Renderer
                                                   )(implicit val executionContext: ExecutionContext)
  extends FrontendBaseController
    with I18nSupport {

  def onPageLoad(srn: String, startDate: LocalDate): Action[AnyContent] =
    (identify andThen getData(srn, startDate) andThen requireData).async {
      implicit request =>
        DataRetrievals.retrieveSchemeName { schemeName =>
          val json = Json.obj(
            "schemeName" -> schemeName,
            "returnUrl" -> appConfig.managePensionsSchemeOverviewUrl
          )
          renderer.render("yourActionWasNotProcessed.njk", json).map(Ok(_))
        }
    }
}
