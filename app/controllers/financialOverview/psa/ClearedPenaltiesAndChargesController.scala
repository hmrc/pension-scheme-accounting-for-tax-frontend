/*
 * Copyright 2025 HM Revenue & Customs
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

package controllers.financialOverview.psa

import controllers.actions.{AllowAccessActionProviderForIdentifierRequest, IdentifierAction}
import models.ChargeDetailsFilter
import models.financialStatement.PenaltyType.getPenaltyType
import models.financialStatement.{PenaltyType, PsaFSDetail}
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import services.financialOverview.psa.PsaPenaltiesAndChargesService
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import views.html.financialOverview.psa.ClearedPenaltiesAndChargesView

class ClearedPenaltiesAndChargesController @Inject()(override val messagesApi: MessagesApi,
                                                     identify: IdentifierAction,
                                                     allowAccess: AllowAccessActionProviderForIdentifierRequest,
                                                     val controllerComponents: MessagesControllerComponents,
                                                     psaPenaltiesAndChargesService: PsaPenaltiesAndChargesService,
                                                     view: ClearedPenaltiesAndChargesView)
                                                    (implicit ec: ExecutionContext) extends FrontendBaseController
  with I18nSupport {
  def onPageLoad(period: String, penaltyOrChargeType: PenaltyType, journeyType: ChargeDetailsFilter): Action[AnyContent] =
    (identify andThen allowAccess()).async { implicit request =>
      psaPenaltiesAndChargesService.getPenaltiesForJourney(request.psaIdOrException.id, journeyType).flatMap { penaltiesCache =>
        val filteredPenalties: Seq[PsaFSDetail] = penaltiesCache.penalties
          .filter(_.periodEndDate.getYear == period.toInt)
          .filter(p => getPenaltyType(p.chargeType) == penaltyOrChargeType)

        psaPenaltiesAndChargesService.getClearedPenaltiesAndCharges(request.idOrException, period, penaltyOrChargeType, filteredPenalties).flatMap { table =>
          Future.successful(Ok(view(penaltiesCache.psaName, table)))
        }
      }
  }
}
