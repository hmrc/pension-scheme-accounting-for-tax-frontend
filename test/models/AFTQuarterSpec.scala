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

package models

import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar

import java.time.LocalDate

class AFTQuarterSpec extends AnyWordSpec with Matchers with MockitoSugar with BeforeAndAfterEach {

  "formatForDisplay" when {
    "calling with a valid date" must {
      "return formatted correctly" in {
        val result = AFTQuarter.formatForDisplay(AFTQuarter(
          LocalDate.of(2022, 1, 1),
          LocalDate.of(2022, 2, 28)
        ))
        result mustBe "1 January to 28 February 2022 to 2023"
      }
    }
  }
}
