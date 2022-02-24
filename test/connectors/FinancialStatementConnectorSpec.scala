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
import models.financialStatement.PsaFS
import models.financialStatement.PsaFSChargeType.{AFT_INITIAL_LFP, OTC_6_MONTH_LPP}
import models.financialStatement.SchemeFSChargeType.{EXCESS_RELIEF_INTEREST, PAYMENT_ON_ACCOUNT}
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AsyncWordSpec
import play.api.http.Status
import play.api.libs.json.Json
import uk.gov.hmrc.http._
import utils.WireMockHelper

import java.time.LocalDate

class FinancialStatementConnectorSpec extends AsyncWordSpec with Matchers with WireMockHelper {

  import FinancialStatementConnectorSpec._

  private implicit lazy val hc: HeaderCarrier = HeaderCarrier()

  override protected def portConfigKey: String = "microservice.services.pension-scheme-accounting-for-tax.port"

  private val psaId = "test-psa-id"
  private val pstr = "test-pstr"
  private val psaFSUrl = "/pension-scheme-accounting-for-tax/psa-financial-statement"
  private val schemeFSUrl = "/pension-scheme-accounting-for-tax/scheme-financial-statement"

  "getPsaFS" must {

    "return the PSA financial statement without PAYMENT ON ACCOUNT for a valid request/response with psa id" in {

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

      connector.getPsaFS(psaId).map(fs => fs mustBe psaFSResponse)

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
              .withBody(Json.toJson(SampleData.schemeFSResponseAftAndOTC).toString)
          )
      )

      val connector = injector.instanceOf[FinancialStatementConnector]

      connector.getSchemeFS(pstr).map(fs => fs mustBe SampleData.schemeFSResponseAftAndOTC)

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
  "getSchemeFSPaymentOnAccount" must {

    "return the Scheme financial statement for a valid request/response with pstr" in {

      server.stubFor(
        get(urlEqualTo(schemeFSUrl))
          .withHeader("pstr", equalTo(pstr))
          .willReturn(
            aResponse()
              .withStatus(Status.OK)
              .withHeader("Content-Type", "application/json")
              .withBody(Json.toJson(SampleData.schemeFSResponseAftAndOTC).toString)
          )
      )

      val connector = injector.instanceOf[FinancialStatementConnector]

      connector.getSchemeFSPaymentOnAccount(pstr).map(fs => fs mustBe SampleData.schemeFSResponseAftAndOTC)

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
        connector.getSchemeFSPaymentOnAccount(pstr)
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

object FinancialStatementConnectorSpec {

  val psaFSResponse: Seq[PsaFS] = Seq(
    PsaFS(
      chargeReference = "XY002610150184",
      chargeType = AFT_INITIAL_LFP,
      dueDate = Some(LocalDate.parse("2020-07-15")),
      totalAmount = 80000.00,
      outstandingAmount = 56049.08,
      stoodOverAmount = 25089.08,
      accruedInterestTotal = 0.00,
      amountDue = 1029.05,
      periodStartDate = LocalDate.parse("2020-04-01"),
      periodEndDate = LocalDate.parse("2020-06-30"),
      pstr = "24000040IN"
    ),
    PsaFS(
      chargeReference = "XY002610150185",
      chargeType = OTC_6_MONTH_LPP,
      dueDate = Some(LocalDate.parse("2020-02-15")),
      totalAmount = 80000.00,
      outstandingAmount = 56049.08,
      stoodOverAmount = 25089.08,
      accruedInterestTotal = 0.00,
      amountDue = 1029.05,
      periodStartDate = LocalDate.parse("2020-07-01"),
      periodEndDate = LocalDate.parse("2020-09-30"),
      pstr = "24000041IN"
    )
  )
}
