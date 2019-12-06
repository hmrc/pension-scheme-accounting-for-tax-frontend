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

package forms.chargeA

import forms.mappings.{Constraints, Mappings}
import javax.inject.Inject
import models.chargeA.ChargeDetails
import play.api.data.Form
import play.api.data.Forms.mapping

class ChargeDetailsFormProvider @Inject() extends Mappings with Constraints {

  def apply(): Form[ChargeDetails] =
    Form(mapping(
      "members" -> int(
        requiredKey = "chargeTypeA.members.error.required",
        wholeNumberKey = "chargeTypeA.members.error.nonNumeric",
        nonNumericKey = "chargeTypeA.members.error.nonNumeric").verifying(
        maximumValue[Int](999999, "chargeTypeA.members.error.maximum"),
        minimumValue[Int](0, "chargeTypeA.members.error.maximum")
      ),
      "amountTaxDue20pc" -> bigDecimal2DP(
        requiredKey = "chargeTypeA.amountTax20pcDue.error.required",
        invalidKey = "chargeTypeA.amountTax20pcDue.error.invalid",
        decimalKey = "chargeTypeA.amountTax20pcDue.error.decimal"
      ).verifying(
        maximumValue[BigDecimal](BigDecimal("9999999999.99"), "chargeTypeA.amountTax20pcDue.error.maximum"),
        minimumValue[BigDecimal](BigDecimal("0.01"), "chargeTypeA.amountTax20pcDue.error.minimum")
      ),
      "amountTaxDue50pc" -> bigDecimal2DP(
        requiredKey = "chargeTypeA.amountTax50pcDue.error.required",
        invalidKey = "chargeTypeA.amountTax50pcDue.error.invalid",
        decimalKey = "chargeTypeA.amountTax50pcDue.error.decimal"
      ).verifying(
        maximumValue[BigDecimal](BigDecimal("9999999999.99"), "chargeTypeA.amountTax50pcDue.error.maximum"),
        minimumValue[BigDecimal](BigDecimal("0.01"), "chargeTypeA.amountTax50pcDue.error.minimum")
      )
    )(ChargeDetails.apply)(ChargeDetails.unapply))
}
