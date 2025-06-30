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

package controllers.financialOverview.scheme

import config.FrontendAppConfig
import connectors.FinancialStatementConnector
import controllers.actions._
import helpers.FormatHelper
import models.ChargeDetailsFilter.{All, History}
import models.SchemeDetails
import models.financialStatement.SchemeFSDetail
import play.api.Logger
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc._
import services.SchemeService
import services.financialOverview.scheme.{PaymentsAndChargesService, PaymentsCache}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import views.html.financialOverview.scheme.SchemeFinancialOverviewView

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class SchemeFinancialOverviewController @Inject()(identify: IdentifierAction,
                                                  override val messagesApi: MessagesApi,
                                                  val controllerComponents: MessagesControllerComponents,
                                                  schemeService: SchemeService,
                                                  financialStatementConnector: FinancialStatementConnector,
                                                  paymentsAndChargesService: PaymentsAndChargesService,
                                                  config: FrontendAppConfig,
                                                  schemeFinancialOverview: SchemeFinancialOverviewView,
                                                  accessAction: AllowAccessActionProviderForIdentifierRequest
                                                 )(implicit ec: ExecutionContext)
  extends FrontendBaseController
    with I18nSupport {

  private val logger = Logger(classOf[SchemeFinancialOverviewController])

  def schemeFinancialOverview(srn: String): Action[AnyContent] = (identify andThen accessAction(Some(srn))).async {
    implicit request =>
      val response = for {
        schemeDetails  <- schemeService.retrieveSchemeDetails(request.idOrException, srn)
        schemeFS       <- paymentsAndChargesService.getPaymentsFromCache(loggedInId = request.idOrException , srn = srn, request.isLoggedInAsPsa)
        creditSchemeFS <- financialStatementConnector.getSchemeFSPaymentOnAccount(schemeDetails.pstr, srn, request.isLoggedInAsPsa)
      } yield {
        renderFinancialOverview(srn, schemeDetails, schemeFS, creditSchemeFS.seqSchemeFSDetail)
      }
      response.flatten
  }

// scalastyle:off parameter.number
  private def renderFinancialOverview(srn: String,
                                      schemeDetails: SchemeDetails,
                                      schemeFSCache: PaymentsCache,
                                      creditSchemeFSDetail: Seq[SchemeFSDetail]
                                     )(implicit request: Request[_]): Future[Result] = {
    val schemeFSDetail                        = schemeFSCache.schemeFSDetail
    val schemeName                            = schemeDetails.schemeName
    val overdueCharges: Seq[SchemeFSDetail]   = paymentsAndChargesService.getOverdueCharges(schemeFSDetail)
    val interestCharges: Seq[SchemeFSDetail]  = paymentsAndChargesService.getInterestCharges(schemeFSDetail)
    val totalOverdueCharge: BigDecimal        = overdueCharges.map(_.amountDue).sum
    val totalInterestAccruing: BigDecimal     = interestCharges.map(_.accruedInterestTotal).sum
    val upcomingCharges: Seq[SchemeFSDetail]  = paymentsAndChargesService.extractUpcomingCharges(schemeFSDetail)
    val totalUpcomingCharge : BigDecimal      = upcomingCharges.map(_.amountDue).sum
    val totalUpcomingChargeFormatted          = s"${FormatHelper.formatCurrencyAmountAsString(totalUpcomingCharge)}"
    val totalOverdueChargeFormatted           = s"${FormatHelper.formatCurrencyAmountAsString(totalOverdueCharge)}"
    val totalInterestAccruingFormatted        = s"${FormatHelper.formatCurrencyAmountAsString(totalInterestAccruing)}"
    val creditBalanceFormatted: String        = creditBalanceAmountFormatted(creditSchemeFSDetail)
    val loggedInAsPsa: Boolean                = schemeFSCache.loggedInId.startsWith("A")
    val returnUrl = if(loggedInAsPsa) {
      Option(config.managePensionsSchemeSummaryUrl).getOrElse("/pension-scheme-summary/%s").format(srn)
    } else {
      Option(config.managePensionsSchemePspUrl).getOrElse("/%s/dashboard/pension-scheme-details").format(srn)
    }
    val isOverdueChargeAvailable = paymentsAndChargesService.isOverdueChargeAvailable(schemeFSDetail)
    val displayHistory = schemeFSDetail.exists(_.outstandingAmount <= 0)

    logger.debug(s"AFT service returned UpcomingCharge - $totalUpcomingCharge")
    logger.debug(s"AFT service returned OverdueCharge - $totalOverdueCharge")
    logger.debug(s"AFT service returned InterestAccruing - $totalInterestAccruing")
    logger.warn(s"$srn SchemeFinancialOverviewController totalUpcomingCharge: $totalUpcomingChargeFormatted")

    val requestRefundUrl = if (schemeFSCache.inhibitRefundSignal) {
      controllers.financialOverview.routes.RefundUnavailableController.onPageLoad.url
    } else {
      routes.RequestRefundController.onPageLoad(srn).url
    }

    Future.successful(Ok(schemeFinancialOverview(
      schemeName                         = schemeName,
      totalUpcomingCharge                = totalUpcomingChargeFormatted,
      totalOverdueCharge                 = totalOverdueChargeFormatted,
      totalInterestAccruing              = totalInterestAccruingFormatted,
      requestRefundUrl                   = requestRefundUrl,
      allOverduePenaltiesAndInterestLink = routes.PaymentsAndChargesController.onPageLoad(srn, "overdue").url,
      duePaymentLink                     = routes.PaymentsAndChargesController.onPageLoad(srn, "upcoming").url,
      allPaymentLink                     = routes.PaymentOrChargeTypeController.onPageLoad(srn, All).url,
      creditBalanceFormatted             = creditBalanceFormatted,
      creditBalance                      = getCreditBalanceAmount(creditSchemeFSDetail),
      isOverdueChargeAvailable           = isOverdueChargeAvailable,
      returnUrl                          = returnUrl,
      displayHistory                     = displayHistory,
      historyLink                        = routes.PaymentOrChargeTypeController.onPageLoad(srn, History).url
    )))
  }

  def getCreditBalanceAmount(schemeFs: Seq[SchemeFSDetail]): BigDecimal = {
    val sumAmountOverdue = schemeFs.filter(_.dueDate.nonEmpty).map(_.amountDue).sum

    val creditBalanceAmt = if (sumAmountOverdue >= 0) {
      BigDecimal(0.00)
    } else {
      sumAmountOverdue.abs
    }
    creditBalanceAmt
  }

  def creditBalanceAmountFormatted(schemeFs: Seq[SchemeFSDetail]): String = {
    val creditToDisplay = getCreditBalanceAmount(schemeFs)
    s"${FormatHelper.formatCurrencyAmountAsString(creditToDisplay)}"
  }
}