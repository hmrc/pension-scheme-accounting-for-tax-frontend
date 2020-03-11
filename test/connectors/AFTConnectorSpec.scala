/*
 * Copyright 2020 HM Revenue & Customs
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
import data.SampleData
import models.UserAnswers
import org.scalatest._
import play.api.http.Status
import play.api.libs.json.Json
import uk.gov.hmrc.http._
import utils.WireMockHelper
import models.LocalDateBinder._

import scala.concurrent.ExecutionContext.Implicits.global

class AFTConnectorSpec extends AsyncWordSpec with MustMatchers with WireMockHelper {

  private implicit lazy val hc: HeaderCarrier = HeaderCarrier()

  override protected def portConfigKey: String = "microservice.services.pension-scheme-accounting-for-tax.port"

  private lazy val connector: AFTConnector = injector.instanceOf[AFTConnector]
  private val pstr = "test-pstr"
  private val aftSubmitUrl = "/pension-scheme-accounting-for-tax/aft-file-return"
  private val aftListOfVersionsUrl = "/pension-scheme-accounting-for-tax/get-aft-versions"
  private val getAftDetailsUrl = "/pension-scheme-accounting-for-tax/get-aft-details"

  "fileAFTReturn" must {

    "return successfully when the backend has returned OK" in {
      val data = Json.obj(fields = "Id" -> "value")
      server.stubFor(
        post(urlEqualTo(aftSubmitUrl))
          .withRequestBody(equalTo(Json.stringify(data)))
          .willReturn(
            ok
          )
      )

      connector.fileAFTReturn(pstr, UserAnswers(data)) map { _ =>
        server.findAll(postRequestedFor(urlEqualTo(aftSubmitUrl))).size() mustBe 1
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
        connector.fileAFTReturn(pstr, UserAnswers(data))
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
        connector.fileAFTReturn(pstr, UserAnswers(data))
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

      recoverToExceptionIf[Upstream5xxResponse](connector.fileAFTReturn(pstr, UserAnswers(data))) map {
        _.upstreamResponseCode mustBe Status.INTERNAL_SERVER_ERROR
      }
    }
  }

  "getAFTDetails" must {
    val data = Json.obj(fields = "Id" -> "value")
    val startDate = "2020-01-01"
    val aftVersion = "1"

    "return addRequiredDetailsToUserAnswers when the backend has returned OK with UserAnswers Json" in {
      server.stubFor(
        get(urlEqualTo(getAftDetailsUrl))
          .withHeader("pstr", equalTo(pstr))
          .withHeader("startDate", equalTo(startDate))
          .withHeader("aftVersion", equalTo(aftVersion))
          .willReturn(
            ok(Json.stringify(UserAnswers(data).data))
          )
      )

      connector.getAFTDetails(pstr, startDate, aftVersion) map { response =>
        response mustBe data
      }
    }

    "return BAD REQUEST when the backend has returned BadRequestException" in {
      server.stubFor(
        get(urlEqualTo(getAftDetailsUrl))
          .withHeader("pstr", equalTo(pstr))
          .withHeader("startDate", equalTo(startDate))
          .withHeader("aftVersion", equalTo(aftVersion))
          .willReturn(
            badRequest()
          )
      )

      recoverToExceptionIf[BadRequestException] {
        connector.getAFTDetails(pstr, startDate, aftVersion)
      } map {
        _.responseCode mustEqual Status.BAD_REQUEST
      }
    }

    "return NOT FOUND when the backend has returned NotFoundException" in {
      server.stubFor(
        get(urlEqualTo(getAftDetailsUrl))
          .withHeader("pstr", equalTo(pstr))
          .withHeader("startDate", equalTo(startDate))
          .withHeader("aftVersion", equalTo(aftVersion))
          .willReturn(
            notFound()
          )
      )

      recoverToExceptionIf[NotFoundException] {
        connector.getAFTDetails(pstr, startDate, aftVersion)
      } map { response =>
        response.responseCode mustEqual Status.NOT_FOUND
      }
    }

    "return Upstream5xxResponse when the backend has returned Internal Server Error" in {
      val data = Json.obj(fields = "Id" -> "value")
      server.stubFor(
        get(urlEqualTo(getAftDetailsUrl))
          .withHeader("pstr", equalTo(pstr))
          .withHeader("startDate", equalTo(startDate))
          .withHeader("aftVersion", equalTo(aftVersion))
          .willReturn(
            serverError()
          )
      )

      recoverToExceptionIf[Upstream5xxResponse](connector.getAFTDetails(pstr, startDate, aftVersion)) map { response =>
        response.upstreamResponseCode mustBe Status.INTERNAL_SERVER_ERROR
      }
    }
  }

  "getListOfVersions" must {
    "return successfully when the backend has returned OK" in {
      val expectedResult = Seq(1)
      server.stubFor(
        get(urlEqualTo(aftListOfVersionsUrl))
          .withHeader("pstr", equalTo(pstr))
          .withHeader("startDate", equalTo(SampleData.startDate))
          .willReturn(
            ok(Json.stringify(Json.toJson(expectedResult)))
          )
      )

      connector.getListOfVersions(pstr, SampleData.startDate) map { result =>
        result mustBe expectedResult
      }
    }

    "throw exception when the backend has returned something other than OK" in {
      server.stubFor(
        get(urlEqualTo(aftListOfVersionsUrl))
          .withHeader("pstr", equalTo(pstr))
          .withHeader("startDate", equalTo(SampleData.startDate))
          .willReturn(
            badRequest()
          )
      )

      recoverToExceptionIf[BadRequestException] {
        connector.getListOfVersions(pstr, SampleData.startDate)
      }.map {
        _.responseCode mustEqual Status.BAD_REQUEST
      }
    }
  }
}
