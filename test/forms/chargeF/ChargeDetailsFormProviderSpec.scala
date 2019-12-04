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

package forms.chargeF

import java.time.LocalDate

import forms.ChargeDetailsFormProvider
import forms.behaviours._
import play.api.data.FormError

class ChargeDetailsFormProviderSpec extends DateBehaviours with BigDecimalFieldBehaviours {

  val dynamicErrorMsg: String = "The date the scheme was de-registered must be between 1 April 2020 and 30 June 2020"
  val form = new ChargeDetailsFormProvider()(dynamicErrorMsg)

  "deregistrationDate" - {

    behave like dateFieldWithMin(
      form = form,
      key = "deregistrationDate",
      min = LocalDate.of(2020, 4, 1),
      formError = FormError("deregistrationDate.error.date", dynamicErrorMsg)
    )
    behave like dateFieldWithMax(
      form = form,
      key = "deregistrationDate",
      max = LocalDate.of(2020, 6, 30),
      formError = FormError("deregistrationDate.error.date", dynamicErrorMsg)
    )

    behave like mandatoryDateField(
      form = form,
      key = "deregistrationDate",
      requiredAllKey = "chargeDetails.error.required.all")
  }

  "amountTaxDue" - {

    behave like bigDecimalField(
      form,
      "amountTaxDue",
      FormError("amountTaxDue", Seq("amountTaxDue.error.invalid")),
      FormError("amountTaxDue", Seq("amountTaxDue.error.required"))
    )
  }
}
