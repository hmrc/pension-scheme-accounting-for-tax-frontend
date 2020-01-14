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

import data.SampleData._
import forms.behaviours._
import play.api.data.FormError

class ChargeDetailsFormProviderSpec extends DateBehaviours with BigDecimalFieldBehaviours with IntFieldBehaviours {

  private val form = new ChargeDetailsFormProvider()()

  private val totalNumberOfMembersKey = "numberOfMembers"
  private val totalAmtOfTaxDueAtLowerRateKey = "totalAmtOfTaxDueAtLowerRate"
  private val totalAmtOfTaxDueAtHigherRateKey = "totalAmtOfTaxDueAtHigherRate"
  private val messageKeyNumberOfMembersKey = "chargeA.numberOfMembers"
  private val messageKeyAmountTaxDueLowerRateKey = "chargeA.totalAmtOfTaxDueAtLowerRate"
  private val messageKeyAmountTaxDueHigherRateKey = "chargeA.totalAmtOfTaxDueAtHigherRate"

  private val decimalFormat = new DecimalFormat("0.00")

  "numberOfMembers" must {

    behave like intField(
      form = form,
      fieldName = totalNumberOfMembersKey,
      nonNumericError = FormError(totalNumberOfMembersKey, s"$messageKeyNumberOfMembersKey.error.nonNumeric")
    )

    behave like intFieldWithRange(
      form = form,
      fieldName = totalNumberOfMembersKey,
      minimum = 0,
      maximum = 999999,
      expectedError = FormError(totalNumberOfMembersKey, s"$messageKeyNumberOfMembersKey.error.maximum")
    )
  }

  "totalAmtOfTaxDueAtLowerRate" must {

    behave like bigDecimalField(
      form = form,
      fieldName = totalAmtOfTaxDueAtLowerRateKey,
      nonNumericError = FormError(totalAmtOfTaxDueAtLowerRateKey, s"$messageKeyAmountTaxDueLowerRateKey.error.invalid"),
      decimalsError = FormError(totalAmtOfTaxDueAtLowerRateKey, s"$messageKeyAmountTaxDueLowerRateKey.error.decimal")
    )

    behave like bigDecimalFieldWithMinimum(
      form = form,
      fieldName = totalAmtOfTaxDueAtLowerRateKey,
      minimum = BigDecimal("0.00"),
      expectedError = FormError(totalAmtOfTaxDueAtLowerRateKey, s"$messageKeyAmountTaxDueLowerRateKey.error.minimum")
    )

    behave like longBigDecimal(
      form = form,
      fieldName = totalAmtOfTaxDueAtLowerRateKey,
      length = 11,
      expectedError = FormError(totalAmtOfTaxDueAtLowerRateKey, s"$messageKeyAmountTaxDueLowerRateKey.error.maximum")
    )
  }

  "totalAmtOfTaxDueAtHigherRate" must {

    behave like bigDecimalField(
      form = form,
      fieldName = totalAmtOfTaxDueAtHigherRateKey,
      nonNumericError = FormError(totalAmtOfTaxDueAtHigherRateKey, s"$messageKeyAmountTaxDueHigherRateKey.error.invalid"),
      decimalsError = FormError(totalAmtOfTaxDueAtHigherRateKey, s"$messageKeyAmountTaxDueHigherRateKey.error.decimal")
    )

    behave like bigDecimalFieldWithMinimum(
      form = form,
      fieldName = totalAmtOfTaxDueAtHigherRateKey,
      minimum = BigDecimal("0.00"),
      expectedError = FormError(totalAmtOfTaxDueAtHigherRateKey, s"$messageKeyAmountTaxDueHigherRateKey.error.minimum")
    )

    behave like longBigDecimal(
      form = form,
      fieldName = totalAmtOfTaxDueAtHigherRateKey,
      length = 11,
      expectedError = FormError(totalAmtOfTaxDueAtHigherRateKey, s"$messageKeyAmountTaxDueHigherRateKey.error.maximum")
    )
  }

  "totalAmount" must {
    "must bind correctly calculated total to form" in {
      val resultForm = form.bind(Map(
        totalNumberOfMembersKey -> chargeAChargeDetails.numberOfMembers.toString,
        totalAmtOfTaxDueAtLowerRateKey -> decimalFormat.format(chargeAChargeDetails.totalAmtOfTaxDueAtLowerRate.get),
        totalAmtOfTaxDueAtHigherRateKey -> decimalFormat.format(chargeAChargeDetails.totalAmtOfTaxDueAtHigherRate.get)
      ))

      resultForm.value mustEqual Some(chargeAChargeDetails)
    }
  }
}
