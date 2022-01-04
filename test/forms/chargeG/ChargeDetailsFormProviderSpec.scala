/*
 * Copyright 2022 HM Revenue & Customs
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

package forms.chargeG

import base.SpecBase
import forms.behaviours.{DateBehaviours, StringFieldBehaviours}
import models.chargeG.ChargeDetails
import play.api.data.FormError
import utils.AFTConstants.{QUARTER_END_DATE, QUARTER_START_DATE}
import utils.DateHelper
import utils.DateHelper.dateFormatterDMY

class ChargeDetailsFormProviderSpec extends SpecBase with DateBehaviours with StringFieldBehaviours {

  private val dynamicErrorMsg: String = messages("chargeG.chargeDetails.qropsTransferDate.error.date", QUARTER_START_DATE.format(dateFormatterDMY),
    QUARTER_END_DATE.format(dateFormatterDMY))

  val futureErrorMsg: String = "chargeG.chargeDetails.qropsTransferDate.error.future"
  val form = new ChargeDetailsFormProvider()(QUARTER_START_DATE, QUARTER_END_DATE)
  val qropsRefKey = "qropsReferenceNumber"
  val qropsDateKey = "qropsTransferDate"

  "qropsTransferDate" must {

    behave like dateFieldWithMin(
      form = form,
      key = qropsDateKey,
      min = QUARTER_START_DATE,
      formError = FormError(qropsDateKey, dynamicErrorMsg)
    )

    behave like dateFieldWithMax(
      form = form,
      key = qropsDateKey,
      max = QUARTER_END_DATE,
      formError = FormError(qropsDateKey, dynamicErrorMsg)
    )

    behave like mandatoryDateField(form, qropsDateKey, "chargeG.chargeDetails.qropsTransferDate.error.required.all")
  }

  "qropsReferenceNumber" must {
    behave like fieldWithMaxLength(
      form = form,
      fieldName = qropsRefKey,
      maxLength = 7,
      lengthError = FormError(qropsRefKey, "chargeG.chargeDetails.qropsReferenceNumber.error.valid", Seq(7))
    )

    behave like qrOps(
      form,
      fieldName = qropsRefKey,
      requiredKey = "chargeG.chargeDetails.qropsReferenceNumber.error.required",
      invalidKey = "chargeG.chargeDetails.qropsReferenceNumber.error.valid"
    )

    "successfully bind when valid QROPS with spaces is provided" in {
      DateHelper.setDate(Some(QUARTER_START_DATE.plusDays(2)))
      val res = form.bind(Map("firstName" -> "Jane", "lastName" -> "Doe",
        qropsRefKey -> " 1 2 3 1 2 3 ",
        s"$qropsDateKey.day" -> QUARTER_START_DATE.getDayOfMonth.toString,
        s"$qropsDateKey.month" -> QUARTER_START_DATE.getMonthValue.toString,
        s"$qropsDateKey.year" -> QUARTER_START_DATE.getYear.toString
      )
      )
      res.get mustEqual ChargeDetails("123123", QUARTER_START_DATE)
    }
  }
}
