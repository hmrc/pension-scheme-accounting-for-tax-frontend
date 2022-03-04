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
import data.SampleData
import models.LocalDateBinder._
import models.SubmitterType.{PSA, PSP}
import models.{AFTOverview, AFTOverviewVersion, AFTVersion, JourneyType, SubmitterDetails, UserAnswers, VersionsWithSubmitter}
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AsyncWordSpec
import play.api.http.Status
import play.api.libs.json.{JsBoolean, JsNumber, Json}
import uk.gov.hmrc.http._
import utils.{DateHelper, WireMockHelper}

import java.time.LocalDate

class AFTConnectorSpec extends AsyncWordSpec with Matchers with WireMockHelper {

  private implicit lazy val hc: HeaderCarrier = HeaderCarrier()

  override protected def portConfigKey: String = "microservice.services.pension-scheme-accounting-for-tax.port"

  private lazy val connector: AFTConnector = injector.instanceOf[AFTConnector]
  private val pstr = "test-pstr"
  private val aftSubmitUrl = "/pension-scheme-accounting-for-tax/aft-file-return/AFTReturnSubmitted"
  private val aftListOfVersionsUrl = "/pension-scheme-accounting-for-tax/get-versions-with-submitter"
  private val getAftDetailsUrl = "/pension-scheme-accounting-for-tax/get-aft-details"
  private val getIsAftNonZeroUrl = "/pension-scheme-accounting-for-tax/get-is-aft-non-zero"
  private val aftOverview: String = "/pension-scheme-accounting-for-tax/get-aft-overview"

  private val validAftOverviewResponse = Json.arr(
    Json.obj(
      "periodStartDate" -> "2028-04-01",
      "periodEndDate" -> "2028-06-30",
      "tpssReportPresent" -> false,
      "versionDetails" -> Json.obj(
      "numberOfVersions" -> JsNumber(1),
      "submittedVersionAvailable" -> false,
      "compiledVersionAvailable" -> true)
    ),
    Json.obj(
      "periodStartDate" -> "2022-01-01",
      "periodEndDate" -> "2022-03-31",
      "tpssReportPresent" -> false,
      "versionDetails" -> Json.obj(
      "numberOfVersions" -> JsNumber(1),
      "submittedVersionAvailable" -> true,
      "compiledVersionAvailable" -> false)
    )
  ).toString()

  val aftOverviewModel = Seq(
    AFTOverview(LocalDate.of(2028, 4, 1), LocalDate.of(2028, 6, 30), tpssReportPresent = false,
      Some(AFTOverviewVersion(1, submittedVersionAvailable = false, compiledVersionAvailable = true))),
    AFTOverview(LocalDate.of(2022, 1, 1), LocalDate.of(2022, 3, 31), tpssReportPresent = false,
      Some(AFTOverviewVersion(1, submittedVersionAvailable = true, compiledVersionAvailable = false)))
  )

  val aftOverviewVersion: Option[AFTOverviewVersion] = Some(AFTOverviewVersion(
    numberOfVersions = 1,
    submittedVersionAvailable = false,
    compiledVersionAvailable = true
  ))

  val seqAftOverview = Seq(
    AFTOverview(LocalDate.of(2020, 4, 1), LocalDate.of(2020, 6, 30), tpssReportPresent = false, aftOverviewVersion),
    AFTOverview(LocalDate.of(2020, 7, 1), LocalDate.of(2020, 9, 30), tpssReportPresent = false, aftOverviewVersion),
    AFTOverview(LocalDate.of(2020, 10, 1), LocalDate.of(2020, 12, 31), tpssReportPresent = false, aftOverviewVersion),
    AFTOverview(LocalDate.of(2021, 1, 1), LocalDate.of(2021, 3, 31), tpssReportPresent = false, aftOverviewVersion),
    AFTOverview(LocalDate.of(2021, 4, 1), LocalDate.of(2021, 6, 30), tpssReportPresent = false, aftOverviewVersion),
    AFTOverview(LocalDate.of(2021, 7, 1), LocalDate.of(2021, 9, 30), tpssReportPresent = false, aftOverviewVersion)
  )

  "fileSubmitReturn" must {

    "return successfully when the backend has returned OK" in {
      val data = Json.obj(fields = "Id" -> "value")
      server.stubFor(
        post(urlEqualTo(aftSubmitUrl))
          .withRequestBody(equalTo(Json.stringify(data)))
          .willReturn(
            ok
          )
      )

      connector.fileAFTReturn(pstr, UserAnswers(data), JourneyType.AFT_SUBMIT_RETURN) map {
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
        connector.fileAFTReturn(pstr, UserAnswers(data), JourneyType.AFT_SUBMIT_RETURN)
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
        connector.fileAFTReturn(pstr, UserAnswers(data), JourneyType.AFT_SUBMIT_RETURN)
      } map {
        _.responseCode mustEqual Status.NOT_FOUND
      }
    }

    "throw ReturnAlreadySubmittedException when 403 returned with message containing RETURN_ALREADY_SUBMITTED" in {
      val data = Json.obj(fields = "Id" -> "value")
      server.stubFor(
        post(urlEqualTo(aftSubmitUrl))
          .withRequestBody(equalTo(Json.stringify(data)))
          .willReturn(
            forbidden().withBody("""RETURN_ALREADY_SUBMITTED""")
          )
      )

      recoverToExceptionIf[ReturnAlreadySubmittedException](connector.fileAFTReturn(pstr, UserAnswers(data), JourneyType.AFT_SUBMIT_RETURN)) map {
        _ => assert(true)
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

    "return UpstreamErrorResponse when the backend has returned Internal Server Error" in {
      server.stubFor(
        get(urlEqualTo(getAftDetailsUrl))
          .withHeader("pstr", equalTo(pstr))
          .withHeader("startDate", equalTo(startDate))
          .withHeader("aftVersion", equalTo(aftVersion))
          .willReturn(
            serverError()
          )
      )

      recoverToExceptionIf[UpstreamErrorResponse](connector.getAFTDetails(pstr, startDate, aftVersion)) map { response =>
        response.statusCode mustBe Status.INTERNAL_SERVER_ERROR
      }
    }
  }

  "getAFTDetailsWithFbNumber" must {
    val data = Json.obj(fields = "Id" -> "value")
    val fbNumber = "123456789192"

    "return addRequiredDetailsToUserAnswers when the backend has returned OK with UserAnswers Json" in {
      server.stubFor(
        get(urlEqualTo(getAftDetailsUrl))
          .withHeader("pstr", equalTo(pstr))
          .withHeader("fbNumber", equalTo(fbNumber))
          .willReturn(
            ok(Json.stringify(UserAnswers(data).data))
          )
      )

      connector.getAFTDetailsWithFbNumber(pstr, fbNumber) map { response =>
        response mustBe data
      }
    }

    "return BAD REQUEST when the backend has returned BadRequestException" in {
      server.stubFor(
        get(urlEqualTo(getAftDetailsUrl))
          .withHeader("pstr", equalTo(pstr))
          .withHeader("fbNumber", equalTo(fbNumber))
          .willReturn(
            badRequest()
          )
      )

      recoverToExceptionIf[BadRequestException] {
        connector.getAFTDetailsWithFbNumber(pstr, fbNumber)
      } map {
        _.responseCode mustEqual Status.BAD_REQUEST
      }
    }

    "return NOT FOUND when the backend has returned NotFoundException" in {
      server.stubFor(
        get(urlEqualTo(getAftDetailsUrl))
          .withHeader("pstr", equalTo(pstr))
          .withHeader("fbNumber", equalTo(fbNumber))
          .willReturn(
            notFound()
          )
      )

      recoverToExceptionIf[NotFoundException] {
        connector.getAFTDetailsWithFbNumber(pstr, fbNumber)
      } map { response =>
        response.responseCode mustEqual Status.NOT_FOUND
      }
    }

    "return UpstreamErrorResponse when the backend has returned Internal Server Error" in {
      server.stubFor(
        get(urlEqualTo(getAftDetailsUrl))
          .withHeader("pstr", equalTo(pstr))
          .withHeader("fbNumber", equalTo(fbNumber))
          .willReturn(
            serverError()
          )
      )

      recoverToExceptionIf[UpstreamErrorResponse](connector.getAFTDetailsWithFbNumber(pstr, fbNumber)) map { response =>
        response.statusCode mustBe Status.INTERNAL_SERVER_ERROR
      }
    }
  }

  "getIsAftNonZero" must {
    val startDate = "2020-01-01"
    val aftVersion = "1"

    "return the boolean which backend has returned with an OK" in {
      server.stubFor(
        get(urlEqualTo(getIsAftNonZeroUrl))
          .withHeader("pstr", equalTo(pstr))
          .withHeader("startDate", equalTo(startDate))
          .withHeader("aftVersion", equalTo(aftVersion))
          .willReturn(
            ok(Json.stringify(JsBoolean(true)))
          )
      )

      connector.getIsAftNonZero(pstr, startDate, aftVersion) map { response =>
        response mustBe true
      }
    }

    "return BAD REQUEST when the backend has returned BadRequestException" in {
      server.stubFor(
        get(urlEqualTo(getIsAftNonZeroUrl))
          .withHeader("pstr", equalTo(pstr))
          .withHeader("startDate", equalTo(startDate))
          .withHeader("aftVersion", equalTo(aftVersion))
          .willReturn(
            badRequest()
          )
      )

      recoverToExceptionIf[BadRequestException] {
        connector.getIsAftNonZero(pstr, startDate, aftVersion)
      } map {
        _.responseCode mustEqual Status.BAD_REQUEST
      }
    }
  }

  "getListOfVersions" must {
    "return successfully when the backend has returned OK" in {
      val version1 = AFTVersion(1, LocalDate.of(2020, 4, 17), "submitted")
      val version2 = AFTVersion(2, LocalDate.of(2020, 5, 17), "submitted")
      val version3 = AFTVersion(3, LocalDate.of(2020, 6, 17), "submitted")
      val submitter1 = SubmitterDetails(PSA, "abc", "A1234567", None, LocalDate.of(2020, 4, 17))
      val submitter2 = SubmitterDetails(PSP, "def", "12345678", Some("A1234567"), LocalDate.of(2020, 5, 17))
      val submitter3 = SubmitterDetails(PSA, "ghi", "A2345678", None, LocalDate.of(2020, 6, 17))
      val versions = Seq(VersionsWithSubmitter(version1, Some(submitter1)),
        VersionsWithSubmitter(version2, Some(submitter2)),
        VersionsWithSubmitter(version3, Some(submitter3)))

      server.stubFor(
        get(urlEqualTo(aftListOfVersionsUrl))
          .withHeader("pstr", equalTo(pstr))
          .withHeader("startDate", equalTo(SampleData.startDate))
          .willReturn(
            ok(Json.stringify(Json.toJson(versions)))
          )
      )

      connector.getListOfVersions(pstr, SampleData.startDate) map { result =>
        result mustBe versions
      }
    }

    "return Seq.empty for NOT_FOUND response" in {
      server.stubFor(
        get(urlEqualTo(aftListOfVersionsUrl))
          .withHeader("pstr", equalTo(pstr))
          .withHeader("startDate", equalTo(SampleData.startDate))
          .willReturn(
            aResponse()
              .withStatus(Status.NOT_FOUND)
              .withHeader("Content-Type", "application/json")
              .withBody(Json.arr().toString())
          )
      )

      connector.getListOfVersions(pstr, SampleData.startDate) map { result =>
        result mustBe Seq.empty
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

  "getAftOverview" must {
    "return the AFTOverview for a valid request/response with correct dates" in {
      DateHelper.setDate(Some(LocalDate.of(2028, 5, 23)))

      server.stubFor(
        get(urlEqualTo(aftOverview)).withHeader("pstr", equalTo(pstr)).withHeader("startDate", equalTo("2022-01-01"))
          .withHeader("endDate", equalTo("2028-06-30")).willReturn(
          aResponse().withStatus(Status.OK).withHeader("Content-Type", "application/json").withBody(validAftOverviewResponse)))

      connector.getAftOverview(pstr).map(aftOverview => aftOverview mustBe aftOverviewModel)

    }

    "throw BadRequestException for a 400 INVALID_PSTR response" in {
      server.stubFor(
        get(urlEqualTo(aftOverview)).withHeader("pstr", equalTo(pstr)).withHeader("startDate", equalTo("2022-01-01"))
          .withHeader("endDate", equalTo("2028-06-30")).willReturn(
          badRequest.withHeader("Content-Type", "application/json").withBody(errorResponse("INVALID_PSTR"))))

      recoverToSucceededIf[BadRequestException] {
        connector.getAftOverview(pstr)
      }
    }

    "throw BadRequestException for a 400 INVALID_REPORT_TYPE response" in {
      server.stubFor(
        get(urlEqualTo(aftOverview)).withHeader("pstr", equalTo(pstr)).withHeader("startDate", equalTo("2022-01-01"))
          .withHeader("endDate", equalTo("2028-06-30")).willReturn(
          badRequest.withHeader("Content-Type", "application/json").withBody(errorResponse("INVALID_REPORT_TYPE"))))

      recoverToSucceededIf[BadRequestException] {
        connector.getAftOverview(pstr)
      }

    }

    "throw BadRequestException for a 400 INVALID_FROM_DATE response" in {
      server.stubFor(get(urlEqualTo(aftOverview)).willReturn(
        badRequest.withHeader("Content-Type", "application/json").withBody(errorResponse("INVALID_FROM_DATE"))))

      recoverToSucceededIf[BadRequestException] {
        connector.getAftOverview(pstr)
      }

    }

    "throw BadRequest for a 400 INVALID_TO_DATE response" in {
      server.stubFor(get(urlEqualTo(aftOverview)).willReturn(
        badRequest.withHeader("Content-Type", "application/json").withBody(errorResponse("INVALID_TO_DATE"))))
      val connector = injector.instanceOf[AFTConnector]

      recoverToSucceededIf[BadRequestException] {
        connector.getAftOverview(pstr)
      }

    }
  }

  "filterOverviewResponse" must {
    "filter records before startDate and after endDate" in {
      connector.filterOverviewResponse(Some("2020-10-01"), Some("2021-06-30"), seqAftOverview) mustBe
        Seq(seqAftOverview(2), seqAftOverview(3), seqAftOverview(4))
    }
  }


  def errorResponse(code: String): String = {
    Json.stringify(
      Json.obj(
        "code" -> code,
        "reason" -> s"Reason for $code"
      )
    )
  }

}
