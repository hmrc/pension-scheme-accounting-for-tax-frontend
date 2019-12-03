/*
 * Copyright 2019 HM Revenue & Customs
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

import forms.mappings.Mappings
import javax.inject.Inject
import models.chargeF.ChargeDetails
import play.api.data.Form
import play.api.data.Forms.mapping

class ChargeDetailsFormProvider @Inject() extends Mappings {

  def apply(): Form[ChargeDetails] =
    Form(mapping(
      "deregistrationDate" -> localDate(
        invalidKey = "chargeDetails.error.invalid",
        allRequiredKey = "chargeDetails.error.required.all",
        twoRequiredKey = "chargeDetails.error.required.two",
        requiredKey = "chargeDetails.error.required"
      ),
      "amountTaxDue" -> bigDecimal(
        "chargeDetails.error.required",
        "chargeDetails.error.invalid"
      )
    )(ChargeDetails.apply)(ChargeDetails.unapply))
}
