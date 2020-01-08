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

package forms.behaviours

import play.api.data.{Form, FormError}

import scala.math.BigDecimal.RoundingMode

trait BigDecimalFieldBehaviours extends FieldBehaviours {

  def bigDecimalField(form: Form[_],
                      fieldName: String,
                      nonNumericError: FormError,
                      decimalsError: FormError): Unit = {

    "must not bind non-numeric numbers" in {

      forAll(nonNumerics -> "nonNumeric") {
        nonNumeric =>
          val result = form.bind(Map(fieldName -> nonNumeric)).apply(fieldName)
          result.errors mustEqual Seq(nonNumericError)
      }
    }

    "must not bind decimals that are not 2 dp" in {

      forAll(decimals -> "decimal") {
        decimal =>
          val result = form.bind(Map(fieldName -> decimal)).apply(fieldName)
          result.errors mustEqual Seq(decimalsError)
      }
    }
  }

  def bigDecimalFieldWithMinimum(form: Form[_],
                                 fieldName: String,
                                 minimum: BigDecimal,
                                 expectedError: FormError): Unit = {

    s"must not bind decimals below $minimum" in {

      forAll(decimalsBelowValue(minimum) -> "decimalBelowMin") {
        decimal: String =>
          val result = form.bind(Map(fieldName -> decimal)).apply(fieldName)
          result.errors.head.key mustEqual expectedError.key
          result.errors.head.message mustEqual expectedError.message
      }
    }
  }

  def longBigDecimal(form: Form[_],
                     fieldName: String,
                     length: Int,
                     expectedError: FormError): Unit = {

    s"must not bind decimals longer than $length characters" in {

      forAll(longDecimalString(length) -> "decimalAboveMax") {
        decimal: String =>
          val result = form.bind(Map(fieldName -> decimal)).apply(fieldName)
          result.errors.head.key mustEqual expectedError.key
          result.errors.head.message mustEqual expectedError.message
      }
    }
  }

  def bigDecimalFieldWithRange(form: Form[_],
                               fieldName: String,
                               minimum: BigDecimal,
                               maximum: BigDecimal,
                               expectedError: FormError): Unit = {

    s"must not bind decimals outside the range $minimum to $maximum" in {

      forAll(decimalsOutsideRange(minimum, maximum) -> "decimalOutsideRange") {
        number =>
          val result = form.bind(Map(fieldName -> number.toString)).apply(fieldName)
          result.errors mustEqual Seq(expectedError)
      }
    }
  }
}
