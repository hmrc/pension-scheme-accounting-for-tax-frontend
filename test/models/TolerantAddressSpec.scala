/*
 * Copyright 2025 HM Revenue & Customs
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

class TolerantAddressSpec extends AnyWordSpec with Matchers with MockitoSugar with BeforeAndAfterEach {

  "deriveMinimumChargeValueAllowed" must {
    "must correctly convert TolerantAddress to Address if one value missing" in {
      val tolerantAddress = TolerantAddress(
        None,
        Some("Line 2"),
        Some("Line 3"),
        Some("Line 4"),
        Some("12345"),
        Some("Country")
      )

      val addressOption = tolerantAddress.toAddress

      addressOption mustBe defined
      addressOption.get.line1 mustEqual "Line 2"
      addressOption.get.line2 mustEqual "Line 3"
      addressOption.get.line3 mustEqual Some("Line 4")
      addressOption.get.line4 mustEqual None
      addressOption.get.postcode mustEqual Some("12345")
      addressOption.get.country mustEqual "Country"
    }
    "must correctly handle shuffling of address lines in TolerantAddress if UK address" in {
      val tolerantAddress = TolerantAddress(
        None,
        None,
        Some("Line 3"),
        Some("Line 4"),
        Some("12345"),
        Some("GB")
      )

      val addressOption = tolerantAddress.toAddress

      addressOption mustBe defined
      addressOption.get.line1 mustEqual "Line 3"
      addressOption.get.line2 mustEqual "Line 4"
      addressOption.get.line3 mustEqual None
      addressOption.get.line4 mustEqual None
      addressOption.get.postcode mustEqual Some("12345")
      addressOption.get.country mustEqual "GB"
    }
  }
}
