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

package controllers

import controllers.actions.{AllowAccessActionProviderForIdentifierRequest, IdentifierAction}
import models.ChargeType
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import services.FileProviderService
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.NunjucksSupport

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class FileDownloadController @Inject()(override val messagesApi: MessagesApi,
                                       identify: IdentifierAction,
                                       allowAccess: AllowAccessActionProviderForIdentifierRequest,
                                       fileProviderService: FileProviderService,
                                       val controllerComponents: MessagesControllerComponents
                                      )(implicit ec: ExecutionContext)
  extends FrontendBaseController
    with I18nSupport
    with NunjucksSupport {

  def templateFile(chargeType: ChargeType): Action[AnyContent] = {
    (identify andThen allowAccess()).async { implicit request =>
      Future.successful(
        Ok.sendFile(
          content = fileProviderService.getTemplateFile(chargeType),
          inline = false
        ))
    }
  }

  def instructionsFile(chargeType: ChargeType): Action[AnyContent] = {
    (identify andThen allowAccess()).async { implicit request =>
      Future.successful(
        Ok.sendFile(
          content = fileProviderService.getInstructionsFile(chargeType),
          inline = false
        ))
    }
  }

}