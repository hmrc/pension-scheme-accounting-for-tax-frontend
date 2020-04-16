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

import java.time.LocalDate

import generators.Generators
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Gen
import org.scalatest.AsyncWordSpec
import org.scalatest.MustMatchers
import org.scalatest.OptionValues
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import play.api.libs.json.JsString
import play.api.libs.json.Json
import utils.DateHelper

class YearRangeSpec
    extends AsyncWordSpec
    with MustMatchers
    with ScalaCheckPropertyChecks
    with Generators
    with OptionValues
    with Enumerable.Implicits
    with MockitoSugar {

  private def genYear: Gen[Int] =
    Gen.oneOf(Seq(2019, 2020, 2021, 2022, 2023, 2024, 2025, 2026, 2027, 2028))

  "YearRange" must {

    "must deserialise valid createSeqYearRange" in {

      val gen = Gen.oneOf(YearRange.createSeqYearRange)

      forAll(gen) { yearRange =>
        JsString(yearRange.toString)
          .validate[YearRange](reads(YearRange.enumerable))
          .asOpt
          .value mustEqual yearRange
      }
    }

    "must fail to deserialise invalid createSeqYearRange" in {

      val gen = arbitrary[String] suchThat (!YearRange.createSeqYearRange
        .map(_.toString)
        .contains(_))

      forAll(gen) { invalidValue =>
        a[RuntimeException] shouldBe thrownBy {
          JsString(invalidValue)
            .validate[YearRange](reads(YearRange.enumerable))
        }
      }
    }

    "must serialise" in {
      val gen = Gen.oneOf(YearRange.createSeqYearRange)

      forAll(gen) { yearRange =>
        Json.toJson(yearRange) mustEqual JsString(yearRange.toString)
      }
    }
  }

  "createSeqYearRange" must {
    "yield seq of tax year start years for all years up to BUT NOT INCLUDING current calendar year " +
      "where current calendar date is set to 5th April (end of old tax year) of a random year" in {
      forAll(genYear -> "valid years") { year =>
        DateHelper.setDate(Some(LocalDate.of(year, 4, 5)))
        val expectedResult =
          (2018 until year).reverse.map(yr => YearRange(yr.toString))
        YearRange.createSeqYearRange mustBe expectedResult
      }
    }

    "yield tax year start years for all years up to AND INCLUDING current calendar year " +
      "where current calendar date is set to 6th April (start of new tax year) of a random year" in {
      forAll(genYear -> "valid years") { year =>
        DateHelper.setDate(Some(LocalDate.of(year, 4, 6)))
        val expectedResult =
          (2018 to year).reverse.map(yr => YearRange(yr.toString))
        YearRange.createSeqYearRange mustBe expectedResult
      }
    }
  }
}
