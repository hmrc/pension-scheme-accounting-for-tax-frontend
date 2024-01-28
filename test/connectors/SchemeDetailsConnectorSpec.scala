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

package connectors

import com.github.tomakehurst.wiremock.client.WireMock._
import models.SchemeDetails
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AsyncWordSpec
import play.api.libs.json.{JsBoolean, JsResultException, JsString, Json}
import uk.gov.hmrc.http._
import utils.WireMockHelper

class SchemeDetailsConnectorSpec
  extends AsyncWordSpec
    with Matchers
    with WireMockHelper {
  override protected def portConfigKey: String = "microservice.services.pensions-scheme.port"

  private implicit val headerCarrier: HeaderCarrier = HeaderCarrier()
  private val psaId = "0000"
  private val srn = "test srn"
  private val idNumber = "00000000AA"

  "getSchemeDetails" must {
    val schemeDetailsUrl = s"/pensions-scheme/scheme"
    "return the SchemeDetails for a valid request/response" in {
      val jsonResponse = """{"schemeName":"test scheme", "pstr": "test pstr", "schemeStatus": "test status"}"""
      server.stubFor(
        get(urlEqualTo(schemeDetailsUrl))
          .withHeader("idNumber", equalTo(idNumber))
          .withHeader("psaId", equalTo(psaId))
          .withHeader("schemeIdType", equalTo("pstr"))
          .willReturn(ok(jsonResponse)
            .withHeader("Content-Type", "application/json")
          )
      )

      val connector = injector.instanceOf[SchemeDetailsConnector]

      connector.getSchemeDetails(psaId, idNumber, "pstr").map(schemeDetails =>
        schemeDetails mustBe SchemeDetails("test scheme", "test pstr", "test status", None)
      )
    }

    "throw BadRequestException for a 400 Bad Request response" in {
      server.stubFor(
        get(urlEqualTo(schemeDetailsUrl))
          .withHeader("idNumber", equalTo(idNumber))
          .withHeader("psaId", equalTo(psaId))
          .willReturn(
            badRequest
              .withHeader("Content-Type", "application/json")
          )
      )

      val connector = injector.instanceOf[SchemeDetailsConnector]

      recoverToSucceededIf[BadRequestException] {
        connector.getSchemeDetails(psaId, idNumber, "srn")
      }
    }
  }

  "checkForAssociation" must {
    val checkAssociationUrl = "/pensions-scheme/is-psa-associated"

    "return ok with a valid response" in {
      server.stubFor(
        get(urlEqualTo(checkAssociationUrl))
          .withHeader("Content-Type", equalTo("application/json"))
          .withHeader("psaId", equalTo(psaId))
          .withHeader("schemeReferenceNumber", equalTo(srn))
          .willReturn(
            ok(Json.stringify(
              JsBoolean(true)
            )).withHeader("Content-Type", "application/json")
          )
      )

      val connector = injector.instanceOf[SchemeDetailsConnector]

      connector.checkForAssociation(psaId, srn, "psaId").map(isPsaAssociated =>
        isPsaAssociated mustBe true
      )
    }

    "throw jsResultException for an invalid response" in {
      server.stubFor(
        get(urlEqualTo(checkAssociationUrl))
          .withHeader("Content-Type", equalTo("application/json"))
          .withHeader("psaId", equalTo(psaId))
          .withHeader("schemeReferenceNumber", equalTo(srn))
          .willReturn(
            ok(Json.stringify(
              JsString("invalid response")
            )).withHeader("Content-Type", "application/json")
          )
      )

      val connector = injector.instanceOf[SchemeDetailsConnector]

      recoverToSucceededIf[JsResultException] {
        connector.checkForAssociation(psaId, srn, "psaId")
      }
    }

    "return InternalServerException for a 500 Internal Server Error" in {
      server.stubFor(
        get(urlEqualTo(checkAssociationUrl))
          .withHeader("Content-Type", equalTo("application/json"))
          .withHeader("psaId", equalTo(psaId))
          .withHeader("schemeReferenceNumber", equalTo(srn))
          .willReturn(serverError()))

      val connector = injector.instanceOf[SchemeDetailsConnector]

      recoverToSucceededIf[UpstreamErrorResponse] {
        connector.checkForAssociation(psaId, srn, "psaId")
      }
    }
  }
}
