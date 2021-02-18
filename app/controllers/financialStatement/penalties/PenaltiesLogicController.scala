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

import controllers.actions._
import models.LocalDateBinder._
import models.Quarters
import models.financialStatement.PsaFS
import models.requests.IdentifierRequest
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Result, AnyContent, MessagesControllerComponents, Action}
import services.PenaltiesService
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.NunjucksSupport
import java.time.LocalDate

import javax.inject.Inject

import scala.concurrent.{Future, ExecutionContext}

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
      } else if (yearsSeq.size == 1) {
          skipYearsPage(penalties, yearsSeq.head)
      } else {
        Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad()))
      }
    }
  }

  private def skipYearsPage(penalties: Seq[PsaFS], year: Int)
                           (implicit request: IdentifierRequest[AnyContent]): Future[Result] = {
    val quartersSeq = penalties
      .filter(_.periodStartDate.getYear == year)
      .map { penalty => Quarters.getQuarter(penalty.periodStartDate) }.distinct

    if (quartersSeq.size > 1) {
      Future.successful(Redirect(routes.SelectPenaltiesQuarterController.onPageLoad(year.toString)))
    } else if (quartersSeq.size == 1) {
      skipQuartersPage(penalties, quartersSeq.head.startDate)
    } else {
      Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad()))
    }
  }

  private def skipQuartersPage(penalties: Seq[PsaFS], startDate: LocalDate)(implicit request: IdentifierRequest[AnyContent]): Future[Result] =
    service.penaltySchemes(startDate, request.psaIdOrException.id).map { schemes =>
      if(schemes.size > 1) {
        Redirect(routes.SelectSchemeController.onPageLoad(startDate))
      } else if (schemes.size == 1) {
        schemes.head.srn match {
          case Some(srn) =>
            Redirect(controllers.financialStatement.penalties.routes.PenaltiesController.onPageLoad(startDate, srn))
          case _ =>
            val pstrIndex: String = penalties.map(_.pstr).indexOf(schemes.head.pstr).toString
            Redirect(controllers.financialStatement.penalties.routes.PenaltiesController.onPageLoad(startDate, pstrIndex))
        }
      } else {
        Redirect(controllers.routes.SessionExpiredController.onPageLoad())
      }
    }
}
