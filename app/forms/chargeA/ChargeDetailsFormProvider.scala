/*
 * Copyright 2021 HM Revenue & Customs
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
import models.chargeA.ChargeDetails
import play.api.data.Form
import play.api.data.Forms.mapping
import play.api.i18n.Messages
import uk.gov.voa.play.form.Condition
import uk.gov.voa.play.form.ConditionalMappings._

import javax.inject.Inject

class ChargeDetailsFormProvider @Inject() extends Mappings with Constraints {
  private def otherFieldEmptyOrZeroOrBothFieldsNonEmptyAndNotZero(otherField: String): Condition =
    map =>
      (
        (map(otherField).isEmpty | map(otherField) == "0.00")
          |
        ((map("totalAmtOfTaxDueAtLowerRate").nonEmpty && map("totalAmtOfTaxDueAtLowerRate") != "0.00")
          &&
        (map("totalAmtOfTaxDueAtHigherRate").nonEmpty && map("totalAmtOfTaxDueAtHigherRate") != "0.00"))
      )

  implicit private val ignoredParam: Option[BigDecimal] = None

  def apply(minimumChargeValueAllowed: BigDecimal)(implicit messages: Messages): Form[ChargeDetails] =
    Form(mapping(
      "numberOfMembers" -> int(
        requiredKey = "chargeA.numberOfMembers.error.required",
        wholeNumberKey = "chargeA.numberOfMembers.error.nonNumeric",
        nonNumericKey = "chargeA.numberOfMembers.error.nonNumeric",
        min = Some(Tuple2("chargeA.numberOfMembers.error.maximum", 0)),
        max = Some(Tuple2("chargeA.numberOfMembers.error.maximum", ChargeDetailsFormProvider.noOfMembersMax))
      ),
      "totalAmtOfTaxDueAtLowerRate" -> onlyIf[Option[BigDecimal]](
        otherFieldEmptyOrZeroOrBothFieldsNonEmptyAndNotZero(otherField = "totalAmtOfTaxDueAtHigherRate"),
        optionBigDecimal2DP(
          requiredKey = "chargeA.totalAmtOfTaxDueAtLowerRate.error.required",
          invalidKey = "chargeA.totalAmtOfTaxDueAtLowerRate.error.invalid",
          decimalKey = "chargeA.totalAmtOfTaxDueAtLowerRate.error.decimal"
        ).verifying(
          maximumValueOption[BigDecimal](
            BigDecimal("99999999999.99"),
            "chargeA.totalAmtOfTaxDueAtLowerRate.error.maximum"
          ),
          minimumValueOption[BigDecimal](
            minimumChargeValueAllowed,
            messages("chargeA.totalAmtOfTaxDueAtLowerRate.error.minimum", minimumChargeValueAllowed.formatted("%s"))
          )
        )
      ),
      "totalAmtOfTaxDueAtHigherRate" -> onlyIf[Option[BigDecimal]](
        otherFieldEmptyOrZeroOrBothFieldsNonEmptyAndNotZero(otherField = "totalAmtOfTaxDueAtLowerRate"),
        optionBigDecimal2DP(
          requiredKey = "chargeA.totalAmtOfTaxDueAtHigherRate.error.required",
          invalidKey = "chargeA.totalAmtOfTaxDueAtHigherRate.error.invalid",
          decimalKey = "chargeA.totalAmtOfTaxDueAtHigherRate.error.decimal"
        ).verifying(
          maximumValueOption[BigDecimal](
            BigDecimal("99999999999.99"),
            "chargeA.totalAmtOfTaxDueAtHigherRate.error.maximum"
          ),
          minimumValueOption[BigDecimal](
            minimumChargeValueAllowed,
            messages("chargeA.totalAmtOfTaxDueAtHigherRate.error.minimum", minimumChargeValueAllowed.formatted("%s"))
          )
        )
      ),
      "totalAmount" -> bigDecimalTotal("totalAmtOfTaxDueAtLowerRate", "totalAmtOfTaxDueAtHigherRate")
    )(ChargeDetails.apply)(ChargeDetails.unapply))
}

object ChargeDetailsFormProvider {
  val noOfMembersMax: Int = 999999
}
