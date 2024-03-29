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

package forms

import forms.behaviours.StringFieldBehaviours
import play.api.data.FormError

class EnterPsaIdFormProviderSpec extends StringFieldBehaviours {

  private val psaIdRegex = "^A[0-9]{7}$"

  private val authorisingPsaId = "A1234567"

  private val form = new EnterPsaIdFormProvider().apply(authorisingPSAID = Some(authorisingPsaId))

  ".value" must {

    val fieldName = "value"
    val requiredKey = "enterPsaId.error.required"
    val invalidKey = "enterPsaId.error.invalid"
    val noMatchKey = "enterPsaId.error.noMatch"

    "bind valid data" in {
      val validValue = "A1234567"
      val result = form.bind(Map(fieldName -> validValue)).apply(fieldName)
      result.value.value mustBe validValue
    }

    behave like mandatoryField(
      form,
      fieldName,
      requiredError = FormError(fieldName, requiredKey)
    )

    behave like fieldWithRegex(
      form,
      fieldName,
      invalidValues = Seq("11111111", "$5%d122s"),
      invalidError = FormError(fieldName, invalidKey, Seq(psaIdRegex))
    )

    "give form error when psa ID is not same as authorising psa id for psp" in {
      val nonMatchingValue = "A1234568"
      val result = form.bind(Map(fieldName -> nonMatchingValue)).apply(fieldName)
      result.errors mustEqual Seq(FormError(fieldName, noMatchKey, Nil))
    }

    "give no form error when there is no authorising PSA ID" in {
      val validValue = "A1234568"
      val form = new EnterPsaIdFormProvider().apply(authorisingPSAID = None)
      val result = form.bind(Map(fieldName -> validValue)).apply(fieldName)
      result.value mustBe Some(validValue)
    }
  }
}
