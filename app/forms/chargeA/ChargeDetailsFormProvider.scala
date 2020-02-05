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
import uk.gov.voa.play.form.Condition
import uk.gov.voa.play.form.ConditionalMappings._

class ChargeDetailsFormProvider @Inject() extends Mappings with Constraints {
  private def otherFieldEmptyOrBothFieldsNonEmpty(otherField: String): Condition =
    map =>
      map(otherField).isEmpty | (map("totalAmtOfTaxDueAtLowerRate").nonEmpty && map("totalAmtOfTaxDueAtHigherRate").nonEmpty)

  implicit private val ignoredParam: Option[BigDecimal] = None

  def apply(): Form[ChargeDetails] =
    Form(mapping(
      "numberOfMembers" -> int(
        requiredKey = "chargeA.numberOfMembers.error.required",
        wholeNumberKey = "chargeA.numberOfMembers.error.nonNumeric",
        nonNumericKey = "chargeA.numberOfMembers.error.nonNumeric",
        min = Some(Tuple2("chargeA.numberOfMembers.error.maximum", 0)),
        max = Some(Tuple2("chargeA.numberOfMembers.error.maximum", 999999))
      ),
      "totalAmtOfTaxDueAtLowerRate" -> onlyIf[Option[BigDecimal]](
        otherFieldEmptyOrBothFieldsNonEmpty(otherField = "totalAmtOfTaxDueAtHigherRate"),
        optionBigDecimal2DP(
          requiredKey = "chargeA.totalAmtOfTaxDueAtLowerRate.error.required",
          invalidKey = "chargeA.totalAmtOfTaxDueAtLowerRate.error.invalid",
          decimalKey = "chargeA.totalAmtOfTaxDueAtLowerRate.error.decimal"
        ).verifying(
          maximumValueOption[BigDecimal](BigDecimal("99999999999.99"), "chargeA.totalAmtOfTaxDueAtLowerRate.error.maximum"),
          minimumValueOption[BigDecimal](BigDecimal("0.00"), "chargeA.totalAmtOfTaxDueAtLowerRate.error.minimum")
        )
      ),
      "totalAmtOfTaxDueAtHigherRate" -> onlyIf[Option[BigDecimal]](
        otherFieldEmptyOrBothFieldsNonEmpty(otherField = "totalAmtOfTaxDueAtLowerRate"),
        optionBigDecimal2DP(
          requiredKey = "chargeA.totalAmtOfTaxDueAtHigherRate.error.required",
          invalidKey = "chargeA.totalAmtOfTaxDueAtHigherRate.error.invalid",
          decimalKey = "chargeA.totalAmtOfTaxDueAtHigherRate.error.decimal"
        ).verifying(
          maximumValueOption[BigDecimal](BigDecimal("99999999999.99"), "chargeA.totalAmtOfTaxDueAtHigherRate.error.maximum"),
          minimumValueOption[BigDecimal](BigDecimal("0.00"), "chargeA.totalAmtOfTaxDueAtHigherRate.error.minimum")
        )
      ),
      "totalAmount" -> bigDecimalTotal("totalAmtOfTaxDueAtLowerRate", "totalAmtOfTaxDueAtHigherRate")
    )(ChargeDetails.apply)(ChargeDetails.unapply))
}
