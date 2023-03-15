/*
 * Copyright 2023 HM Revenue & Customs
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

package pages

import models.{AFTQuarter, ChargeType, UserAnswers, YearRange}
import pages.behaviours.PageBehaviours
import pages.mccloud._

import java.time.LocalDate

class IsPublicServicePensionsRemedyPageSpec extends PageBehaviours {

  import IsPublicServicePensionsRemedyPageSpec._

  "IsPublicServicePensionsRemedyPage" - {
    "when set to false must remove any other McCloud fields where there is a member index number" in {
      val page = IsPublicServicePensionsRemedyPage(chargeType, Some(0))
      val updatedUA = userAnswers(isPSR = true).setOrException(page, false)
      updatedUA.get(page) mustBe Some(false)
      updatedUA.get(IsChargeInAdditionReportedPage(chargeType, 0)) mustBe None
      updatedUA.get(WasAnotherPensionSchemePage(chargeType, 0)) mustBe None
      updatedUA.get(TaxYearReportedAndPaidPage(chargeType, 0, None)) mustBe None
      updatedUA.get(TaxQuarterReportedAndPaidPage(chargeType, 0, None)) mustBe None
      updatedUA.get(ChargeAmountReportedPage(chargeType, 0, None)) mustBe None
    }
  }
}

object IsPublicServicePensionsRemedyPageSpec {
  private val chargeType = ChargeType.ChargeTypeAnnualAllowance

  //scalastyle:off: magic.number
  private val startDate = LocalDate.of(2022, 4, 1)
  private val endDate = LocalDate.of(2022, 6, 30)
  private val bigDecimalValue = BigDecimal(10)

  private def userAnswers(isPSR: Boolean): UserAnswers = {
    UserAnswers().setOrException(IsPublicServicePensionsRemedyPage(chargeType, Some(0)), isPSR)
      .setOrException(IsChargeInAdditionReportedPage(chargeType, 0), true)
      .setOrException(WasAnotherPensionSchemePage(chargeType, 0), false)
      .setOrException(TaxYearReportedAndPaidPage(chargeType, 0, None), YearRange("2023"))
      .setOrException(TaxQuarterReportedAndPaidPage(chargeType, 0, None),
        AFTQuarter(startDate, endDate))
      .setOrException(ChargeAmountReportedPage(chargeType, 0, None), bigDecimalValue)
  }
}
