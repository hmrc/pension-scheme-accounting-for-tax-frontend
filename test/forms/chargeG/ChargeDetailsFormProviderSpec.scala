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

package forms.chargeG

import java.time.LocalDate
import java.time.format.DateTimeFormatter

import forms.behaviours.{DateBehaviours, StringFieldBehaviours}
import models.chargeG.ChargeDetails
import play.api.data.FormError
import utils.AFTConstants.{QUARTER_END_DATE, QUARTER_START_DATE}
import utils.DateHelper

class ChargeDetailsFormProviderSpec extends DateBehaviours with StringFieldBehaviours {

  private val dateFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy")
  private val startDate = LocalDate.parse(QUARTER_START_DATE)
  private val endDate = LocalDate.parse(QUARTER_END_DATE)
  private val dynamicErrorMsg: String = s"The date of the transfer into the QROPS must be between" +
    s"${startDate.format(dateFormatter)} and ${endDate.format(dateFormatter)}"
  val futureErrorMsg: String = "chargeG.chargeDetails.qropsTransferDate.error.future"
  val form = new ChargeDetailsFormProvider()(startDate, endDate, dynamicErrorMsg)
  val qropsRefKey = "qropsReferenceNumber"
  val qropsDateKey = "qropsTransferDate"

  "qropsTransferDate" must {

    behave like dateFieldWithMin(
      form = form,
      key = qropsDateKey,
      min = startDate,
      formError = FormError(qropsDateKey, dynamicErrorMsg)
    )

    behave like dateFieldWithMax(
      form = form,
      key = qropsDateKey,
      max = DateHelper.today,
      formError = FormError(qropsDateKey, futureErrorMsg)
    )

    behave like dateFieldWithMax(
      form = form,
      key = qropsDateKey,
      max = endDate,
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
      DateHelper.setDate(Some(startDate.plusDays(2)))
      val res = form.bind(Map("firstName" -> "Jane", "lastName" -> "Doe",
        qropsRefKey -> " 1 2 3 1 2 3 ",
        s"$qropsDateKey.day"   -> startDate.getDayOfMonth.toString,
        s"$qropsDateKey.month" -> startDate.getMonthValue.toString,
        s"$qropsDateKey.year"  -> startDate.getYear.toString
      )
      )
      res.get mustEqual ChargeDetails("123123", startDate)
    }
  }
}
