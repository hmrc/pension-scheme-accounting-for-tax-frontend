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

package controllers.financialOverview.psa

import config.FrontendAppConfig
import connectors.{FinancialStatementConnector, MinimalConnector}
import controllers.actions._
import helpers.FormatHelper
import models.financialStatement.{PsaFS, PsaFSChargeType, PsaFSDetail}
import play.api.Logger
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc._
import services.AFTPartialService
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import views.html.financialOverview.psa.PsaFinancialOverviewNewView
import views.html.financialOverview.psa.PsaFinancialOverviewView

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class PsaFinancialOverviewController @Inject()(
                                                identify: IdentifierAction,
                                                override val messagesApi: MessagesApi,
                                                val controllerComponents: MessagesControllerComponents,
                                                financialStatementConnector: FinancialStatementConnector,
                                                service: AFTPartialService,
                                                config: FrontendAppConfig,
                                                minimalConnector: MinimalConnector,
                                                psaFinancialOverviewNew: PsaFinancialOverviewNewView,
                                                psaFinancialOverview: PsaFinancialOverviewView
                                              )(implicit ec: ExecutionContext)
  extends FrontendBaseController
    with I18nSupport {

  private val logger = Logger(classOf[PsaFinancialOverviewController])

  def psaFinancialOverview: Action[AnyContent] = identify.async { implicit request =>
      val response = for {
        psaOrPspName <- minimalConnector.getPsaOrPspName
        psaFSWithPaymentOnAccount <- financialStatementConnector.getPsaFSWithPaymentOnAccount(request.psaIdOrException.id)
      } yield {
        val psaFSWithoutPaymentOnAccount: Seq[PsaFSDetail] = psaFSWithPaymentOnAccount.seqPsaFSDetail
          .filterNot(_.chargeType == PsaFSChargeType.PAYMENT_ON_ACCOUNT)

        renderFinancialOverview(psaOrPspName, psaFSWithoutPaymentOnAccount, psaFSWithPaymentOnAccount)
      }
    response.flatten
  }

  private def renderFinancialOverview(
                                       psaName: String,
                                       psaFSDetail: Seq[PsaFSDetail],
                                       creditPsaFS: PsaFS
                                     )(implicit request: Request[_]): Future[Result] = {
    val creditPsaFSDetails = creditPsaFS.seqPsaFSDetail
    val psaCharges: (String, String, String) = service.retrievePsaChargesAmount(psaFSDetail)
    val creditBalance = service.getCreditBalanceAmount(creditPsaFSDetails)
    val creditBalanceFormatted: String = s"${FormatHelper.formatCurrencyAmountAsString(creditBalance)}"

    logger.debug(s"AFT service returned UpcomingCharge - ${psaCharges._1}")
    logger.debug(s"AFT service returned OverdueCharge - ${psaCharges._2}")
    logger.debug(s"AFT service returned InterestAccruing - ${psaCharges._3}")

    val requestRefundUrl = if (creditPsaFS.inhibitRefundSignal) {
      controllers.financialOverview.routes.RefundUnavailableController.onPageLoad.url
    } else {
      routes.PsaRequestRefundController.onPageLoad.url
    }

    val allOverduePenaltiesAndInterestLink = routes.PsaPaymentsAndChargesController.onPageLoad(journeyType = "overdue").url
    val duePaymentLink = routes.PsaPaymentsAndChargesController.onPageLoad("upcoming").url
    val allPaymentLink = routes.PenaltyTypeController.onPageLoad().url
    val returnUrl = config.managePensionsSchemeOverviewUrl

    val templateToRender = if (config.podsNewFinancialCredits) {
      psaFinancialOverviewNew(
        psaName = psaName,
        totalUpcomingCharge = psaCharges._1,
        totalOverdueCharge = psaCharges._2,
        totalInterestAccruing = psaCharges._3,
        requestRefundUrl = requestRefundUrl,
        allOverduePenaltiesAndInterestLink = allOverduePenaltiesAndInterestLink,
        duePaymentLink = duePaymentLink,
        allPaymentLink = allPaymentLink,
        creditBalanceFormatted = creditBalanceFormatted,
        creditBalance = creditBalance,
        returnUrl = returnUrl
      )
    } else {
      psaFinancialOverview(
        psaName = psaName,
        totalUpcomingCharge = psaCharges._1,
        totalOverdueCharge = psaCharges._2,
        totalInterestAccruing = psaCharges._3,
        requestRefundUrl = requestRefundUrl,
        allOverduePenaltiesAndInterestLink = allOverduePenaltiesAndInterestLink,
        duePaymentLink = duePaymentLink,
        allPaymentLink = allPaymentLink,
        creditBalanceFormatted = creditBalanceFormatted,
        creditBalance = creditBalance,
        returnUrl = returnUrl
      )
    }

    Future.successful(Ok(templateToRender))

  }
}