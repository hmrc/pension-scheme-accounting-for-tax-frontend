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

import forms.mappings.{Constraints, Mappings}
import models.chargeG.ChargeAmounts
import play.api.data.Form
import play.api.data.Forms.mapping
import play.api.i18n.Messages

class ChargeAmountsFormProvider extends Mappings with Constraints {

  def apply(memberName: String, minimumChargeValueAllowed: BigDecimal)(implicit messages: Messages) =
    Form(
      mapping(
        "amountTransferred" -> bigDecimal2DP(
          requiredKey = messages("amountTransferred.error.required", memberName),
          invalidKey = messages("amountTransferred.error.invalid", memberName),
          decimalKey = messages("amountTransferred.error.decimal", memberName)
        ).verifying(
          maximumValue[BigDecimal](BigDecimal("99999999999.99"), messages("amountTransferred.error.maximum", memberName)),
          minimumValue[BigDecimal](minimumChargeValueAllowed, messages("amountTransferred.error.minimum", memberName))
        ),
        "amountTaxDue" -> bigDecimal2DP(
          requiredKey = "amountTaxDue.error.required",
          invalidKey = "amountTaxDue.error.invalid",
          decimalKey = "amountTaxDue.error.decimal"
        ).verifying(
          maximumValue[BigDecimal](BigDecimal("99999999999.99"), "amountTaxDue.error.maximum"),
          minimumValue[BigDecimal](minimumChargeValueAllowed, "amountTaxDue.error.minimum")
        )
      )(ChargeAmounts.apply)(ChargeAmounts.unapply))
}
