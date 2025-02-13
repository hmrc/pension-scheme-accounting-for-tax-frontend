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

package viewmodels

import play.api.data.Field
import play.api.i18n.Messages
import uk.gov.hmrc.govukfrontend.views.Aliases.{Hint, Label, Text}
import uk.gov.hmrc.govukfrontend.views.viewmodels.radios.RadioItem
object Radios  {

  final case class Radio(
                          label: Text,
                          value: String,
                          hint: Option[Hint] = None,
                          classes: Seq[String] = Seq.empty,
                          labelClasses: Option[LabelClasses] = None)

  def apply(field: Field, items: Seq[Radio]): Seq[RadioItem] = {
    val head = items.headOption.map {
      item =>
        RadioItem(
          content = item.label,
          value = Some(item.value),
          checked = field.value.contains(item.value),
          id = Some(field.id),
          label = item.labelClasses.map(label =>
            Label(classes = label.classes.mkString(" "))
          ),
          hint = item.hint
        )
    }

    val tail = items.zipWithIndex.tail.map {
      case (item, i) =>
        RadioItem(
          content = item.label,
          value = Some(item.value),
          checked = field.value.contains(item.value),
          id = Some(s"${field.id}_$i"),
          label = item.labelClasses.map(label =>
            Label(classes = label.classes.mkString(" "))
          ),
          hint = item.hint
        )
    }

    head.toSeq ++ tail
  }

  def yesNo(field: Field)(implicit messages: Messages): Seq[RadioItem] = Seq(
    RadioItem(id = Some(field.id), content = Text(Messages("site.yes")), value = Some("true"), checked = field.value.contains("true")),
    RadioItem(id = Some(s"${field.id}-no"), content = Text(Messages("site.no")), value = Some("false"), checked = field.value.contains("false"))
  )
}
