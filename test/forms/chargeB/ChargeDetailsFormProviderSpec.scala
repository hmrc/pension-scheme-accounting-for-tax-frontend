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

package forms.chargeB

import forms.behaviours._
import play.api.data.FormError

class ChargeDetailsFormProviderSpec extends IntFieldBehaviours with BigDecimalFieldBehaviours {

  val form = new ChargeDetailsFormProvider()()
  val numberOfDeceased = "numberOfDeceased"
  val amountTaxDueKey = "amountTaxDue"

  "numberOfDeceased" must {

    behave like intField(
      form = form,
      fieldName = numberOfDeceased,
      nonNumericError = FormError(numberOfDeceased, "numberOfDeceased.error.wholeNumber"),
      wholeNumberError = FormError(numberOfDeceased, "numberOfDeceased.error.wholeNumber"),
      minError = FormError(numberOfDeceased, "numberOfDeceased.error.wholeNumber"),
      maxError = FormError(numberOfDeceased, "numberOfDeceased.error.maxLength")
    )

    behave like intFieldWithMinimum(
      form = form,
      fieldName = numberOfDeceased,
      minimum = 0,
      expectedError = FormError(numberOfDeceased, "numberOfDeceased.error.wholeNumber", Seq(0))
    )

    behave like intFieldWithMaximum(
      form = form,
      fieldName = numberOfDeceased,
      maximum = 999999,
      expectedError = FormError(numberOfDeceased, "numberOfDeceased.error.maxLength", Seq(999999))
    )
  }

  "amountTaxDue" must {

    behave like bigDecimalField(
      form = form,
      fieldName = amountTaxDueKey,
      nonNumericError = FormError(amountTaxDueKey, "totalTaxDue.error.invalid"),
      decimalsError = FormError(amountTaxDueKey, "totalTaxDue.error.decimal")
    )

    behave like bigDecimalFieldWithMinimum(
      form = form,
      fieldName = amountTaxDueKey,
      minimum = BigDecimal("0.01"),
      expectedError = FormError(amountTaxDueKey, "totalTaxDue.error.minimum")
    )

    behave like longBigDecimal(
      form = form,
      fieldName = amountTaxDueKey,
      length = 11,
      expectedError = FormError(amountTaxDueKey, "totalTaxDue.error.maximum")
    )
  }
}
