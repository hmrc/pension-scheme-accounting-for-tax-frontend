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
import connectors.{FinancialInfoCreditAccessConnector, FinancialStatementConnector, MinimalConnector}
import controllers.actions.{AllowAccessActionProviderForIdentifierRequest, FakeIdentifierAction, IdentifierAction}
import controllers.base.ControllerSpecBase
import data.SampleData._
import matchers.JsonMatchers
import models.CreditAccessType.{AccessedByLoggedInPsaOrPsp, AccessedByOtherPsa, AccessedByOtherPsp}
import models.requests.IdentifierRequest
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, when}
import org.scalatest.BeforeAndAfterEach
import play.api.Application
import play.api.i18n.Messages
import play.api.inject.bind
import play.api.inject.guice.{GuiceApplicationBuilder, GuiceableModule}
import play.api.test.Helpers.{route, _}
import services.{PsaSchemePartialService, SchemeService}
import views.html.financialOverview.RequestRefundView

import scala.concurrent.Future

class RequestRefundControllerSpec extends ControllerSpecBase with JsonMatchers with BeforeAndAfterEach {

  import RequestRefundControllerSpec._

  private def httpPathGET: String = routes.RequestRefundController.onPageLoad(srn).url

  private val mockFinancialStatementConnector = mock[FinancialStatementConnector]
  private val mockPsaSchemePartialService = mock[PsaSchemePartialService]
  private val mockSchemeService = mock[SchemeService]
  private val mockMinimalConnector = mock[MinimalConnector]
  private val mockFinancialInfoCreditAccessConnector = mock[FinancialInfoCreditAccessConnector]

  private def application: Application = new GuiceApplicationBuilder()
    .overrides(
      Seq[GuiceableModule](
        bind[FrontendAppConfig].toInstance(mockAppConfig),
        bind[IdentifierAction].to[FakeIdentifierAction],
        bind[AllowAccessActionProviderForIdentifierRequest].toInstance(mockAllowAccessActionProviderForIdentifierRequest),
        bind[FinancialStatementConnector].toInstance(mockFinancialStatementConnector),
        bind[PsaSchemePartialService].toInstance(mockPsaSchemePartialService),
        bind[SchemeService].toInstance(mockSchemeService),
        bind[MinimalConnector].toInstance(mockMinimalConnector),
        bind[FinancialInfoCreditAccessConnector].toInstance(mockFinancialInfoCreditAccessConnector)
      ): _*
    )
    .build()

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockAppConfig)
    reset(mockFinancialStatementConnector)
    reset(mockPsaSchemePartialService)
    reset(mockSchemeService)
    reset(mockMinimalConnector)
    reset(mockFinancialInfoCreditAccessConnector)
    when(mockAppConfig.schemeDashboardUrl(any(): IdentifierRequest[_])).thenReturn(dummyCall.url)
    when(mockAppConfig.timeoutSeconds).thenReturn(5)
    when(mockAppConfig.countdownSeconds).thenReturn(1)
    when(mockAppConfig.betaFeedbackUnauthenticatedUrl).thenReturn("/mockUrl")
    when(mockFinancialStatementConnector.getSchemeFSPaymentOnAccount(any())(any(), any()))
      .thenReturn(Future.successful(schemeFSResponseAftAndOTC))
    when(mockPsaSchemePartialService.getCreditBalanceAmount(any())).thenReturn(BigDecimal(44.4))
    when(mockSchemeService.retrieveSchemeDetails(any(), any(), any())(any(), any()))
      .thenReturn(Future.successful(schemeDetails))
    when(mockMinimalConnector.getPsaOrPspName(any(), any(), any()))
      .thenReturn(Future.successful("John Doe"))
    when(mockAppConfig.creditBalanceRefundLink).thenReturn(dummyURL)
  }

  "RequestRefundController" must {

    "when accessed by same PSA return OK and render correct content on page" in {
      when(mockFinancialInfoCreditAccessConnector.creditAccessForSchemePsa(any(), any())(any(), any()))
        .thenReturn(Future.successful(Some(AccessedByLoggedInPsaOrPsp)))

      val request = httpGETRequest(httpPathGET)
      val result = route(application, httpGETRequest(httpPathGET)).value

      status(result) mustEqual OK

      val view = application.injector.instanceOf[RequestRefundView].apply(
        heading = Messages(s"requestRefund.youAlready.h1"),
        p1 = Messages(s"requestRefund.youAlready.p1"),
        continueUrl = s"$dummyURL?requestType=1&psaName=John Doe&pstr=pstr&availAmt=44.4"
      )(request, messages)

      compareResultAndView(result, view)
    }

    "when accessed by different PSA return OK and render correct content on page" in {
      when(mockFinancialInfoCreditAccessConnector.creditAccessForSchemePsa(any(), any())(any(), any()))
        .thenReturn(Future.successful(Some(AccessedByOtherPsa)))

      val request = httpGETRequest(httpPathGET)
      val result = route(application, httpGETRequest(httpPathGET)).value
      status(result) mustEqual OK

      val view = application.injector.instanceOf[RequestRefundView].apply(
        heading = Messages(s"requestRefund.psaAlready.h1"),
        p1 = Messages(s"requestRefund.psaAlready.p1"),
        continueUrl = s"$dummyURL?requestType=1&psaName=John Doe&pstr=pstr&availAmt=44.4")(request, messages)

      compareResultAndView(result, view)
    }

    "when accessed by different PSP return OK and render correct content on page" in {
      when(mockFinancialInfoCreditAccessConnector.creditAccessForSchemePsa(any(), any())(any(), any()))
        .thenReturn(Future.successful(Some(AccessedByOtherPsp)))

      val request = httpGETRequest(httpPathGET)
      val result = route(application, httpGETRequest(httpPathGET)).value
      status(result) mustEqual OK

      val view = application.injector.instanceOf[RequestRefundView].apply(
        heading = Messages(s"requestRefund.pspAlready.h1"),
        p1 = Messages(s"requestRefund.pspAlready.p1"),
        continueUrl = s"$dummyURL?requestType=1&psaName=John Doe&pstr=pstr&availAmt=44.4")(request, messages)

      compareResultAndView(result, view)
    }

    "when not accessed return redirect to correct page" in {
      when(mockFinancialInfoCreditAccessConnector.creditAccessForSchemePsa(any(), any())(any(), any()))
        .thenReturn(Future.successful(None))
      val result = route(application, httpGETRequest(httpPathGET)).value
      status(result) mustEqual SEE_OTHER
      redirectLocation(result).value mustBe s"$dummyURL?requestType=1&psaName=John Doe&pstr=pstr&availAmt=44.4"
    }
  }
}

object RequestRefundControllerSpec {
  private val srn = "test-srn"
  private val dummyURL = "/DUMMY"
}

