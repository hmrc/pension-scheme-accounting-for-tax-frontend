/*
 * Copyright 2023 HM Revenue & Customs
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
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AsyncWordSpec
import play.api.libs.json.Json
import play.api.mvc.Results._
import uk.gov.hmrc.http.{HeaderCarrier, HttpException}
import utils.WireMockHelper

class FinancialInfoCacheConnectorSpec extends AsyncWordSpec with Matchers with WireMockHelper with OptionValues with RecoverMethods {

  private implicit lazy val hc: HeaderCarrier = HeaderCarrier()
  override protected def portConfigKey: String = "microservice.services.pension-scheme-accounting-for-tax.port"

  private lazy val connector: CacheConnector = injector.instanceOf[CacheConnector]
  private val url = "/pension-scheme-accounting-for-tax/cache/financialInfo"

  ".fetch" must {

    "return `None` when there is no data in the collection" in {
      server.stubFor(
        get(urlEqualTo(url))
          .willReturn(
            notFound
          )
      )

      connector.fetch map {
        result =>
          result mustNot be(defined)
      }
    }

    "return data if data is present in the collection" in {
      server.stubFor(
        get(urlEqualTo(url))
          .willReturn(
            ok(Json.obj(fields = "testId" -> "data").toString())
          )
      )

      connector.fetch map {
        result =>
          result.value mustEqual Json.obj(fields = "testId" -> "data")
      }
    }

    "return a failed future on upstream error" in {
      server.stubFor(
        get(urlEqualTo(url))
          .willReturn(
            serverError
          )
      )

      recoverToSucceededIf[HttpException] {
        connector.fetch
      }
    }
  }

  ".save" must {
    val json = Json.obj(
      fields = "testId" -> "foobar"
    )
    "save the data in the collection" in {
      server.stubFor(
        post(urlEqualTo(url))
          .withRequestBody(equalTo(Json.stringify(json)))
          .willReturn(
            aResponse.withStatus(201)
          )
      )

      connector.save(json) map {
        _ mustEqual json
      }
    }

    "return a failed future on upstream error" in {

      server.stubFor(
        post(urlEqualTo(url))
          .withRequestBody(equalTo(Json.stringify(json)))
          .willReturn(
            serverError()
          )
      )
      recoverToSucceededIf[HttpException] {
        connector.save(json)
      }
    }
  }

  ".removeAll" must {

    "return OK after removing all the data from the collection" in {
      server.stubFor(delete(urlEqualTo(url)).
        willReturn(ok)
      )
      connector.removeAll.map {
        _ mustEqual Ok
      }
    }
  }
}
