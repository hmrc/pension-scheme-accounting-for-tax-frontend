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

package controllers.fileUpload

import config.FrontendAppConfig
import connectors.cache.FileUploadEventsLogConnector
import controllers.actions._
import models.AccessType
import models.LocalDateBinder._
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import renderer.Renderer
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController

import java.time.LocalDate
import javax.inject.Inject
import scala.concurrent.ExecutionContext

class ProcessingRequestController @Inject()(val appConfig: FrontendAppConfig,
                                             override val messagesApi: MessagesApi,
                                            identify: IdentifierAction,
                                            val controllerComponents: MessagesControllerComponents,
                                            renderer: Renderer,
                                            fileUploadEventsLogConnector: FileUploadEventsLogConnector
                                           )(implicit ec: ExecutionContext)
  extends FrontendBaseController
    with I18nSupport {

  def onPageLoad(srn: String, startDate: LocalDate, accessType: AccessType, version: Int): Action[AnyContent] = {
    identify.async {
      implicit request =>

        def headerContentAndRedirect(status: Int): (String, String, String) = {
          status match {
            case ACCEPTED =>
              Tuple3(
                "messages__processingRequest__h1_processed",
                "messages__processingRequest__content_processed",
                controllers.routes.ConfirmationController.onPageLoad(srn, startDate, accessType, version).url
              )
            case NOT_FOUND =>
              Tuple3(
                "messages__processingRequest__h1_processing",
                "messages__processingRequest__content_processing",
                controllers.fileUpload.routes.ProcessingRequestController.onPageLoad(srn, startDate, accessType, version).url
              )
            case _ =>
              Tuple3(
                "messages__processingRequest__h1_failure",
                "messages__processingRequest__content_failure",
                controllers.routes.DeclarationController.onPageLoad(srn, startDate, accessType, version).url
              )
          }
        }

        fileUploadEventsLogConnector.getStatus.flatMap { status =>
          val (header, content, redirect) = headerContentAndRedirect(status)
          val json = Json.obj(
            "pageTitle" -> header,
            "heading" -> header,
            "content" -> content,
            "continueUrl" -> redirect
          )
          renderer.render("racdac/processingRequest.njk", json).map(Ok(_))
        }
    }
  }

}

