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

package services

import helpers.FormatHelper
import models.Member
import play.api.i18n.Messages
import uk.gov.hmrc.viewmodels.Text.Literal
import uk.gov.hmrc.viewmodels.{Html, _}
import viewmodels.Table
import viewmodels.Table.Cell

object AddMembersService {

  def mapChargeXMembersToTable(chargeName: String,
    members: Seq[Member],
    canChange: Boolean,
    totalAmount: Option[BigDecimal] = None
  )(implicit messages: Messages): Table = {

    val head = Seq(
      Cell(msg"addMembers.members.header"),
      Cell(msg"addMembers.nino.header"),
      Cell(msg"addMembers.$chargeName.amount.header", classes = Seq("govuk-table__header--numeric")),
      Cell(Html(s"""<span class=govuk-visually-hidden>${messages("addMember.link.hiddenText.header.viewMember")}</span>"""))
    ) ++ (
      if (canChange)
        Seq(Cell(Html(s"""<span class=govuk-visually-hidden>${messages("addMember.link.hiddenText.header.removeMember")}</span>""")))
      else
        Nil
      )

    val rows = members.map { data =>
      Seq(
        Cell(Literal(data.name), classes = Seq("govuk-!-width-one-quarter")),
        Cell(Literal(data.nino), classes = Seq("govuk-!-width-one-quarter")),
        Cell(Literal(s"${FormatHelper.formatCurrencyAmountAsString(data.amount)}"), classes = Seq("govuk-!-width-one-quarter", "govuk-table__header--numeric")),
        Cell(link(data.viewLinkId, "site.view", data.viewLink, data.name, chargeName), classes =
          Seq("govuk-!-width-one-quarter", "govuk-table__header--numeric"))
      ) ++ (if (canChange) {
        Seq(Cell(link(data.removeLinkId, "site.remove", data.removeLink, data.name, chargeName), classes = Seq("govuk-!-width-one-quarter")))
      } else {
        Nil
      })
    }



    val totalRow = Seq(
      Seq(
        Cell(msg""),
        Cell(msg"addMembers.total", classes = Seq("govuk-!-font-weight-bold govuk-table__header--numeric")),
        Cell(Literal(s"${FormatHelper.formatCurrencyAmountAsString(totalAmount.getOrElse(members.map(_.amount).sum))}"),
          classes = Seq("govuk-!-font-weight-bold govuk-table__header--numeric")),
        Cell(msg"")
      ) ++ (if (canChange) Seq(Cell(msg"")) else Nil))

    Table(head = head, rows = rows ++ totalRow,attributes = Map("role" -> "table"))
  }

  def link(id: String, text: String, url: String, name: String, chargeName: String)(implicit messages: Messages): Html = {
    val hiddenTag = "govuk-visually-hidden"
    Html(
      s"""<a class= govuk-link id=$id href=$url><span aria-hidden=true>${messages(text)}</span><span class= $hiddenTag>${messages(text)} ${messages(s"$chargeName.addMembers.visuallyHidden", name)}</span> </a>""".stripMargin)
  }

}
