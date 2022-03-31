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
import data.SampleData
import models.ChargeType.ChargeTypeAnnualAllowance
import models.requests.DataRequest
import models.{Draft, UploadId, UserAnswers}
import org.mockito.MockitoSugar.mock
import org.scalatest._
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AsyncWordSpec
import play.api.http.Status.OK
import play.api.mvc.AnyContent
import play.api.test.FakeRequest
import play.api.test.Helpers.GET
import uk.gov.hmrc.domain.PsaId
import uk.gov.hmrc.http.HeaderCarrier
import utils.WireMockHelper

import java.time.LocalDate

class UpscanInitiateConnectorSpec extends AsyncWordSpec with Matchers with WireMockHelper with OptionValues with RecoverMethods {

  private implicit lazy val hc: HeaderCarrier = HeaderCarrier()

  override protected def portConfigKey: String = "microservice.services.upscan-initiate.port"

  private lazy val connector: UpscanInitiateConnector = injector.instanceOf[UpscanInitiateConnector]
  implicit val appConfig: FrontendAppConfig = mock[FrontendAppConfig]
  private val url = "/upscan/v2/initiate"

  //scalastyle.off: magic.number
  private val startDate = LocalDate.of(2020,1,1)
  private val uploadId = UploadId.generate

  private implicit val dataRequest: DataRequest[AnyContent] =
    DataRequest(FakeRequest(GET, "/"), "test-internal-id", Some(PsaId("A2100000")), None, UserAnswers(), SampleData.sessionData())

  ".initiateV2" must {
    val successRedirectUrl = appConfig.successEndpointTarget("srn", startDate, Draft, 1, ChargeTypeAnnualAllowance, uploadId)

    val errorRedirectUrl = appConfig
      .failureEndpointTarget("srn", startDate, Draft, 1, ChargeTypeAnnualAllowance )

    val response1 = s"""{
                  |    "reference": "11370e18-6e24-453e-b45a-76d3e32ea33d",
                  |    "uploadRequest": {
                  |        "href": "https://xxxx/upscan-upload-proxy/bucketName",
                  |        "fields": {
                  |            "acl": "private",
                  |            "key": "11370e18-6e24-453e-b45a-76d3e32ea33d",
                  |            "policy": "xxxxxxxx==",
                  |            "x-amz-algorithm": "AWS4-HMAC-SHA256",
                  |            "x-amz-credential": "ASIAxxxxxxxxx/20180202/eu-west-2/s3/aws4_request",
                  |            "x-amz-date": "yyyyMMddThhmmssZ",
                  |            "x-amz-meta-callback-url": "https://myservice.com/callback",
                  |            "x-amz-signature": "xxxx",
                  |            "success_action_redirect": "https://myservice.com/nextPage",
                  |            "error_action_redirect": "https://myservice.com/errorPage"
                  |        }
                  |    }
                  |}""".stripMargin
    "save the data in the collection" in {
      server.stubFor(
        post(urlEqualTo(url))
          .willReturn(
            aResponse.withBody(response1).withStatus(OK)
          )
      )

      connector.initiateV2(Some(successRedirectUrl), Some(errorRedirectUrl), ChargeTypeAnnualAllowance) map { result =>
        result.fileReference.reference mustEqual "11370e18-6e24-453e-b45a-76d3e32ea33d"
        result.formFields.get("success_action_redirect") mustEqual Some("https://myservice.com/nextPage")
      }
    }
  }

}
