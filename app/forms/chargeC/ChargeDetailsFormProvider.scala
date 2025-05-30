/*
 * Copyright 2024 HM Revenue & Customs
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

package forms.chargeC


import forms.mappings.Mappings
import models.chargeC.ChargeCDetails
import play.api.data.Form
import play.api.data.Forms.mapping
import play.api.i18n.Messages
import utils.DateConstraintHandlers.{localDateMappingWithDateRange, localDatesConstraintHandler}

import java.time.LocalDate
import javax.inject.Inject

class ChargeDetailsFormProvider @Inject() extends Mappings {

  def apply(min: LocalDate, max: LocalDate, minimumChargeValueAllowed: BigDecimal)(implicit messages: Messages): Form[ChargeCDetails] =
    Form(mapping(
      localDateMappingWithDateRange(field = "paymentDate", date = (min, max), dateDescription = "payment"),
      "amountTaxDue" -> bigDecimal2DP(
        requiredKey = "chargeC.amountTaxDue.error.required",
        invalidKey = "chargeC.amountTaxDue.error.invalid",
        decimalKey = "chargeC.amountTaxDue.error.decimal"
      ).verifying(
        maximumValue[BigDecimal](BigDecimal("99999999999.99"), "chargeC.amountTaxDue.error.invalid"),
        minimumValue[BigDecimal](minimumChargeValueAllowed, "chargeC.amountTaxDue.error.invalid")
      )
    )(ChargeCDetails.apply)(ChargeCDetails.unapply))
}
