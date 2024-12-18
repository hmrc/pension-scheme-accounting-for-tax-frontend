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
import uk.gov.hmrc.govukfrontend.views.Aliases.RadioItem
import uk.gov.hmrc.govukfrontend.views.viewmodels.content.Text

object TwirlRadios {
  def yesNo(field: Field)(implicit messages: Messages): Seq[RadioItem] = Seq(
    RadioItem(id = Some(field.id), content = Text(Messages("site.yes")), value = Some("true"), checked = field.value.contains("true")),
    RadioItem(id = Some(s"${field.id}-no"), content = Text(Messages("site.no")), value = Some("false"), checked = field.value.contains("false"))
  )
}
