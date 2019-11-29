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

package connectors

import com.github.tomakehurst.wiremock.client.WireMock._
import models.UserAnswers
import org.scalatest._
import play.api.http.Status
import play.api.libs.json.Json
import uk.gov.hmrc.http._
import utils.WireMockHelper

import scala.concurrent.ExecutionContext.Implicits.global

class AFTConnectorSpec extends AsyncWordSpec with MustMatchers with WireMockHelper {

  private implicit lazy val hc: HeaderCarrier = HeaderCarrier()

  override protected def portConfigKey: String = "microservice.services.pension-scheme-accounting-for-tax.port"

  private lazy val connector: AFTConnector = injector.instanceOf[AFTConnector]
  private val pstr = "test-pstr"
  private val aftSubmitUrl = "/pension-scheme-accounting-for-tax/submitAftReturn"

  ".submitAFTReturn" must {

    "return successfully when the backend has returned OK" in {
      val data = Json.obj(fields = "Id" -> "value")
      server.stubFor(
        post(urlEqualTo(aftSubmitUrl))
          .withRequestBody(equalTo(Json.stringify(data)))
          .willReturn(
            ok
          )
      )

      connector.submitAFTReturn(pstr, UserAnswers(data)) map {
        _ => server.findAll(postRequestedFor(urlEqualTo(aftSubmitUrl))).size() mustBe 1
      }
    }

    "return BAD REQUEST when the backend has returned BadRequestException" in {
      val data = Json.obj(fields = "Id" -> "value")
      server.stubFor(
        post(urlEqualTo(aftSubmitUrl))
          .withRequestBody(equalTo(Json.stringify(data)))
          .willReturn(
            badRequest()
          )
      )

      recoverToExceptionIf[BadRequestException] {
        connector.submitAFTReturn(pstr, UserAnswers(data))
      } map {
        _.responseCode mustEqual Status.BAD_REQUEST
      }
    }

    "return NOT FOUND when the backend has returned NotFoundException" in {
      val data = Json.obj(fields = "Id" -> "value")
      server.stubFor(
        post(urlEqualTo(aftSubmitUrl))
          .withRequestBody(equalTo(Json.stringify(data)))
          .willReturn(
            notFound()
          )
      )

      recoverToExceptionIf[NotFoundException] {
        connector.submitAFTReturn(pstr, UserAnswers(data))
      } map {
        _.responseCode mustEqual Status.NOT_FOUND
      }
    }

    "return Upstream5xxResponse when the backend has returned Internal Server Error" in {
      val data = Json.obj(fields = "Id" -> "value")
      server.stubFor(
        post(urlEqualTo(aftSubmitUrl))
          .withRequestBody(equalTo(Json.stringify(data)))
          .willReturn(
            serverError()
          )
      )

      recoverToExceptionIf[Upstream5xxResponse](connector.submitAFTReturn(pstr, UserAnswers(data))) map {
        _.upstreamResponseCode mustBe Status.INTERNAL_SERVER_ERROR
      }
    }
  }

}
