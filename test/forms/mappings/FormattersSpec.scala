/*
 * Copyright 2024 HM Revenue & Customs
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

package forms.mappings

import org.scalatest.OptionValues
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import play.api.data.FormError


class FormattersSpec extends AnyFreeSpec with Matchers with OptionValues with Mappings {

  val requiredKey = "error.required"
  val invalidKey = "error.invalid"
  val nonUkLengthKey = "error.nonUkLength"
  val countryFieldName = "country"
  val postcodeFieldName = "postcode"

  "optionalPostcodeFormatter" - {

    "must bind a valid postcode" - {

      "when post code is as per the regex and without spaces" in {
        val result = optionalPostcodeFormatter(requiredKey, invalidKey, nonUkLengthKey, countryFieldName).
          bind(postcodeFieldName, Map(postcodeFieldName -> " zz11zz ", countryFieldName -> "GB"))

        result.toOption.get must be(Some("ZZ1 1ZZ"))
      }

      "when post code is as per the regex and with spaces" in {
        val result = optionalPostcodeFormatter(requiredKey, invalidKey, nonUkLengthKey, countryFieldName).
          bind(postcodeFieldName, Map(postcodeFieldName -> " zz1 1zz ", countryFieldName -> "GB"))

        result.toOption.get must be(Some("ZZ1 1ZZ"))
      }

      "when postcode is non UK and length is 10 and country is non UK" in {
        val result = optionalPostcodeFormatter(requiredKey, invalidKey, nonUkLengthKey, countryFieldName).
          bind(postcodeFieldName, Map(postcodeFieldName -> " 1234567890 ", countryFieldName -> "FR"))

        result.toOption.get must be(Some("1234567890"))
      }

      "when postcode is non UK and country is None" in {
        val result = optionalPostcodeFormatter(requiredKey, invalidKey, nonUkLengthKey, countryFieldName).
          bind(postcodeFieldName, Map(postcodeFieldName -> " 12345 "))

        result.toOption.get must be(Some("12345"))
      }
    }

    "bind None when no country and no postcode" in {
      val result = optionalPostcodeFormatter(requiredKey, invalidKey, nonUkLengthKey, countryFieldName).
        bind(postcodeFieldName, Map.empty)

      result.toOption.get must be(None)
    }

    "return form error" - {

      "when postcode is required for UK" in {
        val result = optionalPostcodeFormatter(requiredKey, invalidKey, nonUkLengthKey, countryFieldName).
          bind(postcodeFieldName, Map(countryFieldName -> "GB"))

        result.left.toOption.get must be(Seq(FormError(postcodeFieldName, requiredKey)))
      }

      "when postcode is not a valid postcode for UK" in {
        val result = optionalPostcodeFormatter(requiredKey, invalidKey, nonUkLengthKey, countryFieldName).
          bind(postcodeFieldName, Map(postcodeFieldName -> " 123456 ", countryFieldName -> "GB"))

        result.left.toOption.get must be(Seq(FormError(postcodeFieldName, invalidKey)))
      }

      "when postcode is not a valid postcode for NON UK" in {
        val result = optionalPostcodeFormatter(requiredKey, invalidKey, nonUkLengthKey, countryFieldName).
          bind(postcodeFieldName, Map(postcodeFieldName -> " 12345678909 ", countryFieldName -> "FR"))

        result.left.toOption.get must be(Seq(FormError(postcodeFieldName, nonUkLengthKey)))
      }
    }

    "unbind a postcode string" in {
      val result = optionalPostcodeFormatter(requiredKey, invalidKey, nonUkLengthKey, countryFieldName).
        unbind(postcodeFieldName, Some("ZZ1 1ZZ"))

      result mustBe Map(postcodeFieldName -> "ZZ1 1ZZ")
    }
  }

  "bigDecimalFormatter" - {

    val key = "bdKey"

    "must bind a valid bigDecimal" - {

      "when separated by ," in {
        val result = bigDecimalFormatter(requiredKey, invalidKey).bind(key, Map(key -> "1,00,000"))
        result.toOption.get mustBe BigDecimal(100000)
      }

      "when not separated by ," in {
        val result = bigDecimalFormatter(requiredKey, invalidKey).bind(key, Map(key -> "100000"))
        result.toOption.get mustBe BigDecimal(100000)
      }
    }

    "return FormError when invalid big decimal" in {
      val result = bigDecimalFormatter(requiredKey, invalidKey).bind(key, Map(key -> "invalid"))
      result.left.toOption.get must be(Seq(FormError(key, invalidKey)))
    }

    "return FormError when no big decimal" in {
      val result = bigDecimalFormatter(requiredKey, invalidKey).bind(key, Map.empty)
      result.left.toOption.get must be(Seq(FormError(key, requiredKey)))
    }

    "unbind a bigdecimal" in {
      val result = bigDecimalFormatter(requiredKey, invalidKey).unbind(key, BigDecimal(100))
      result must be(Map(key -> "100"))
    }
  }
}
