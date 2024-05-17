/*
 * Copyright 2024 HM Revenue & Customs
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

import config.FrontendAppConfig
import controllers.AFTOverviewController.{OverviewViewModel, outstandingAmountStr}
import controllers.actions.{AllowAccessActionProviderForIdentifierRequest, IdentifierAction}
import models.financialStatement.PaymentOrChargeType.{AccountingForTaxCharges, getPaymentOrChargeType}
import models.financialStatement.SchemeFSDetail
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.{JsObject, Json, OWrites}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import renderer.Renderer
import services.SchemeService
import services.financialOverview.scheme.PaymentsAndChargesService
import uk.gov.hmrc.nunjucks.NunjucksSupport
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController

import javax.inject.Inject
import scala.concurrent.ExecutionContext

class AFTOverviewController @Inject()(
                                       identify: IdentifierAction,
                                       renderer: Renderer,
                                       allowAccess: AllowAccessActionProviderForIdentifierRequest,
                                       config: FrontendAppConfig,
                                       schemeService: SchemeService,
                                       paymentsAndChargesService: PaymentsAndChargesService,
                                       override val messagesApi: MessagesApi,
                                       val controllerComponents: MessagesControllerComponents
)(implicit ec: ExecutionContext)
    extends FrontendBaseController
    with I18nSupport
    with NunjucksSupport {

  def onPageLoad(srn: String): Action[AnyContent] = (identify andThen allowAccess(Some(srn))).async {
    implicit request =>

      val payment = paymentsAndChargesService.getPaymentsForJourney(request.idOrException, srn, "all").map { paymentsCache =>
        val filteredPayments: Seq[SchemeFSDetail] =
          paymentsCache.schemeFSDetail.filter(p => getPaymentOrChargeType(p.chargeType) == AccountingForTaxCharges)

        val dueCharges: Seq[SchemeFSDetail] = paymentsAndChargesService.getDueCharges(filteredPayments)
        val totalDueCharges: BigDecimal = dueCharges.map(_.amountDue).sum
        val interestCharges: Seq[SchemeFSDetail] = paymentsAndChargesService.getInterestCharges(filteredPayments)
        val totalInterestCharges: BigDecimal = interestCharges.map(_.accruedInterestTotal).sum
        val totalCharges: BigDecimal = totalDueCharges + totalInterestCharges
        totalCharges
      }

      payment

        schemeService.retrieveSchemeDetails(
        psaId = request.idOrException,
        schemeIdType = "srn",
        srn = srn
      ).flatMap { sD =>
          payment flatMap { p =>
            val json: JsObject = Json.obj("viewModel" -> OverviewViewModel(
              returnUrl = config.schemeDashboardUrl(request).format(srn),
              schemeName = sD.schemeName,
              outstandingAmount = outstandingAmountStr(p)
              )
            )
            renderer.render("aftOverview.njk", json).map(Ok(_))
          }
      }
  }
}

object AFTOverviewController {

  private val outstandingAmountStr: BigDecimal => String = amount => s"£$amount"
  case class OverviewViewModel(
                                returnUrl: String,
                                schemeName: String,
                                outstandingAmount: String
                              )

  object OverviewViewModel {
    implicit lazy val writes: OWrites[OverviewViewModel] = Json.writes[OverviewViewModel]
  }
}
