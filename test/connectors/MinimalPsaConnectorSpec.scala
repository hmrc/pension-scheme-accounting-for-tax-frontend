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

import com.github.tomakehurst.wiremock.client.WireMock._
import org.scalatest._
import play.api.libs.json.Json
import uk.gov.hmrc.http._
import utils.WireMockHelper

import scala.concurrent.ExecutionContext.Implicits.global

class MinimalPsaConnectorSpec extends AsyncWordSpec with MustMatchers with WireMockHelper {
  import MinimalPsaConnector._
  private implicit lazy val hc: HeaderCarrier = HeaderCarrier()

  override protected def portConfigKey: String = "microservice.services.pension-administrator.port"

  private lazy val connector: MinimalPsaConnector = injector.instanceOf[MinimalPsaConnector]
  private val psaId = "test-psa"
  private val minimalPsaDetailsUrl = "/pension-administrator/get-minimal-psa"
  private val email = "test@test.com"

  private def validResponse(b:Boolean) =
    Json.stringify(
      Json.obj(
        "email" -> email,
        "isPsaSuspended" -> b,
        "organisationName" -> "test ltd"
      )
    )

  "getMinimalPsaDetails" must {

    "return successfully when the backend has returned OK and a false response" in {
      server.stubFor(
        get(urlEqualTo(minimalPsaDetailsUrl))
          .willReturn(
            ok(validResponse(false))
              .withHeader("Content-Type", "application/json")
          )
      )

      connector.getMinimalPsaDetails(psaId) map {
        _ mustBe MinimalPSA(email, isPsaSuspended = false, Some("test ltd"), None)
      }
    }

    "return successfully when the backend has returned OK and a true response" in {
      server.stubFor(
        get(urlEqualTo(minimalPsaDetailsUrl))
          .willReturn(
            ok(validResponse(true))
              .withHeader("Content-Type", "application/json")
          )
      )

      connector.getMinimalPsaDetails(psaId) map {
        _ mustBe MinimalPSA(email, isPsaSuspended = true, Some("test ltd"), None)
      }
    }

    "return BadRequestException when the backend has returned anything other than ok" in {
      server.stubFor(
        get(urlEqualTo(minimalPsaDetailsUrl))
          .willReturn(
            badRequest
              .withHeader("Content-Type", "application/json")
          )
      )

      recoverToSucceededIf[BadRequestException] {
        connector.getMinimalPsaDetails(psaId)
      }
    }
  }
}
