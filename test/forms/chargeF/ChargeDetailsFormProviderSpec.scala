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

package forms.chargeF

import java.time.format.DateTimeFormatter

import forms.behaviours._
import play.api.data.FormError
import utils.AFTConstants.{QUARTER_END_DATE, QUARTER_START_DATE}

class ChargeDetailsFormProviderSpec extends DateBehaviours with BigDecimalFieldBehaviours {

  private val dateFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy")
  private val dynamicErrorMsg: String = s"The date the scheme was de-registered must be between" +
    s"${QUARTER_START_DATE.format(dateFormatter)} and ${QUARTER_END_DATE.format(dateFormatter)}"
  val form = new ChargeDetailsFormProvider()(QUARTER_START_DATE, QUARTER_END_DATE, dynamicErrorMsg, BigDecimal("0.01"))
  val deRegDateMsgKey = "chargeF.deregistrationDate"
  val deRegDateKey = "deregistrationDate"
  val amountTaxDueMsgKey = "chargeF.amountTaxDue"
  val amountTaxDueKey = "amountTaxDue"

  "deregistrationDate" must {

    behave like dateFieldWithMin(
      form = form,
      key = deRegDateKey,
      min = QUARTER_START_DATE,
      formError = FormError(deRegDateKey, dynamicErrorMsg)
    )

    behave like dateFieldWithMax(
      form = form,
      key = deRegDateKey,
      max = QUARTER_END_DATE,
      formError = FormError(deRegDateKey, dynamicErrorMsg)
    )

    behave like mandatoryDateField(
      form = form,
      key = deRegDateKey,
      requiredAllKey = s"$deRegDateMsgKey.error.required.all")
  }

  "amountTaxDue" must {

    behave like bigDecimalField(
      form = form,
      fieldName = amountTaxDueKey,
      nonNumericError = FormError(amountTaxDueKey, s"$amountTaxDueMsgKey.error.invalid"),
      decimalsError = FormError(amountTaxDueKey, s"$amountTaxDueMsgKey.error.decimal")
    )

    behave like bigDecimalFieldWithMinimum(
      form = form,
      fieldName = amountTaxDueKey,
      minimum = BigDecimal("0.01"),
      expectedError = FormError(amountTaxDueKey, s"$amountTaxDueMsgKey.error.minimum")
    )

    behave like longBigDecimal(
      form = form,
      fieldName = amountTaxDueKey,
      length = 12,
      expectedError = FormError(amountTaxDueKey, s"$amountTaxDueMsgKey.error.maximum")
    )
  }
}
