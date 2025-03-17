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
import models.{ChargeDetailsFilter, Index}
import models.financialStatement.{PaymentOrChargeType, SchemeFSDetail}
import models.financialStatement.PaymentOrChargeType.getPaymentOrChargeType
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import services.financialOverview.scheme.PaymentsAndChargesService
import uk.gov.hmrc.govukfrontend.views.viewmodels.summarylist.SummaryListRow
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import utils.DateHelper
import views.html.financialOverview.scheme.ClearedPaymentOrChargeView

import javax.inject.Inject
import scala.concurrent.ExecutionContext

class ClearedPaymentOrChargeController @Inject()(override val messagesApi: MessagesApi,
                                                  config: FrontendAppConfig,
                                                  identify: IdentifierAction,
                                                  allowAccess: AllowAccessActionProviderForIdentifierRequest,
                                                  val controllerComponents: MessagesControllerComponents,
                                                  paymentsAndChargesService: PaymentsAndChargesService,
                                                  clearedPaymentOrChargeView: ClearedPaymentOrChargeView
                                                )(implicit ec: ExecutionContext)
  extends FrontendBaseController
    with I18nSupport {
  def onPageLoad(srn: String, period: String, paymentOrChargeType: PaymentOrChargeType, journeyType: ChargeDetailsFilter, index: Index): Action[AnyContent] = {
    (identify andThen allowAccess()).async { implicit request =>
      paymentsAndChargesService.getPaymentsForJourney(request.idOrException, srn, journeyType, request.isLoggedInAsPsa).map { paymentsCache =>
        val filteredPayment: SchemeFSDetail = paymentsCache.schemeFSDetail.filter(_.periodEndDate match {
          case Some(endDate) => endDate.getYear == period.toInt
          case _ => false
        }).filter(p => getPaymentOrChargeType(p.chargeType) == paymentOrChargeType)(index)

        val paymentDates = filteredPayment.documentLineItemDetails.flatMap { documentLineItemDetail =>
          (documentLineItemDetail.paymDateOrCredDueDate, documentLineItemDetail.clearingDate) match {
            case (Some(paymDateOrCredDueDate), _) =>
              Some(paymDateOrCredDueDate)
            case (None, Some(clearingDate)) =>
              Some(clearingDate)
            case _ => None
          }
        }

        val datePaid = if (paymentDates.nonEmpty) {
          DateHelper.formatDateDMY(paymentDates.max)
        } else {
          ""
        }

        val chargeDetailsList: Seq[SummaryListRow] = paymentsAndChargesService
          .getChargeDetailsForSelectedChargeV2(filteredPayment, paymentsCache.schemeDetails, isClearedCharge = true)

        val paymentsTable = paymentsAndChargesService.chargeAmountDetailsRowsV2(filteredPayment)
        val loggedInAsPsa: Boolean = request.isLoggedInAsPsa
        val returnUrl = routes.ClearedPaymentsAndChargesController.onPageLoad(srn, period, paymentOrChargeType).url

        Ok(clearedPaymentOrChargeView(
          filteredPayment.chargeType.toString,
          paymentsCache.schemeDetails.schemeName,
          datePaid,
          chargeDetailsList,
          paymentsTable,
          returnDashboardUrl = if(loggedInAsPsa) {
            Option(config.managePensionsSchemeSummaryUrl).getOrElse("/pension-scheme-summary/%s").format(srn)
          } else {
            Option(config.managePensionsSchemePspUrl).getOrElse("/%s/dashboard/pension-scheme-details").format(srn)
          },
          returnUrl
        ))

      }
    }
  }
}
