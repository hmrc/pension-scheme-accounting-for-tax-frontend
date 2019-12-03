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
        invalidKey = "deregistrationDate.error.invalid",
        allRequiredKey = "deregistrationDate.error.required.all",
        twoRequiredKey = "deregistrationDate.error.required.two",
        requiredKey = "deregistrationDate.error.required"
      ),
      "amountTaxDue" -> bigDecimal(
        "amountTaxDue.error.required",
        "amountTaxDue.error.invalid"
      )
    )(ChargeDetails.apply)(ChargeDetails.unapply))
}
