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

package viewmodels

import play.api.i18n.Messages
import play.api.libs.json.OWrites
import play.api.libs.functional.syntax._
import play.api.libs.json._
import uk.gov.hmrc.viewmodels.{Content, Html, Text, WithContent}

final case class Hint(content: Content, id: String, classes: Seq[String] = Seq.empty,
                      attributes: Map[String, String] = Map.empty) extends WithContent
object Hint {

  implicit def writes(implicit messages: Messages): OWrites[Hint] = (
    (__ \ "text").writeNullable[Text] and
      (__ \ "html").writeNullable[Html] and
      (__ \ "id").write[String] and
      (__ \ "classes").writeNullable[String] and
      (__ \ "attributes").writeNullable[Map[String, String]]
    ){hint =>
    val attributes = Some(hint.attributes).filter(_.nonEmpty)
    (hint.text, hint.html, hint.id, classes(hint.classes), attributes)}

  private def classes(classes: Seq[String]): Option[String] =
    if (classes.isEmpty) None else Some(classes.mkString(" "))
}