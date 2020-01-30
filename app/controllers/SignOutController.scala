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
import connectors.cache.UserAnswersCacheConnector
import controllers.actions.{DataRetrievalAction, IdentifierAction}
import javax.inject.Inject
import play.api.i18n.I18nSupport
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import uk.gov.hmrc.play.bootstrap.controller.FrontendBaseController

import scala.concurrent.ExecutionContext

class SignOutController @Inject()(
                                   config: FrontendAppConfig,
                                   identify: IdentifierAction,
                                   getData: DataRetrievalAction,
                                   val controllerComponents: MessagesControllerComponents,
                                   userAnswersCacheConnector: UserAnswersCacheConnector
)(implicit ec: ExecutionContext) extends FrontendBaseController with I18nSupport {

  def signOut: Action[AnyContent] = (identify andThen getData("srn")).async {
    implicit request =>
      userAnswersCacheConnector.removeAll(request.internalId).map { _ =>
        Redirect(config.signOutUrl).withNewSession
      }
  }
}
