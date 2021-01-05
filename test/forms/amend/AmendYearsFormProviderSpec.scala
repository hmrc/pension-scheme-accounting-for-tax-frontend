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

package forms.amend

import base.SpecBase
import config.FrontendAppConfig
import forms.behaviours.OptionFieldBehaviours
import models.{Year, AmendYears}
import play.api.data.FormError

class AmendYearsFormProviderSpec extends SpecBase with OptionFieldBehaviours {

  implicit val config: FrontendAppConfig = frontendAppConfig
  val years: Seq[Int] = Seq(2020, 2021, 2022)
  val form = new AmendYearsFormProvider()(years)

  ".value" must {

    val fieldName = "value"
    val requiredKey = "amendYears.error.required"

    behave like optionsField[Year](
      form,
      fieldName,
      validValues  = AmendYears.values(years),
      invalidError = FormError(fieldName, "error.invalid")
    )

    behave like mandatoryField(
      form,
      fieldName,
      requiredError = FormError(fieldName, requiredKey)
    )
  }
}
