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

package forms.chargeA

import base.SpecBase
import forms.behaviours._
import models.chargeA.ChargeDetails
import play.api.data.{Form, FormError}

class ChargeDetailsFormProviderSpec extends SpecBase with DateBehaviours with BigDecimalFieldBehaviours with IntFieldBehaviours {

  private val form = new ChargeDetailsFormProvider().apply(minimumChargeValueAllowed = BigDecimal("0.01"))

  private val totalNumberOfMembersKey = "numberOfMembers"
  private val totalAmtOfTaxDueAtLowerRateKey = "totalAmtOfTaxDueAtLowerRate"
  private val totalAmtOfTaxDueAtHigherRateKey = "totalAmtOfTaxDueAtHigherRate"
  private val messageKeyNumberOfMembersKey = "chargeA.numberOfMembers"
  private val messageKeyAmountTaxDueLowerRateKey = "chargeA.totalAmtOfTaxDueAtLowerRate"
  private val messageKeyAmountTaxDueHigherRateKey = "chargeA.totalAmtOfTaxDueAtHigherRate"

  private def chargeADetails(
                      members: String = "12",
                      lowerTax: String = "1.00",
                      higherTax: String = "1.00"
                    ): Map[String, String] =
    Map(
      totalNumberOfMembersKey -> members,
      totalAmtOfTaxDueAtLowerRateKey -> lowerTax,
      totalAmtOfTaxDueAtHigherRateKey -> higherTax
    )

  "numberOfMembers" must {

    "must not bind non-numeric numbers" in {
      forAll(nonNumerics -> "nonNumeric") {
        (nonNumeric: String) =>
          val result = form.bind(chargeADetails(members = nonNumeric))
          result.errors mustEqual Seq(FormError(totalNumberOfMembersKey, s"$messageKeyNumberOfMembersKey.error.nonNumeric"))
      }
    }

    "must not bind ints outside the range 0 to 999999" in {
      forAll(intsOutsideRange(0, 999999) -> "intOutsideRange") {
        (number: Int) =>
          val result = form.bind(chargeADetails(members = number.toString))
          result.errors.head.key mustEqual totalNumberOfMembersKey
      }
    }
  }

  "totalAmtOfTaxDueAtLowerRate" must {

    "not bind non-numeric numbers" in {
      forAll(nonNumerics -> "nonNumeric") {
        (nonNumeric: String) =>
          val result = form.bind(chargeADetails(lowerTax = nonNumeric))
          result.errors mustEqual Seq(FormError(totalAmtOfTaxDueAtLowerRateKey, s"$messageKeyAmountTaxDueLowerRateKey.error.invalid"))
      }
    }

    "not bind decimals that are more than 2 dp" in {
      forAll(decimals -> "decimal") {
        (decimal: String) =>
          val result = form.bind(chargeADetails(lowerTax = decimal))
          result.errors mustEqual Seq(FormError(totalAmtOfTaxDueAtLowerRateKey, s"$messageKeyAmountTaxDueLowerRateKey.error.decimal"))
      }
    }

    "not bind decimals below 0.00" in {
      forAll(decimalsBelowValue(BigDecimal("0.00")) -> "decimalBelowMin") {
        (decimal: String) =>
          val result = form.bind(chargeADetails(lowerTax = decimal))
          result.errors.head.key mustEqual totalAmtOfTaxDueAtLowerRateKey
          result.errors.head.message mustEqual messages(s"$messageKeyAmountTaxDueLowerRateKey.error.minimum", "0.01")
      }
    }

    "not bind decimals longer than 11 characters" in {
      forAll(longDecimalString(12) -> "decimalAboveMax") {
        (decimal: String) =>
          val result = form.bind(chargeADetails(lowerTax = decimal))
          result.errors.head.key mustEqual totalAmtOfTaxDueAtLowerRateKey
          result.errors.head.message mustEqual s"$messageKeyAmountTaxDueLowerRateKey.error.maximum"
      }
    }

    "bind 0.00 when positive value bound to totalAmtOfTaxDueAtHigherRate" in {
      val result = form.bind(chargeADetails(lowerTax = "0.00"))
      result.errors mustBe Seq.empty
    }

    "bind integers to totalAmtOfTaxDueAtLowerRate" in {
      forAll(intsAboveValue(0) -> "intAboveMax") {
        (i: Int) =>
          val result = form.bind(chargeADetails(lowerTax = i.toString))
          result.errors mustBe Seq.empty
          result.value.flatMap(_.totalAmtOfTaxDueAtLowerRate) mustBe Some(BigDecimal(i))
      }
    }
  }

  "totalAmtOfTaxDueAtHigherRate" must {

    "not bind non-numeric numbers" in {
      forAll(nonNumerics -> "nonNumeric") {
        (nonNumeric: String) =>
          val result = form.bind(chargeADetails(higherTax = nonNumeric))
          result.errors mustEqual Seq(FormError(totalAmtOfTaxDueAtHigherRateKey, s"$messageKeyAmountTaxDueHigherRateKey.error.invalid"))
      }
    }

    "not bind decimals that are greater than 2 dp" in {
      forAll(decimals -> "decimal") {
        (decimal: String) =>
          val result = form.bind(chargeADetails(higherTax = decimal))
          result.errors mustEqual Seq(FormError(totalAmtOfTaxDueAtHigherRateKey, s"$messageKeyAmountTaxDueHigherRateKey.error.decimal"))
      }
    }

    "not bind decimals below 0.00" in {
      forAll(decimalsBelowValue(BigDecimal("0.00")) -> "decimalBelowMin") {
        (decimal: String) =>
          val result = form.bind(chargeADetails(higherTax = decimal))
          result.errors.head.key mustEqual totalAmtOfTaxDueAtHigherRateKey
          result.errors.head.message mustEqual messages(s"$messageKeyAmountTaxDueHigherRateKey.error.minimum", "0.01")
      }
    }

    "not bind decimals longer than 11 characters" in {
      forAll(longDecimalString(12) -> "decimalAboveMax") {
        (decimal: String) =>
          val result = form.bind(chargeADetails(higherTax = decimal))
          result.errors.head.key mustEqual totalAmtOfTaxDueAtHigherRateKey
          result.errors.head.message mustEqual s"$messageKeyAmountTaxDueHigherRateKey.error.maximum"
      }
    }

    "bind 0.00 when positive value bound to totalAmtOfTaxDueAtLowerRate" in {
      val result = form.bind(chargeADetails(higherTax = "0.00"))
      result.errors mustBe Seq.empty
    }

    "bind integers to totalAmtOfTaxDueAtHigherRate" in {
      forAll(intsAboveValue(0) -> "intAboveMax") {
        (i: Int) =>
          val result = form.bind(chargeADetails(higherTax = i.toString))
          result.errors mustBe Seq.empty
          result.value.flatMap(_.totalAmtOfTaxDueAtHigherRate) mustBe Some(BigDecimal(i))
      }
    }
  }

  "form" must {
    "not allow both higher and lower rates of tax to be 0.00" in {
      val result = form.bind(chargeADetails(lowerTax = "0.00", higherTax = "0.00"))
      result.errors.head.key mustEqual totalAmtOfTaxDueAtLowerRateKey
      result.errors.head.message mustEqual messages(s"$messageKeyAmountTaxDueLowerRateKey.error.minimum", "0.01")
      result.errors(1).key mustEqual totalAmtOfTaxDueAtHigherRateKey
      result.errors(1).message mustEqual messages(s"$messageKeyAmountTaxDueHigherRateKey.error.minimum", "0.01")
    }

    "not allow higher and lower rates of tax to be combination 0.00 and \"\" " in {
      val result = form.bind(chargeADetails(lowerTax = "0.00", higherTax = ""))
      result.errors.head.key mustEqual totalAmtOfTaxDueAtLowerRateKey
      result.errors.head.message mustEqual messages(s"$messageKeyAmountTaxDueLowerRateKey.error.minimum", "0.01")
      result.errors(1).key mustEqual totalAmtOfTaxDueAtHigherRateKey
      result.errors(1).message mustEqual s"$messageKeyAmountTaxDueHigherRateKey.error.required"

      val result2 = form.bind(chargeADetails(lowerTax = "", higherTax = "0.00"))
      result2.errors.head.key mustEqual totalAmtOfTaxDueAtLowerRateKey
      result2.errors.head.message mustEqual s"$messageKeyAmountTaxDueLowerRateKey.error.required"
      result2.errors(1).key mustEqual totalAmtOfTaxDueAtHigherRateKey
      result2.errors(1).message mustEqual messages(s"$messageKeyAmountTaxDueHigherRateKey.error.minimum", "0.01")
    }
  }

  "totalAmount" must {
    "bind correctly calculated total to form when both rates of tax are present" in {
      val resultForm: Form[ChargeDetails] = form.bind(chargeADetails(lowerTax = "3.00", higherTax = "3.00"))

      resultForm.value.get.totalAmount mustEqual BigDecimal(6.00)
    }

    "bind correctly calculated total to form when both rates of tax are present and comma is present" in {
      val resultForm: Form[ChargeDetails] = form.bind(chargeADetails(lowerTax = "4,123.00", higherTax = "3.00"))

      resultForm.value.get.totalAmount mustEqual BigDecimal(4126.00)
    }

    "bind correctly calculated total to form when only lower rate of tax present" in {
      val resultForm: Form[ChargeDetails] = form.bind(chargeADetails(lowerTax = "3.00", higherTax = ""))

      resultForm.value.get.totalAmount mustEqual BigDecimal(3.00)
    }

    "bind correctly calculated total to form when lower rate of tax present amd higher rate 0.00" in {
      val resultForm: Form[ChargeDetails] = form.bind(chargeADetails(lowerTax = "3.00", higherTax = "0.00"))

      resultForm.value.get.totalAmount mustEqual BigDecimal(3.00)
    }

    "bind correctly calculated total to form when only higher rate of tax present" in {
      val resultForm: Form[ChargeDetails] = form.bind(chargeADetails(lowerTax = "", higherTax = "3.00"))

      resultForm.value.get.totalAmount mustEqual BigDecimal(3.00)
    }

    "bind correctly calculated total to form when higher rate of tax present lower rate 0.00" in {
      val resultForm: Form[ChargeDetails] = form.bind(chargeADetails(lowerTax = "0.00", higherTax = "3.00"))

      resultForm.value.get.totalAmount mustEqual BigDecimal(3.00)
    }
  }
}
