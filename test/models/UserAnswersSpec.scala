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

package models

import base.SpecBase
import pages.IsNewReturn

class UserAnswersSpec extends SpecBase with Enumerable.Implicits {
  "deriveMinimumChargeValueAllowed" must {
    "return 0.01 when IsNewReturn is true" in {
      UserAnswers.deriveMinimumChargeValueAllowed(UserAnswers().setOrException(IsNewReturn, true)) mustBe BigDecimal("0.01")
    }

    "return 0.00 when IsNewReturn is not present" in {
      UserAnswers.deriveMinimumChargeValueAllowed(UserAnswers()) mustBe BigDecimal("0.00")
    }
  }
}
