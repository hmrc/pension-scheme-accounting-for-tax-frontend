/*
 * Copyright 2023 HM Revenue & Customs
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
import play.api.libs.functional.syntax._
import play.api.libs.json._
import uk.gov.hmrc.viewmodels.{NunjucksSupport, Text}

object Radios extends NunjucksSupport {


  final case class Radio(
                          label: Text,
                          value: String,
                          hint: Option[Hint] = None,
                          classes: Seq[String] = Seq.empty,
                          labelClasses: Option[LabelClasses] = None)

  object Radio {
    implicit def writes(implicit messages: Messages): OWrites[Radio] = (
      (__ \ "label").write[Text] and
        (__ \ "value").write[String] and
        (__ \ "hint").writeNullable[Hint] and
        (__ \ "classes").writeNullable[String]
      ) { radio =>
      (radio.label, radio.value, radio.hint, classes(radio.classes))
    }
  }

  final case class Item(id: String, text: Text, value: String, checked: Boolean, hint: Option[Hint] = None, classes: Seq[String] = Seq.empty,
                        label: Option[LabelClasses] = None)

  object Item {

      implicit def writes(implicit messages: Messages): OWrites[Item] =
        Json.writes[Item]
  }

  def apply(field: Field, items: Seq[Radio]): Seq[Item] = {
    val head = items.headOption.map {
      item =>
        Item(
          id      = field.id,
          text    = item.label,
          value   = item.value,
          checked = field.values.contains(item.value),
          hint = item.hint,
          classes = item.classes,
          label = item.labelClasses
        )
    }

    val tail = items.zipWithIndex.tail.map {
      case (item, i) =>
        Item(
          id      = s"${field.id}_$i",
          text    = item.label,
          value   = item.value,
          checked = field.values.contains(item.value),
          hint = item.hint,
          classes = item.classes,
          label = item.labelClasses
        )
    }

    head.toSeq ++ tail
  }

  def yesNo(field: Field): Seq[Item] = Seq(
    Item(id = field.id, text = msg"site.yes", value = "true", checked = field.value.contains("true")),
    Item(id = s"${field.id}-no", text = msg"site.no", value = "false", checked = field.value.contains("false"))
  )

  private def classes(classes: Seq[String]): Option[String] =
    if (classes.isEmpty) None else Some(classes.mkString(" "))
}
