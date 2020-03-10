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

package forms.chargeE

import forms.mappings.Constraints
import forms.mappings.Mappings
import javax.inject.Inject
import models.chargeE.ChargeEDetails
import play.api.data.Form
import play.api.data.Forms.mapping

class ChargeDetailsFormProvider @Inject() extends Mappings with Constraints {

  def apply(minimumChargeValueAllowed: BigDecimal): Form[ChargeEDetails] =
    Form(
      mapping(
        "chargeAmount" -> bigDecimal2DP(
          requiredKey = "chargeAmount.error.required",
          invalidKey = "chargeAmount.error.invalid",
          decimalKey = "chargeAmount.error.decimal"
        ).verifying(
          maximumValue[BigDecimal](BigDecimal("99999999999.99"), "chargeAmount.error.maximum"),
          minimumValue[BigDecimal](minimumChargeValueAllowed, "chargeAmount.error.invalid")
        ),
        "dateNoticeReceived" -> localDate(
          invalidKey = "dateNoticeReceived.error.invalid",
          allRequiredKey = "dateNoticeReceived.error.required",
          twoRequiredKey = "dateNoticeReceived.error.incomplete",
          requiredKey = "dateNoticeReceived.error.required"
        ).verifying(
          futureDate("dateNoticeReceived.error.future"),
          yearHas4Digits("dateNoticeReceived.error.invalid")
        ),
        "isPaymentMandatory" -> boolean("isPaymentMandatory.error")
      )(ChargeEDetails.apply)(ChargeEDetails.unapply))
}
