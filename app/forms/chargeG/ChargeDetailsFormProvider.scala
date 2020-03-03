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

import forms.mappings.Mappings
import javax.inject.Inject
import models.chargeG.ChargeDetails
import play.api.data.Form
import play.api.data.Forms.mapping
import play.api.i18n.Messages
import utils.DateHelper.formatDateDMY

class ChargeDetailsFormProvider @Inject() extends Mappings {

  val qropsReferenceNumberKey: String = "chargeG.chargeDetails.qropsReferenceNumber.error"
  val qropsTransferDateKey: String = "chargeG.chargeDetails.qropsTransferDate.error"

  def apply(min: LocalDate, max: LocalDate)(implicit messages: Messages): Form[ChargeDetails] =
    Form(mapping(
      "qropsReferenceNumber" -> text(
        errorKey = s"$qropsReferenceNumberKey.required"
      ).transform(noSpaceWithUpperCaseTransform, noTransform)
        .verifying(
          regexp("""^[0-9]{6}""", s"$qropsReferenceNumberKey.valid")
        ),
      "qropsTransferDate" -> localDate(
        invalidKey = s"$qropsTransferDateKey.invalid",
        allRequiredKey = s"$qropsTransferDateKey.required.all",
        twoRequiredKey = s"$qropsTransferDateKey.required.two",
        requiredKey = s"$qropsTransferDateKey.required"
      ).verifying(
        minDate(min, messages("chargeG.chargeDetails.qropsTransferDate.error.date", formatDateDMY(min), formatDateDMY(max))),
        maxDate(max, messages("chargeG.chargeDetails.qropsTransferDate.error.date", formatDateDMY(min), formatDateDMY(max))),
        yearHas4Digits(s"$qropsTransferDateKey.invalid")
      )
    )(ChargeDetails.apply)(ChargeDetails.unapply))
}
