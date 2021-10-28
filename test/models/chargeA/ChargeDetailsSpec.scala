/*
 * Copyright 2021 HM Revenue & Customs
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

package models.chargeA

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers

class ChargeDetailsSpec extends AnyFreeSpec with Matchers {

  "calcTotalAmount" - {

    "must return the sum of totalAmtOfTaxDueAtLowerRate and totalAmtOfTaxDueAtHigherRate" in {
      ChargeDetails(1, Some(100.00), Some(200.00), 50.00).calcTotalAmount mustEqual 300.00
    }

    "must return zero if there is no totalAmtOfTaxDueAtLowerRate and totalAmtOfTaxDueAtHigherRate" in {
      ChargeDetails(1, None, None, 50.00).calcTotalAmount mustEqual 0.00
    }
  }
}
