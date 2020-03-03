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

package forms.chargeC

import base.SpecBase
import forms.behaviours.{BigDecimalFieldBehaviours, DateBehaviours}
import models.chargeC.ChargeCDetails
import play.api.data.FormError
import utils.AFTConstants.{QUARTER_END_DATE, QUARTER_START_DATE}
import utils.DateHelper.dateFormatterDMY

class ChargeDetailsFormProviderSpec extends SpecBase with DateBehaviours with BigDecimalFieldBehaviours {

  val form = new ChargeDetailsFormProvider().apply(
    QUARTER_START_DATE, QUARTER_END_DATE, minimumChargeValueAllowed = BigDecimal("0.01")
  )
  val amountTaxDueMsgKey = "chargeC.amountTaxDue"
  val amountTaxDueKey = "amountTaxDue"
  val dateKey = "paymentDate"
  private val dynamicErrorMsg: String = messages("chargeC.paymentDate.error.date", QUARTER_START_DATE.format(dateFormatterDMY),
    QUARTER_END_DATE.format(dateFormatterDMY))

  "paymentDate" must {
    "must bind valid data" in {
      val expectedResult = ChargeCDetails(
        paymentDate = QUARTER_START_DATE,
        amountTaxDue = BigDecimal(12.33)
      )

      val data = Map(
        "paymentDate.day" -> QUARTER_START_DATE.getDayOfMonth.toString,
        "paymentDate.month" -> QUARTER_START_DATE.getMonthValue.toString,
        "paymentDate.year" -> QUARTER_START_DATE.getYear.toString,
        "amountTaxDue" -> "12.33"
      )

      val result = form.bind(data)
      result.value.value mustEqual expectedResult
    }

    behave like mandatoryDateField(form, dateKey, "chargeC.paymentDate.error.required")

    behave like dateFieldWithMin(
      form = form,
      key = dateKey,
      min = QUARTER_START_DATE,
      formError = FormError(dateKey, dynamicErrorMsg)
    )

    behave like dateFieldWithMax(
      form = form,
      key = dateKey,
      max = QUARTER_END_DATE,
      formError = FormError(dateKey, dynamicErrorMsg)
    )

    behave like dateFieldInvalid(
      form = form,
      key = dateKey,
      formError = FormError(dateKey, "chargeC.paymentDate.error.invalid")
    )

    behave like dateFieldDayMonthMissing(
      form = form,
      key = dateKey,
      formError = FormError(dateKey, "chargeC.paymentDate.error.incomplete", Seq("day", "month"))
    )

    behave like dateFieldYearNot4Digits(
      form = form,
      key = dateKey,
      formError = FormError(dateKey, "chargeC.paymentDate.error.invalid")
    )
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
      minimum = BigDecimal("0.00"),
      expectedError = FormError(amountTaxDueKey, s"$amountTaxDueMsgKey.error.invalid")
    )

    behave like longBigDecimal(
      form = form,
      fieldName = amountTaxDueKey,
      length = 12,
      expectedError = FormError(amountTaxDueKey, s"$amountTaxDueMsgKey.error.invalid")
    )
  }
}
