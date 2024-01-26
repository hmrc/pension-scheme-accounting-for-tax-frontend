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

package forms

import base.SpecBase
import config.FrontendAppConfig
import forms.behaviours.OptionFieldBehaviours
import models.PenaltySchemes
import models.financialStatement.PenaltyType.ContractSettlementCharges
import play.api.data.FormError

class SelectSchemeFormProviderSpec extends SpecBase with OptionFieldBehaviours {

  implicit val config: FrontendAppConfig = frontendAppConfig
  val valid: Seq[PenaltySchemes] = Seq(PenaltySchemes(Some("Assoc scheme"), "XY123", Some("SRN123"), None), PenaltySchemes(None, "XY345", None, None))
  val errorMessage: String = messages("selectScheme.error", messages(s"penaltyType.${ContractSettlementCharges.toString}").toLowerCase())
  val form = new SelectSchemeFormProvider()(valid, errorMessage)

  ".value" must {

    val fieldName = "value"

    behave like optionsField[PenaltySchemes](
      form,
      fieldName,
      validValues  = valid,
      invalidError = FormError(fieldName, "error.invalid")
    )

    behave like mandatoryField(
      form,
      fieldName,
      requiredError = FormError(fieldName, errorMessage)
    )
  }
}
