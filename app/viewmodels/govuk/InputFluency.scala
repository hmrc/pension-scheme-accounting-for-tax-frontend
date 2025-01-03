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

package viewmodels.govuk

import play.api.data.Field
import play.api.i18n.Messages
import uk.gov.hmrc.govukfrontend.views.viewmodels.hint.Hint
import uk.gov.hmrc.govukfrontend.views.viewmodels.input.{Input, PrefixOrSuffix}
import uk.gov.hmrc.govukfrontend.views.viewmodels.label.Label
import viewmodels.ErrorMessageAwareness

object input extends InputFluency

trait InputFluency {

  object InputViewModel extends ErrorMessageAwareness {

    def apply(
               field: Field,
               label: Label,
               classes: String = "",
               hint: Option[Hint] = None,
               inputType: String = "text",
               autocomplete: Option[String] = None,
               inputmode: Option[String] = None,
               pattern: Option[String] = None
             )(implicit messages: Messages): Input =
      Input(
        id           = field.id,
        name         = field.name,
        value        = field.value,
        label        = label,
        errorMessage = errorMessage(field),
        classes = classes,
        hint = hint,
        inputType = inputType,
        autocomplete = autocomplete,
        inputmode = inputmode,
        pattern = pattern
      )
  }

  implicit class FluentInput(input: Input) {
    def withCssClass(newClass: String): Input =
      input copy (classes = s"${input.classes} $newClass")

    def withAttribute(attribute: (String, String)): Input =
      input copy (attributes = input.attributes + attribute)

    def withPrefix(prefix: PrefixOrSuffix): Input =
      input copy (prefix = Some(prefix))
  }
}
