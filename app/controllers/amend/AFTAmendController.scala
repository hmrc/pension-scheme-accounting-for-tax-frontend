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

package controllers.amend

import config.FrontendAppConfig
import connectors.AFTConnector
import controllers.actions._
import javax.inject.Inject
import models.LocalDateBinder._
import models.StartQuarters
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import services.{AFTService, AllowAccessService, SchemeService}
import uk.gov.hmrc.play.bootstrap.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.NunjucksSupport

import scala.concurrent.{ExecutionContext, Future}

class AFTAmendController @Inject()(
                                    override val messagesApi: MessagesApi,
                                    aftConnector: AFTConnector,
                                    schemeService: SchemeService,
                                    identify: IdentifierAction,
                                    getData: DataRetrievalAction,
                                    allowAccess: AllowAccessActionProvider,
                                    requireData: DataRequiredAction,
                                    val controllerComponents: MessagesControllerComponents,
                                    config: FrontendAppConfig,
                                    aftService: AFTService,
                                    allowService: AllowAccessService
                                    )(implicit ec: ExecutionContext) extends FrontendBaseController with I18nSupport with NunjucksSupport {

  def onPageLoad(srn: String): Action[AnyContent] = identify.async {
    implicit request =>

      schemeService.retrieveSchemeDetails(request.psaId.id, srn).flatMap { schemeDetails =>
        aftConnector.getAftOverview(schemeDetails.pstr).flatMap { aftOverview =>
          if (aftOverview.nonEmpty) {
            val yearsSeq = aftOverview.map(_.periodStartDate.getYear).distinct.sorted

            if (yearsSeq.nonEmpty && yearsSeq.size > 1)
              Future.successful(Redirect(controllers.amend.routes.AmendYearsController.onPageLoad(srn)))

            else {
              val quartersSeq = aftOverview.filter(_.periodStartDate.getYear == yearsSeq.head).map { overviewElement =>
                StartQuarters.getQuarter(overviewElement.periodStartDate)
              }.distinct

              if (quartersSeq.nonEmpty && quartersSeq.size > 1)
                Future.successful(Redirect(controllers.routes.QuartersController.onPageLoad(srn, yearsSeq.head.toString)))
              else {
                Future.successful(Redirect(controllers.amend.routes.ReturnHistoryController.onPageLoad(srn, quartersSeq.head.startDate)))
              }
            }
          } else
            Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad()))
        }
      }
  }


}
