/*
 * Copyright 2022 HM Revenue & Customs
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
import uk.gov.hmrc.viewmodels.Radios
import uk.gov.hmrc.viewmodels._

sealed trait InputSelection

object InputSelection extends Enumerable.Implicits {

  case object ManualInput extends WithName("manualInput") with InputSelection
  case object FileUploadInput extends WithName("fileUploadInput") with InputSelection

  val values: Seq[InputSelection] = Seq(
    ManualInput,
    FileUploadInput
  )

  def radios(form: Form[_]): Seq[Radios.Item] = {

    val field = form("value")
    val items = Seq(
      Radios.Radio(msg"inputSelection.radio.manualInput", "manualInput"),
      Radios.Radio(msg"inputSelection.radio.fileUploadInput", "fileUploadInput")
    )

    Radios(field, items)
  }

  implicit val enumerable: Enumerable[InputSelection] =
    Enumerable(values.map(v => v.toString -> v): _*)
}