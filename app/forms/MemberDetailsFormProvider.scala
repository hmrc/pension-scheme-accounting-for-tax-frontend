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

import forms.mappings.{Constraints, Mappings}
import models.MemberDetails
import play.api.data.Form
import play.api.data.Forms._

import javax.inject.Inject

class MemberDetailsFormProvider @Inject() extends Mappings with Constraints {

  def apply(): Form[MemberDetails] = Form(
    mapping(
      "firstName" -> text("memberDetails.error.firstName.required")
        .verifying(maxLength(MemberDetailsFormProvider.maxLength, "memberDetails.error.firstName.length"))
        .verifying(regexp(nameRegex, "memberDetails.error.firstName.invalid")),
      "lastName" -> text("memberDetails.error.lastName.required")
        .verifying(maxLength(MemberDetailsFormProvider.maxLength, "memberDetails.error.lastName.length"))
        .verifying(regexp(nameRegex, "memberDetails.error.lastName.invalid")),
      "nino" -> text("memberDetails.error.nino.required")
        .transform(noSpaceWithUpperCaseTransform, noTransform).
        verifying(validNino("memberDetails.error.nino.invalid"))
    )(MemberDetails.applyDelete)(MemberDetails.unapplyDelete)
  )
}

object MemberDetailsFormProvider {
  val maxLength: Int = 35
}
