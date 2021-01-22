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

package forms.chargeD

import java.time.LocalDate

import forms.mappings.{Formatters, Constraints, Mappings}
import javax.inject.Inject
import models.chargeD.ChargeDDetails
import play.api.data.Form
import play.api.data.Forms.mapping
import play.api.i18n.Messages
import uk.gov.voa.play.form.Condition
import uk.gov.voa.play.form.ConditionalMappings._
import utils.DateHelper.formatDateDMY

class ChargeDetailsFormProvider @Inject() extends Mappings with Constraints with Formatters {
  private def otherFieldEmptyOrZeroOrBothFieldsNonEmptyAndNotZero(otherField: String): Condition =
    map =>
      (
        (map(otherField).isEmpty | map(otherField) == "0.00")
          |
        ((map("taxAt25Percent").nonEmpty && map("taxAt25Percent") != "0.00")
          &&
        (map("taxAt55Percent").nonEmpty && map("taxAt55Percent") != "0.00"))
      )

  implicit private val ignoredParam: Option[BigDecimal] = None

  def apply(min: LocalDate, max: LocalDate, minimumChargeValueAllowed:BigDecimal)(implicit messages: Messages): Form[ChargeDDetails] =
    Form(mapping(
      "dateOfEvent" -> localDate(
        invalidKey = "dateOfEvent.error.invalid",
        allRequiredKey = "dateOfEvent.error.required",
        twoRequiredKey = "dateOfEvent.error.incomplete",
        requiredKey = "dateOfEvent.error.required"
      ).verifying(
        minDate(min, messages("dateOfEvent.error.date", formatDateDMY(min), formatDateDMY(max))),
        maxDate(max, messages("dateOfEvent.error.date", formatDateDMY(min), formatDateDMY(max))),
        yearHas4Digits("dateOfEvent.error.invalid")
      ),
      "taxAt25Percent" -> onlyIf[Option[BigDecimal]](
        otherFieldEmptyOrZeroOrBothFieldsNonEmptyAndNotZero(otherField = "taxAt55Percent"),
        optionBigDecimal2DP(
          requiredKey = messages("chargeD.amountTaxDue.error.required", "25"),
          invalidKey = messages("chargeD.amountTaxDue.error.invalid", "25")
        ).verifying(
          maximumValueOption[BigDecimal](BigDecimal("99999999999.99"), messages("chargeD.amountTaxDue.error.maximum", "25")),
          minimumValueOption[BigDecimal](minimumChargeValueAllowed, messages("chargeD.amountTaxDue.error.invalid", "25"))
        )
      ),
      "taxAt55Percent" -> onlyIf[Option[BigDecimal]](
        otherFieldEmptyOrZeroOrBothFieldsNonEmptyAndNotZero(otherField = "taxAt25Percent"),
        optionBigDecimal2DP(
          requiredKey = messages("chargeD.amountTaxDue.error.required", "55"),
          invalidKey = messages("chargeD.amountTaxDue.error.invalid", "55")
        ).verifying(
          maximumValueOption[BigDecimal](BigDecimal("99999999999.99"), messages("chargeD.amountTaxDue.error.maximum", "55")),
          minimumValueOption[BigDecimal](minimumChargeValueAllowed, messages("chargeD.amountTaxDue.error.invalid", "55"))
        )
      )
    )(ChargeDDetails.apply)(ChargeDDetails.unapply))
}
