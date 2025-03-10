/*
 * Copyright 2024 HM Revenue & Customs
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
import data.SampleData.srn
import models.financialStatement.PsaFSChargeType.{AFT_INITIAL_LFP, INTEREST_ON_CONTRACT_SETTLEMENT, OTC_6_MONTH_LPP, PAYMENT_ON_ACCOUNT}
import models.financialStatement.{DocumentLineItemDetail, FSClearingReason, PsaFS, PsaFSDetail}
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
  private val schemeFSUrl = s"/pension-scheme-accounting-for-tax/scheme-financial-statement/$srn?loggedInAsPsa=true"

  "getPsaFS" must {

    "return the PSA financial statement without PAYMENT ON ACCOUNT for a valid request/response with psa id" in {

      server.stubFor(
        get(urlEqualTo(psaFSUrl))
          .withHeader("psaId", equalTo(psaId))
          .willReturn(
            aResponse()
              .withStatus(Status.OK)
              .withHeader("Content-Type", "application/json")
              .withBody(Json.toJson(psaFsToValidate).toString)
          )
      )
      val connector = injector.instanceOf[FinancialStatementConnector]

      connector.getPsaFS(psaId).map(fs => fs mustBe psaFsToValidate)

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

  "getPsaFSWithPaymentOnAccount" must {

    "return the PSA financial statement with PAYMENT ON ACCOUNT and without PAYMENT ON ACCOUNT for a valid request/response with psa id" in {

      server.stubFor(
        get(urlEqualTo(psaFSUrl))
          .withHeader("psaId", equalTo(psaId))
          .willReturn(
            aResponse()
              .withStatus(Status.OK)
              .withHeader("Content-Type", "application/json")
              .withBody(Json.toJson(psaFs).toString)
          )
      )
      val connector = injector.instanceOf[FinancialStatementConnector]

      connector.getPsaFSWithPaymentOnAccount(psaId).map(
        fs => fs mustBe psaFs
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
        connector.getPsaFSWithPaymentOnAccount(psaId)
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

      connector.getSchemeFS(pstr, srn, true).map(_.seqSchemeFSDetail mustBe SampleData.schemeFSResponseAftAndOTC.seqSchemeFSDetail)
    }

    "return the Scheme financial statement for a valid request/response with pstr and with extra fields" in {

      server.stubFor(
        get(urlEqualTo(schemeFSUrl))
          .withHeader("pstr", equalTo(pstr))
          .willReturn(
            aResponse()
              .withStatus(Status.OK)
              .withHeader("Content-Type", "application/json")
              .withBody(Json.toJson(SampleData.schemeFSResponseAftAndOTCWithExtraFieldValues).toString)
          )
      )

      val connector = injector.instanceOf[FinancialStatementConnector]

      connector.getSchemeFS(pstr, srn, true).map(_.seqSchemeFSDetail mustBe SampleData.schemeFSResponseAftAndOTCWithExtraFieldValues.seqSchemeFSDetail)
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
        connector.getSchemeFS(pstr, srn, true)
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

      connector.getSchemeFSPaymentOnAccount(pstr, srn, true).map(fs => fs mustBe SampleData.schemeFSResponseAftAndOTC)

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
        connector.getSchemeFSPaymentOnAccount(pstr, srn, true)
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

  val psaFSResponse: Seq[PsaFSDetail] = Seq(
    PsaFSDetail(
      index = 1,
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
      pstr = "24000040IN",
      sourceChargeRefForInterest = None,
      documentLineItemDetails = Seq(DocumentLineItemDetail(
        clearingReason= Some(FSClearingReason.CLEARED_WITH_PAYMENT),
        clearingDate = Some(LocalDate.parse("2020-06-30")),
        paymDateOrCredDueDate = Some(LocalDate.parse("2020-04-24")),
        clearedAmountItem = BigDecimal(0.00)))
    ),
    PsaFSDetail(
      index = 2,
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
      pstr = "24000041IN",
      sourceChargeRefForInterest = None,
      documentLineItemDetails = Seq(DocumentLineItemDetail(
        clearingReason= Some(FSClearingReason.CLEARED_WITH_PAYMENT),
        clearingDate = Some(LocalDate.parse("2020-06-30")),
        paymDateOrCredDueDate = Some(LocalDate.parse("2020-04-24")),
        clearedAmountItem = BigDecimal(0.00)))
    ),
    PsaFSDetail(
      index = 3,
      chargeReference = "XY002610150186",
      chargeType = PAYMENT_ON_ACCOUNT,
      dueDate = Some(LocalDate.parse("2020-02-15")),
      totalAmount = 80000.00,
      outstandingAmount = 56049.08,
      stoodOverAmount = 25089.08,
      accruedInterestTotal = 0.00,
      amountDue = 1029.05,
      periodStartDate = LocalDate.parse("2020-07-01"),
      periodEndDate = LocalDate.parse("2020-09-30"),
      pstr = "24000041IN",
      sourceChargeRefForInterest = None,
      documentLineItemDetails = Seq(DocumentLineItemDetail(
        clearingReason= Some(FSClearingReason.CLEARED_WITH_PAYMENT),
        clearingDate = Some(LocalDate.parse("2020-06-30")),
        paymDateOrCredDueDate = Some(LocalDate.parse("2020-04-24")),
        clearedAmountItem = BigDecimal(0.00)))
    )
  )

  val interestPsaFSResponse: Seq[PsaFSDetail] = Seq(
    PsaFSDetail(
      index = 0,
      chargeReference = "XY002610150184",
      chargeType = INTEREST_ON_CONTRACT_SETTLEMENT,
      dueDate = Some(LocalDate.parse("2020-07-15")),
      totalAmount = 80000.00,
      outstandingAmount = 56049.08,
      stoodOverAmount = 25089.08,
      accruedInterestTotal = 0.00,
      amountDue = 1029.05,
      periodStartDate = LocalDate.parse("2020-04-01"),
      periodEndDate = LocalDate.parse("2020-06-30"),
      pstr = "24000040IN",
      sourceChargeRefForInterest = None,
      documentLineItemDetails = Seq(DocumentLineItemDetail(
        clearingReason= Some(FSClearingReason.CLEARED_WITH_PAYMENT),
        clearingDate = Some(LocalDate.parse("2020-06-30")),
        paymDateOrCredDueDate = Some(LocalDate.parse("2020-04-24")),
        clearedAmountItem = BigDecimal(0.00)))
    )
  )
  val psaFs: PsaFS = PsaFS (false, psaFSResponse)

  val psaFSResponseToValidate: Seq[PsaFSDetail] = Seq(
    PsaFSDetail(
      index = 0,
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
      pstr = "24000040IN",
      sourceChargeRefForInterest = None,
      documentLineItemDetails = Seq(DocumentLineItemDetail(
        clearingReason= Some(FSClearingReason.CLEARED_WITH_PAYMENT),
        clearingDate = Some(LocalDate.parse("2020-06-30")),
        paymDateOrCredDueDate = Some(LocalDate.parse("2020-04-24")),
        clearedAmountItem = BigDecimal(0.00)))
    ),
    PsaFSDetail(
      index = 1,
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
      pstr = "24000041IN",
      sourceChargeRefForInterest = None,
      documentLineItemDetails = Seq(DocumentLineItemDetail(
        clearingReason= Some(FSClearingReason.CLEARED_WITH_PAYMENT),
        clearingDate = Some(LocalDate.parse("2020-06-30")),
        paymDateOrCredDueDate = Some(LocalDate.parse("2020-04-24")),
        clearedAmountItem = BigDecimal(0.00)))
    )
  )
  val psaFsToValidate: PsaFS = PsaFS (true, psaFSResponseToValidate)
}