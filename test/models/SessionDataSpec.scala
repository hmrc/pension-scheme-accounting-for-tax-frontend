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

package models

import data.SampleData
import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar

class SessionDataSpec extends AnyWordSpec with Matchers with MockitoSugar with BeforeAndAfterEach {

  "deriveMinimumChargeValueAllowed" must {
    "give 0.01 when version is 1 and access mode is pre-compile" in {
      val sd = SampleData.sessionData(sessionAccessData = SessionAccessData(1, AccessMode.PageAccessModePreCompile, areSubmittedVersionsAvailable = false))
      sd.deriveMinimumChargeValueAllowed mustBe BigDecimal(0.01)
    }

    "give 0.00 when version is 1 and access mode is compile" in {
      val sd = SampleData.sessionData(sessionAccessData = SessionAccessData(1, AccessMode.PageAccessModeCompile, areSubmittedVersionsAvailable = false))
      sd.deriveMinimumChargeValueAllowed mustBe BigDecimal(0.00)
    }

    "give 0.00 when version is 2 and access mode is pre-compile" in {
      val sd = SampleData.sessionData(sessionAccessData = SessionAccessData(2, AccessMode.PageAccessModePreCompile, areSubmittedVersionsAvailable = false))
      sd.deriveMinimumChargeValueAllowed mustBe BigDecimal(0.00)
    }
  }

}
