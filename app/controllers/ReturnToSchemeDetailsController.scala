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

import config.FrontendAppConfig
import connectors.cache.UserAnswersCacheConnector
import controllers.actions.{AllowAccessActionProviderForIdentifierRequest, IdentifierAction}
import models.AccessType
import play.api.i18n.I18nSupport
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController

import java.time.LocalDate
import javax.inject.Inject
import scala.concurrent.ExecutionContext

class ReturnToSchemeDetailsController @Inject()(
    config: FrontendAppConfig,
    identify: IdentifierAction,
    val controllerComponents: MessagesControllerComponents,
    userAnswersCacheConnector: UserAnswersCacheConnector,
    allowAccess: AllowAccessActionProviderForIdentifierRequest
)(implicit ec: ExecutionContext)
    extends FrontendBaseController
    with I18nSupport {

  def returnToSchemeDetails(srn: String, startDate: LocalDate, accessType: AccessType, version: Int): Action[AnyContent] = (identify andThen allowAccess(Some(srn))).async { implicit request =>
    val id = s"$srn$startDate"
    userAnswersCacheConnector.removeAll(id).map(_ => Redirect(config.schemeDashboardUrl(request).format(srn)))
  }

}
