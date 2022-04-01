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

import audit.{AFTUpscanFileUploadAuditEvent, AuditService}
import com.github.tomakehurst.wiremock.client.WireMock._
import config.FrontendAppConfig
import data.SampleData
import models.ChargeType.ChargeTypeAnnualAllowance
import models.requests.DataRequest
import models.{AdministratorOrPractitioner, Draft, UploadId, UserAnswers}
import org.mockito.ArgumentMatchers.any
import org.mockito.{ArgumentCaptor, MockitoSugar}
import org.scalatest._
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AsyncWordSpec
import play.api.http.Status
import play.api.http.Status.OK
import play.api.inject.bind
import play.api.inject.guice.GuiceableModule
import play.api.mvc.AnyContent
import play.api.test.FakeRequest
import play.api.test.Helpers.GET
import uk.gov.hmrc.domain.PsaId
import uk.gov.hmrc.http.{BadRequestException, HeaderCarrier}
import utils.WireMockHelper

import java.time.LocalDate

class UpscanInitiateConnectorSpec extends AsyncWordSpec with Matchers with WireMockHelper with OptionValues with RecoverMethods with MockitoSugar {
  //scalastyle.off: magic.number

  private implicit lazy val hc: HeaderCarrier = HeaderCarrier()

  override protected def portConfigKey: String = "microservice.services.upscan-initiate.port"

  private lazy val connector: UpscanInitiateConnector = app.injector.instanceOf[UpscanInitiateConnector]
  implicit val appConfig: FrontendAppConfig = mock[FrontendAppConfig]
  private val url = "/upscan/v2/initiate"

  private val startDate = LocalDate.of(2020, 1, 1)
  private val uploadId = UploadId.generate
  private val mockAuditService = mock[AuditService]
  private val psaId = "A2100000"
  private implicit val dataRequest: DataRequest[AnyContent] =
    DataRequest(FakeRequest(GET, "/"), "test-internal-id", Some(PsaId(psaId)), None, UserAnswers(), SampleData.sessionData())

  override protected def bindings: Seq[GuiceableModule] = Seq[GuiceableModule](
    bind[AuditService].toInstance(mockAuditService)
  )

  ".initiateV2" must {
    val successRedirectUrl = appConfig.successEndpointTarget("srn", startDate, Draft, 1, ChargeTypeAnnualAllowance, uploadId)

    val errorRedirectUrl = appConfig
      .failureEndpointTarget("srn", startDate, Draft, 1, ChargeTypeAnnualAllowance)

    val response1 =
      s"""{
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


    "Capture an AFTUpscanFileUploadAuditEvent when there is no connection to upscan" in {
      val eventCaptor: ArgumentCaptor[AFTUpscanFileUploadAuditEvent] = ArgumentCaptor.forClass(classOf[AFTUpscanFileUploadAuditEvent])
      server.stubFor(
        post(urlEqualTo(url))
          .willReturn(
            aResponse()
              .withStatus(400)
              .withBody("test")
          )
      )
      recoverToExceptionIf[BadRequestException] {
        connector.initiateV2(Some(successRedirectUrl), Some(errorRedirectUrl), ChargeTypeAnnualAllowance)
      } map { ex =>
        verify(mockAuditService, times(1)).sendEvent(eventCaptor.capture())(any(), any())
        ex.responseCode mustEqual Status.BAD_REQUEST
        val actualEvent: AFTUpscanFileUploadAuditEvent = eventCaptor.getValue
        actualEvent.psaOrPspId mustBe psaId
        actualEvent.schemeAdministratorType mustBe AdministratorOrPractitioner.Administrator
        actualEvent.chargeType mustBe ChargeTypeAnnualAllowance
        val error = actualEvent.outcome.fold(identity, _ => "")
        error.contains("returned 400 (Bad Request). Response body 'test'") mustBe true
      }
    }
  }
}
