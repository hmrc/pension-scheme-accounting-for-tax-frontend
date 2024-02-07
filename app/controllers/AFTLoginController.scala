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

import audit.{AuditService, StartNewAFTAuditEvent}
import config.FrontendAppConfig
import controllers.actions._
import models.LocalDateBinder._
import models.{Draft, Quarters, StartYears}
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import services.SchemeService
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.NunjucksSupport

import javax.inject.Inject
import scala.concurrent.ExecutionContext

class AFTLoginController @Inject()(
                                    override val messagesApi: MessagesApi,
                                    identify: IdentifierAction,
                                    val controllerComponents: MessagesControllerComponents,
                                    auditService: AuditService,
                                    schemeService: SchemeService,
                                    allowAccess: AllowAccessActionProviderForIdentifierRequest,
                                    config: FrontendAppConfig)
                                  (implicit ec: ExecutionContext)
                                    extends FrontendBaseController
                                    with I18nSupport
                                    with NunjucksSupport {

  def onPageLoad(srn: String): Action[AnyContent] = (identify andThen allowAccess(Some(srn))).async { implicit request =>
    val defaultYear = StartYears.minYear(config)
    schemeService.retrieveSchemeDetails(request.idOrException, srn, "srn").map { schemeDetails =>
      auditService.sendEvent(StartNewAFTAuditEvent(request.idOrException, schemeDetails.pstr))
      (StartYears.values(config).size, Quarters.availableQuarters(defaultYear)(config).size) match {
        case (years, _) if years > 1 =>
          Redirect(controllers.routes.YearsController.onPageLoad(srn))

        case (_, quarters) if quarters > 1 =>
          Redirect(controllers.routes.QuartersController.onPageLoad(srn, defaultYear.toString))

        case _ =>
          val defaultQuarter = Quarters.availableQuarters(defaultYear)(config).headOption.getOrElse(throw NoQuartersAvailableException)
          Redirect(controllers.routes.ChargeTypeController.onPageLoad(srn, Quarters.getStartDate(defaultQuarter, defaultYear), Draft, version = 1))
      }
    }

  }

  case object NoQuartersAvailableException extends Exception("No quarters are available to be be selected from")

}
