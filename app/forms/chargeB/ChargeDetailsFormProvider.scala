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

package forms.chargeB

import forms.mappings.{Constraints, Mappings}
import javax.inject.Inject
import models.chargeB.ChargeBDetails
import play.api.data.Form
import play.api.data.Forms.mapping

class ChargeDetailsFormProvider @Inject() extends Mappings with Constraints {

  def apply(): Form[ChargeBDetails] =
    Form(mapping(
      "numberOfDeceased" -> int(
        requiredKey = "numberOfDeceased.error.required",
        wholeNumberKey = "numberOfDeceased.error.wholeNumber",
        nonNumericKey = "numberOfDeceased.error.wholeNumber"
      ).verifying(
        maximumValue(999999, "numberOfDeceased.error.maxLength"),
        minimumValue(0, "numberOfDeceased.error.wholeNumber")
      ),
      "amountTaxDue" -> bigDecimal2DP(
        requiredKey = "totalTaxDue.error.required",
        invalidKey = "totalTaxDue.error.invalid",
        decimalKey = "totalTaxDue.error.decimal"
      ).verifying(
        maximumValue[BigDecimal](BigDecimal("9999999999.99"), "totalTaxDue.error.maximum"),
        minimumValue[BigDecimal](BigDecimal("0.01"), "totalTaxDue.error.minimum")
      )
    )(ChargeBDetails.apply)(ChargeBDetails.unapply))
}
