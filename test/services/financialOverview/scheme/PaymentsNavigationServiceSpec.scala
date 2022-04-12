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

package services.financialOverview.scheme

import base.SpecBase
import data.SampleData._
import models.financialStatement.PaymentOrChargeType._
import models.financialStatement.SchemeFSChargeType._
import models.financialStatement.{SchemeFSChargeType, SchemeFSDetail}
import org.mockito.MockitoSugar
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import play.api.mvc.Results.Redirect
import utils.AFTConstants._
import controllers.financialOverview.scheme.routes._

import java.time.LocalDate

class PaymentsNavigationServiceSpec extends SpecBase with MockitoSugar with BeforeAndAfterEach with ScalaFutures {

  private val year: Int = 2020

  private def payments(charge2Start: LocalDate = Q3_2020_START,
                       charge2End: LocalDate = Q3_2020_END,
                       charge1Type: SchemeFSChargeType = PSS_AFT_RETURN,
                       charge2Type: SchemeFSChargeType = PSS_OTC_AFT_RETURN): Seq[SchemeFSDetail] = {
    Seq(
      SchemeFSDetail(
        index = 0,
        chargeReference = "1",
        chargeType = charge1Type,
        dueDate = Some(LocalDate.parse("2020-05-15")),
        totalAmount = -20000.00,
        outstandingAmount = 0.00,
        stoodOverAmount = 0.00,
        amountDue = 0.00,
        accruedInterestTotal = 0.00,
        periodStartDate = Some(QUARTER_START_DATE),
        periodEndDate = Some(QUARTER_END_DATE),
        formBundleNumber = None,
        version = None,
        receiptDate = None,
        sourceChargeRefForInterest = None,
        sourceChargeInfo = None,
        documentLineItemDetails = Nil
      ),
      SchemeFSDetail(
        index = 0,
        chargeReference = "2",
        chargeType = charge2Type,
        dueDate = Some(LocalDate.parse("2020-05-15")),
        totalAmount = -20000.00,
        outstandingAmount = 0.00,
        stoodOverAmount = 0.00,
        amountDue = 0.00,
        accruedInterestTotal = 0.00,
        periodStartDate = Some(charge2Start),
        periodEndDate = Some(charge2End),
        formBundleNumber = None,
        version = None,
        receiptDate = None,
        sourceChargeRefForInterest = None,
        sourceChargeInfo = None,
        documentLineItemDetails = Nil
      )
    )
  }

  val paymentsNavigationService: PaymentsNavigationService = new PaymentsNavigationService

  "navFromAFTYearsPage" must {
    "redirect to SelectQuarters page if there are multiple quarters to choose from" in {
      whenReady(paymentsNavigationService.navFromAFTYearsPage(payments(), year, srn, pstr)){
        _ mustBe Redirect(SelectQuarterController.onPageLoad(srn, pstr, year.toString))
      }
    }

    "redirect to PaymentsAndCharges page if there is only one quarter in the given year" in {
      whenReady(paymentsNavigationService.navFromAFTYearsPage(payments(QUARTER_START_DATE, QUARTER_END_DATE), year, srn, pstr)){
        _ mustBe Redirect(AllPaymentsAndChargesController.onPageLoad(srn, pstr, QUARTER_START_DATE.toString, AccountingForTaxCharges))
      }
    }

    "redirect to SessionExpired page if there are no quarters in the given year" in {
      whenReady(paymentsNavigationService.navFromAFTYearsPage(Nil, year, srn, pstr)){
        _ mustBe Redirect(controllers.routes.SessionExpiredController.onPageLoad)
      }
    }
  }

  "navFromPaymentsTypePage" must {
    "redirect to SelectYear page if there are multiple years to choose from" in {
      whenReady(paymentsNavigationService.navFromPaymentsTypePage(
        payments(Q3_2021_START, Q3_2021_END, PSS_CHARGE, PSS_CHARGE_INTEREST),
        srn,
        pstr,
        PensionsCharges)
      ){
        _ mustBe Redirect(SelectYearController.onPageLoad(srn, pstr, PensionsCharges))
      }
    }

    "redirect to navFromAFTYears method if there is only one year in the AFT category" in {
      whenReady(paymentsNavigationService.navFromPaymentsTypePage(payments(), srn, pstr, AccountingForTaxCharges)){
        _ mustBe Redirect(SelectQuarterController.onPageLoad(srn, pstr, year.toString))
      }
    }

    "redirect to PaymentsAndCharges page if there is only one year in the selected nonAFT category" in {
      whenReady(paymentsNavigationService.navFromPaymentsTypePage(payments(charge1Type = EXCESS_RELIEF_PAID), srn, pstr, ExcessReliefPaidCharges)){
        _ mustBe Redirect(AllPaymentsAndChargesController.onPageLoad(srn, pstr, QUARTER_START_DATE.getYear.toString, ExcessReliefPaidCharges))
      }
    }

    "redirect to SessionExpired page if there are no quarters in the given year" in {
      whenReady(paymentsNavigationService.navFromPaymentsTypePage(Nil, srn, pstr, ContractSettlementCharges)){
        _ mustBe Redirect(controllers.routes.SessionExpiredController.onPageLoad)
      }
    }
  }

  "navFromSchemeDashboard" must {
    "redirect to PaymentOrChargeType page if there are multiple payment types to choose from" in {
      whenReady(paymentsNavigationService.navFromSchemeDashboard(payments(charge2Type = CONTRACT_SETTLEMENT), srn, pstr)){
        _ mustBe Redirect(PaymentOrChargeTypeController.onPageLoad(srn, pstr))
      }
    }

    "redirect to PaymentsAndCharges page if there is only one quarter in the given year" in {
      whenReady(paymentsNavigationService.navFromSchemeDashboard(payments(QUARTER_START_DATE, QUARTER_END_DATE), srn, pstr)){
        _ mustBe Redirect(AllPaymentsAndChargesController.onPageLoad(srn, pstr, QUARTER_START_DATE.toString, AccountingForTaxCharges))
      }
    }

    "redirect to SessionExpired page if there are no quarters in the given year" in {
      whenReady(paymentsNavigationService.navFromSchemeDashboard(Nil, srn, pstr)){
        _ mustBe Redirect(controllers.routes.SessionExpiredController.onPageLoad)
      }
    }
  }

}
