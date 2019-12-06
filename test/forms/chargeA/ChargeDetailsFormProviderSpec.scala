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

  private val amountTaxDue20pcKey = "amountTaxDue20pc"
  private val amountTaxDue50pcKey = "amountTaxDue50pc"

  private val messageKeyAmountTaxDue20pcKey = "chargeA.amountTaxDue20pc"
  private val messageKeyAmountTaxDue50pcKey = "chargeA.amountTaxDue50pc"

  "amountTaxDue 20%" - {

    behave like bigDecimalField(
      form = form,
      fieldName = amountTaxDue20pcKey,
      nonNumericError = FormError(amountTaxDue20pcKey, s"$messageKeyAmountTaxDue20pcKey.error.invalid"),
      decimalsError = FormError(amountTaxDue20pcKey, s"$messageKeyAmountTaxDue20pcKey.error.decimal")
    )

    behave like bigDecimalFieldWithMinimum(
      form = form,
      fieldName = amountTaxDue20pcKey,
      minimum = BigDecimal("0.01"),
      expectedError = FormError(amountTaxDue20pcKey, s"$messageKeyAmountTaxDue20pcKey.error.minimum")
    )

    behave like longBigDecimal(
      form = form,
      fieldName = amountTaxDue20pcKey,
      length = 12,
      expectedError = FormError(amountTaxDue20pcKey, s"$messageKeyAmountTaxDue20pcKey.error.maximum")
    )
  }

  "amountTaxDue 50%" - {

    behave like bigDecimalField(
      form = form,
      fieldName = amountTaxDue50pcKey,
      nonNumericError = FormError(amountTaxDue50pcKey, s"$messageKeyAmountTaxDue50pcKey.error.invalid"),
      decimalsError = FormError(amountTaxDue50pcKey, s"$messageKeyAmountTaxDue50pcKey.error.decimal")
    )

    behave like bigDecimalFieldWithMinimum(
      form = form,
      fieldName = amountTaxDue50pcKey,
      minimum = BigDecimal("0.01"),
      expectedError = FormError(amountTaxDue50pcKey, s"$messageKeyAmountTaxDue50pcKey.error.minimum")
    )

    behave like longBigDecimal(
      form = form,
      fieldName = amountTaxDue50pcKey,
      length = 12,
      expectedError = FormError(amountTaxDue50pcKey, s"$messageKeyAmountTaxDue50pcKey.error.maximum")
    )
  }
}
