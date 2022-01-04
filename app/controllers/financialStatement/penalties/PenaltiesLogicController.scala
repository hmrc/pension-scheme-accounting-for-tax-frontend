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

package controllers.financialStatement.penalties

import controllers.actions._
import models.PenaltiesFilter
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import services.PenaltiesService
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.NunjucksSupport

import javax.inject.Inject
import scala.concurrent.ExecutionContext

class PenaltiesLogicController @Inject()(override val messagesApi: MessagesApi,
                                          service: PenaltiesService,
                                          identify: IdentifierAction,
                                          val controllerComponents: MessagesControllerComponents
                                      )(implicit ec: ExecutionContext)
                                          extends FrontendBaseController
                                          with I18nSupport
                                          with NunjucksSupport {

  def onPageLoad(journeyType: PenaltiesFilter): Action[AnyContent] = identify.async { implicit request =>

    service.getPenaltiesForJourney(request.psaIdOrException.id, journeyType).flatMap { penaltiesCache =>
      service.navFromOverviewPage(penaltiesCache.penalties, request.psaIdOrException.id, journeyType)
    }
  }

}
