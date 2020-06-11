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

import java.time.LocalDate

import config.FrontendAppConfig
import controllers.actions._
import javax.inject.Inject
import models.LocalDateBinder._
import models.{Quarters, StartYears}
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import uk.gov.hmrc.play.bootstrap.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.NunjucksSupport
import utils.DateHelper

import scala.concurrent.{ExecutionContext, Future}

class AFTLoginController @Inject()(
    override val messagesApi: MessagesApi,
    identify: IdentifierAction,
    val controllerComponents: MessagesControllerComponents,
    config: FrontendAppConfig
)(implicit ec: ExecutionContext)
    extends FrontendBaseController
    with I18nSupport
    with NunjucksSupport {

  def onPageLoad(srn: String): Action[AnyContent] = identify.async { implicit request =>
    val defaultYear = StartYears.minYear(config)
    if (!LocalDate.parse(config.overviewApiEnablementDate).isAfter(DateHelper.today)) {
      (StartYears.values(config).size, Quarters.availableQuarters(defaultYear)(config).size) match {
        case (years, _) if years > 1 =>
          Future.successful(Redirect(controllers.routes.YearsController.onPageLoad(srn)))

        case (_, quarters) if quarters > 1 =>
          Future.successful(Redirect(controllers.routes.QuartersController.onPageLoad(srn, defaultYear.toString)))

        case _ =>
          val defaultQuarter = Quarters.availableQuarters(defaultYear)(config).headOption.getOrElse(throw NoQuartersAvailableException)
          Future.successful(Redirect(controllers.routes.ChargeTypeController.onPageLoad(srn, Quarters.getStartDate(defaultQuarter, defaultYear))))
      }
    } else {
      val defaultQuarter = Quarters.availableQuarters(defaultYear)(config).headOption.getOrElse(throw NoQuartersAvailableException)
      Future.successful(Redirect(controllers.routes.ChargeTypeController.onPageLoad(srn, Quarters.getStartDate(defaultQuarter, defaultYear))))
    }
  }

  case object NoQuartersAvailableException extends Exception("No quarters are available to be be selected from")

}
