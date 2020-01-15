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

package forms.chargeA

import java.text.DecimalFormat

import forms.behaviours._
import models.chargeA.ChargeDetails
import play.api.data.{Form, FormError}

class ChargeDetailsFormProviderSpec extends DateBehaviours with BigDecimalFieldBehaviours with IntFieldBehaviours {

  private val form = new ChargeDetailsFormProvider()()

  private val totalNumberOfMembersKey = "numberOfMembers"
  private val totalAmtOfTaxDueAtLowerRateKey = "totalAmtOfTaxDueAtLowerRate"
  private val totalAmtOfTaxDueAtHigherRateKey = "totalAmtOfTaxDueAtHigherRate"
  private val messageKeyNumberOfMembersKey = "chargeA.numberOfMembers"
  private val messageKeyAmountTaxDueLowerRateKey = "chargeA.totalAmtOfTaxDueAtLowerRate"
  private val messageKeyAmountTaxDueHigherRateKey = "chargeA.totalAmtOfTaxDueAtHigherRate"

  def chargeADetails(members: String = "12", lowerTax: String = "1.00", higherTax: String = "1.00") =
    Map(
      totalNumberOfMembersKey -> members,
      totalAmtOfTaxDueAtLowerRateKey -> lowerTax,
      totalAmtOfTaxDueAtHigherRateKey -> higherTax
    )

  "numberOfMembers" must {

    "must not bind non-numeric numbers" in {
      forAll(nonNumerics -> "nonNumeric") {
        nonNumeric: String =>
          val result = form.bind(chargeADetails(members = nonNumeric))
          result.errors mustEqual Seq(FormError(totalNumberOfMembersKey, s"$messageKeyNumberOfMembersKey.error.nonNumeric"))
      }
    }

    "must not bind ints outside the range 0 to 999999" in {
      forAll(intsOutsideRange(0, 999999) -> "intOutsideRange") {
        number: Int =>
          val result = form.bind(chargeADetails(members = number.toString))
          result.errors.head.key mustEqual totalNumberOfMembersKey
      }
    }
  }

  "totalAmtOfTaxDueAtLowerRate" must {

    "must not bind non-numeric numbers" in {
      forAll(nonNumerics -> "nonNumeric") {
        nonNumeric: String =>
          val result = form.bind(chargeADetails(lowerTax = nonNumeric))
          result.errors mustEqual Seq(FormError(totalAmtOfTaxDueAtLowerRateKey, s"$messageKeyAmountTaxDueLowerRateKey.error.invalid"))
      }
    }

    "must not bind decimals that are not 2 dp" in {
      forAll(decimals -> "decimal") {
        decimal: String =>
          val result = form.bind(chargeADetails(lowerTax = decimal))
          result.errors mustEqual Seq(FormError(totalAmtOfTaxDueAtLowerRateKey, s"$messageKeyAmountTaxDueLowerRateKey.error.decimal"))
      }
    }

    "must not bind decimals below 0.00" in {
      forAll(decimalsBelowValue(BigDecimal("0.00")) -> "decimalBelowMin") {
        decimal: String =>
          val result = form.bind(chargeADetails(lowerTax = decimal))
          result.errors.head.key mustEqual totalAmtOfTaxDueAtLowerRateKey
          result.errors.head.message mustEqual s"$messageKeyAmountTaxDueLowerRateKey.error.minimum"
      }
    }

    "must not bind decimals longer than 11 characters" in {
      forAll(longDecimalString(11) -> "decimalAboveMax") {
        decimal: String =>
          val result = form.bind(chargeADetails(lowerTax = decimal))
          result.errors.head.key mustEqual totalAmtOfTaxDueAtLowerRateKey
          result.errors.head.message mustEqual s"$messageKeyAmountTaxDueLowerRateKey.error.maximum"
      }
    }
  }

  "totalAmtOfTaxDueAtHigherRate" must {

    "must not bind non-numeric numbers" in {
      forAll(nonNumerics -> "nonNumeric") {
        nonNumeric: String =>
          val result = form.bind(chargeADetails(higherTax = nonNumeric))
          result.errors mustEqual Seq(FormError(totalAmtOfTaxDueAtHigherRateKey, s"$messageKeyAmountTaxDueHigherRateKey.error.invalid"))
      }
    }

    "must not bind decimals that are not 2 dp" in {
      forAll(decimals -> "decimal") {
        decimal: String =>
          val result = form.bind(chargeADetails(higherTax = decimal))
          result.errors mustEqual Seq(FormError(totalAmtOfTaxDueAtHigherRateKey, s"$messageKeyAmountTaxDueHigherRateKey.error.decimal"))
      }
    }

    "must not bind decimals below 0.00" in {
      forAll(decimalsBelowValue(BigDecimal("0.00")) -> "decimalBelowMin") {
        decimal: String =>
          val result = form.bind(chargeADetails(higherTax = decimal))
          result.errors.head.key mustEqual totalAmtOfTaxDueAtHigherRateKey
          result.errors.head.message mustEqual s"$messageKeyAmountTaxDueHigherRateKey.error.minimum"
      }
    }

    "must not bind decimals longer than 11 characters" in {
      forAll(longDecimalString(11) -> "decimalAboveMax") {
        decimal: String =>
          val result = form.bind(chargeADetails(higherTax = decimal))
          result.errors.head.key mustEqual totalAmtOfTaxDueAtHigherRateKey
          result.errors.head.message mustEqual s"$messageKeyAmountTaxDueHigherRateKey.error.maximum"
      }
    }
  }

  "totalAmount" must {
    "must bind correctly calculated total to form when both rates of tax are present" in {
      val resultForm: Form[ChargeDetails] = form.bind(chargeADetails(lowerTax = "3.00", higherTax = "3.00"))

      resultForm.value.get.totalAmount mustEqual BigDecimal(6.00)
    }

    "must bind correctly calculated total to form when only lower rate of tax present" in {
      val resultForm: Form[ChargeDetails] = form.bind(chargeADetails(lowerTax = "3.00", higherTax = ""))

      resultForm.value.get.totalAmount mustEqual BigDecimal(3.00)
    }

    "must bind correctly calculated total to form when only higher rate of tax present" in {
      val resultForm: Form[ChargeDetails] = form.bind(chargeADetails(lowerTax = "", higherTax = "3.00"))

      resultForm.value.get.totalAmount mustEqual BigDecimal(3.00)
    }
  }
}
