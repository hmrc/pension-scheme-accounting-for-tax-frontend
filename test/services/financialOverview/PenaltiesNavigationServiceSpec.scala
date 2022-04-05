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
import connectors.ListOfSchemesConnector
import controllers.financialOverview.psa.routes._
import controllers.routes
import data.SampleData.psaId
import models.ChargeDetailsFilter
import models.ChargeDetailsFilter.All
import models.financialStatement.PenaltyType.AccountingForTaxPenalties
import models.financialStatement.PsaFSChargeType.{CONTRACT_SETTLEMENT_INTEREST, OTC_6_MONTH_LPP}
import models.financialStatement.PsaFSDetail
import org.mockito.ArgumentMatchers.any
import org.mockito.MockitoSugar
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import play.api.mvc.Results.Redirect
import services.PenaltiesServiceSpec.listOfSchemes
import services.financialOverview.PenaltiesNavigationServiceSpec.{getAftPenalties, penalties}

import java.time.LocalDate
import scala.concurrent.{ExecutionContext, Future}

class PenaltiesNavigationServiceSpec extends SpecBase with MockitoSugar with BeforeAndAfterEach with ScalaFutures {

  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
  private val journeyType: ChargeDetailsFilter = All
  private val year: Int = 2020
  private val aftPenalty = AccountingForTaxPenalties
  private val quarterPeriodStartDate = LocalDate.parse("2020-07-01")

  private val mockListOfSchemesConn: ListOfSchemesConnector = mock[ListOfSchemesConnector]
  val penaltiesNavigationServiceSpec: PenaltiesNavigationService = new PenaltiesNavigationService(mockListOfSchemesConn)

  "navFromAFTYearsPage" must {

    when(mockListOfSchemesConn.getListOfSchemes(any())(any(), any())).thenReturn(Future(Right(listOfSchemes)))

    "redirect to SelectQuarters page if there are multiple quarters to choose from" in {
      whenReady(penaltiesNavigationServiceSpec.navFromAFTYearsPage(penalties, year, psaId, aftPenalty )) {
        _ mustBe Redirect(SelectPenaltiesQuarterController.onPageLoad(year.toString))
      }
    }

    "redirect to Select Scheme page if there is only one quarter for the penalties" in {
      whenReady(penaltiesNavigationServiceSpec.navFromAFTYearsPage(getAftPenalties("24000040IN"), year, psaId, aftPenalty )) {
        _ mustBe Redirect(SelectSchemeController.onPageLoad(aftPenalty, quarterPeriodStartDate.toString))
      }
    }

    "redirect to SessionExpired page if there are no quarters in the given year" in {
      whenReady(penaltiesNavigationServiceSpec.navFromAFTYearsPage(getAftPenalties("24000041IN"), 0, psaId, aftPenalty)) {
        _ mustBe Redirect(routes.SessionExpiredController.onPageLoad)
      }
    }
  }

  "navFromPenaltiesTypePage" must {

    "redirect to SelectYear page if there are multiple years to choose from" in {
      whenReady(penaltiesNavigationServiceSpec.navFromPenaltiesTypePage(
        getAftPenalties("24000041IN", LocalDate.parse("2021-07-01"), LocalDate.parse("2021-07-01")), psaId, AccountingForTaxPenalties)) {
        _ mustBe Redirect(SelectPenaltiesYearController.onPageLoad(AccountingForTaxPenalties))
      }

    }
  }

}

object PenaltiesNavigationServiceSpec {

  val penalties: Seq[PsaFSDetail] = Seq(
    PsaFSDetail(
      chargeReference = "XY002610150186",
      chargeType = OTC_6_MONTH_LPP,
      dueDate = Some(LocalDate.parse("2020-11-15")),
      totalAmount = 80000.00,
      outstandingAmount = 56049.08,
      stoodOverAmount = 25089.08,
      accruedInterestTotal = 0.00,
      amountDue = 100.00,
      periodStartDate = LocalDate.parse("2020-07-01"),
      periodEndDate = LocalDate.parse("2020-09-30"),
      pstr = "24000041IN",
      sourceChargeRefForInterest = None,
      documentLineItemDetails = Nil
    ),
    PsaFSDetail(
      chargeReference = "XY002610150184",
      chargeType = OTC_6_MONTH_LPP,
      dueDate = Some(LocalDate.parse("2020-11-15")),
      totalAmount = 80000.00,
      outstandingAmount = 56049.08,
      stoodOverAmount = 25089.08,
      accruedInterestTotal = 0.00,
      amountDue = 100.00,
      periodStartDate = LocalDate.parse("2020-10-01"),
      periodEndDate = LocalDate.parse("2020-12-31"),
      pstr = "24000041IN",
      sourceChargeRefForInterest = None,
      documentLineItemDetails = Nil
    ),
    PsaFSDetail(
      chargeReference = "XY002610150185",
      chargeType = CONTRACT_SETTLEMENT_INTEREST,
      dueDate = Some(LocalDate.parse("2020-02-15")),
      totalAmount = 80000.00,
      outstandingAmount = 56049.08,
      stoodOverAmount = 25089.08,
      accruedInterestTotal = 0.00,
      amountDue = 100.00,
      periodStartDate = LocalDate.parse("2020-10-01"),
      periodEndDate = LocalDate.parse("2020-12-31"),
      pstr = "24000041IN",
      sourceChargeRefForInterest = None,
      documentLineItemDetails = Nil
    )
  )

  def getAftPenalties(
      schemeName2: String = "24000041IN",
      periodStartDate2: LocalDate = LocalDate.parse("2020-07-01"),
      periodEndDate2: LocalDate = LocalDate.parse("2020-09-30")
                     ): Seq[PsaFSDetail] = Seq(
    PsaFSDetail(
      chargeReference = "XY002610150186",
      chargeType = OTC_6_MONTH_LPP,
      dueDate = Some(LocalDate.parse("2020-11-15")),
      totalAmount = 80000.00,
      outstandingAmount = 56049.08,
      stoodOverAmount = 25089.08,
      accruedInterestTotal = 0.00,
      amountDue = 100.00,
      periodStartDate = LocalDate.parse("2020-07-01"),
      periodEndDate = LocalDate.parse("2020-09-30"),
      pstr = "24000041IN",
      sourceChargeRefForInterest = None,
      documentLineItemDetails = Nil
    ),
    PsaFSDetail(
      chargeReference = "XY002610150186",
      chargeType = OTC_6_MONTH_LPP,
      dueDate = Some(LocalDate.parse("2020-11-15")),
      totalAmount = 80000.00,
      outstandingAmount = 56049.08,
      stoodOverAmount = 25089.08,
      accruedInterestTotal = 0.00,
      amountDue = 100.00,
      periodStartDate = periodStartDate2,
      periodEndDate = periodEndDate2,
      pstr = schemeName2,
      sourceChargeRefForInterest = None,
      documentLineItemDetails = Nil
    )
  )

}