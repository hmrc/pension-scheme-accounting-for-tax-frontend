/*
 * Copyright 2021 HM Revenue & Customs
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
import play.api.i18n.Messages
import play.api.libs.functional.syntax._
import play.api.libs.json.Reads.JsObjectReducer
import play.api.libs.json.{Json, Writes, _}
import play.api.mvc.AnyContent
import uk.gov.hmrc.viewmodels.SummaryList.{Action, Key, Row, Value}
import uk.gov.hmrc.viewmodels.Text.{Literal, Message}
import uk.gov.hmrc.viewmodels._

@Singleton
class MemberSearchService @Inject()(
                                     chargeDService: ChargeDService,
                                     chargeEService: ChargeEService,
                                     chargeGService: ChargeGService,
                                     fuzzyMatchingService: FuzzyMatchingService
                                   ) {

  import MemberSearchService._

  def search(ua: UserAnswers, srn: String, startDate: LocalDate, searchText: String, accessType: AccessType, version: Int)(implicit
    request: DataRequest[AnyContent]): Seq[MemberRow] = {
      jsonSearch(searchText.toUpperCase, ua.data).fold[Seq[MemberRow]](Nil)(jsValue =>
        listOfRows(listOfMembers(UserAnswers(jsValue.as[JsObject]), srn, startDate, accessType, version), request.isViewOnly))
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

  private def listOfRows(listOfMembers: Seq[MemberSummary], isViewOnly: Boolean): Seq[MemberRow] = {
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
          content = msg"site.view",
          href = data.viewLink,
          visuallyHiddenText = None
        )
      ) ++ removeAction


      MemberRow(data.name, rowNino ++ rowChargeType ++ rowAmount, actions)
    }
    allRows.sortBy(_.name)
  }

  def jsonSearch: (String, JsValue) => Option[JsValue] = (searchString, ua) => {

    val conditionalFilter: JsValue => Boolean = jsValue => if(searchString.matches(ninoRegex)) {
      (jsValue \ "memberDetails" \ "nino").as[String] == searchString
    } else {
      val memberName = s"${(jsValue \ "memberDetails" \ "firstName").as[String]} ${(jsValue \ "memberDetails" \ "lastName").as[String]}"
      fuzzyMatchingService.doFuzzyMatching(searchString, memberName)
    }

    val chargeFilter: String => Reads[JsObject] = chargeType =>
      (__ \ s"charge${chargeType}Details").readNullable[JsObject].flatMap {
        case Some(_) => (__ \ s"charge${chargeType}Details" \ "members").json.update(__.read[JsArray].map { jsArray =>
                          JsArray(jsArray.value.filter(conditionalFilter))
                        })
        case _ => __.json.pickBranch
      }

    val pruneEmptyCharges: String => Reads[JsObject] = chargeType =>
      (__ \ s"charge${chargeType}Details").readNullable[JsObject].flatMap {
        case Some(_) => (__ \ s"charge${chargeType}Details" \ "members").readNullable[JsArray] flatMap {
          case Some(array) if array.value.nonEmpty => __.json.pickBranch
          case _ => ((__ \ s"charge${chargeType}Details").json.prune  and (__ \ s"charge${chargeType}NoMatch").json.put(JsBoolean(true))).reduce
        }
        case _ => __.json.pickBranch
      }

    def filteredAndPruned: JsObject = ua.transform(chargeFilter("D")).flatMap(
      _.transform(chargeFilter("E")).flatMap(
        _.transform(chargeFilter("G")).flatMap(
          _.transform(pruneEmptyCharges("D")).flatMap(
            _.transform(pruneEmptyCharges("E")).flatMap(
              _.transform(pruneEmptyCharges("G"))))))).get

    val noMatchBoolean: String => Boolean = chargeType => (filteredAndPruned \ s"charge${chargeType}NoMatch").asOpt[Boolean].getOrElse(false)
    if(Seq(noMatchBoolean("D"), noMatchBoolean("E"), noMatchBoolean("G")).contains(false)) Some(filteredAndPruned) else None
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
