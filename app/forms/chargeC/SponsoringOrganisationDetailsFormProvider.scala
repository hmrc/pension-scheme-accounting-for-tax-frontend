/*
 * Copyright 2021 HM Revenue & Customs
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

import forms.mappings.{CrnMapping, Mappings}
import models.chargeC.SponsoringOrganisationDetails
import play.api.data.Form
import play.api.data.Forms.mapping

import javax.inject.Inject

class SponsoringOrganisationDetailsFormProvider @Inject() extends Mappings with CrnMapping {

  def apply(): Form[SponsoringOrganisationDetails] =
    Form(
      mapping(
        "name" -> text("chargeC.sponsoringOrganisationDetails.name.error.required")
          .verifying(maxLength(SponsoringOrganisationDetailsFormProvider.maxLength, "chargeC.sponsoringOrganisationDetails.name.error.length")),
        "crn" -> crnMapping(
          requiredCRNKey = "chargeC.sponsoringOrganisationDetails.crn.error.required",
          lengthKey = "chargeC.sponsoringOrganisationDetails.crn.error.length",
          invalidKey = "chargeC.sponsoringOrganisationDetails.crn.error.invalid"
        )
      )
      (SponsoringOrganisationDetails.applyDelete)(SponsoringOrganisationDetails.unapplyDelete)
    )
}

object SponsoringOrganisationDetailsFormProvider {
  val maxLength: Int = 155
}
