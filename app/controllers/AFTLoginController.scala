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
import models.{Quarters, Years}
import navigators.CompoundNavigator
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import renderer.Renderer
import services.{AFTService, AllowAccessService}
import uk.gov.hmrc.play.bootstrap.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.NunjucksSupport

import scala.concurrent.{ExecutionContext, Future}

class AFTLoginController @Inject()(
                                      override val messagesApi: MessagesApi,
                                      userAnswersCacheConnector: UserAnswersCacheConnector,
                                      navigator: CompoundNavigator,
                                      identify: IdentifierAction,
                                      getData: DataRetrievalAction,
                                      allowAccess: AllowAccessActionProvider,
                                      requireData: DataRequiredAction,
                                      val controllerComponents: MessagesControllerComponents,
                                      renderer: Renderer,
                                      config: FrontendAppConfig,
                                      auditService: AuditService,
                                      aftService: AFTService,
                                      allowService: AllowAccessService
                                    )(implicit ec: ExecutionContext) extends FrontendBaseController with I18nSupport with NunjucksSupport {

  def onPageLoad(srn: String): Action[AnyContent] = identify.async {
    implicit request =>

      val defaultYear = Years.minYear(config)
      (Years.values(config).size, Quarters.values(defaultYear)(config).size) match {
      case (years, _) if years > 1 =>

       Future.successful(Redirect(controllers.routes.YearsController.onPageLoad(srn)))

      case (_, quarters) if quarters > 1 =>

        Future.successful(Redirect(controllers.routes.QuartersController.onPageLoad(srn, defaultYear.toString)))

      case _ =>
        val defaultQuarter = Quarters.values(defaultYear)(config).headOption.getOrElse(throw NoQuartersAvailableException)
        Future.successful(Redirect(controllers.routes.ChargeTypeController.onPageLoad(srn, Quarters.getStartDate(defaultQuarter, defaultYear))))
    }
  }

  case object NoQuartersAvailableException extends Exception("No quarters are available to be be selected from")


}
