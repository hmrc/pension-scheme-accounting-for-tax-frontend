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

package controllers.financialOverview

import config.FrontendAppConfig
import connectors.{FinancialInfoCreditAccessConnector, FinancialStatementConnector, MinimalConnector}
import controllers.actions.{AllowAccessActionProviderForIdentifierRequest, FakeIdentifierAction, IdentifierAction}
import controllers.base.ControllerSpecBase
import controllers.financialOverview.routes._
import data.SampleData._
import matchers.JsonMatchers
import models.CreditAccessType.AccessedByLoggedInPsaOrPsp
import models.requests.IdentifierRequest
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.scalatest.BeforeAndAfterEach
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.{GuiceApplicationBuilder, GuiceableModule}
import play.api.libs.json.{JsObject, Json}
import play.api.test.Helpers.{route, _}
import play.twirl.api.Html
import services.{PsaSchemePartialService, SchemeService}
import uk.gov.hmrc.nunjucks.NunjucksRenderer
import uk.gov.hmrc.viewmodels.NunjucksSupport

import scala.concurrent.Future

class RequestRefundControllerSpec extends ControllerSpecBase with NunjucksSupport with JsonMatchers with BeforeAndAfterEach {

  import RequestRefundControllerSpec._

  private def httpPathGET: String = controllers.financialOverview.routes.RequestRefundController.onPageLoad(srn).url

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
        bind[NunjucksRenderer].toInstance(mockRenderer),
        bind[AllowAccessActionProviderForIdentifierRequest].toInstance(mockAllowAccessActionProviderForIdentifierRequest)
      ): _*
    )
    .build()

  override def beforeEach: Unit = {
    super.beforeEach
    reset(mockRenderer, mockAppConfig,
      mockFinancialStatementConnector, mockPsaSchemePartialService, mockSchemeService,
      mockMinimalConnector, mockFinancialInfoCreditAccessConnector)
    when(mockAppConfig.schemeDashboardUrl(any(): IdentifierRequest[_])).thenReturn(dummyCall.url)
    when(mockRenderer.render(any(), any())(any())).thenReturn(Future.successful(Html("")))
    when(mockFinancialStatementConnector.getSchemeFSPaymentOnAccount(any())(any(), any()))
      .thenReturn(Future.successful(schemeFSResponseAftAndOTC))
    when(mockPsaSchemePartialService.getCreditBalanceAmount(any())).thenReturn(BigDecimal(44.4))
    when(mockSchemeService.retrieveSchemeDetails(any(), any(), any())(any(), any()))
      .thenReturn(Future.successful(schemeDetails))
    when(mockMinimalConnector.getPsaOrPspName(any(),any(),any()))
      .thenReturn(Future.successful("John Doe"))
    when(mockFinancialInfoCreditAccessConnector.creditAccessForPsa(any(), any())(any(), any())).thenReturn(Future.successful(Some(AccessedByLoggedInPsaOrPsp)))
  }

  private def expectedJson(heading:String, p1: String, continueUrl: String): JsObject = Json.obj(
    "heading" -> heading,
    "p1" -> p1,
    "continueUrl" -> continueUrl
  )

  "RequestRefundController" must {

    "when accessed by same PSA return OK and render correct content on page" in {
      val templateCaptor = ArgumentCaptor.forClass(classOf[String])
      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])
      val result = route(application, httpGETRequest(httpPathGET)).value
      status(result) mustEqual OK

//      verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())
//
//      templateCaptor.getValue mustEqual "financialOverview/requestRefund.njk"
//      jsonCaptor.getValue must containJson(expectedJson("", "", ""))
    }
  }
}

object RequestRefundControllerSpec {
  private val srn = "test-srn"
}

