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

package services.financialOverview

import base.SpecBase
import connectors.cache.FinancialInfoCacheConnector
import connectors.{FinancialStatementConnector, MinimalConnector}
import data.SampleData.{psaFsSeq, psaId, pstr, schemeDetails}
import models.financialStatement.PsaFSChargeType.{AFT_INITIAL_LFP, OTC_6_MONTH_LPP}
import models.financialStatement.{DocumentLineItemDetail, PsaFS, FSClearingReason}
import models.{ChargeDetailsFilter, SchemeDetails}
import org.mockito.ArgumentMatchers.any
import org.mockito.MockitoSugar
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import play.api.libs.json.Json
import services.PenaltiesServiceSpec.dateNow
import services.financialOverview.PsaPenaltiesAndChargesServiceSpec.psaFS
import services.financialOverview.PsaPenaltiesAndChargesServiceSpec.psaFSResponse
import services.SchemeService
import uk.gov.hmrc.http.HeaderCarrier

import java.time.LocalDate
import scala.concurrent.{ExecutionContext, Future}

class PsaPenaltiesAndChargesServiceSpec extends SpecBase with MockitoSugar with BeforeAndAfterEach with ScalaFutures {

  val mockSchemeService: SchemeService = mock[SchemeService]
  val mockFSConnector: FinancialStatementConnector = mock[FinancialStatementConnector]
  val mockFinancialInfoCacheConnector: FinancialInfoCacheConnector = mock[FinancialInfoCacheConnector]
  val mockMinimalConnector: MinimalConnector = mock[MinimalConnector]
  val dateNow: LocalDate = LocalDate.now()
  val penaltiesCache: PenaltiesCache = PenaltiesCache(psaId, "psa-name", psaFsSeq)

  private val psaPenaltiesAndChargesService = new PsaPenaltiesAndChargesService(fsConnector = mockFSConnector,
    financialInfoCacheConnector = mockFinancialInfoCacheConnector, schemeService = mockSchemeService, minimalConnector = mockMinimalConnector)

  override def beforeEach: Unit = {
    super.beforeEach

    when(mockSchemeService.retrieveSchemeDetails(any(), any(), any())(any(), any()))
      .thenReturn(Future.successful(SchemeDetails(schemeDetails.schemeName, pstr, "Open", None)))
    when(mockFinancialInfoCacheConnector.fetch(any(), any())).thenReturn(Future.successful(Some(Json.toJson(psaFSResponse()))))
  }

  "getPenaltiesAndCharges" must {

  }

  "retrievePsaChargesAmount" must {
    "return true if the amount due is positive and due date is before today" in {
      val psaFS: PsaFS = psaFsSeq.head
      psaPenaltiesAndChargesService.retrievePsaChargesAmount(psaFsSeq) must equal("£0.00", "£200.00", "£0.00")
    }

    "getPenaltiesFromCache" must {
      "return payload from cache for psaId and match the payload" in {
        when(mockFinancialInfoCacheConnector.fetch(any(), any()))
          .thenReturn(Future.successful(Some(Json.toJson(penaltiesCache))))
        whenReady(psaPenaltiesAndChargesService.getPenaltiesForJourney(psaId, ChargeDetailsFilter.All)(
          any(classOf[ExecutionContext]), any(classOf[HeaderCarrier]))) {
          _ mustBe PenaltiesCache(psaId, "psa-name", psaFsSeq)
        }
      }

      "isPaymentOverdue" must {
        "return true if amountDue is greater than 0 and due date is after today" in {
          psaPenaltiesAndChargesService.isPaymentOverdue(psaFS(BigDecimal(0.01), Some(dateNow.minusDays(1)))) mustBe true
        }

        "return false if amountDue is less than or equal to 0" in {
          psaPenaltiesAndChargesService.isPaymentOverdue(psaFS(BigDecimal(0.00), Some(dateNow.minusDays(1)))) mustBe false
        }

        "return false if amountDue is greater than 0 and due date is not defined" in {
          psaPenaltiesAndChargesService.isPaymentOverdue(psaFS(BigDecimal(0.01), None)) mustBe false
        }

        "return false if amountDue is greater than 0 and due date is today" in {
          psaPenaltiesAndChargesService.isPaymentOverdue(psaFS(BigDecimal(0.01), Some(dateNow))) mustBe false
        }
      }

    }
  }
}

object PsaPenaltiesAndChargesServiceSpec {

  def psaFS(
             amountDue: BigDecimal = BigDecimal(1029.05),
             dueDate: Option[LocalDate] = Some(dateNow),
             totalAmount: BigDecimal = BigDecimal(80000.00),
             outStandingAmount: BigDecimal = BigDecimal(56049.08),
             stoodOverAmount: BigDecimal = BigDecimal(25089.08)
           ): PsaFS =
    PsaFS("XY002610150184", AFT_INITIAL_LFP, dueDate, totalAmount, amountDue, outStandingAmount, stoodOverAmount,
      accruedInterestTotal = 0.00, dateNow, dateNow, pstr, Seq(DocumentLineItemDetail(
        clearingReason = Some(FSClearingReason.CLEARED_WITH_PAYMENT),
        clearingDate = Some(LocalDate.parse("2020-06-30")),
        clearedAmountItem = BigDecimal(0.00))))


  def createPsaFSCharge(chargeReference: String): PsaFS =
    PsaFS(
      chargeReference = chargeReference,
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
      documentLineItemDetails = Seq(DocumentLineItemDetail(
        clearingReason = Some(FSClearingReason.CLEARED_WITH_PAYMENT),
        clearingDate = Some(LocalDate.parse("2020-06-30")),
        clearedAmountItem = BigDecimal(0.00)))
    )

  def psaFSResponse(amountDue: BigDecimal = BigDecimal(0.01), dueDate: LocalDate = dateNow): Seq[PsaFS] = Seq(
    PsaFS(
      chargeReference = "XY002610150184",
      chargeType = AFT_INITIAL_LFP,
      dueDate = Some(dueDate),
      totalAmount = 80000.00,
      outstandingAmount = 56049.08,
      stoodOverAmount = 25089.08,
      accruedInterestTotal = 0.00,
      amountDue = amountDue,
      periodStartDate = LocalDate.parse("2020-04-01"),
      periodEndDate = LocalDate.parse("2020-06-30"),
      pstr = "24000040IN",
      documentLineItemDetails = Seq(DocumentLineItemDetail(
        clearingReason= Some(FSClearingReason.CLEARED_WITH_PAYMENT),
        clearingDate = Some(LocalDate.parse("2020-06-30")),
        clearedAmountItem = BigDecimal(0.00)))
    ),
    PsaFS(
      chargeReference = "XY002610150185",
      chargeType = OTC_6_MONTH_LPP,
      dueDate = Some(dueDate),
      totalAmount = 80000.00,
      outstandingAmount = 56049.08,
      stoodOverAmount = 25089.08,
      accruedInterestTotal = 0.00,
      amountDue = amountDue,
      periodStartDate = LocalDate.parse("2020-04-01"),
      periodEndDate = LocalDate.parse("2020-06-30"),
      pstr = "24000041IN",
      documentLineItemDetails = Seq(DocumentLineItemDetail(
        clearingReason= Some(FSClearingReason.CLEARED_WITH_PAYMENT),
        clearingDate = Some(LocalDate.parse("2020-06-30")),
        clearedAmountItem = BigDecimal(0.00)))
    ),
    PsaFS(
      chargeReference = "XY002610150186",
      chargeType = OTC_6_MONTH_LPP,
      dueDate = Some(dueDate),
      totalAmount = 80000.00,
      outstandingAmount = 56049.08,
      stoodOverAmount = 25089.08,
      accruedInterestTotal = 0.00,
      amountDue = amountDue,
      periodStartDate = LocalDate.parse("2020-10-01"),
      periodEndDate = LocalDate.parse("2020-12-31"),
      pstr = "24000041IN",
      documentLineItemDetails = Seq(DocumentLineItemDetail(
        clearingReason= Some(FSClearingReason.CLEARED_WITH_PAYMENT),
        clearingDate = Some(LocalDate.parse("2020-06-30")),
        clearedAmountItem = BigDecimal(0.00)))
    )
  )

}