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

package forms.chargeF

import java.time.LocalDate

import forms.mappings.{Constraints, Mappings}
import javax.inject.Inject
import models.chargeF.ChargeDetails
import play.api.data.Form
import play.api.data.Forms.mapping

class ChargeDetailsFormProvider @Inject() extends Mappings with Constraints {

  def apply(min: LocalDate, max: LocalDate, dateErrorMsg: String, minimumChargeValueAllowed:BigDecimal): Form[ChargeDetails] =
    Form(mapping(
      "deregistrationDate" -> localDate(
        invalidKey = "chargeF.deregistrationDate.error.invalid",
        allRequiredKey = "chargeF.deregistrationDate.error.required.all",
        twoRequiredKey = "chargeF.deregistrationDate.error.required.two",
        requiredKey = "chargeF.deregistrationDate.error.required.all"
      ).verifying(
        minDate(min, dateErrorMsg),
        maxDate(max, dateErrorMsg)
      ),
      "amountTaxDue" -> bigDecimal2DP(
        requiredKey = "chargeF.amountTaxDue.error.required",
        invalidKey = "chargeF.amountTaxDue.error.invalid",
        decimalKey = "chargeF.amountTaxDue.error.decimal"
      ).verifying(
        maximumValue[BigDecimal](BigDecimal("99999999999.99"), "chargeF.amountTaxDue.error.maximum"),
        minimumValue[BigDecimal](minimumChargeValueAllowed, "chargeF.amountTaxDue.error.minimum")
      )
    )(ChargeDetails.apply)(ChargeDetails.unapply))
}
