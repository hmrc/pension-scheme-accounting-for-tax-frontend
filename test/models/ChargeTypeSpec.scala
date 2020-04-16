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

import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Gen
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import org.scalatest.{FreeSpec, MustMatchers, OptionValues}
import play.api.libs.json.{JsError, JsString, Json}

class ChargeTypeSpec extends FreeSpec with MustMatchers with ScalaCheckPropertyChecks with OptionValues {

  "ChargeType" - {

    "must deserialise valid values" in {

      val gen = Gen.oneOf(ChargeType.values)

      forAll(gen) {
        chargeType =>

          JsString(chargeType.toString).validate[ChargeType].asOpt.value mustEqual chargeType
      }
    }

    "must fail to deserialise invalid values" in {

      val gen = arbitrary[String] suchThat (!ChargeType.values.map(_.toString).contains(_))

      forAll(gen) {
        invalidValue =>

          a[RuntimeException] shouldBe thrownBy {
            JsString(invalidValue).validate[ChargeType]
          }
      }
    }

    "must serialise" in {

      val gen = Gen.oneOf(ChargeType.values)

      forAll(gen) {
        chargeType =>

          Json.toJson(chargeType) mustEqual JsString(chargeType.toString)
      }
    }
  }
}
