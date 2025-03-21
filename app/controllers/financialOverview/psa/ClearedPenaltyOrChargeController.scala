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
import helpers.FormatHelper
import models.financialStatement.PenaltyType.getPenaltyType
import models.financialStatement.{PenaltyType, PsaFSDetail}
import models.{ChargeDetailsFilter, Index}
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import services.financialOverview.psa.PsaPenaltiesAndChargesService
import uk.gov.hmrc.govukfrontend.views.viewmodels.summarylist.SummaryListRow
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import views.html.financialOverview.psa.ClearedPenaltyOrChargeView

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}



class ClearedPenaltyOrChargeController @Inject()(override val messagesApi: MessagesApi,
                                                 identify: IdentifierAction,
                                                 allowAccess: AllowAccessActionProviderForIdentifierRequest,
                                                 val controllerComponents: MessagesControllerComponents,
                                                 psaPenaltiesAndChargesService: PsaPenaltiesAndChargesService,
                                                 clearedPenaltyOrChargeView: ClearedPenaltyOrChargeView)
                                                (implicit ec: ExecutionContext) extends FrontendBaseController
  with I18nSupport {

  def onPageLoad(period: String, paymentOrChargeType: PenaltyType, journeyType: ChargeDetailsFilter, index: Index): Action[AnyContent] =
    (identify andThen allowAccess()).async { implicit request =>
      psaPenaltiesAndChargesService.getPenaltiesForJourney(request.psaIdOrException.id, journeyType).flatMap { penaltiesCache =>
        val filteredPenalties: Seq[PsaFSDetail] = penaltiesCache.penalties
          .filter(_.periodEndDate.getYear == period.toInt)
          .filter(p => getPenaltyType(p.chargeType) == paymentOrChargeType)

        val penalty = filteredPenalties(index)
        val chargeDetails: Seq[SummaryListRow] = psaPenaltiesAndChargesService.getChargeDetailsForClearedCharge(penalty)

        val datePaid = psaPenaltiesAndChargesService.getClearingDate(penalty.documentLineItemDetails)

        val paymentsTable = psaPenaltiesAndChargesService
          .chargeAmountDetailsRows(penalty, Some(Messages("psa.pension.scheme.charge.details.new")), "govuk-table__caption--l")

        val returnUrl = routes.ClearedPenaltiesAndChargesController.onPageLoad(period, paymentOrChargeType).url

        Future.successful(Ok(clearedPenaltyOrChargeView(
          penalty.chargeType.toString,
          penaltiesCache.psaName,
          FormatHelper.formatCurrencyAmountAsString(penalty.outstandingAmount),
          datePaid,
          chargeDetails,
          paymentsTable,
          returnUrl
        )))
      }
    }
}
