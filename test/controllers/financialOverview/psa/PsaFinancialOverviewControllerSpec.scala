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

import connectors.{FinancialStatementConnector, MinimalConnector}
import controllers.base.ControllerSpecBase
import data.SampleData._
import matchers.JsonMatchers
import models.{ChargeDetailsFilter, Enumerable}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceableModule
import play.api.mvc.Results
import play.api.test.Helpers.{route, status, _}
import services.AFTPartialService
import uk.gov.hmrc.govukfrontend.views.Aliases.Table
import views.html.financialOverview.psa.{PsaFinancialOverviewNewView, PsaFinancialOverviewView}

import scala.concurrent.Future

class PsaFinancialOverviewControllerSpec
  extends ControllerSpecBase
    with JsonMatchers
    with BeforeAndAfterEach
    with Enumerable.Implicits
    with Results
    with ScalaFutures {

  private def getPartial: String = routes.PsaFinancialOverviewController.psaFinancialOverview.url

  private val mockAFTPartialService: AFTPartialService = mock[AFTPartialService]
  private val mockFinancialStatementConnector: FinancialStatementConnector = mock[FinancialStatementConnector]
  private val mockMinimalPsaConnector: MinimalConnector = mock[MinimalConnector]
  private val extraModules: Seq[GuiceableModule] =
    Seq[GuiceableModule](
      bind[AFTPartialService].toInstance(mockAFTPartialService),
      bind[FinancialStatementConnector].toInstance(mockFinancialStatementConnector),
      bind[MinimalConnector].toInstance(mockMinimalPsaConnector)
    )
  val application: Application = applicationBuilder(extraModules = extraModules).build()

  private val psaName = "psa-name"
  val requestRefundUrl = s"test.com?requestType=3&psaName=$psaName&availAmt=1000"

  val emptyChargesTable: Table = Table()

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockAFTPartialService)
    reset(mockAppConfig)
    when(mockAppConfig.timeoutSeconds).thenReturn("5")
    when(mockAppConfig.countdownSeconds).thenReturn("1")
    when(mockAppConfig.betaFeedbackUnauthenticatedUrl).thenReturn("/mockUrl")
    when(mockAppConfig.creditBalanceRefundLink).thenReturn("test.com")
    when(mockFinancialStatementConnector.getPsaFSWithPaymentOnAccount(any())(any(), any()))
      .thenReturn(Future.successful(psaFs))
  }

  "PsaFinancialOverviewController" must {
      "return old html with information received from overview api for new financial credits is false" in {
        when(mockAppConfig.countdownSeconds).thenReturn("60")
        when(mockAFTPartialService.retrievePsaChargesAmount(any()))
          .thenReturn(("10", "10", "10"))
        when(mockAFTPartialService.retrievePaidPenaltiesAndCharges(any())).thenReturn(Seq())
        when(mockAppConfig.podsNewFinancialCredits).thenReturn(false)
        when(mockFinancialStatementConnector.getPsaFSWithPaymentOnAccount(any())(any(), any()))
          .thenReturn(Future.successful(psaFs))
        when(mockAFTPartialService.getCreditBalanceAmount(any()))
          .thenReturn(BigDecimal("1000"))
        when(mockMinimalPsaConnector.getPsaOrPspName(any(), any(), any()))
          .thenReturn(Future.successful(psaName))

        val request = httpGETRequest(getPartial)
        val result = route(application, request).value

        status(result) mustEqual OK

        val view = application.injector.instanceOf[PsaFinancialOverviewView].apply(
          psaName = psaName,
          totalUpcomingCharge = "10",
          totalOverdueCharge = "10",
          totalInterestAccruing = "10",
          requestRefundUrl = routes.PsaRequestRefundController.onPageLoad.url,
          allOverduePenaltiesAndInterestLink = routes.PsaPaymentsAndChargesController.onPageLoad(journeyType = "overdue").url,
          duePaymentLink = routes.PsaPaymentsAndChargesController.onPageLoad("upcoming").url,
          allPaymentLink = routes.PenaltyTypeController.onPageLoad(ChargeDetailsFilter.All).url,
          creditBalanceFormatted = "£1,000.00",
          creditBalance = 1000,
          returnUrl = mockAppConfig.managePensionsSchemeOverviewUrl
        )(messages, request)

        compareResultAndView(result, view)

      }

      "return new html with information received from overview api for new financial credits is true" in {
        when(mockAppConfig.countdownSeconds).thenReturn("60")
        when(mockAFTPartialService.retrievePsaChargesAmount(any()))
          .thenReturn(("10", "10", "10"))
        when(mockAFTPartialService.retrievePaidPenaltiesAndCharges(any())).thenReturn(psaFsSeq)
        when(mockAppConfig.podsNewFinancialCredits).thenReturn(true)
        when(mockFinancialStatementConnector.getPsaFSWithPaymentOnAccount(any())(any(), any()))
          .thenReturn(Future.successful(psaFs))
        when(mockAFTPartialService.getCreditBalanceAmount(any()))
          .thenReturn(BigDecimal("1000"))
        when(mockMinimalPsaConnector.getPsaOrPspName(any(), any(), any()))
          .thenReturn(Future.successful(psaName))

        val request = httpGETRequest(getPartial)
        val result = route(application, httpGETRequest(getPartial)).value

        status(result) mustEqual OK

        val view = application.injector.instanceOf[PsaFinancialOverviewNewView].apply(
          psaName = psaName,
          totalUpcomingCharge = "10",
          totalOverdueCharge = "10",
          totalInterestAccruing = "10",
          requestRefundUrl = routes.PsaRequestRefundController.onPageLoad.url,
          allOverduePenaltiesAndInterestLink = routes.PsaPaymentsAndChargesController.onPageLoad(journeyType = "overdue").url,
          duePaymentLink = routes.PsaPaymentsAndChargesController.onPageLoad("upcoming").url,
          allPaymentLink = routes.RefundsController.onPageLoad().url,
          creditBalanceFormatted = "£1,000.00",
          creditBalance = 1000,
          displayReceivedPayments = true,
          receivedPaymentsLink = routes.PsaFinancialOverviewController.psaFinancialOverview.url,
          displayHistory = true,
          historyLink = routes.PsaFinancialOverviewController.psaFinancialOverview.url,
          returnUrl = mockAppConfig.managePensionsSchemeOverviewUrl
        )(messages, request)

        compareResultAndView(result, view)
      }
    }

  }
