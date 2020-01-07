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

import base.SpecBase
import forms.behaviours._
import play.api.data.FormError

class ChargeDetailsFormProviderSpec extends SpecBase with DateBehaviours with BigDecimalFieldBehaviours {

  val form = new ChargeDetailsFormProvider()()
  val dateKey = "dateOfEvent"
  val tax25PercentKey = "taxAt25Percent"
  val tax55PercentKey = "taxAt55Percent"

  "dateOfEvent" must {

   behave like dateFieldWithMax(
      form = form,
      key = dateKey,
      max = LocalDate.now(),
      formError = FormError(dateKey, s"$dateKey.error.future")
    )

    behave like mandatoryDateField(
      form = form,
      key = dateKey,
      requiredAllKey = s"$dateKey.error.required")
  }

  "taxAt25Percent" must {

    behave like bigDecimalField(
      form = form,
      fieldName = tax25PercentKey,
      nonNumericError = FormError(tax25PercentKey, messages("amountTaxDue.error.invalid", "25")),
      decimalsError = FormError(tax25PercentKey, messages("amountTaxDue.error.decimal", "25"))
    )

    behave like bigDecimalFieldWithMinimum(
      form = form,
      fieldName = tax25PercentKey,
      minimum = BigDecimal("0.00"),
      expectedError = FormError(tax25PercentKey, messages("amountTaxDue.error.invalid", "25"))
    )

    behave like longBigDecimal(
      form = form,
      fieldName = tax25PercentKey,
      length = 12,
      expectedError = FormError(tax25PercentKey, messages("amountTaxDue.error.maximum", "25"))
    )
  }

  "taxAt55Percent" must {

    behave like bigDecimalField(
      form = form,
      fieldName = tax55PercentKey,
      nonNumericError = FormError(tax55PercentKey, messages("amountTaxDue.error.invalid", "55")),
      decimalsError = FormError(tax55PercentKey, messages("amountTaxDue.error.decimal", "55"))
    )

    behave like bigDecimalFieldWithMinimum(
      form = form,
      fieldName = tax55PercentKey,
      minimum = BigDecimal("0.00"),
      expectedError = FormError(tax55PercentKey, messages("amountTaxDue.error.invalid", "55"))
    )

    behave like longBigDecimal(
      form = form,
      fieldName = tax55PercentKey,
      length = 12,
      expectedError = FormError(tax55PercentKey, messages("amountTaxDue.error.maximum", "55"))
    )
  }
}
