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

import com.google.inject.Inject
import helpers.FormatHelper
import javax.inject.Singleton
import models.requests.DataRequest
import models.{AccessType, ChargeType, Member, UserAnswers}
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.libs.functional.syntax._
import play.api.libs.json.{Json, Writes, _}
import play.api.mvc.AnyContent
import play.twirl.api.{Html => TwirlHtml}
import uk.gov.hmrc.viewmodels.SummaryList.{Action, Key, Row, Value}
import uk.gov.hmrc.viewmodels.Text.{Literal, Message}
import uk.gov.hmrc.viewmodels._

import scala.language.implicitConversions

@Singleton
class MemberSearchService @Inject()(
                                     chargeDService: ChargeDService,
                                     chargeEService: ChargeEService,
                                     chargeGService: ChargeGService,
                                     fuzzyMatchingService: FuzzyMatchingService
                                   ) {

  import MemberSearchService._

  def search(ua: UserAnswers, srn: String, startDate: LocalDate, searchText: String, accessType: AccessType, version: Int)(implicit messages: Messages,
                                                                                                                           request: DataRequest[AnyContent]): Seq[MemberRow] = {
    val searchTextUpper = searchText.toUpperCase
    val searchFunc: MemberSummary => Boolean = { member =>
      if (searchTextUpper.matches(ninoRegex)) {
        member.nino.toUpperCase == searchTextUpper
      } else {
        fuzzyMatchingService.doFuzzyMatching(searchTextUpper, member.name)
      }
    }

    listOfRows(listOfMembers(ua, srn, startDate, accessType, version).filter(searchFunc), request.isViewOnly)
  }

  private def listOfMembers(ua: UserAnswers, srn: String, startDate: LocalDate, accessType: AccessType, version: Int)
                           (implicit request: DataRequest[AnyContent]): Seq[MemberSummary] = {
    val chargeDMembers = chargeDService
      .getLifetimeAllowanceMembers(ua, srn, startDate, accessType, version)
      .map(MemberSummary(_, ChargeType.ChargeTypeLifetimeAllowance))
    val chargeEMembers = chargeEService
      .getAnnualAllowanceMembers(ua, srn, startDate, accessType, version)
      .map(MemberSummary(_, ChargeType.ChargeTypeAnnualAllowance))
    val chargeGMembers = chargeGService
      .getOverseasTransferMembers(ua, srn, startDate, accessType, version)
      .map(MemberSummary(_, ChargeType.ChargeTypeOverseasTransfer))
    chargeDMembers ++ chargeEMembers ++ chargeGMembers
  }

  private def listOfRows(listOfMembers: Seq[MemberSummary], isViewOnly: Boolean)(implicit messages: Messages ): Seq[MemberRow] = {
    val allRows = listOfMembers.map { data =>
      val rowNino =
        Seq(
          Row(
            key = Key(msg"memberDetails.nino", classes = Seq("govuk-!-width-one-half")),
            value = Value(Literal(s"${data.nino}"), classes = Seq("govuk-!-width-one-half"))
          ))

      val rowChargeType =
        Seq(
          Row(
            key = Key(msg"aft.summary.search.chargeType", classes = Seq("govuk-!-width-one-half")),
            value = Value(Message(s"${getDescriptionMessageKeyFromChargeType(data.chargeType)}"), classes = Seq("govuk-!-width-one-half"))
          ))
      val rowAmount =
        Seq(
          Row(
            key = Key(msg"aft.summary.search.amount", classes = Seq("govuk-!-width-one-half")),
            value = Value(Literal(s"${FormatHelper.formatCurrencyAmountAsString(data.amount)}"), classes = Seq("govuk-!-width-one-half"))
          ))

      val removeAction = if (isViewOnly) {
        Nil
      } else {
         List(
          Action(
            content = msg"site.remove",
            href = data.removeLink,
            visuallyHiddenText = None
          )

        )
      }

      val actions = List(
        Action(
          content = Html(s"<span  aria-hidden=true >${messages("site.view")}</span>"),
          href = data.viewLink,
          visuallyHiddenText = None
        )
      ) ++ removeAction

      println("\n actions XXXX"+ actions)
      MemberRow(data.name, rowNino ++ rowChargeType ++ rowAmount, actions)
    }
    allRows.sortBy(_.name)
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
                                   removeLink: String) {
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
