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

package forms.behaviours

import play.api.data.{Form, FormError}

trait IntFieldBehaviours extends FieldBehaviours {

  def intField(form: Form[?],
               fieldName: String,
               nonNumericError: FormError,
               wholeNumberError: FormError,
               minError:FormError,
               maxError:FormError
              ): Unit = {

    "must not bind non-numeric numbers" in {

      forAll(nonNumerics -> "nonNumeric") {
        nonNumeric =>
          val result = form.bind(Map(fieldName -> nonNumeric)).apply(fieldName)
          result.errors mustEqual Seq(nonNumericError)
      }
    }

    "must not bind decimals" in {

      forAll(decimals -> "decimal") {
        decimal =>
          val result = form.bind(Map(fieldName -> decimal)).apply(fieldName)
          result.errors mustEqual Seq(wholeNumberError)
      }
    }

    "must not bind integers larger than Int.MaxValue" in {

      forAll(intsLargerThanMaxValue -> "massiveInt") {
        (num: BigInt) =>
          val result = form.bind(Map(fieldName -> num.toString)).apply(fieldName)
          result.errors mustEqual Seq(maxError)
      }
    }

    "must not bind integers smaller than Int.MinValue" in {

      forAll(intsSmallerThanMinValue -> "massivelySmallInt") {
        (num: BigInt) =>
          val result = form.bind(Map(fieldName -> num.toString)).apply(fieldName)
          result.errors mustEqual Seq(minError)
      }
    }
  }

  def intFieldWithMinimum(form: Form[?],
                          fieldName: String,
                          minimum: Int,
                          expectedError: FormError): Unit = {

    s"must not bind integers below $minimum" in {

      forAll(intsBelowValue(minimum) -> "intBelowMin") {
        (number: Int) =>
          val result = form.bind(Map(fieldName -> number.toString)).apply(fieldName)
          result.errors.head.key mustEqual expectedError.key
      }
    }
  }

  def intFieldWithMaximum(form: Form[?],
                          fieldName: String,
                          maximum: Int,
                          expectedError: FormError): Unit = {

    s"must not bind integers above $maximum" in {

      forAll(intsAboveValue(maximum) -> "intAboveMax") {
        (number: Int) =>
          val result = form.bind(Map(fieldName -> number.toString)).apply(fieldName)
          result.errors.head.key mustEqual expectedError.key
      }
    }
  }

  def intFieldWithRange(form: Form[?],
                        fieldName: String,
                        minimum: Int,
                        maximum: Int,
                        expectedError: FormError): Unit = {

    s"must not bind ints outside the range $minimum to $maximum" in {

      forAll(intsOutsideRange(minimum, maximum) -> "intOutsideRange") {
        number =>
          val result = form.bind(Map(fieldName -> number.toString)).apply(fieldName)
          result.errors.head.key mustEqual expectedError.key
      }
    }
  }


  def intField(form: Form[?],
               fieldName: String,
               nonNumericError: FormError): Unit = {

    "must not bind non-numeric numbers" in {

      forAll(nonNumerics -> "nonNumeric") {
        nonNumeric =>
          val result = form.bind(Map(fieldName -> nonNumeric)).apply(fieldName)
          result.errors mustEqual Seq(nonNumericError)
      }
    }
  }
}
