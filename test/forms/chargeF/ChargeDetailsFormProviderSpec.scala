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

package forms.chargeF

import base.SpecBase
import forms.behaviours._
import play.api.data.FormError
import utils.AFTConstants.{QUARTER_END_DATE, QUARTER_START_DATE}
import utils.DateHelper.dateFormatterDMY

class ChargeDetailsFormProviderSpec extends SpecBase with DateBehaviours with BigDecimalFieldBehaviours {

  private val dynamicErrorMsg: String = messages("genericDate.error.outsideReportedYear",
    QUARTER_START_DATE.format(dateFormatterDMY), QUARTER_END_DATE.format(dateFormatterDMY))

  val form = new ChargeDetailsFormProvider()(QUARTER_START_DATE, QUARTER_END_DATE, BigDecimal("0.01"))
  val deRegDateMsgKey = "chargeF.deregistrationDate"
  val deRegDateKey = "deregistrationDate"
  val amountTaxDueMsgKey = "chargeF.amountTaxDue"
  val amountTaxDueKey = "amountTaxDue"

  "deregistrationDate" must {

    behave like dateFieldWithMin(
      form = form,
      key = deRegDateKey,
      min = QUARTER_START_DATE,
      formError = FormError(deRegDateKey, dynamicErrorMsg, List("day", "month", "year"))
    )

    behave like dateFieldWithMax(
      form = form,
      key = deRegDateKey,
      max = QUARTER_END_DATE,
      formError = FormError(deRegDateKey, dynamicErrorMsg, List("day", "month", "year"))
    )

    behave like mandatoryDateField(
      form = form,
      key = deRegDateKey,
      requiredAllKey = messages("genericDate.error.invalid.allFieldsMissing", "de-registration"))
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
