/*
 * Copyright 2020 HM Revenue & Customs
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

import com.github.tomakehurst.wiremock.client.WireMock.{urlEqualTo, _}
import org.scalatest.{AsyncWordSpec, MustMatchers}
import play.api.http.Status
import uk.gov.hmrc.domain.PsaId
import uk.gov.hmrc.http.HeaderCarrier
import utils.WireMockHelper

class EmailConnectorSpec extends AsyncWordSpec with MustMatchers with WireMockHelper {
  override protected def portConfigKey: String = "microservice.services.email.port"

  private val url: String = "/hmrc/email"
  private implicit val hc: HeaderCarrier = HeaderCarrier()
  private val testPsaId = PsaId("A1234567")
  private val requestId = "test-Request-Id"

  private val testEmailAddress = "test@test.com"
  private val testTemplate = "testTemplate"
  private val journeyType = "AFTReturnSubmitted"

  private lazy val connector = injector.instanceOf[EmailConnector]

  "Email Connector" must {
    "return an EmailSent" when {
      "email sent successfully with status 202 (Accepted)" in {
        server.stubFor(
          post(urlEqualTo(url)).willReturn(
            aResponse()
              .withStatus(Status.ACCEPTED)
              .withHeader("Content-Type", "application/json")
          )
        )
        connector.sendEmail(requestId, testPsaId, journeyType, testEmailAddress, testTemplate, Map.empty).map { result => result mustBe EmailSent
        }
      }
    }

    "return an EmailNotSent" when {
      "email service is down" in {
        server.stubFor(
          post(urlEqualTo(url)).willReturn(
            serviceUnavailable()
              .withHeader("Content-Type", "application/json")
          )
        )

        connector.sendEmail(requestId, testPsaId, journeyType, testEmailAddress, testTemplate, Map.empty).map { result => result mustBe EmailNotSent
        }
      }

      "email service returns back with 204 (No Content)" in {
        server.stubFor(
          post(urlEqualTo(url)).willReturn(
            noContent()
              .withHeader("Content-Type", "application/json")
          )
        )
        connector.sendEmail(requestId, testPsaId, journeyType, testEmailAddress, testTemplate, Map.empty).map { result => result mustBe EmailNotSent
        }
      }
    }
  }
}
