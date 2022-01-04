/*
 * Copyright 2022 HM Revenue & Customs
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
import models.MemberDetails
import play.api.data.Form
import play.api.data.Forms.mapping

import javax.inject.Inject

class SponsoringIndividualDetailsFormProvider @Inject() extends Mappings {

  def apply(): Form[MemberDetails] =
    Form(
      mapping(
        "firstName" -> text("chargeC.sponsoringIndividualDetails.firstName.error.required")
          .verifying(
            firstError(
              maxLength(SponsoringIndividualDetailsFormProvider.maxLength, "chargeC.sponsoringIndividualDetails.firstName.error.length"),
              regexp(nameRegex, "chargeC.sponsoringIndividualDetails.firstName.error.invalid"))
          ),
        "lastName" -> text("chargeC.sponsoringIndividualDetails.lastName.error.required")
          .verifying(
            firstError(
              maxLength(SponsoringIndividualDetailsFormProvider.maxLength, "chargeC.sponsoringIndividualDetails.lastName.error.length"),
              regexp(nameRegex, "chargeC.sponsoringIndividualDetails.lastName.error.invalid"))
          ),
        "nino" -> text("chargeC.sponsoringIndividualDetails.nino.error.required")
          .transform(noSpaceWithUpperCaseTransform, noTransform).
          verifying(validNino("chargeC.sponsoringIndividualDetails.nino.error.invalid"))
      )
      (MemberDetails.applyDelete)(MemberDetails.unapplyDelete)
    )
}

object SponsoringIndividualDetailsFormProvider {
  val maxLength: Int = 35
}
