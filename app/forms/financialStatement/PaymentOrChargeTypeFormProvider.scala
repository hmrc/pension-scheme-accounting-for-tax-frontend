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

package forms.financialStatement

import forms.mappings.Mappings
import models.ChargeDetailsFilter
import models.ChargeDetailsFilter.All
import models.financialStatement.PaymentOrChargeType
import play.api.data.Form

class PaymentOrChargeTypeFormProvider extends Mappings {

  def apply(journeyType: ChargeDetailsFilter = All): Form[PaymentOrChargeType] =
    Form(
      "value" -> enumerable[PaymentOrChargeType](requiredKey = s"paymentOrChargeType.$journeyType.error.required")
    )
}
