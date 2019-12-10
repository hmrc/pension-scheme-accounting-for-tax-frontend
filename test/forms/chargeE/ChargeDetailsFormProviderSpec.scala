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

package forms.chargeE

import java.time.LocalDate

import forms.behaviours._
import play.api.data.FormError

class ChargeDetailsFormProviderSpec extends DateBehaviours with BigDecimalFieldBehaviours with BooleanFieldBehaviours {

  val dynamicErrorMsg: String = "The date you received notice to pay the charge must be between 1 April 2020 and 30 June 2020"
  val form = new ChargeDetailsFormProvider()(dynamicErrorMsg)
  val dateKey = "dateNoticeReceived"
  val amountTaxDueKey = "chargeAmount"
  val isMandatoryKey = "isPaymentMandatory"

  "dateNoticeReceived" - {

    behave like dateFieldWithMin(
      form = form,
      key = dateKey,
      min = LocalDate.of(2020, 4, 1),
      formError = FormError(dateKey, dynamicErrorMsg)
    )

    behave like dateFieldWithMax(
      form = form,
      key = dateKey,
      max = LocalDate.of(2020, 6, 30),
      formError = FormError(dateKey, dynamicErrorMsg)
    )

    behave like mandatoryDateField(
      form = form,
      key = dateKey,
      requiredAllKey = s"$dateKey.error.required")
  }

  "chargeAmount" - {

    behave like bigDecimalField(
      form = form,
      fieldName = amountTaxDueKey,
      nonNumericError = FormError(amountTaxDueKey, s"$amountTaxDueKey.error.invalid"),
      decimalsError = FormError(amountTaxDueKey, s"$amountTaxDueKey.error.decimal")
    )

    behave like bigDecimalFieldWithMinimum(
      form = form,
      fieldName = amountTaxDueKey,
      minimum = BigDecimal("0.01"),
      expectedError = FormError(amountTaxDueKey, s"$amountTaxDueKey.error.invalid")
    )

    behave like longBigDecimal(
      form = form,
      fieldName = amountTaxDueKey,
      length = 12,
      expectedError = FormError(amountTaxDueKey, s"$amountTaxDueKey.error.maximum")
    )
  }

  "isPaymentMandatory" - {
    behave like booleanField(
      form = form,
      fieldName = isMandatoryKey,
      invalidError = FormError(isMandatoryKey, s"$isMandatoryKey.error.required")
    )
  }
}
