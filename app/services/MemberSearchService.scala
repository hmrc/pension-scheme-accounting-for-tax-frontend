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

package services

import java.time.LocalDate

import helpers.{ChargeDHelper, ChargeEHelper, ChargeGHelper}
import javax.inject.Singleton
import models.{ChargeType, Member, UserAnswers}
import play.api.i18n.Messages
import play.api.libs.functional.syntax._
import play.api.libs.json.{Json, Writes, _}
import uk.gov.hmrc.viewmodels.SummaryList.{Action, Key, Row, Value}
import uk.gov.hmrc.viewmodels.Text.{Literal, Message}
import uk.gov.hmrc.viewmodels._

import scala.language.implicitConversions

@Singleton
class MemberSearchService {
  import MemberSearchService._

  def search(ua: UserAnswers, srn: String, startDate: LocalDate, searchText: String)(implicit messages: Messages): Seq[MemberRow] = {
    val searchTextUpper = searchText.toUpperCase
    val searchFunc: MemberSummary => Boolean =
      if (searchTextUpper.matches(ninoRegex)) _.nino.toUpperCase == searchTextUpper else _.name.toUpperCase.contains(searchTextUpper)
    listOfRows(listOfMembers(ua, srn, startDate).filter(searchFunc))
  }

  private def listOfMembers(ua: UserAnswers, srn: String, startDate: LocalDate): Seq[MemberSummary] = {
    val chargeDMembers = ChargeDHelper
      .getLifetimeAllowanceMembers(ua, srn, startDate)
      .map(MemberSummary(_, ChargeType.ChargeTypeLifetimeAllowance))
    val chargeEMembers = ChargeEHelper
      .getAnnualAllowanceMembers(ua, srn, startDate)
      .map(MemberSummary(_, ChargeType.ChargeTypeAnnualAllowance))
    val chargeGMembers = ChargeGHelper
      .getOverseasTransferMembers(ua, srn, startDate)
      .map(MemberSummary(_, ChargeType.ChargeTypeOverseasTransfer))
    chargeDMembers ++ chargeEMembers ++ chargeGMembers
  }

  private def listOfRows(listOfMembers: Seq[MemberSummary]): Seq[MemberRow] = {
    listOfMembers.map { data =>
      val rowNino =
        Seq(
          Row(
            key = Key(msg"memberDetails.nino", classes = Seq("govuk-!-width-three-quarters")),
            value = Value(Literal(s"${data.nino}"), classes = Seq("govuk-!-width-one-quarter", "govuk-table__cell--numeric"))
          ))

      val rowChargeType =
        Seq(
          Row(
            key = Key(msg"aft.summary.search.chargeType", classes = Seq("govuk-!-width-three-quarters")),
            value = Value(Message(s"${getDescriptionMessageKeyFromChargeType(data.chargeType)}"),
              classes = Seq("govuk-!-width-one-quarter", "govuk-table__cell--numeric"))
          ))
      val rowAmount =
        Seq(
          Row(
            key = Key(msg"aft.summary.search.amount", classes = Seq("govuk-!-width-three-quarters")),
            value = Value(Literal(s"${data.amount}"), classes = Seq("govuk-!-width-one-quarter", "govuk-table__cell--numeric"))
          ))

      val actions = List(
        Action(
          content = msg"site.view",
          href = data.viewLink,
          visuallyHiddenText = Some(msg"aft.summary.${data.chargeType.toString}.visuallyHidden.row")
        ),
        Action(
          content = msg"site.remove",
          href = data.removeLink,
          visuallyHiddenText = Some(msg"aft.summary.${data.chargeType.toString}.visuallyHidden.row")
        )
      )

      MemberRow(data.name, rowNino ++ rowChargeType ++ rowAmount, actions)
    }
  }
}

object MemberSearchService {
  private val ninoRegex = "[[A-Z]&&[^DFIQUV]][[A-Z]&&[^DFIQUVO]] ?\\d{2} ?\\d{2} ?\\d{2} ?[A-D]{1}"

  private def getDescriptionMessageKeyFromChargeType(chargeType: ChargeType): String =
    chargeType match {
      case ChargeType.ChargeTypeAnnualAllowance => "aft.summary.annualAllowance.description"
      case ChargeType.ChargeTypeOverseasTransfer => "aft.summary.overseasTransfer.description"
      case _ => "aft.summary.lifeTimeAllowance.description"
    }

  case class MemberRow(name: String, rows: Seq[Row], actions: Seq[Action])

  private case class MemberSummary(index: Int,
                                   name: String,
                                   nino: String,
                                   chargeType: ChargeType,
                                   amount: BigDecimal,
                                   viewLink: String,
                                   removeLink: String,
                                   isDeleted: Boolean = false) {
    def linkIdRemove = s"$id-remove"

    def linkIdView = s"$id-view"

    def id = s"member-$index"
  }

  object MemberRow {
    implicit def writes(implicit messages: Messages): Writes[MemberRow] =
      ((JsPath \ "name").write[String] and
        (JsPath \ "rows").write[Seq[Row]] and
        (JsPath \ "actions").write[Seq[Action]]) (mr => Tuple3(mr.name, mr.rows, mr.actions))
  }

  private object MemberSummary {
    implicit lazy val formats: Format[Member] =
      Json.format[Member]

    def apply(member: Member, chargeType: ChargeType): MemberSummary =
      MemberSummary(member.index, member.name, member.nino, chargeType, member.amount, member.viewLink, member.removeLink)
  }

}
