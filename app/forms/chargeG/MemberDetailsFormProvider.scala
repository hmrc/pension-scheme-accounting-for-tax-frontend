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

package forms.chargeG

import forms.mappings.Constraints
import forms.mappings.Mappings
import forms.mappings.Transforms
import javax.inject.Inject
import models.chargeG.MemberDetails
import play.api.data.Form
import play.api.data.Forms._
import utils.AFTConstants.MIN_DATE

class MemberDetailsFormProvider @Inject() extends Mappings with Constraints with Transforms {

  def apply(): Form[MemberDetails] = Form(
    mapping(
      "firstName" -> text("memberDetails.error.firstName.required")
        .verifying(maxLength(35, "memberDetails.error.firstName.length"))
        .verifying(regexp(nameRegex, "memberDetails.error.firstName.invalid")),
      "lastName" -> text("memberDetails.error.lastName.required")
        .verifying(maxLength(35, "memberDetails.error.lastName.length"))
        .verifying(regexp(nameRegex, "memberDetails.error.lastName.invalid")),
      "dob" -> localDate(
        invalidKey = "dob.error.invalid",
        allRequiredKey = "dob.error.required",
        twoRequiredKey = "dob.error.incomplete",
        requiredKey = "dob.error.required"
      ).verifying(
        minDate(MIN_DATE, "dob.error.past"),
        futureDate("dob.error.future"),
        yearHas4Digits("dob.error.invalid")
      ),
      "nino" -> text("memberDetails.error.nino.required")
        .transform(noSpaceWithUpperCaseTransform, noTransform)
        .verifying(validNino("memberDetails.error.nino.invalid"))
    )(MemberDetails.applyDelete)(MemberDetails.unapplyDelete)
  )
}
