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

package controllers.financialOverview.psa

import config.FrontendAppConfig
import connectors.{FinancialStatementConnector, MinimalConnector}
import controllers.actions._
import controllers.financialOverview.psa
import helpers.FormatHelper
import models.financialStatement.{PsaFS, PsaFSChargeType, PsaFSDetail}
import play.api.Logger
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.Json
import play.api.mvc._
import renderer.Renderer
import services.AFTPartialService
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.NunjucksSupport

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class PsaFinancialOverviewController @Inject()(
                                                appConfig: FrontendAppConfig,
                                                identify: IdentifierAction,
                                                override val messagesApi: MessagesApi,
                                                val controllerComponents: MessagesControllerComponents,
                                                financialStatementConnector: FinancialStatementConnector,
                                                service: AFTPartialService,
                                                renderer: Renderer,
                                                minimalConnector: MinimalConnector
                                              )(implicit ec: ExecutionContext)
  extends FrontendBaseController
    with I18nSupport
    with NunjucksSupport {

  private val logger = Logger(classOf[PsaFinancialOverviewController])

  def psaFinancialOverview: Action[AnyContent] = identify.async {
    implicit request =>
      val response = for {
        psaOrPspName <- minimalConnector.getPsaOrPspName
        psaFSWithPaymentOnAccount <- financialStatementConnector.getPsaFSWithPaymentOnAccount(request.psaIdOrException.id)
      } yield {
        val psaFSWithoutPaymentOnAccount: Seq[PsaFSDetail] = psaFSWithPaymentOnAccount.seqPsaFSDetail
          .filterNot(_.chargeType == PsaFSChargeType.PAYMENT_ON_ACCOUNT)
        renderFinancialOverview(psaOrPspName, psaFSWithoutPaymentOnAccount, request, psaFSWithPaymentOnAccount)
      }
      response.flatten
  }

  private def renderFinancialOverview(psaName: String, psaFSDetail: Seq[PsaFSDetail],
                                      request: RequestHeader, creditPsaFS: PsaFS): Future[Result] = {
    val creditPsaFSDetails = creditPsaFS.seqPsaFSDetail
    val psaCharges: (String, String, String) = service.retrievePsaChargesAmount(psaFSDetail)
    val creditBalance = service.getCreditBalanceAmount(creditPsaFSDetails)
    val creditBalanceFormatted: String = s"${FormatHelper.formatCurrencyAmountAsString(creditBalance)}"

    logger.debug(s"AFT service returned UpcomingCharge - ${psaCharges._1}")
    logger.debug(s"AFT service returned OverdueCharge - ${psaCharges._2}")
    logger.debug(s"AFT service returned InterestAccruing - ${psaCharges._3}")

    val requestRefundUrl = creditPsaFS.inhibitRefundSignal match {
      case true => controllers.financialOverview.routes.RefundUnavailableController.onPageLoad.url
      case false => routes.PsaRequestRefundController.onPageLoad.url
    }

    renderer.render(
      template = "financialOverview/psa/psaFinancialOverview.njk",
      ctx = Json.obj("totalUpcomingCharge" -> psaCharges._1,
        "totalOverdueCharge" -> psaCharges._2,
        "totalInterestAccruing" -> psaCharges._3,
        "psaName" -> psaName, "requestRefundUrl" -> requestRefundUrl,
        "allOverduePenaltiesAndInterestLink" -> routes.PsaPaymentsAndChargesController.onPageLoad(journeyType = "overdue").url,
        "duePaymentLink" -> routes.PsaPaymentsAndChargesController.onPageLoad("upcoming").url,
        "allPaymentLink" -> psa.routes.PenaltyTypeController.onPageLoad().url,
        "creditBalanceFormatted" -> creditBalanceFormatted, "creditBalance" -> creditBalance)
    )(request).map(Ok(_))
  }
}