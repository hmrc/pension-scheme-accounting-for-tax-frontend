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

package forms.chargeE

import forms.behaviours._
import play.api.data.FormError
import utils.AFTConstants._
import utils.DateHelper

class ChargeDetailsFormProviderSpec extends DateBehaviours with BigDecimalFieldBehaviours with BooleanFieldBehaviours {

  val form = new ChargeDetailsFormProvider().apply(minimumChargeValueAllowed = BigDecimal("0.01"))
  val dateKey = "dateNoticeReceived"
  val chargeAmountKey = "chargeAmount"
  val isMandatoryKey = "isPaymentMandatory"

  DateHelper.setDate(Some(QUARTER_START_DATE))

  "dateNoticeReceived" must {

   behave like dateFieldWithMax(
      form = form,
      key = dateKey,
      max = QUARTER_START_DATE,
      formError = FormError(dateKey, s"$dateKey.error.future")
    )

    behave like mandatoryDateField(
      form = form,
      key = dateKey,
      requiredAllKey = s"$dateKey.error.required")
  }

  "chargeAmount" must {

    behave like bigDecimalField(
      form = form,
      fieldName = chargeAmountKey,
      nonNumericError = FormError(chargeAmountKey, s"$chargeAmountKey.error.invalid"),
      decimalsError = FormError(chargeAmountKey, s"$chargeAmountKey.error.decimal")
    )

    behave like bigDecimalFieldWithMinimum(
      form = form,
      fieldName = chargeAmountKey,
      minimum = BigDecimal("0.00"),
      expectedError = FormError(chargeAmountKey, s"$chargeAmountKey.error.invalid")
    )

    behave like longBigDecimal(
      form = form,
      fieldName = chargeAmountKey,
      length = 12,
      expectedError = FormError(chargeAmountKey, s"$chargeAmountKey.error.maximum")
    )
  }

  "isPaymentMandatory" must {
    behave like booleanField(
      form = form,
      fieldName = isMandatoryKey,
      invalidError = FormError(isMandatoryKey, "error.boolean")
    )

    behave like mandatoryField(
      form,
      isMandatoryKey,
      requiredError = FormError(isMandatoryKey, s"$isMandatoryKey.error")
    )
  }
}
