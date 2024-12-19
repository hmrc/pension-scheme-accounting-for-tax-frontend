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

package utils

import play.api.Logging
import play.api.i18n.Messages
import uk.gov.hmrc
import uk.gov.hmrc.govukfrontend.views.Aliases._
import uk.gov.hmrc.govukfrontend.views.html.components.{Hint => GovukHint}
import uk.gov.hmrc.govukfrontend.views.viewmodels.summarylist.Key
import viewmodels.Hint


object TwirlMigration extends Logging {

  def toTwirlRadios(nunjucksRadios: Seq[viewmodels.Radios.Item])(implicit messages: Messages): Seq[RadioItem] = {
    nunjucksRadios.map(radio => {
      RadioItem(
        content = Text(radio.text.resolve),
        value = Some(radio.value),
        checked = radio.checked,
        label = radio.label.map(label =>
          Label(classes = label.classes.mkString(" "))
        )
      )
    })
  }

  def toTwirlRadiosWithHintText(nunjucksRadios: Seq[viewmodels.Radios.Item])(implicit messages: Messages): Seq[RadioItem] = {
    nunjucksRadios.map(radio => {
      RadioItem(
        content = Text(radio.text.resolve),
        value = Some(radio.value),
        checked = radio.checked,
        hint = convertHint(radio.hint),
        label = radio.label.map(label =>
          Label(
            attributes = label.attributes,
            classes = label.classes.mkString(" ")
          )
        )
      )

    })
  }

  def convertHint(nunjucksHint: Option[Hint])(implicit messages: Messages): Option[GovukHint] = {
    nunjucksHint.map { hint =>
      GovukHint(
        content = resolveContent(hint.content),
        id = Some(hint.id),
        classes = hint.classes.mkString(" "),
        attributes = hint.attributes
      )
    }
  }

  private def resolveContent(content: hmrc.viewmodels.Content)(implicit messages: Messages): Content = {
    content match {
      case text: hmrc.viewmodels.Text => Text(text.resolve)
      case hmrc.viewmodels.Html(value) => HtmlContent(value)
    }
  }

  def summaryListRow(list: Seq[uk.gov.hmrc.viewmodels.SummaryList.Row])(implicit messages: Messages):Seq[SummaryListRow] = {
    list.map { row =>
      SummaryListRow(
        Key(
          resolveContent(row.key.content),
          classes = row.key.classes.mkString(" ")
        ),
        Value(
          resolveContent(row.value.content),
          classes = row.value.classes.mkString(" ")
        ),
        actions = if(row.actions.isEmpty) None else Some(
          Actions(
            items = row.actions.map { action => ActionItem(
              href = action.href,
              content = resolveContent(action.content),
              visuallyHiddenText = action.visuallyHiddenText.map(_.resolve),
              classes = action.classes.mkString(" "),
              attributes = action.attributes
            )}
          )
        )
      )
    }
  }
}
