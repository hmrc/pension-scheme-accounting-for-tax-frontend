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

package forms.chargeG

import base.SpecBase
import forms.behaviours.{DateBehaviours, StringFieldBehaviours}
import models.chargeG.MemberDetails
import play.api.data.FormError
import utils.AFTConstants.MIN_DATE
import utils.DateHelper
import utils.DateHelper.dateFormatterDMY

import java.time.LocalDate

class MemberDetailsFormProviderSpec extends SpecBase with StringFieldBehaviours with DateBehaviours {

  val form = new MemberDetailsFormProvider()()

  ".firstName" must {

    val fieldName = "firstName"
    val requiredKey = "memberDetails.error.firstName.required"
    val lengthKey = "memberDetails.error.firstName.length"
    val maxLength = 35

    behave like fieldThatBindsValidData(
      form,
      fieldName,
      stringsWithMaxLength(maxLength)
    )

    behave like fieldWithMaxLength(
      form,
      fieldName,
      maxLength = maxLength,
      lengthError = FormError(fieldName, lengthKey, Seq(maxLength))
    )

    behave like mandatoryField(
      form,
      fieldName,
      requiredError = FormError(fieldName, requiredKey)
    )
  }

  ".lastName" must {

    val fieldName = "lastName"
    val requiredKey = "memberDetails.error.lastName.required"
    val lengthKey = "memberDetails.error.lastName.length"
    val maxLength = 35

    behave like fieldThatBindsValidData(
      form,
      fieldName,
      stringsWithMaxLength(maxLength)
    )

    behave like fieldWithMaxLength(
      form,
      fieldName,
      maxLength = maxLength,
      lengthError = FormError(fieldName, lengthKey, Seq(maxLength))
    )

    behave like mandatoryField(
      form,
      fieldName,
      requiredError = FormError(fieldName, requiredKey)
    )
  }

  "nino" must {
    behave like nino(
      form,
      fieldName = "nino",
      requiredKey = "memberDetails.error.nino.required",
      invalidKey = "memberDetails.error.nino.invalid"
    )

    "successfully bind when yes is selected and valid NINO with spaces is provided" in {
      val res = form.bind(Map("firstName" -> "Jane", "lastName" -> "Doe",
        "dob.day" -> "20",
        "dob.month" -> "02",
        "dob.year" -> "2002",
        "nino" -> " a b 0 2 0 2 0 2 a "))
      res.get mustEqual MemberDetails("Jane", "Doe", LocalDate.of(2002, 2, 20), "AB020202A")
    }
  }

  "dob" must {

    val dobKey = "dob"

    behave like dateFieldWithMin(
      form = form,
      key = dobKey,
      min = MIN_DATE,
      formError = FormError(dobKey, messages("genericDate.error.outsideReportedYear",
        MIN_DATE.format(dateFormatterDMY), DateHelper.today.format(dateFormatterDMY)), List("day", "month", "year"))
    )

    behave like dateFieldWithMax(
      form = form,
      key = dobKey,
      max = DateHelper.today,
      formError = FormError(dobKey, messages("genericDate.error.outsideReportedYear",
        MIN_DATE.format(dateFormatterDMY), DateHelper.today.format(dateFormatterDMY)), List("day", "month", "year"))
    )

    behave like mandatoryDateField(
      form = form,
      key = dobKey,
      requiredAllKey = messages("genericDate.error.invalid.allFieldsMissing", "birth"))
  }
}
