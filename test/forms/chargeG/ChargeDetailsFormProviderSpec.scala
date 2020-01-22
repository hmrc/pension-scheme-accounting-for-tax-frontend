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

import forms.behaviours.{DateBehaviours, StringFieldBehaviours}
import models.MemberDetails
import models.chargeG.ChargeDetails
import play.api.data.FormError

class ChargeDetailsFormProviderSpec extends DateBehaviours with StringFieldBehaviours {

  val dynamicErrorMsg: String = "The date of the transfer into the QROPS must be between 1 April 2020 and 30 June 2020"
  val futureErrorMsg: String = "chargeG.chargeDetails.qropsTransferDate.error.future"
  val form = new ChargeDetailsFormProvider()(dynamicErrorMsg)
  val qropsRefKey = "qropsReferenceNumber"
  val qropsDateKey = "qropsTransferDate"

  "qropsTransferDate" must {

    behave like dateFieldWithMin(
      form = form,
      key = qropsDateKey,
      min = LocalDate.of(2020, 1, 1),
      formError = FormError(qropsDateKey, dynamicErrorMsg)
    )

    behave like dateFieldWithMax(
      form = form,
      key = qropsDateKey,
      max = LocalDate.now(),
      formError = FormError(qropsDateKey, futureErrorMsg)
    )

    behave like dateFieldWithMax(
      form = form,
      key = qropsDateKey,
      max = LocalDate.of(2020, 6, 30),
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
      val date = LocalDate.of(2020,1,1)
      val res = form.bind(Map("firstName" -> "Jane", "lastName" -> "Doe",
        qropsRefKey -> " 1 2 3 1 2 3 ",
        s"$qropsDateKey.day"   -> date.getDayOfMonth.toString,
        s"$qropsDateKey.month" -> date.getMonthValue.toString,
        s"$qropsDateKey.year"  -> date.getYear.toString
      )
      )
      res.get mustEqual ChargeDetails("123123", date)
    }
  }
}
