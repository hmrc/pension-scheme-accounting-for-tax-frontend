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

import config.FrontendAppConfig
import models.ChargeType
import models.ChargeType.{ChargeTypeAnnualAllowance, ChargeTypeLifetimeAllowance}
import org.mockito.MockitoSugar.mock
import org.scalatest.matchers.must.Matchers.convertToAnyMustWrapper

class ValidationHelperSpec {

  val validAnnualAllowanceHeader = "FirstName,LastName,Nino,TaxYear,ChargeAmount,DateReceived,PaymentTypeMandatory"
  val validLifeTimeAllowanceHeader = "FirstName,LastName,Nino,TaxYear"
  val invalidHeader = "Invalid Header"
  val mockAppConfig: FrontendAppConfig = mock[FrontendAppConfig]

  "ValidationHelper" must {

    "should return true if header is valid for Annual Allowance" in {
      ValidationHelper.isHeaderValid(validAnnualAllowanceHeader, ChargeType.ChargeTypeAnnualAllowance, mockAppConfig)
    }

    "should return false if header is invalid for Annual Allowance" in {
      ValidationHelper.isHeaderValid(invalidHeader, ChargeType.ChargeTypeAnnualAllowance, mockAppConfig)
    }

    "should return true if header is valid for LifeTime Allowance" in {
      ValidationHelper.isHeaderValid(validLifeTimeAllowanceHeader, ChargeType.ChargeTypeAnnualAllowance, mockAppConfig)
    }

    "should return false if header is invalid for LifeTime Allowance" in {
      ValidationHelper.isHeaderValid(invalidHeader, ChargeType.ChargeTypeAnnualAllowance, mockAppConfig)
    }

  }
}
