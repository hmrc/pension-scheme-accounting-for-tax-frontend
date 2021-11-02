/*
 * Copyright 2021 HM Revenue & Customs
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

import audit.{AuditService, StartAmendAFTAuditEvent}
import connectors.AFTConnector
import controllers.actions._
import javax.inject.Inject
import models.Quarters
import models.LocalDateBinder._
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import services.SchemeService
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.NunjucksSupport

import scala.concurrent.{ExecutionContext, Future}

class AFTAmendController @Inject()(
    override val messagesApi: MessagesApi,
    aftConnector: AFTConnector,
    auditService: AuditService,
    schemeService: SchemeService,
    identify: IdentifierAction,
    val controllerComponents: MessagesControllerComponents
)(implicit ec: ExecutionContext)
    extends FrontendBaseController
    with I18nSupport
    with NunjucksSupport {

  def onPageLoad(srn: String): Action[AnyContent] = identify.async { implicit request =>
    schemeService.retrieveSchemeDetails(
      psaId = request.idOrException,
      srn = srn,
      schemeIdType = "srn"
    ) flatMap { schemeDetails =>
      aftConnector.getAftOverview(schemeDetails.pstr).flatMap { aftOverview =>
        val futureResult = if (aftOverview.nonEmpty) {
          val yearsSeq = aftOverview.map(_.periodStartDate.getYear).distinct.sorted

          if (yearsSeq.nonEmpty && yearsSeq.size > 1) {
            Future.successful(Redirect(controllers.amend.routes.AmendYearsController.onPageLoad(srn)))
          } else {
            val quartersSeq = aftOverview
              .filter(_.periodStartDate.getYear == yearsSeq.head)
              .map { overviewElement =>
                Quarters.getQuarter(overviewElement.periodStartDate)
              }
              .distinct

            if (quartersSeq.size > 1) {
              Future.successful(Redirect(controllers.amend.routes.AmendQuartersController.onPageLoad(srn, yearsSeq.head.toString)))
            } else {
              Future.successful(Redirect(controllers.amend.routes.ReturnHistoryController.onPageLoad(srn, quartersSeq.head.startDate)))
            }
          }
        } else {
          Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad))
        }

        futureResult.map { result =>
          auditService.sendEvent(StartAmendAFTAuditEvent(request.idOrException, schemeDetails.pstr))
          result
        }
      }
    }
  }
}
