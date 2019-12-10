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
      "numberOfMembers" -> int(
        requiredKey = "chargeA.numberOfMembers.error.required",
        wholeNumberKey = "chargeA.numberOfMembers.error.nonNumeric",
        nonNumericKey = "chargeA.numberOfMembers.error.nonNumeric").verifying(
        maximumValue[Int](999999, "chargeA.numberOfMembers.error.maximum"),
        minimumValue[Int](0, "chargeA.numberOfMembers.error.maximum")
      ),
      "totalAmtOfTaxDueAtLowerRate" -> bigDecimal2DP(
        requiredKey = "chargeA.totalAmtOfTaxDueAtLowerRate.error.required",
        invalidKey = "chargeA.totalAmtOfTaxDueAtLowerRate.error.invalid",
        decimalKey = "chargeA.totalAmtOfTaxDueAtLowerRate.error.decimal"
      ).verifying(
        maximumValue[BigDecimal](BigDecimal("9999999999.99"), "chargeA.totalAmtOfTaxDueAtLowerRate.error.maximum"),
        minimumValue[BigDecimal](BigDecimal("0.01"), "chargeA.totalAmtOfTaxDueAtLowerRate.error.minimum")
      ),
      "totalAmtOfTaxDueAtHigherRate" -> bigDecimal2DP(
        requiredKey = "chargeA.totalAmtOfTaxDueAtHigherRate.error.required",
        invalidKey = "chargeA.totalAmtOfTaxDueAtHigherRate.error.invalid",
        decimalKey = "chargeA.totalAmtOfTaxDueAtHigherRate.error.decimal"
      ).verifying(
        maximumValue[BigDecimal](BigDecimal("9999999999.99"), "chargeA.totalAmtOfTaxDueAtHigherRate.error.maximum"),
        minimumValue[BigDecimal](BigDecimal("0.01"), "chargeA.totalAmtOfTaxDueAtHigherRate.error.minimum")
      ),
      "totalAmount" -> bigDecimalCalculated
    )(ChargeDetails.apply)(ChargeDetails.unapply))
}
