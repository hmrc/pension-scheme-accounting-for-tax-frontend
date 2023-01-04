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
import models.fileUpload.FileUploadOutcome
import models.fileUpload.FileUploadOutcomeStatus.Success
import org.scalatest._
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AsyncWordSpec
import play.api.libs.json.Json
import uk.gov.hmrc.http.HeaderCarrier
import utils.WireMockHelper

class FileUploadOutcomeConnectorSpec extends AsyncWordSpec with Matchers with WireMockHelper with OptionValues with RecoverMethods {

  private implicit lazy val hc: HeaderCarrier = HeaderCarrier()
  override protected def portConfigKey: String = "microservice.services.pension-scheme-accounting-for-tax.port"

  private lazy val connector: FileUploadOutcomeConnector = injector.instanceOf[FileUploadOutcomeConnector]
  private val url = "/pension-scheme-accounting-for-tax/file-upload-outcome"

  private val successOutcome = FileUploadOutcome(Success, fileName = Some("test"))

  ".getOutcome" must {

    "return `None` when there is no data in the collection" in {
      server.stubFor(
        get(urlEqualTo(url))
          .willReturn(
            notFound
          )
      )

      connector.getOutcome map {
        result =>
          result mustNot be(defined)
      }
    }

    "return data if data is present in the collection" in {
      server.stubFor(
        get(urlEqualTo(url))
          .willReturn(
            ok(Json.toJson(successOutcome).toString())
          )
      )

      connector.getOutcome map {
        result =>
          result.value mustEqual successOutcome
      }
    }
  }

  ".setOutcome" must {
    val json = Json.toJson(successOutcome)

    "save the data in the collection" in {
      server.stubFor(
        post(urlEqualTo(url))
          .withRequestBody(equalTo(Json.stringify(json)))
          .willReturn(
            aResponse.withStatus(201)
          )
      )

      connector.setOutcome(successOutcome) map {
        _ mustEqual(():Unit)
      }
    }
  }

  ".deleteOutcome" must {
    "return OK after removing all the data from the collection" in {
      server.stubFor(delete(urlEqualTo(url)).
        willReturn(ok)
      )
      connector.deleteOutcome.map {
        _ mustEqual(():Unit)
      }
    }
  }
}
