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

package forms.chargeC

import forms.behaviours.StringFieldBehaviours
import models.MemberDetails
import play.api.data.FormError

class SponsoringIndividualDetailsFormProviderSpec extends StringFieldBehaviours {


  val maxLength = 35

  val form = new SponsoringIndividualDetailsFormProvider()()

  "firstName" must {
    val requiredKey = "chargeC.sponsoringIndividualDetails.firstName.error.required"
    val lengthKey = "chargeC.sponsoringIndividualDetails.firstName.error.length"
    val fieldName = "firstName"

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

  "lastName" must {
    val requiredKey = "chargeC.sponsoringIndividualDetails.lastName.error.required"
    val lengthKey = "chargeC.sponsoringIndividualDetails.lastName.error.length"
    val fieldName = "lastName"

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
      requiredKey = "chargeC.sponsoringIndividualDetails.nino.error.required",
      invalidKey = "chargeC.sponsoringIndividualDetails.nino.error.invalid"
    )

    "successfully bind when valid NINO with spaces is provided" in {
      val res = form.bind(Map("firstName" -> "Jane", "lastName" -> "Doe",
        "nino" -> " a b 0 2 0 2 0 2 a "))
      res.get mustEqual MemberDetails("Jane", "Doe", "AB020202A")
    }
  }
}
