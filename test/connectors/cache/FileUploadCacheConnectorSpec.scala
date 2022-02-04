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

package connectors.cache

import com.github.tomakehurst.wiremock.client.WireMock._
import connectors.Reference
import models.{Failed, UploadId}
import org.scalatest._
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AsyncWordSpec
import play.api.libs.json.Json
import uk.gov.hmrc.http.{HeaderCarrier, HttpException}
import utils.WireMockHelper

class FileUploadCacheConnectorSpec extends AsyncWordSpec with Matchers with WireMockHelper with OptionValues with RecoverMethods {

  private implicit lazy val hc: HeaderCarrier = HeaderCarrier()
  override protected def portConfigKey: String = "microservice.services.pension-scheme-accounting-for-tax.port"

  private lazy val connector: FileUploadCacheConnector = injector.instanceOf[FileUploadCacheConnector]
  private val url = "/pension-scheme-accounting-for-tax/cache/fileUpload"
  private val urlUploadResult = "/pension-scheme-accounting-for-tax/cache/fileUploadResult"

  ".getUploadResult" must {

    "return `None` when there is no data in the collection" in {
      server.stubFor(
        get(urlEqualTo(url))
          .willReturn(
            notFound
          )
      )

      connector.getUploadResult(UploadId("")) map {
        result =>
          result mustNot be(defined)
      }
    }

    "return data if data is present in the collection" in {
      server.stubFor(
        get(urlEqualTo(url))
          .willReturn(
            ok(Json.obj(fields = "_type" -> Failed.toString).toString())
          )
      )

      connector.getUploadResult(UploadId("test")) map {
        result =>
          result.value mustEqual Failed
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
        connector.getUploadResult(UploadId(""))
      }
    }
  }

  ".requestUpload" must {
    val json = Json.obj(
      fields =
               "reference" -> "reference"
    )
    "save the data in the collection" in {
      server.stubFor(
        post(urlEqualTo(url))
            .withHeader("uploadId" ,equalTo("uploadId"))
          .withRequestBody(equalTo(Json.stringify(json)))
          .willReturn(
            aResponse.withStatus(200)
          )
      )

      connector.requestUpload(UploadId("uploadId"),Reference("reference")) map {
        _ mustEqual ()
      }
    }

    "return a failed future on upstream error" in {

      server.stubFor(
        post(urlEqualTo(url))
          .withHeader("uploadId" ,equalTo("uploadId"))
          .withRequestBody(equalTo(Json.stringify(json)))
          .willReturn(
            serverError()
          )
      )
      recoverToSucceededIf[HttpException] {
        connector.requestUpload(UploadId(""),Reference(""))
      }
    }
  }

  ".registerUploadResult" must {
    val json = Json.obj(
      fields = "_type" -> Failed.toString
    )
    "save the data in the collection" in {
      server.stubFor(
        post(urlEqualTo(urlUploadResult))
          .withRequestBody(equalTo(Json.stringify(json)))
          .willReturn(
            aResponse.withStatus(200)
          )
      )

      connector.registerUploadResult(Reference(""),Failed) map {
        _ mustEqual ()
      }
    }

    "return a failed future on upstream error" in {

      server.stubFor(
        post(urlEqualTo(urlUploadResult))
          .withRequestBody(equalTo(Json.stringify(json)))
          .willReturn(
            serverError()
          )
      )
      recoverToSucceededIf[HttpException] {
        connector.registerUploadResult(Reference(""),Failed)
      }
    }
  }

}
