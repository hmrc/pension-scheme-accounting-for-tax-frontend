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

package forms.chargeD

import forms.mappings.{Constraints, Mappings}
import javax.inject.Inject
import models.chargeD.ChargeDDetails
import play.api.data.Form
import play.api.data.Forms.mapping
import play.api.i18n.Messages
import uk.gov.voa.play.form.Condition
import uk.gov.voa.play.form.ConditionalMappings._

class ChargeDetailsFormProvider @Inject() extends Mappings with Constraints {
  val taxAt25PercentIsEmpty: Condition = p => p.get("taxAt25Percent").isEmpty && p("taxAt25Percent").matches("""^-?( )*$""")
  val taxAt55PercentIsEmpty: Condition = p => p.get("taxAt55Percent").isEmpty && p("taxAt55Percent").matches("""^-?( )*$""")
  implicit val ignoredParam: Option[BigDecimal] = None

  def apply()(implicit messages: Messages): Form[ChargeDDetails] =
    Form(mapping(
      "dateOfEvent" -> localDate(
        invalidKey = "dateOfEvent.error.invalid",
        allRequiredKey = "dateOfEvent.error.required",
        twoRequiredKey = "dateOfEvent.error.incomplete",
        requiredKey = "dateOfEvent.error.required"
      ).verifying(
        futureDate("dateOfEvent.error.future"),
        yearHas4Digits("dateOfEvent.error.invalid")
      ),
      "taxAt25Percent" -> onlyIf[Option[BigDecimal]](taxAt55PercentIsEmpty, optionBigDecimal2DP(
        requiredKey = messages("amountTaxDue.error.required", "25"),
        invalidKey = messages("amountTaxDue.error.invalid", "25"),
        decimalKey = messages("amountTaxDue.error.decimal", "25")
      ).verifying(
        maximumValueOption[BigDecimal](BigDecimal("9999999999.99"), messages("amountTaxDue.error.maximum", "25")),
        minimumValueOption[BigDecimal](BigDecimal("0.00"), messages("amountTaxDue.error.invalid", "25"))
      )),
      "taxAt55Percent" -> onlyIf[Option[BigDecimal]](taxAt25PercentIsEmpty, optionBigDecimal2DP(
        requiredKey = messages("amountTaxDue.error.required", "55"),
        invalidKey = messages("amountTaxDue.error.invalid", "55"),
        decimalKey = messages("amountTaxDue.error.decimal", "55")
      ).verifying(
        maximumValueOption[BigDecimal](BigDecimal("9999999999.99"), messages("amountTaxDue.error.maximum", "25")),
        minimumValueOption[BigDecimal](BigDecimal("0.00"), messages("amountTaxDue.error.invalid", "25"))
      ))
    )(ChargeDDetails.apply)(ChargeDDetails.unapply))
}
