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
  //val lengthKey = "sponsoringEmployerAddress.error.length"
  val addressLineMaxLength = 35

  val form = new SponsoringEmployerAddressFormProvider()()

  "line1" must {
    val requiredKey = "chargeC.sponsoringEmployerAddress.line1.error.required"
    val fieldName = "line1"

    behave like fieldThatBindsValidData(
      form,
      fieldName,
      stringsWithMaxLength(addressLineMaxLength)
    )

//    behave like fieldWithMaxLength(
//      form,
//      fieldName,
//      maxLength = maxLength,
//      lengthError = FormError(fieldName, lengthKey, Seq(maxLength))
//    )

    behave like mandatoryField(
      form,
      fieldName,
      requiredError = FormError(fieldName, requiredKey)
    )
  }

  "line2" must {
    val requiredKey = "chargeC.sponsoringEmployerAddress.line2.error.required"
    val fieldName = "line2"

    behave like fieldThatBindsValidData(
      form,
      fieldName,
      stringsWithMaxLength(addressLineMaxLength)
    )

    //    behave like fieldWithMaxLength(
    //      form,
    //      fieldName,
    //      maxLength = maxLength,
    //      lengthError = FormError(fieldName, lengthKey, Seq(maxLength))
    //    )

    behave like mandatoryField(
      form,
      fieldName,
      requiredError = FormError(fieldName, requiredKey)
    )
  }

  "country" must {
    val requiredKey = "chargeC.sponsoringEmployerAddress.country.error.required"
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
    val requiredKey = "chargeC.sponsoringEmployerAddress.postcode.error.required"
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
