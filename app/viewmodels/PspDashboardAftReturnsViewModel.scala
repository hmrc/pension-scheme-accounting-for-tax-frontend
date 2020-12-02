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

package viewmodels

import play.api.i18n.Messages
import play.api.libs.functional.syntax._
import play.api.libs.json.{JsObject, OWrites, __}

case class PspDashboardAftReturnsViewModel(
                                            subHeading: Option[JsObject],
                                            links: Seq[Link] = Seq.empty
                                          )

object PspDashboardAftReturnsViewModel {

  implicit def writes(implicit messages: Messages): OWrites[PspDashboardAftReturnsViewModel] = (
    (__ \ "subHeading").writeNullable[JsObject] and
      (__ \ "links").write[Seq[Link]]
    ) { model => (model.subHeading, model.links) }
}
