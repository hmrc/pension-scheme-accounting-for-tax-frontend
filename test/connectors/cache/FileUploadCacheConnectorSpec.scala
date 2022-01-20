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
import models.{InProgress, Status, UploadId, UploadedSuccessfully}
import org.scalatest._
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AsyncWordSpec
import play.api.http.Status.OK
import play.api.libs.json.Json
import services.fileUpload.UploadProgressTracker
import uk.gov.hmrc.http.{HeaderCarrier, HttpException}
import utils.WireMockHelper

class FileUploadCacheConnectorSpec extends AsyncWordSpec with Matchers with WireMockHelper with OptionValues with RecoverMethods {

  private implicit lazy val hc: HeaderCarrier = HeaderCarrier()

  override protected def portConfigKey: String = "microservice.services.pension-scheme-accounting-for-tax.port"

  private lazy val connector: UploadProgressTracker = injector.instanceOf[UploadProgressTracker]
  private val url = "/pension-scheme-accounting-for-tax/cache/fileUpload"
  private val urlFileUploadResult = "/pension-scheme-accounting-for-tax/cache/fileUploadResult"

  ".getUploadResult" must {

    "return `None` when there is no data in the collection" in {
      server.stubFor(
        get(urlEqualTo(url))
          .willReturn(
            notFound
          )
      )

      connector.getUploadResult(UploadId("uploadId")) map {
        result =>
          result mustNot be(defined)
      }
    }

    "return data if data is present in the collection" in {
      server.stubFor(
        get(urlEqualTo(url))
          .willReturn(
            ok(Json.obj(fields = "_type" -> "InProgress").toString())
          )
      )

      connector.getUploadResult(UploadId("uploadId")) map {
        result =>
          result.value mustEqual InProgress
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
        connector.getUploadResult(UploadId("uploadId"))
      }
    }
  }

  ".requestUpload" must {
    val json = Json.obj(
      fields = "reference" -> "referenceId"
    )
    "save the data in the collection" in {
      server.stubFor(
        post(urlEqualTo(url))
          .withRequestBody(equalTo(Json.stringify(json)))
          .willReturn(
            aResponse.withStatus(OK)
          )
      )

      connector.requestUpload(UploadId("uploadId"), Reference("referenceId")) map {
        _.mustEqual((): Unit)
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
        connector.requestUpload(UploadId("uploadId"), Reference("referenceId"))
      }
    }
  }

  ".registerUploadResult" must {
    val fileUploadStatus=UploadedSuccessfully("test.csv","text/binary","www.test.com",Some("100".toLong))
    val response=Json.toJson(Status("UploadedSuccessfully", Some("www.test.com"), Some("text/binary"), Some("test.csv"), Some("100".toLong)))
    "save the data in the collection" in {
      server.stubFor(
        post(urlEqualTo(urlFileUploadResult))
          .withRequestBody(equalTo(Json.stringify(response)))
          .willReturn(
            aResponse.withStatus(OK)
          )
      )

      connector.registerUploadResult(Reference("referenceId"),fileUploadStatus) map {
        _.mustEqual((): Unit)
      }
    }

    "return a failed future on upstream error" in {

      server.stubFor(
        post(urlEqualTo(urlFileUploadResult))
          .withRequestBody(equalTo(Json.stringify(response)))
          .willReturn(
            serverError()
          )
      )
      recoverToSucceededIf[HttpException] {
        connector.registerUploadResult(Reference("referenceId"),fileUploadStatus)
      }
    }
  }


}
