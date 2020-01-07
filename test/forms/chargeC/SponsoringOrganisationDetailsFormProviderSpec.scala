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

import forms.behaviours.StringFieldBehaviours
import play.api.data.FormError

class SponsoringOrganisationDetailsFormProviderSpec extends StringFieldBehaviours {

  private val nameKey = "name"
  private val crnKey = "crn"

  private val nameRequiredKey = "chargeC.sponsoringOrganisationDetails.name.error.required"
  private val nameLengthKey = "chargeC.sponsoringOrganisationDetails.name.error.length"
  private val nameMaxLength = 155

  private val crnRequiredKey = "chargeC.sponsoringOrganisationDetails.crn.error.required"
  private val crnMinLengthKey = "chargeC.sponsoringOrganisationDetails.crn.error.length"
  private val crnMaxLengthKey = "chargeC.sponsoringOrganisationDetails.crn.error.length"
  private val crnMinLength = 8
  private val crnMaxLength = 8

  val form = new SponsoringOrganisationDetailsFormProvider()()

  "name" must {
    behave like fieldThatBindsValidData(
      form,
      nameKey,
      stringsWithMaxLength(nameMaxLength)
    )

    behave like mandatoryField(
      form,
      nameKey,
      requiredError = FormError(nameKey, nameRequiredKey)
    )

    behave like fieldWithMaxLength(
      form,
      nameKey,
      maxLength = nameMaxLength,
      lengthError = FormError(nameKey, nameLengthKey, Seq(nameKey))
    )
  }

  "crn" must {
    behave like fieldThatBindsValidData(
      form,
      crnKey,
      stringsWithMaxLength(crnMaxLength)
    )

    behave like mandatoryField(
      form,
      crnKey,
      requiredError = FormError(crnKey, crnRequiredKey)
    )

    behave like fieldWithMinLength(
      form,
      crnKey,
      minLength = crnMinLength,
      lengthError = FormError(crnKey, crnMinLengthKey, Seq(crnKey))
    )

    behave like fieldWithMaxLength(
      form,
      crnKey,
      maxLength = crnMaxLength,
      lengthError = FormError(crnKey, crnMaxLengthKey, Seq(crnKey))
    )
  }
}
