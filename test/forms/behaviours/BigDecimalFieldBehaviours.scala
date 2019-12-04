/*
 * Copyright 2019 HM Revenue & Customs
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
          println(s"\n\n\nbigDecimalField:${result.errors}\n\n\n")
          result.errors shouldEqual Seq(nonNumericError)
      }
    }

    "must not bind decimals that are not 2 dp" in {

      forAll(decimals -> "decimal") {
        decimal =>
          val result = form.bind(Map(fieldName -> decimal)).apply(fieldName)
          result.errors shouldEqual Seq(decimalsError)
      }
    }
  }

  def bigDecimalFieldWithMinimum(form: Form[_],
                                 fieldName: String,
                                 minimum: BigDecimal,
                                 expectedError: FormError): Unit = {

    s"must not bind decimals below $minimum" in {

      forAll(decimalBelowValue(minimum) -> "decimalBelowMin") {
        number: BigDecimal =>
          val result = form.bind(Map(fieldName -> number.setScale(2, RoundingMode.CEILING).toString)).apply(fieldName)
          println(s"\n\n\nbigDecimalFieldWithMinimum:${result.errors}\n\n\n")
          result.errors shouldEqual Seq(expectedError)
      }
    }
  }

  def bigDecimalFieldWithMaximum(form: Form[_],
                                 fieldName: String,
                                 maximum: BigDecimal,
                                 expectedError: FormError): Unit = {

    s"must not bind decimals above $maximum" in {

      forAll(decimalAboveValue(maximum) -> "decimalAboveMax") {
        number: BigDecimal =>
          val result = form.bind(Map(fieldName -> number.setScale(2, RoundingMode.CEILING).toString)).apply(fieldName)
          println(s"\n\n\nbigDecimalFieldWithMaximum:${result.errors}\n\n\n")
          result.errors shouldEqual Seq(expectedError)
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
          result.errors shouldEqual Seq(expectedError)
      }
    }
  }
}
