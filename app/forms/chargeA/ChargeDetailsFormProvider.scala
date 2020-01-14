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

package forms.chargeA

import forms.mappings.{Constraints, Mappings}
import javax.inject.Inject
import models.chargeA.ChargeDetails
import play.api.data.Form
import play.api.data.Forms.mapping

class ChargeDetailsFormProvider @Inject() extends Mappings with Constraints {

  val errorKeys: (String, String, String) = Tuple3(
    "Both None", "Both 0.00", "One None, One 0.00"
  )

  def apply(): Form[ChargeDetails] =
    Form(mapping(
      "numberOfMembers" -> int(
        requiredKey = "chargeA.numberOfMembers.error.required",
        wholeNumberKey = "chargeA.numberOfMembers.error.nonNumeric",
        nonNumericKey = "chargeA.numberOfMembers.error.nonNumeric",
        min = Some(Tuple2("chargeA.numberOfMembers.error.maximum", 0)),
        max = Some(Tuple2("chargeA.numberOfMembers.error.maximum", 999999))
      ),
      "totalAmtOfTaxDueAtLowerRate" -> optionalBigDecimal2DP(
        otherKey = "totalAmtOfTaxDueAtHigherRate",
        requiredKeyA = "chargeA.totalAmtOfTaxDueAtLowerRate.error.required",
        requiredKeyB = "chargeA.totalAmtOfTaxDueAtHigherRate.error.required",
        invalidKeyA = "chargeA.totalAmtOfTaxDueAtLowerRate.error.invalid",
        invalidKeyB = "chargeA.totalAmtOfTaxDueAtHigherRate.error.invalid",
        decimalKeyA = "chargeA.totalAmtOfTaxDueAtLowerRate.error.decimal",
        decimalKeyB = "chargeA.totalAmtOfTaxDueAtHigherRate.error.decimal"
      ).verifying(
        maximumValueOption[BigDecimal](BigDecimal("9999999999.99"), "chargeA.totalAmtOfTaxDueAtLowerRate.error.maximum"),
        minimumValueOption[BigDecimal](BigDecimal("0.00"), "chargeA.totalAmtOfTaxDueAtLowerRate.error.minimum")
      ),
      "totalAmtOfTaxDueAtHigherRate" -> optionalBigDecimal2DP(
        otherKey = "totalAmtOfTaxDueAtLowerRate",
        requiredKeyA = "chargeA.totalAmtOfTaxDueAtHigherRate.error.required",
        requiredKeyB = "chargeA.totalAmtOfTaxDueAtLowerRate.error.required",
        invalidKeyA = "chargeA.totalAmtOfTaxDueAtHigherRate.error.invalid",
        invalidKeyB = "chargeA.totalAmtOfTaxDueAtLowerRate.error.invalid",
        decimalKeyA = "chargeA.totalAmtOfTaxDueAtHigherRate.error.decimal",
        decimalKeyB = "chargeA.totalAmtOfTaxDueAtLowerRate.error.decimal"
      ).verifying(
        maximumValueOption[BigDecimal](BigDecimal("9999999999.99"), "chargeA.totalAmtOfTaxDueAtHigherRate.error.maximum"),
        minimumValueOption[BigDecimal](BigDecimal("0.00"), "chargeA.totalAmtOfTaxDueAtHigherRate.error.minimum")
      ),
      "totalAmount" -> bigDecimalTotal("totalAmtOfTaxDueAtLowerRate", "totalAmtOfTaxDueAtHigherRate")
    )(ChargeDetails.apply)(ChargeDetails.unapply))
}
