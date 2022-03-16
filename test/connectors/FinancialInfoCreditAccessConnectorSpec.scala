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

package connectors

import com.github.tomakehurst.wiremock.client.WireMock._
import config.FrontendAppConfig
import models.CreditAccessType.{AccessedByLoggedInPsaOrPsp, AccessedByOtherPsa, AccessedByOtherPsp}
import org.mockito.MockitoSugar
import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AsyncWordSpec
import play.api.http.Status
import play.api.libs.json.JsString
import uk.gov.hmrc.http._
import utils.WireMockHelper

class FinancialInfoCreditAccessConnectorSpec extends AsyncWordSpec with Matchers with WireMockHelper with MockitoSugar with BeforeAndAfterEach {

  private implicit lazy val hc: HeaderCarrier = HeaderCarrier()

  override protected def portConfigKey: String = "microservice.services.pension-scheme-accounting-for-tax.port"

  private val psaPspId = "test-psa-id"
  private val srn = "srn"

  private val mockConfig = mock[FrontendAppConfig]

  override def beforeEach(): Unit = {
    reset(mockConfig)
  }


  "creditAccessForPsa" must {
    "return correct value when accessed by current PSA" in {
      val url = s"/pension-scheme-accounting-for-tax/cache/financial-info-credit-access/psa/$psaPspId/$srn"
      server.stubFor(
        get(urlEqualTo(url))
          .willReturn(
            aResponse()
              .withStatus(Status.OK)
              .withHeader("Content-Type", "application/json")
              .withBody(JsString(AccessedByLoggedInPsaOrPsp.toString).toString)
          )
      )
      val connector = app.injector.instanceOf[FinancialInfoCreditAccessConnector]

      connector.creditAccessForPsa(psaPspId, srn).map(fs => fs mustBe Some(AccessedByLoggedInPsaOrPsp))
    }

    "return correct value when accessed by different PSA" in {
      val url = s"/pension-scheme-accounting-for-tax/cache/financial-info-credit-access/psa/$psaPspId/$srn"
      server.stubFor(
        get(urlEqualTo(url))
          .willReturn(
            aResponse()
              .withStatus(Status.OK)
              .withHeader("Content-Type", "application/json")
              .withBody(JsString(AccessedByOtherPsa.toString).toString)
          )
      )
      val connector = app.injector.instanceOf[FinancialInfoCreditAccessConnector]

      connector.creditAccessForPsa(psaPspId, srn).map(fs => fs mustBe Some(AccessedByOtherPsa))
    }

    "return correct value when not accessed by any PSA or PSP" in {
      val url = s"/pension-scheme-accounting-for-tax/cache/financial-info-credit-access/psa/$psaPspId/$srn"
      server.stubFor(
        get(urlEqualTo(url))
          .willReturn(
            aResponse()
              .withStatus(Status.NOT_FOUND)
              .withHeader("Content-Type", "application/json")
          )
      )
      val connector = app.injector.instanceOf[FinancialInfoCreditAccessConnector]

      connector.creditAccessForPsa(psaPspId, srn).map(fs => fs mustBe None)
    }
  }

  "creditAccessForPsp" must {
    "return correct value when accessed by current PSP" in {
      val url = s"/pension-scheme-accounting-for-tax/cache/financial-info-credit-access/psp/$psaPspId/$srn"
      server.stubFor(
        get(urlEqualTo(url))
          .willReturn(
            aResponse()
              .withStatus(Status.OK)
              .withHeader("Content-Type", "application/json")
              .withBody(JsString(AccessedByLoggedInPsaOrPsp.toString).toString)
          )
      )
      val connector = app.injector.instanceOf[FinancialInfoCreditAccessConnector]

      connector.creditAccessForPsp(psaPspId, srn).map(fs => fs mustBe Some(AccessedByLoggedInPsaOrPsp))
    }

    "return correct value when accessed by different PSP" in {
      val url = s"/pension-scheme-accounting-for-tax/cache/financial-info-credit-access/psp/$psaPspId/$srn"
      server.stubFor(
        get(urlEqualTo(url))
          .willReturn(
            aResponse()
              .withStatus(Status.OK)
              .withHeader("Content-Type", "application/json")
              .withBody(JsString(AccessedByOtherPsp.toString).toString)
          )
      )
      val connector = app.injector.instanceOf[FinancialInfoCreditAccessConnector]

      connector.creditAccessForPsp(psaPspId, srn).map(fs => fs mustBe Some(AccessedByOtherPsp))
    }

    "return correct value when accessed by no PSA or PSP" in {
      val url = s"/pension-scheme-accounting-for-tax/cache/financial-info-credit-access/psp/$psaPspId/$srn"
      server.stubFor(
        get(urlEqualTo(url))
          .willReturn(
            aResponse()
              .withStatus(Status.NOT_FOUND)
              .withHeader("Content-Type", "application/json")
          )
      )
      val connector = app.injector.instanceOf[FinancialInfoCreditAccessConnector]

      connector.creditAccessForPsp(psaPspId, srn).map(fs => fs mustBe None)
    }
  }
}
