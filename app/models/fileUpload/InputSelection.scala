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

package models.fileUpload

import models.{Enumerable, WithName}
import play.api.data.Form
import play.api.i18n.Messages
import uk.gov.hmrc.govukfrontend.views.Aliases.{Hint, RadioItem, Text}
import viewmodels.Radios

sealed trait InputSelection

object InputSelection extends Enumerable.Implicits {

  case object ManualInput extends WithName("manualInput") with InputSelection
  case object FileUploadInput extends WithName("fileUploadInput") with InputSelection

  val values: Seq[InputSelection] = Seq(
    ManualInput,
    FileUploadInput
  )

  def radios(form: Form[?])(implicit messages: Messages): Seq[RadioItem] = {

    val field = form("value")
    val items = Seq(
      Radios.Radio(Text(Messages("inputSelection.radio.manualInput")), "manualInput"),
      Radios.Radio(Text(Messages("inputSelection.radio.fileUploadInput")), "fileUploadInput",
        Some(Hint(content = Text(Messages("fileupload.inputSelection.fileUploadInput.hint")), id = Some("hint-id"))
        )))

    Radios(field, items)
  }

  implicit val enumerable: Enumerable[InputSelection] =
    Enumerable(values.map(v => v.toString -> v)*)
}