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

import play.api.libs.json.Writes
import play.api.libs.functional.syntax._

import scala.language.implicitConversions
import play.api.libs.json.Json
import helpers.ChargeCHelper
import helpers.ChargeDHelper
import helpers.ChargeEHelper
import helpers.ChargeGHelper
import javax.inject.Singleton
import models.ChargeType
import models.Member
import models.UserAnswers
import play.api.libs.json._
import uk.gov.hmrc.viewmodels.SummaryList.Key
import uk.gov.hmrc.viewmodels.SummaryList.Row
import uk.gov.hmrc.viewmodels.SummaryList.Value
import play.api.i18n.Messages
import services.MemberSearchService.MemberRow
import uk.gov.hmrc.viewmodels.Text.Literal
import uk.gov.hmrc.viewmodels._

import scala.language.implicitConversions

@Singleton
class MemberSearchService {
  import MemberSearchService._

  def search(ua: UserAnswers, srn: String, startDate: LocalDate, searchText:String)(implicit messages: Messages):Seq[MemberRow] = {
    listOfRows(listOfMembers(ua, srn, startDate).filter(_.name.contains(searchText)))
  }

  private def toMemberSummary(member:Member, chargeType:ChargeType):MemberSummary =
    MemberSummary(member.index, member.name, Some(member.nino), chargeType, member.amount, member.viewLink, member.removeLink)

  private def listOfMembers(ua: UserAnswers, srn: String, startDate: LocalDate): Seq[MemberSummary] = {
    val chargeDMembers = ChargeDHelper.getLifetimeAllowanceMembersIncludingDeleted(ua, srn, startDate)
      .map(toMemberSummary(_, ChargeType.ChargeTypeLifetimeAllowance))
    val chargeEMembers = ChargeEHelper.getAnnualAllowanceMembersIncludingDeleted(ua, srn, startDate)
      .map(toMemberSummary(_, ChargeType.ChargeTypeAnnualAllowance))
    val chargeGMembers = ChargeGHelper.getOverseasTransferMembersIncludingDeleted(ua, srn, startDate)
      .map(toMemberSummary(_, ChargeType.ChargeTypeOverseasTransfer))
    val chargeCMembers = ChargeCHelper.getSponsoringEmployersIncludingDeleted(ua, srn, startDate).map { employer =>
      MemberSummary(employer.index, employer.name, employer.nino, ChargeType.ChargeTypeAuthSurplus, employer.amount, employer.viewLink, employer.removeLink)
    }
    chargeDMembers ++ chargeEMembers ++ chargeGMembers ++ chargeCMembers
  }

  private def listOfRows(listOfMembers:Seq[MemberSummary]): Seq[MemberRow] = {
    listOfMembers.map { data =>

    val rowNino =
      data.nino match {
        case None => Nil
        case Some(n) => Seq(Row(
              key = Key(msg"chargeC.sponsoringIndividualDetails.nino.label", classes = Seq("govuk-!-width-three-quarters")),
              value = Value(Literal(s"$n"), classes = Seq("govuk-!-width-one-quarter", "govuk-table__cell--numeric"))
            ))
      }
      val rowChargeType =
        Seq(Row(
          key = Key(msg"aft.summary.search.chargeType", classes = Seq("govuk-!-width-three-quarters")),
          value = Value(Literal(s"${data.chargeType.toString}"), classes = Seq("govuk-!-width-one-quarter", "govuk-table__cell--numeric"))
        ))
      val rowAmount =
        Seq(Row(
          key = Key(msg"aft.summary.search.amount", classes = Seq("govuk-!-width-three-quarters")),
          value = Value(Literal(s"${data.amount}"), classes = Seq("govuk-!-width-one-quarter", "govuk-table__cell--numeric"))
        ))

      MemberRow(data.name, rowNino ++ rowChargeType ++ rowAmount)
    }
  }
}

object MemberSearchService {
  private val ninoRegex = "[[A-Z]&&[^DFIQUV]][[A-Z]&&[^DFIQUVO]] ?\\d{2} ?\\d{2} ?\\d{2} ?[A-D]{1}".r

  case class MemberSummary(index: Int, name: String, nino: Option[String], chargeType: ChargeType, amount: BigDecimal, viewLink: String, removeLink: String, isDeleted: Boolean = false) {
    def id = s"member-$index"

    def linkIdRemove = s"$id-remove"

    def linkIdView = s"$id-view"
  }

  object MemberSummary {
    implicit lazy val formats: Format[Member] =
      Json.format[Member]
  }

  case class MemberRow(h2:String, rows:Seq[Row])
  object MemberRow {
    implicit def writes(implicit messages: Messages): Writes[MemberRow] =
      ((JsPath \ "h2").write[String] and
        (JsPath \ "rows").write[Seq[Row]])(mr => (mr.h2, mr.rows))
  }
}
