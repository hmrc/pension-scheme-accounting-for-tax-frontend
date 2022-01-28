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

package utils

import base.SpecBase
import models.ChargeType
import org.mockito.MockitoSugar
import play.api.Configuration

class ValidationHelperSpec extends SpecBase with MockitoSugar{

  val validAnnualAllowanceHeader = "First name,Last name,National Insurance number,Tax year,Charge amount,Date,Payment type mandatory"
  val validLifeTimeAllowanceHeader = "First name,Last name,National Insurance number,Date,Tax due 25%,Tax due 55%"
  val invalidHeader = "Invalid Header"
  val appConfig: Configuration = injector.instanceOf[Configuration]

  private val validationHelper = new ValidationHelper(appConfig)

  "ValidationHelper" must {

    "return true if header is valid for Annual Allowance" in {
      validationHelper.isHeaderValid(validAnnualAllowanceHeader, ChargeType.ChargeTypeAnnualAllowance) mustBe true
    }

    "return false if header is invalid for Annual Allowance" in {
      validationHelper.isHeaderValid(invalidHeader, ChargeType.ChargeTypeAnnualAllowance)  mustBe false
    }

    "return true if header is valid for LifeTime Allowance" in {
      validationHelper.isHeaderValid(validLifeTimeAllowanceHeader, ChargeType.ChargeTypeLifetimeAllowance)  mustBe true
    }

    "return false if header is invalid for LifeTime Allowance" in {
      validationHelper.isHeaderValid(invalidHeader, ChargeType.ChargeTypeLifetimeAllowance)  mustBe false
    }

  }
}
