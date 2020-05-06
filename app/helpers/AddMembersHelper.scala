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

package helpers

import controllers.chargeB.{routes => _}
import models.Member
import play.api.i18n.Messages
import uk.gov.hmrc.viewmodels.Text.Literal
import uk.gov.hmrc.viewmodels.{Html, _}
import viewmodels.Table
import viewmodels.Table.Cell

object AddMembersHelper {

  private[helpers] def mapChargeXMembersToTable(chargeName: String, members: Seq[Member], canChange: Boolean)(implicit messages: Messages): Table = {

    val head = Seq(
      Cell(msg"addMembers.members.header", classes = Seq("govuk-!-width-one-quarter")),
      Cell(msg"addMembers.nino.header", classes = Seq("govuk-!-width-one-quarter")),
      Cell(msg"addMembers.$chargeName.amount.header", classes = Seq("govuk-!-width-one-quarter", "govuk-table__header--numeric")),
      Cell(msg"")
    ) ++ (if (canChange) Seq(Cell(msg"")) else Nil)

    val rows = members.map { data =>
      Seq(
        Cell(Literal(data.name), classes = Seq("govuk-!-width-one-quarter")),
        Cell(Literal(data.nino), classes = Seq("govuk-!-width-one-quarter")),
        Cell(Literal(s"${FormatHelper.formatCurrencyAmountAsString(data.amount)}"), classes = Seq("govuk-!-width-one-quarter", "govuk-table__header--numeric")),
        Cell(link(data.viewLinkId, "site.view", data.viewLink, data.name, chargeName), classes = Seq("govuk-!-width-one-quarter"))
      ) ++ (if (canChange) {
              Seq(Cell(link(data.removeLinkId, "site.remove", data.removeLink, data.name, chargeName), classes = Seq("govuk-!-width-one-quarter")))
      } else {
        Nil
      })
    }
    val totalAmount = members.map(_.amount).sum

    val totalRow = Seq(
      Seq(
        Cell(msg""),
        Cell(msg"addMembers.total", classes = Seq("govuk-table__header--numeric")),
        Cell(Literal(s"${FormatHelper.formatCurrencyAmountAsString(totalAmount)}"), classes = Seq("govuk-!-width-one-quarter", "govuk-table__header--numeric")),
        Cell(msg"")
      ) ++ (if (canChange) Seq(Cell(msg"")) else Nil))

    Table(head = head, rows = rows ++ totalRow)
  }

  def link(id: String, text: String, url: String, name: String, chargeName: String)(implicit messages: Messages): Html = {
    val hiddenTag = "govuk-visually-hidden"
    Html(
      s"<a id=$id href=$url> ${messages(text)}" +
        s"<span class= $hiddenTag>${messages(s"$chargeName.addMembers.visuallyHidden", name)}</span> </a>")
  }

}
