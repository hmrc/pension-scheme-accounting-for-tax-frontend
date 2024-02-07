/*
 * Copyright 2024 HM Revenue & Customs
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

import connectors.cache.UserAnswersCacheConnector
import controllers.actions.{AllowAccessActionProviderForIdentifierRequest, IdentifierAction}
import play.api.i18n.I18nSupport
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class KeepAliveController @Inject()(
                                     identify: IdentifierAction,
                                     val controllerComponents: MessagesControllerComponents,
                                     userAnswersCacheConnector: UserAnswersCacheConnector,
                                     allowAccess: AllowAccessActionProviderForIdentifierRequest
                                   )(implicit ec: ExecutionContext)
  extends FrontendBaseController
    with I18nSupport {

  def keepAlive(srn: Option[String], startDate: Option[String]): Action[AnyContent] = (identify andThen allowAccess(srn)).async {
    implicit request =>
      (srn, startDate) match {
        case (Some(sr), Some(startDt)) =>
          val id = s"$sr$startDt"
          userAnswersCacheConnector.fetch(id).flatMap {
            case Some(ua) =>
              userAnswersCacheConnector.save(id, ua).map(_ => NoContent)
            case _ =>
              Future.successful(NoContent)
          }
        case _ =>
          Future.successful(NoContent)
      }
  }
}
