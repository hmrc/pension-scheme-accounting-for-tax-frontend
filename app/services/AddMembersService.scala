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

package services

import helpers.FormatHelper
import models.Member
import play.api.i18n.Messages
import uk.gov.hmrc.govukfrontend.views.viewmodels.table.{HeadCell, TableRow}
import uk.gov.hmrc.govukfrontend.views.Aliases.{Table, Text}
import uk.gov.hmrc.govukfrontend.views.viewmodels.content.HtmlContent
import uk.gov.hmrc.govukfrontend.views.viewmodels.table

object AddMembersService {

  def mapChargeXMembersToTable(chargeName: String,
    members: Seq[Member],
    canChange: Boolean,
    totalAmount: Option[BigDecimal] = None
  )(implicit messages: Messages): Table = {

    val head = Seq(
      HeadCell(Text(Messages("addMembers.members.header"))),
      HeadCell(Text(Messages("addMembers.nino.header"))),
      HeadCell(Text(Messages(s"addMembers.$chargeName.amount.header")), classes = "govuk-table__header--numeric"),
      HeadCell(HtmlContent(s"""<span class=govuk-visually-hidden>${messages("addMember.link.hiddenText.header.viewMember")}</span>"""))
    ) ++ (
      if (canChange)
        Seq(HeadCell(HtmlContent(s"""<span class=govuk-visually-hidden>${messages("addMember.link.hiddenText.header.removeMember")}</span>""")))
      else
        Nil
      )

    val rows = members.map { data =>
      Seq(
        TableRow(Text(data.name), classes = "govuk-!-width-one-quarter"),
        TableRow(Text(data.nino), classes = "govuk-!-width-one-quarter"),
        TableRow(Text(s"${FormatHelper.formatCurrencyAmountAsString(data.amount)}"), classes = "govuk-!-width-one-quarter,govuk-table__header--numeric"),
        TableRow(link(data.viewLinkId, "site.view", data.viewLink, data.name, chargeName), classes =
          "govuk-!-width-one-quarter,govuk-table__header--numeric")
      ) ++ (if (canChange) {
        Seq(TableRow(link(data.removeLinkId, "site.remove", data.removeLink, data.name, chargeName), classes = "govuk-!-width-one-quarter"))
      } else {
        Nil
      })
    }



    val totalRow = Seq(
      Seq(
        TableRow(Text("")),
        TableRow(Text(Messages("addMembers.total")), classes = "govuk-!-font-weight-bold govuk-!-width-one-half"),
        TableRow(Text(s"${FormatHelper.formatCurrencyAmountAsString(totalAmount.getOrElse(members.map(_.amount).sum))}"),
          classes = "govuk-!-font-weight-bold govuk-table__header--numeric"),
        TableRow(Text(""))
      ) ++ (if (canChange) Seq(TableRow(Text(""))) else Nil))

    Table(rows = rows ++ totalRow, head = Some(head), attributes = Map("role" -> "table"))
  }

  def mapChargeXMembersToTableTwirlMigration(chargeName: String,
                               members: Seq[Member],
                               canChange: Boolean,
                               totalAmount: Option[BigDecimal] = None
                              )(implicit messages: Messages): table.Table = {

    val head: Seq[HeadCell] = Seq(
      HeadCell(Text(Messages("addMembers.members.header"))),
        HeadCell(Text(Messages("addMembers.nino.header"))),
        HeadCell(Text(Messages(s"addMembers.$chargeName.amount.header")), classes = "govuk-table__header--numeric"),
        HeadCell(HtmlContent(s"""<span class=govuk-visually-hidden>${messages("addMember.link.hiddenText.header.viewMember")}</span>"""))
    ) ++ (
      if (canChange)
        Seq(HeadCell(HtmlContent(s"""<span class=govuk-visually-hidden>${messages("addMember.link.hiddenText.header.removeMember")}</span>""")))
      else
        Nil
      )

    val rows = members.map { data =>
      Seq(
        TableRow(Text(data.name), classes = "govuk-!-width-one-quarter"),
        TableRow(Text(data.nino), classes = "govuk-!-width-one-quarter"),
        TableRow(Text(s"${FormatHelper.formatCurrencyAmountAsString(data.amount)}"), classes = "govuk-!-width-one-quarter govuk-table__header--numeric"),
        TableRow(linkTwirlMigration(data.viewLinkId, "site.view", data.viewLink, data.name, chargeName), classes =
          "govuk-!-width-one-quarter govuk-table__header--numeric")
      ) ++ (if (canChange) {
        Seq(TableRow(linkTwirlMigration(data.removeLinkId, "site.remove", data.removeLink, data.name, chargeName), classes = "govuk-!-width-one-quarter"))
      } else {
        Nil
      })
    }



    val totalRow = Seq(
      Seq(
        TableRow(Text(Messages(""))),
        TableRow(Text(Messages("addMembers.total")), classes = "govuk-!-font-weight-bold govuk-!-width-one-half"),
        TableRow(Text(s"${FormatHelper.formatCurrencyAmountAsString(totalAmount.getOrElse(members.map(_.amount).sum))}"),
          classes = "govuk-!-font-weight-bold govuk-table__header--numeric"),
        TableRow(Text(Messages("")))
      ) ++ (if (canChange) Seq(TableRow(Text(Messages("")))) else Nil))

    uk.gov.hmrc.govukfrontend.views.viewmodels.table.Table(head = Some(head), rows = rows ++ totalRow,attributes = Map("role" -> "table"))
  }

  def link(id: String, text: String, url: String, name: String, chargeName: String)(implicit messages: Messages): HtmlContent = {
    val hiddenTag = "govuk-visually-hidden"
    HtmlContent(
      s"""<a class= govuk-link id=$id href=$url><span aria-hidden=true>${messages(text)}</span><span class= $hiddenTag>${messages(text)} ${messages(s"$chargeName.addMembers.visuallyHidden", name)}</span> </a>""".stripMargin)
  }

  def linkTwirlMigration(id: String, text: String, url: String, name: String, chargeName: String)(implicit messages: Messages): HtmlContent = {
    val hiddenTag = "govuk-visually-hidden"
    HtmlContent(
      s"""<a class= govuk-link id=$id href=$url><span aria-hidden=true>${messages(text)}</span><span class= $hiddenTag>${messages(text)} ${messages(s"$chargeName.addMembers.visuallyHidden", name)}</span> </a>""".stripMargin)
  }

}
