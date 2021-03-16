/*
 * Copyright 2021 HM Revenue & Customs
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
import connectors.cache.SessionDataCacheConnector
import models.AdministratorOrPractitioner
import org.scalatest._
import play.api.libs.json.Json
import uk.gov.hmrc.http._
import utils.WireMockHelper

class SessionDataCacheConnectorSpec
  extends AsyncWordSpec
    with MustMatchers
    with WireMockHelper {

  private implicit lazy val hc: HeaderCarrier = HeaderCarrier()

  override protected def portConfigKey: String = "microservice.services.pension-administrator.port"

  private lazy val connector: SessionDataCacheConnector = injector.instanceOf[SessionDataCacheConnector]
  private val externalId = "test-value"
  private val administratorOrPractitionerUrl = s"/pension-administrator/journey-cache/session-data/$externalId"

  private def jsonAOP(aop:AdministratorOrPractitioner) =
    Json.obj("administratorOrPractitioner" -> aop.toString)

  private def validResponse(administratorOrPractitioner:String) =
    Json.stringify(
      Json.obj(
        "administratorOrPractitioner" -> administratorOrPractitioner
      )
    )

  "getAdministratorOrPractitioner" must {
    "return successfully when the backend has returned OK and a correct response for administrator" in {
      server.stubFor(
        get(urlEqualTo(administratorOrPractitionerUrl))
          .willReturn(
            ok(validResponse("administrator"))
              .withHeader("Content-Type", "application/json")
          )
      )

      connector.fetch(externalId) map {
        _ mustBe Some(Json.parse(validResponse("administrator")))
      }
    }

    "return successfully when the backend has returned OK and a correct response for practitioner" in {
      server.stubFor(
        get(urlEqualTo(administratorOrPractitionerUrl))
          .willReturn(
            ok(validResponse("practitioner"))
              .withHeader("Content-Type", "application/json")
          )
      )

      connector.fetch(externalId) map {
        _ mustBe Some(jsonAOP(AdministratorOrPractitioner.Practitioner))
      }
    }

    "return successfully when the backend has returned NOT FOUND and a correct response for practitioner" in {
      server.stubFor(
        get(urlEqualTo(administratorOrPractitionerUrl))
          .willReturn(
            notFound
              .withHeader("Content-Type", "application/json")
          )
      )

      connector.fetch(externalId) map {
        _ mustBe None
      }
    }

    "return BadRequestException when the backend has returned bad request response" in {
      server.stubFor(
        get(urlEqualTo(administratorOrPractitionerUrl))
          .willReturn(
            badRequest
              .withHeader("Content-Type", "application/json")
          )
      )

        recoverToSucceededIf[HttpException] {
          connector.fetch(externalId)
        }
    }
  }
}
