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

import base.SpecBase
import config.FrontendAppConfig
import data.SampleData._
import forms.behaviours.OptionFieldBehaviours
import models.{Quarter, QuarterType}
import models.Quarters._
import play.api.data.FormError

class QuartersFormProviderSpec extends SpecBase with OptionFieldBehaviours {

  implicit val config: FrontendAppConfig = frontendAppConfig
  private val testYear = 2021
  private val errorKey = "quarters.error.required"
  val quarters: Seq[Quarter] = Seq(q22020, q32020, q42020, q12021)
  val form = new QuartersFormProvider()(errorKey, quarters)

  ".value" must {

    val fieldName = "value"
    val requiredKey = "quarters.error.required"

    behave like optionsField[QuarterType](
      form,
      fieldName,
      validValues  = Seq(Q1, Q2),
      invalidError = FormError(fieldName, "error.invalid")
    )

    behave like mandatoryField(
      form,
      fieldName,
      requiredError = FormError(fieldName, requiredKey)
    )
  }
}
