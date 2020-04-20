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

package forms

import java.time.LocalDate

import base.SpecBase
import config.FrontendAppConfig
import forms.behaviours.OptionFieldBehaviours
import models.{AmendQuarters, Quarter, Quarters}
import play.api.data.FormError

class QuartersFormProviderSpec extends SpecBase with OptionFieldBehaviours {

  implicit val config: FrontendAppConfig = frontendAppConfig
  private val testYear = 2021
  private val errorKey = "quarters.error.required"
  val form = new QuartersFormProvider()(errorKey, testYear)

  ".value" must {

    val fieldName = "value"
    val requiredKey = "quarters.error.required"

    behave like optionsField[Quarters](
      form,
      fieldName,
      validValues  = Seq(Quarter(LocalDate.of(2020, 4, 1), LocalDate.of(2020, 6, 30))),
      invalidError = FormError(fieldName, "error.invalid")
    )

    behave like mandatoryField(
      form,
      fieldName,
      requiredError = FormError(fieldName, requiredKey)
    )
  }
}
