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

package forms.chargeD

import java.time.LocalDate
import java.time.format.DateTimeFormatter

import base.SpecBase
import forms.behaviours._
import play.api.data.FormError

class ChargeDetailsFormProviderSpec extends SpecBase with DateBehaviours with BigDecimalFieldBehaviours {

  private val form = new ChargeDetailsFormProvider()()
  private val dateKey = "dateOfEvent"
  private val tax25PercentKey = "taxAt25Percent"
  private val tax55PercentKey = "taxAt55Percent"
  private val maxDate = LocalDate.now()

  private def chargeDetails(
                             date: LocalDate = LocalDate.from(DateTimeFormatter.ofPattern("yyyy-MM-dd").parse("2020-01-01")),
                             tax25: String = "1.00",
                             tax55: String = "1.00"
                           ): Map[String, String] = {

    Map(
      s"$dateKey.day" -> date.getDayOfMonth.toString,
      s"$dateKey.month" -> date.getMonthValue.toString,
      s"$dateKey.year" -> date.getYear.toString,
      tax25PercentKey -> tax25,
      tax55PercentKey -> tax55
    )
  }

  "dateOfEvent" must {
    s"fail to bind a date greater than ${maxDate.format(DateTimeFormatter.ISO_LOCAL_DATE)}" in {
      val generator = datesBetween(maxDate.plusDays(1), maxDate.plusYears(10))

      forAll(generator -> "invalid dates") {
        date =>

          val result = form.bind(chargeDetails(date = date))

          result.errors must contain(FormError(dateKey, s"$dateKey.error.future"))
      }
    }

    "fail to bind empty date" in {
      val result = form.bind(Map(dateKey -> "", tax25PercentKey -> "1.00", tax55PercentKey -> "1.00"))

      result.errors must contain(FormError(dateKey, s"$dateKey.error.required"))
    }
  }

  "taxAt25Percent" must {

    "must not bind non-numeric numbers" in {
      forAll(nonNumerics -> "nonNumeric") {
        nonNumeric: String =>
          val result = form.bind(chargeDetails(tax25 = nonNumeric))
          result.errors mustEqual Seq(FormError(tax25PercentKey, messages("chargeD.amountTaxDue.error.invalid", "25")))
      }
    }

    "must not bind decimals that are not 2 dp" in {
      forAll(decimals -> "decimal") {
        decimal: String =>
          val result = form.bind(chargeDetails(tax25 = decimal))
          result.errors mustEqual Seq(FormError(tax25PercentKey, messages("chargeD.amountTaxDue.error.decimal", "25")))
      }
    }

    "must not bind decimals below 0.00" in {
      forAll(decimalsBelowValue(BigDecimal("0.00")) -> "decimalBelowMin") {
        decimal: String =>
          val result = form.bind(chargeDetails(tax25 = decimal))
          result.errors.head.key mustEqual tax25PercentKey
          result.errors.head.message mustEqual messages("chargeD.amountTaxDue.error.invalid", "25")
      }
    }

    "must not bind decimals longer than 11 characters" in {
      forAll(longDecimalString(12) -> "decimalAboveMax") {
        decimal: String =>
          val result = form.bind(chargeDetails(tax25 = decimal))
          result.errors.head.key mustEqual tax25PercentKey
          result.errors.head.message mustEqual messages("chargeD.amountTaxDue.error.maximum", "25")
      }
    }
  }

  "taxAt55Percent" must {

    "must not bind non-numeric numbers" in {
      forAll(nonNumerics -> "nonNumeric") {
        nonNumeric: String =>
          val result = form.bind(chargeDetails(tax55 = nonNumeric))
          result.errors mustEqual Seq(FormError(tax55PercentKey, messages("chargeD.amountTaxDue.error.invalid", "55")))
      }
    }

    "must not bind decimals that are not 2 dp" in {
      forAll(decimals -> "decimal") {
        decimal: String =>
          val result = form.bind(chargeDetails(tax55 = decimal))
          result.errors mustEqual Seq(FormError(tax55PercentKey, messages("chargeD.amountTaxDue.error.decimal", "55")))
      }
    }

    "must not bind decimals below 0.00" in {
      forAll(decimalsBelowValue(BigDecimal("0.00")) -> "decimalBelowMin") {
        decimal: String =>
          val result = form.bind(chargeDetails(tax55 = decimal))
          result.errors.head.key mustEqual tax55PercentKey
          result.errors.head.message mustEqual messages("chargeD.amountTaxDue.error.invalid", "55")
      }
    }

    "must not bind decimals longer than 11 characters" in {
      forAll(longDecimalString(12) -> "decimalAboveMax") {
        decimal: String =>
          val result = form.bind(chargeDetails(tax55 = decimal))
          result.errors.head.key mustEqual tax55PercentKey
          result.errors.head.message mustEqual messages("chargeD.amountTaxDue.error.maximum", "55")
      }
    }
  }
}
