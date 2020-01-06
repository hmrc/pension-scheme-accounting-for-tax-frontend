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

package services.chargeE

import controllers.chargeB.{routes => _}
import models.chargeE.AnnualAllowanceMember
import models.{MemberDetails, NormalMode, UserAnswers}
import pages.chargeE.ChargeDetailsPage
import play.api.i18n.Messages
import play.api.mvc.Call
import uk.gov.hmrc.viewmodels.Text.Literal
import uk.gov.hmrc.viewmodels.{Html, _}
import viewmodels.Table
import viewmodels.Table.Cell
import utils.CheckYourAnswersHelper.formatBigDecimalAsString

object ChargeEService {

  def getAnnualAllowanceMembersIncludingDeleted(ua: UserAnswers, srn: String): Seq[AnnualAllowanceMember] = {

    val members = for {
        (member, index) <- ua.getAllMembersInCharge[MemberDetails]("chargeEDetails").zipWithIndex
      } yield {
        ua.get(ChargeDetailsPage(index)).map { chargeDetails =>
          AnnualAllowanceMember(
            index,
            member.fullName,
            member.nino,
            chargeDetails.chargeAmount,
            viewUrl(index, srn).url,
            removeUrl(index, srn).url,
            member.isDeleted
          )
        }
      }

    members.flatten
  }

  def getAnnualAllowanceMembers(ua: UserAnswers, srn: String): Seq[AnnualAllowanceMember] =
    getAnnualAllowanceMembersIncludingDeleted(ua, srn).filterNot(_.isDeleted)

  def viewUrl(index: Int, srn: String): Call = controllers.chargeE.routes.CheckYourAnswersController.onPageLoad(srn, index)
  def removeUrl(index: Int, srn: String): Call = controllers.chargeE.routes.DeleteMemberController.onPageLoad(NormalMode, srn, index)

  def mapToTable(members: Seq[AnnualAllowanceMember])(implicit messages: Messages): Table = {

    val head = Seq(
      Cell(msg"chargeE.addMembers.members.header", classes = Seq("govuk-!-width-one-quarter")),
      Cell(msg"chargeE.addMembers.nino.header", classes = Seq("govuk-!-width-one-quarter")),
      Cell(msg"chargeE.addMembers.chargeAmount.header", classes = Seq("govuk-!-width-one-quarter")),
      Cell(msg""),
      Cell(msg"")
    )

    val rows = members.map { data =>
      Seq(
        Cell(Literal(data.name), classes = Seq("govuk-!-width-one-quarter")),
        Cell(Literal(data.nino), classes = Seq("govuk-!-width-one-quarter")),
        Cell(Literal(s"£${formatBigDecimalAsString(data.chargeAmount)}"), classes = Seq("govuk-!-width-one-quarter")),
        Cell(link(data.viewLinkId, "site.view", data.viewLink, data.name), classes = Seq("govuk-!-width-one-quarter")),
        Cell(link(data.removeLinkId, "site.remove", data.removeLink, data.name), classes = Seq("govuk-!-width-one-quarter"))

      )
    }
    val totalAmount = members.map(_.chargeAmount).sum

    val totalRow = Seq(Seq(
      Cell(msg""), Cell(msg"chargeE.addMembers.total", classes = Seq("govuk-table__header--numeric")),
      Cell(Literal(s"£${formatBigDecimalAsString(totalAmount)}"), classes = Seq("govuk-!-width-one-quarter")),
      Cell(msg""),
      Cell(msg"")
    ))

    Table(head = head, rows = rows ++ totalRow)
  }

  def link(id: String, text: String, url: String, name: String)(implicit messages: Messages): Html = {
    val hiddenTag = "govuk-visually-hidden"
    Html(s"<a id=$id href=$url> ${messages(text)}" +
      s"<span class= $hiddenTag>${messages("chargeE.addMembers.visuallyHidden", name)}</span> </a>")
  }

}
