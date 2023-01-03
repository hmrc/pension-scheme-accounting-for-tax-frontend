/*
 * Copyright 2023 HM Revenue & Customs
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
import connectors.cache.{SessionDataCacheConnector, UserAnswersCacheConnector}
import controllers.actions.IdentifierAction
import play.api.i18n.I18nSupport
import play.api.mvc.{Action, AnyContent, BodyParsers, MessagesControllerComponents}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class SignOutController @Inject()(
                                   config: FrontendAppConfig,
                                   identify: IdentifierAction,
                                   val controllerComponents: MessagesControllerComponents,
                                   userAnswersCacheConnector: UserAnswersCacheConnector,
                                   sessionDataCacheConnector: SessionDataCacheConnector,
  val parser: BodyParsers.Default
                                 )(implicit ec: ExecutionContext)
  extends FrontendBaseController
    with I18nSupport{

  def signOut(srn: String, startDate: Option[String]): Action[AnyContent] = identify.async {
    implicit request =>

      sessionDataCacheConnector.removeAll(request.externalId).flatMap{ _ =>
        startDate match {
          case Some(startDt) =>
            val id = s"$srn$startDt"
            userAnswersCacheConnector.removeAll(id).map { _ =>
              Redirect(config.signOutUrl).withNewSession
            }
          case _ =>
            Future.successful(Redirect(config.signOutUrl).withNewSession)
        }
      }
  }
}
