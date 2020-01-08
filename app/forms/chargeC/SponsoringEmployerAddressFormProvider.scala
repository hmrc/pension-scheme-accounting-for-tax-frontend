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

import forms.mappings.Mappings
import javax.inject.Inject
import models.chargeC.SponsoringEmployerAddress
import play.api.data.Form
import play.api.data.Forms.mapping

class SponsoringEmployerAddressFormProvider @Inject() extends Mappings {

  def apply(): Form[SponsoringEmployerAddress] =
    Form(
      mapping(
        "line1" -> text("chargeC.sponsoringEmployerAddress.line1.error.required"),
        "line2" -> text("chargeC.sponsoringEmployerAddress.line2.error.required"),
        "line3" -> optionalText,
        "line4" -> optionalText,
        "country" -> text("chargeC.sponsoringEmployerAddress.country.error.required"),
        "postcode" -> postCodeMapping(
          keyRequired = "chargeC.sponsoringEmployerAddress.postcode.error.required",
          keyInvalid = "chargeC.sponsoringEmployerAddress.postcode.error.invalid"
        )
      )
      (SponsoringEmployerAddress.apply)(SponsoringEmployerAddress.unapply)
    )
}
