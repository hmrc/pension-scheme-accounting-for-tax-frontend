/*
 * Copyright 2019 HM Revenue & Customs
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

package connectors.cache

import com.github.tomakehurst.wiremock.client.WireMock._
import org.scalatest._
import play.api.Application
import play.api.http.Status
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.mvc.Results._
import uk.gov.hmrc.http.{HeaderCarrier, HttpException}
import utils.WireMockHelper

import scala.concurrent.ExecutionContext.Implicits.global

class UserAnswersCacheConnectorSpec extends AsyncWordSpec with MustMatchers with WireMockHelper with OptionValues with RecoverMethods {

  private implicit lazy val hc: HeaderCarrier = HeaderCarrier()
  implicit lazy val app: Application = new GuiceApplicationBuilder()
    .configure(conf = "microservice.services.pension-scheme-accounting-for-tax.port" -> server.port)
    .build()
  private lazy val connector: UserAnswersCacheConnector = app.injector.instanceOf[UserAnswersCacheConnector]

  private val cacheUrl = "/pension-scheme-accounting-for-tax/journey-cache/aft/testId"

  ".fetch" must {

    "return `None` when the server returns NOT FOUND from the collection" in {
      server.stubFor(
        get(urlEqualTo(cacheUrl))
          .willReturn(
            notFound
          )
      )

      connector.fetch(cacheId = "testId") map {
        result =>
          result mustNot be(defined)
      }
    }

    "return data if data is present in the collection" in {
      server.stubFor(
        get(urlEqualTo(cacheUrl))
          .willReturn(
            ok(Json.obj("testId" -> "data").toString())
          )
      )

      connector.fetch(cacheId = "testId") map {
        result =>
          result.value mustEqual Json.obj("testId" -> "data")
      }
    }

    "return a failed future on upstream error" in {

      server.stubFor(
        get(urlEqualTo(cacheUrl))
          .willReturn(
            serverError
          )
      )

      recoverToExceptionIf[HttpException] {
        connector.fetch(cacheId = "testId")
      } map {
        _.responseCode mustEqual Status.INTERNAL_SERVER_ERROR
      }
    }
  }

  ".save" must {

    "save the data in the collection" in {

      val json = Json.obj(
        fields = "fake-identifier" -> "foobar"
      )

      val value = Json.stringify(json)

      server.stubFor(
        post(urlEqualTo(cacheUrl))
          .withRequestBody(equalTo(value))
          .willReturn(
            ok
          )
      )

      connector.save(cacheId = "testId", json) map {
        _ mustEqual json
      }
    }
  }

  ".removeAll" must {
    "remove all the data from the collection" in {
      val json = Json.obj(
        fields = "test-Id" -> "fake value",
        "other-key" -> "fake value"
      )
      val value = Json.stringify(json)

      server.stubFor(delete(urlEqualTo(cacheUrl)).
        willReturn(ok)
      )
      connector.removeAll(cacheId = "testId").map {
        _ mustEqual Ok
      }
    }
  }
}
