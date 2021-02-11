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

package controllers.financialStatement.penalties

import audit.{AuditService, StartAmendAFTAuditEvent}
import connectors.AFTConnector
import controllers.actions._
import models.LocalDateBinder._
import models.Quarters
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import services.{PenaltiesService, SchemeService}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.NunjucksSupport

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class PenaltiesLogicController @Inject()(override val messagesApi: MessagesApi,
                                          service: PenaltiesService,
                                          identify: IdentifierAction,
                                          val controllerComponents: MessagesControllerComponents
                                      )(implicit ec: ExecutionContext)
                                          extends FrontendBaseController
                                          with I18nSupport
                                          with NunjucksSupport {

  def onPageLoad(): Action[AnyContent] = identify.async { implicit request =>
    service.saveAndReturnPenalties(request.psaIdOrException.id).flatMap { penalties =>
      val yearsSeq: Seq[Int] = penalties.map(_.periodStartDate.getYear).distinct.sorted.reverse
      if (yearsSeq.nonEmpty && yearsSeq.size > 1) {
        Future.successful(Redirect(routes.SelectPenaltiesYearController.onPageLoad()))
      } else {
        val quartersSeq = penalties
          .filter(_.periodStartDate.getYear == yearsSeq.head)
          .map { penalty => Quarters.getQuarter(penalty.periodStartDate) }.distinct

        if (quartersSeq.size > 1) {
          Future.successful(Redirect(routes.SelectPenaltiesQuarterController.onPageLoad(yearsSeq.head.toString)))
        } else {
          Future.successful(Redirect(routes.SelectSchemeController.onPageLoad(quartersSeq.head.startDate)))
        }
      }
    }
  }
}
