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

class SponsoringEmployerAddressFormProviderSpec extends StringFieldBehaviours {
  val addressLineMaxLength = 35

  val form = new SponsoringEmployerAddressFormProvider()()

  "line1" must {
    val requiredKey = "address.line1.error.required"
    val lengthKey = "address.line1.error.length"
    val fieldName = "line1"

    behave like fieldThatBindsValidData(
      form,
      fieldName,
      stringsWithMaxLength(addressLineMaxLength)
    )

    behave like fieldWithMaxLength(
      form,
      fieldName,
      maxLength = addressLineMaxLength,
      lengthError = FormError(fieldName, lengthKey, Seq(addressLineMaxLength))
    )

    behave like mandatoryField(
      form,
      fieldName,
      requiredError = FormError(fieldName, requiredKey)
    )
  }

  "line2" must {
    val requiredKey = "address.line2.error.required"
    val lengthKey = "address.line2.error.length"
    val fieldName = "line2"

    behave like fieldThatBindsValidData(
      form,
      fieldName,
      stringsWithMaxLength(addressLineMaxLength)
    )

    behave like fieldWithMaxLength(
      form,
      fieldName,
      maxLength = addressLineMaxLength,
      lengthError = FormError(fieldName, lengthKey, Seq(addressLineMaxLength))
    )

    behave like mandatoryField(
      form,
      fieldName,
      requiredError = FormError(fieldName, requiredKey)
    )
  }

  "line3" must {
    val lengthKey = "address.line3.error.length"
    val fieldName = "line3"

    behave like fieldThatBindsValidData(
      form,
      fieldName,
      stringsWithMaxLength(addressLineMaxLength)
    )

    behave like fieldWithMaxLength(
      form,
      fieldName,
      maxLength = addressLineMaxLength,
      lengthError = FormError(fieldName, lengthKey, Seq(addressLineMaxLength))
    )
  }

  "line4" must {
    val lengthKey = "address.line4.error.length"
    val fieldName = "line4"

    behave like fieldThatBindsValidData(
      form,
      fieldName,
      stringsWithMaxLength(addressLineMaxLength)
    )

    behave like fieldWithMaxLength(
      form,
      fieldName,
      maxLength = addressLineMaxLength,
      lengthError = FormError(fieldName, lengthKey, Seq(addressLineMaxLength))
    )
  }


  "country" must {
    val requiredKey = "address.country.error.required"
    val fieldName = "country"

    behave like fieldThatBindsValidData(
      form,
      fieldName,
      stringsWithMaxLength(addressLineMaxLength)
    )

    behave like mandatoryField(
      form,
      fieldName,
      requiredError = FormError(fieldName, requiredKey)
    )
  }

  "postcode" must {
    val requiredKey = "address.postcode.error.required"
    val fieldName = "postcode"
    "must not bind when key is not present at all when country is GB" in {
      val result = form.bind(Map("country" -> "GB")).apply(fieldName)
      result.errors.head mustEqual FormError(fieldName, Seq(requiredKey), Seq())
    }

    "must  not bind blank values when country is GB" in {
      val result = form.bind(Map("country" -> "GB", fieldName -> "")).apply(fieldName)
      result.errors.head mustEqual FormError(fieldName, Seq(requiredKey), Seq())
    }

    "must have no errors when key is not present at all and country is not GB" in {
      val result = form.bind(Map("country" -> "FR")).apply(fieldName)
      result.errors.size mustBe 0
    }

    "must have no errors when postcode has a blank value and country is not GB" in {
      val result = form.bind(Map("country" -> "FR", fieldName -> "")).apply(fieldName)
      result.errors.size mustBe 0
    }
  }
}
