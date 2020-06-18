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

import java.time.LocalDate

import com.github.tomakehurst.wiremock.client.WireMock._
import models.financialStatement.FSChargeType.{AFT_INITIAL_LLP, OTC_6_MONTH_LLP}
import models.financialStatement.{PsaFS, SchemeFS}
import org.scalatest._
import play.api.http.Status
import play.api.libs.json.Json
import uk.gov.hmrc.http._
import utils.WireMockHelper

import scala.concurrent.ExecutionContext.Implicits.global

class FinancialStatementConnectorSpec extends AsyncWordSpec with MustMatchers with WireMockHelper {

  private implicit lazy val hc: HeaderCarrier = HeaderCarrier()

  override protected def portConfigKey: String = "microservice.services.pension-scheme-accounting-for-tax.port"

  private val psaId = "test-psa-id"
  private val pstr = "test-pstr"
  private val psaFSUrl = "/pension-scheme-accounting-for-tax/psa-financial-statement"
  private val schemeFSUrl = "/pension-scheme-accounting-for-tax/scheme-financial-statement"

  private val psaFSResponse: Seq[PsaFS] = Seq(
    PsaFS(
      chargeReference = "XY002610150184",
      chargeType = AFT_INITIAL_LLP,
      dueDate = Some(LocalDate.parse("2020-02-15")),
      outstandingAmount = 56049.08,
      stoodOverAmount = 25089.08,
      amountDue = 1029.05,
      periodStartDate =  LocalDate.parse("2020-04-01"),
      periodEndDate =  LocalDate.parse("2020-06-30"),
      pstr = "24000040IN"
    ),
    PsaFS(
      chargeReference = "XY002610150184",
      chargeType = OTC_6_MONTH_LLP,
      dueDate = Some(LocalDate.parse("2020-02-15")),
      outstandingAmount = 56049.08,
      stoodOverAmount = 25089.08,
      amountDue = 1029.05,
      periodStartDate =  LocalDate.parse("2020-04-01"),
      periodEndDate =  LocalDate.parse("2020-06-30"),
      pstr = "24000041IN"
    )
  )

  private val schemeFSResponse: Seq[SchemeFS] = Seq(
    SchemeFS(
      chargeReference = "XY002610150184",
      chargeType = AFT_INITIAL_LLP,
      dueDate = Some(LocalDate.parse("2020-02-15")),
      outstandingAmount = 56049.08,
      stoodOverAmount = 25089.08,
      amountDue = 1029.05,
      accruedInterestTotal = 23000.55,
      periodStartDate =  LocalDate.parse("2020-04-01"),
      periodEndDate =  LocalDate.parse("2020-06-30")
    ),
    SchemeFS(
      chargeReference = "XY002610150184",
      chargeType = OTC_6_MONTH_LLP,
      dueDate = Some(LocalDate.parse("2020-02-15")),
      outstandingAmount = 56049.08,
      stoodOverAmount = 25089.08,
      amountDue = 1029.05,
      accruedInterestTotal = 24000.41,
      periodStartDate =  LocalDate.parse("2020-04-01"),
      periodEndDate =  LocalDate.parse("2020-06-30")
    )
  )

  "getPsaFS" must {

    "return the PSA financial statement for a valid request/response with psa id" in {

      server.stubFor(
        get(urlEqualTo(psaFSUrl))
          .withHeader("psaId", equalTo(psaId))
          .willReturn(
            aResponse()
              .withStatus(Status.OK)
              .withHeader("Content-Type", "application/json")
              .withBody(Json.toJson(psaFSResponse).toString)
          )
      )

      val connector = injector.instanceOf[FinancialStatementConnector]

      connector.getPsaFS(psaId).map(fs =>
        fs mustBe psaFSResponse
      )

    }

    "throw BadRequestException for a 400 INVALID_PSAID response" in {

      server.stubFor(
        get(urlEqualTo(psaFSUrl))
          .withHeader("psaId", equalTo(psaId))
          .willReturn(
            badRequest
              .withHeader("Content-Type", "application/json")
              .withBody(errorResponse("INVALID_PSAID"))
          )
      )
      val connector = injector.instanceOf[FinancialStatementConnector]

      recoverToSucceededIf[BadRequestException] {
        connector.getPsaFS(psaId)
      }

    }
  }

  "getSchemeFS" must {

    "return the Scheme financial statement for a valid request/response with pstr" in {

      server.stubFor(
        get(urlEqualTo(schemeFSUrl))
          .withHeader("pstr", equalTo(pstr))
          .willReturn(
            aResponse()
              .withStatus(Status.OK)
              .withHeader("Content-Type", "application/json")
              .withBody(Json.toJson(schemeFSResponse).toString)
          )
      )

      val connector = injector.instanceOf[FinancialStatementConnector]

      connector.getSchemeFS(pstr).map(fs =>
        fs mustBe schemeFSResponse
      )

    }

    "throw BadRequestException for a 400 INVALID_PSTR response" in {

      server.stubFor(
        get(urlEqualTo(schemeFSUrl))
          .withHeader("pstr", equalTo(pstr))
          .willReturn(
            badRequest
              .withHeader("Content-Type", "application/json")
              .withBody(errorResponse("INVALID_PSTR"))
          )
      )
      val connector = injector.instanceOf[FinancialStatementConnector]

      recoverToSucceededIf[BadRequestException] {
        connector.getSchemeFS(pstr)
      }

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
