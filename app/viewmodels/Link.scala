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

import play.api.libs.functional.syntax._
import play.api.libs.json.{OWrites, __}
import uk.gov.hmrc.govukfrontend.views.viewmodels.content.Content


case class Link(id: String, url: String, linkText: Content, hiddenText: Option[Content] = None)


object Link {

  implicit val writes: OWrites[Link] = (
    (__ \ "id").write[String] and
      (__ \ "url").write[String] and
      (__ \ "linkText").write[Content] and
      (__ \ "hiddenText").writeNullable[Content]
    ) { link => (link.id, link.url, link.linkText, link.hiddenText) }
}
