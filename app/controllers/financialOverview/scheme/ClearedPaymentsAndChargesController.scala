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

package controllers.financialOverview.scheme

import config.FrontendAppConfig
import controllers.actions.{AllowAccessActionProviderForIdentifierRequest, IdentifierAction}
import models.ChargeDetailsFilter
import models.financialStatement.PaymentOrChargeType
import models.financialStatement.PaymentOrChargeType.getPaymentOrChargeType
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import services.financialOverview.scheme.PaymentsAndChargesService
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import utils.DateHelper
import views.html.financialOverview.scheme.ClearedPaymentsAndChargesView

import javax.inject.Inject
import scala.concurrent.ExecutionContext

class ClearedPaymentsAndChargesController @Inject()(override val messagesApi: MessagesApi,
                                                    identify: IdentifierAction,
                                                    config: FrontendAppConfig,
                                                    allowAccess: AllowAccessActionProviderForIdentifierRequest,
                                                    val controllerComponents: MessagesControllerComponents,
                                                    paymentsAndChargesService: PaymentsAndChargesService,
                                                    clearedPaymentsAndChargesView: ClearedPaymentsAndChargesView
                                                   )(implicit ec: ExecutionContext)
  extends FrontendBaseController
    with I18nSupport {

  def onPageLoad(srn: String, period: String, paymentOrChargeType: PaymentOrChargeType, journeyType: ChargeDetailsFilter): Action[AnyContent] = {
    (identify andThen allowAccess()).async { implicit request =>
      paymentsAndChargesService.getPaymentsForJourney(request.idOrException, srn, journeyType).map { paymentsCache =>
        val filteredPayments = paymentsCache.schemeFSDetail.filter(_.periodEndDate match {
          case Some(endDate) => DateHelper.getTaxYear(endDate) == period.toInt
          case _ => false
        }).filter(p => getPaymentOrChargeType(p.chargeType) == paymentOrChargeType)
          .filter(_.outstandingAmount <= 0)

        val table = paymentsAndChargesService.getClearedPaymentsAndCharges(srn, period, paymentOrChargeType, filteredPayments)
        Ok(clearedPaymentsAndChargesView(paymentsCache.schemeDetails.schemeName,
          table,
          returnUrl = config.financialOverviewUrl.format(srn),
          returnDashboardUrl = Option(config.managePensionsSchemeSummaryUrl).getOrElse("/pension-scheme-summary/%s").format(srn)
        ))
      }
    }
  }
}
