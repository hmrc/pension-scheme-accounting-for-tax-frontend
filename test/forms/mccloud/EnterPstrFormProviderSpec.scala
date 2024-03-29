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

package forms.mccloud

import forms.behaviours.StringFieldBehaviours
import play.api.data.FormError

class EnterPstrFormProviderSpec extends StringFieldBehaviours {

  val pstrRegx = """^[0-9]{8}[Rr][A-Za-z]{1}$"""

  private val form = new EnterPstrFormProvider().apply()

  ".value" must {

    val fieldName = "value"
    val requiredKey = "enterPstr.error.required"
    val invalidKey = "enterPstr.error.invalid"

    "bind valid data" in {
      val validValue = "12345678RA"
      val result = form.bind(Map(fieldName -> validValue)).apply(fieldName)
      result.value.value mustBe validValue
    }

    "transform valid data to uppercase" in {
      val validValue = "12345678ra"
      val transformedValue = "12345678RA"
      val result = form.bind(Map(fieldName -> validValue))
      result.value.value mustBe transformedValue
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
      invalidError = FormError(fieldName, invalidKey, Seq(pstrRegx))
    )
  }
}
