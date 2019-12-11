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

package forms.chargeA

import forms.behaviours._
import play.api.data.FormError

class ChargeDetailsFormProviderSpec extends DateBehaviours with BigDecimalFieldBehaviours {

  private val form = new ChargeDetailsFormProvider()()

  private val totalAmtOfTaxDueAtLowerRateKey = "totalAmtOfTaxDueAtLowerRate"
  private val totalAmtOfTaxDueAtHigherRateKey = "totalAmtOfTaxDueAtHigherRate"

  private val messageKeyAmountTaxDueLowerRateKey = "chargeA.totalAmtOfTaxDueAtLowerRate"
  private val messageKeyAmountTaxDueHigherRateKey = "chargeA.totalAmtOfTaxDueAtHigherRate"

  "amountTaxDue 20%" - {

    behave like bigDecimalField(
      form = form,
      fieldName = totalAmtOfTaxDueAtLowerRateKey,
      nonNumericError = FormError(totalAmtOfTaxDueAtLowerRateKey, s"$messageKeyAmountTaxDueLowerRateKey.error.invalid"),
      decimalsError = FormError(totalAmtOfTaxDueAtLowerRateKey, s"$messageKeyAmountTaxDueLowerRateKey.error.decimal")
    )

    behave like bigDecimalFieldWithMinimum(
      form = form,
      fieldName = totalAmtOfTaxDueAtLowerRateKey,
      minimum = BigDecimal("0.01"),
      expectedError = FormError(totalAmtOfTaxDueAtLowerRateKey, s"$messageKeyAmountTaxDueLowerRateKey.error.minimum")
    )

    behave like longBigDecimal(
      form = form,
      fieldName = totalAmtOfTaxDueAtLowerRateKey,
      length = 11,
      expectedError = FormError(totalAmtOfTaxDueAtLowerRateKey, s"$messageKeyAmountTaxDueLowerRateKey.error.maximum")
    )
  }

  "amountTaxDue 50%" - {

    behave like bigDecimalField(
      form = form,
      fieldName = totalAmtOfTaxDueAtHigherRateKey,
      nonNumericError = FormError(totalAmtOfTaxDueAtHigherRateKey, s"$messageKeyAmountTaxDueHigherRateKey.error.invalid"),
      decimalsError = FormError(totalAmtOfTaxDueAtHigherRateKey, s"$messageKeyAmountTaxDueHigherRateKey.error.decimal")
    )

    behave like bigDecimalFieldWithMinimum(
      form = form,
      fieldName = totalAmtOfTaxDueAtHigherRateKey,
      minimum = BigDecimal("0.01"),
      expectedError = FormError(totalAmtOfTaxDueAtHigherRateKey, s"$messageKeyAmountTaxDueHigherRateKey.error.minimum")
    )

    behave like longBigDecimal(
      form = form,
      fieldName = totalAmtOfTaxDueAtHigherRateKey,
      length = 11,
      expectedError = FormError(totalAmtOfTaxDueAtHigherRateKey, s"$messageKeyAmountTaxDueHigherRateKey.error.maximum")
    )
  }
}
