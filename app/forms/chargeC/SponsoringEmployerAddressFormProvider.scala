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

import forms.mappings.Mappings
import models.chargeC.SponsoringEmployerAddress
import play.api.data.Form
import play.api.data.Forms.mapping

import javax.inject.Inject

class SponsoringEmployerAddressFormProvider @Inject() extends Mappings {

  private val addressLineMaxLength = 35

  def apply(): Form[SponsoringEmployerAddress] =
    Form(
      mapping(
        "line1" -> text(errorKey = "address.line1.error.required")
          .verifying(
            firstError(
              maxLength(addressLineMaxLength, errorKey = "address.line1.error.length"),
              validAddressLine(invalidKey = "address.line1.error.invalid")
            )
          ),
        "line2" -> text(errorKey = "address.line2.error.required")
          .verifying(
            firstError(
              maxLength(addressLineMaxLength, errorKey = "address.line2.error.length"),
              validAddressLine(invalidKey = "address.line2.error.invalid")
            )
          ),
        "line3" -> optionalText()
          .verifying(
            firstError(
              optionalMaxLength(addressLineMaxLength, errorKey = "address.line3.error.length"),
              optionalValidAddressLine(invalidKey = "address.line3.error.invalid")
            )
          ),
        "line4" -> optionalText()
          .verifying(
            firstError(
              optionalMaxLength(addressLineMaxLength, errorKey = "address.line4.error.length"),
              optionalValidAddressLine(invalidKey = "address.line4.error.invalid")
            )
          ),
        "country" -> text(errorKey = "address.country.error.required"),
        "postcode" -> optionalPostcode(
          requiredKey = "address.postcode.error.required",
          invalidKey = "address.postcode.error.invalid",
          nonUkLengthKey = "address.postcode.error.length",
          countryFieldName = "country"
        )
      )
      (SponsoringEmployerAddress.apply)(SponsoringEmployerAddress.unapply)
    )
}
