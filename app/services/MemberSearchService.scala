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

import com.google.inject.Inject
import helpers.{DeleteChargeHelper, FormatHelper}
import models.ChargeType.{ChargeTypeAnnualAllowance, ChargeTypeLifetimeAllowance, ChargeTypeOverseasTransfer}
import models.LocalDateBinder._
import models.requests.DataRequest
import models.{AccessType, ChargeType, Member, MemberDetails, UserAnswers}
import pages.{chargeD, chargeE, chargeG}
import play.api.i18n.Messages
import play.api.libs.functional.syntax._
import play.api.libs.json.Reads.JsObjectReducer
import play.api.libs.json._
import play.api.mvc.AnyContent
import uk.gov.hmrc.govukfrontend.views.viewmodels.summarylist.{ActionItem, Actions, Key, SummaryListRow, Value}
import uk.gov.hmrc.govukfrontend.views.viewmodels.content.Text

import java.time.LocalDate
import javax.inject.Singleton

@Singleton
class MemberSearchService @Inject()(
                                     fuzzyMatchingService: FuzzyMatchingService,
                                     deleteChargeHelper: DeleteChargeHelper
                                   ) {

  import MemberSearchService._

  def search(ua: UserAnswers, srn: String, startDate: LocalDate, searchText: String, accessType: AccessType, version: Int)
            (implicit request: DataRequest[AnyContent], messages: Messages): Seq[MemberRow] = {

    val upperSearchText = searchText.toUpperCase
    // Step 1: Iterate Over Charge Types and Aggregate Results
    val aggregatedSearchResults = ChargeType.chargeTypeValues.foldLeft(Json.obj()) { (acc, chargeType) =>
      ua.data \ chargeType \ "members" match {
        case JsDefined(membersArray: JsArray) =>
          // Step 2: Filter Members Array to only include those with memberFormCompleted is not defined or memberFormCompleted = true
          val filteredMembers = membersArray.value.filter { member =>
            (member \ "memberFormCompleted").asOpt[Boolean].getOrElse(true)
          }
          // Step 3: Perform Search on Updated Members JSON and ensure it's a JsObject before merging
          val searchResult = jsonSearch(upperSearchText, Json.obj(chargeType -> Json.obj("members" -> JsArray(filteredMembers))))
          acc ++ searchResult.getOrElse(Json.obj()).as[JsObject]
        case _ => acc
      }
    }
    // Step 4: Create MemberRow Instances from Aggregated Results
    if (aggregatedSearchResults == Json.obj()) Nil
    else listOfRows(listOfMembers(UserAnswers(aggregatedSearchResults), srn, startDate, accessType, version, ua), request.isViewOnly)
  }

  private def listOfRows(listOfMembers: Seq[MemberSummary], isViewOnly: Boolean)(implicit messages: Messages) = {
    val allRows = listOfMembers.map { data =>
      val rowNino =
        Seq(
          SummaryListRow(
            key = Key(Text(messages("memberDetails.nino")), classes = "govuk-!-width-one-half"),
            value = Value(Text(s"${data.nino}"), classes = "govuk-!-width-one-half")
          ))

      val rowChargeType =
        Seq(
          SummaryListRow(
            key = Key(Text(messages("aft.summary.search.chargeType")), classes = "govuk-!-width-one-half"),
            value = Value(Text(messages(s"${getDescriptionMessageKeyFromChargeType(data.chargeType)}")), classes = "govuk-!-width-one-half")
          ))
      val rowAmount =
        Seq(
          SummaryListRow(
            key = Key(Text(messages("aft.summary.search.amount")), classes = "govuk-!-width-one-half"),
            value = Value(Text(messages(s"${FormatHelper.formatCurrencyAmountAsString(data.amount)}")), classes = "govuk-!-width-one-half")
          ))

      val removeAction = if (isViewOnly) {
        Nil
      } else {
        List(
          Actions(
            items = Seq(ActionItem(
              content = Text(messages("site.remove")),
              href = data.removeLink,
              visuallyHiddenText = None
            ))
          )
        )
      }

      val actions = List(
        Actions(
          items = Seq(ActionItem(
            content = Text(messages("site.view")),
            href = data.viewLink,
            visuallyHiddenText = None
          ))
        )
      ) ++ removeAction


      MemberRow(data.name, rowNino ++ rowChargeType ++ rowAmount, actions)
    }
    allRows.sortBy(_.name)
  }

  val jsonSearch: (String, JsValue) => Option[JsValue] = (searchString, ua) => {

    val conditionalFilter: JsValue => Boolean = jsValue => if(searchString.matches(ninoRegex)) {
      (jsValue \ "memberDetails" \ "nino").as[String] == searchString
    } else {
      val memberName = s"${(jsValue \ "memberDetails" \ "firstName").as[String]} ${(jsValue \ "memberDetails" \ "lastName").as[String]}"
      fuzzyMatchingService.doFuzzyMatching(searchString, memberName)
    }

    val chargeFilter: String => Reads[JsObject] = chargeType =>
      (__ \ s"charge${chargeType}Details").readNullable[JsObject].flatMap {
        case Some(_) => (__ \ s"charge${chargeType}Details" \ "members").json.update(__.read[JsArray].map { jsArray =>
          JsArray(
            jsArray.value.zipWithIndex.map { case (x, i) =>
              x.as[JsObject] + ("idx" -> JsNumber(i))
            }.filter(conditionalFilter))
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

    val filteredAndPruned: JsObject = ua.transform(chargeFilter("D")).flatMap(
      _.transform(chargeFilter("E")).flatMap(
        _.transform(chargeFilter("G")).flatMap(
          _.transform(pruneEmptyCharges("D")).flatMap(
            _.transform(pruneEmptyCharges("E")).flatMap(
              _.transform(pruneEmptyCharges("G"))))))).get

    val noMatchBoolean: String => Boolean = chargeType => (filteredAndPruned \ s"charge${chargeType}NoMatch").asOpt[Boolean].getOrElse(false)
    if(Seq(noMatchBoolean("D"), noMatchBoolean("E"), noMatchBoolean("G")).contains(false) && filteredAndPruned != Json.obj()) Some(filteredAndPruned) else None
  }

  private val chargeDMembers: (UserAnswers, Int => String, Int => String) => Seq[MemberSummary] = (searchResultsUa, viewLink,removeLink) =>
    searchResultsUa.getAllMembersInCharge[MemberDetails](charge = "chargeDDetails")
      .zipWithIndex.flatMap { case (member, index) =>
        searchResultsUa.get(chargeD.MemberStatusPage(index)) match {
          case Some(status) if status == "Deleted" => Nil
          case _ =>
            (searchResultsUa.get(chargeD.ChargeDetailsPage(index)), searchResultsUa.get(chargeD.SearchIndexPage(index))) match {
              case (Some(chargeDetails), Some(idx)) =>
                Seq(MemberSummary(
                  idx,
                  member.fullName,
                  member.nino,
                  ChargeTypeLifetimeAllowance,
                  chargeDetails.total,
                  viewLink(idx),
                  removeLink(idx)
                ))
              case _ => Nil
            }
        }
      }

  private val chargeEMembers: (UserAnswers, Int => String, Int => String) => Seq[MemberSummary] = (searchResultsUa, viewLink,removeLink) =>
    searchResultsUa.getAllMembersInCharge[MemberDetails](charge = "chargeEDetails").zipWithIndex.flatMap { case (member, index) =>
      searchResultsUa.get(chargeE.MemberStatusPage(index)) match {
        case Some(status) if status == "Deleted" => Nil
        case _ =>
          (searchResultsUa.get(chargeE.ChargeDetailsPage(index)), searchResultsUa.get(chargeE.SearchIndexPage(index))) match {
            case (Some(chargeDetails), Some(idx)) =>
              Seq(MemberSummary(
                idx,
                member.fullName,
                member.nino,
                ChargeTypeAnnualAllowance,
                chargeDetails.chargeAmount,
                viewLink(idx),
                removeLink(idx)
              ))
            case _ => Nil
          }
      }
    }

  private val chargeGMembers: (UserAnswers, Int => String, Int => String) => Seq[MemberSummary] = (searchResultsUa, viewLink,removeLink) =>
    searchResultsUa.getAllMembersInCharge[MemberDetails](charge = "chargeGDetails").zipWithIndex.flatMap { case (member, index) =>
      searchResultsUa.get(chargeG.MemberStatusPage(index)) match {
        case Some(status) if status == "Deleted" => Nil
        case _ =>
          (searchResultsUa.get(chargeG.ChargeAmountsPage(index)), searchResultsUa.get(chargeG.SearchIndexPage(index))) match {
            case (Some(chargeAmounts), Some(idx)) =>
              Seq(MemberSummary(
                idx,
                member.fullName,
                member.nino,
                ChargeTypeOverseasTransfer,
                chargeAmounts.amountTaxDue,
                viewLink(idx),
                removeLink(idx)
              ))
            case _ => Nil
          }
      }
    }

  private def listOfMembers(searchResultsUa: UserAnswers, srn: String, startDate: LocalDate, accessType: AccessType, version: Int, originalUa: UserAnswers)
                           (implicit request: DataRequest[AnyContent]): Seq[MemberSummary] = {

    val viewChargeDUrl: Int => String = idx => controllers.chargeD.routes.CheckYourAnswersController.onPageLoad(srn, startDate, accessType, version, idx).url
    val viewChargeEUrl: Int => String = idx => controllers.chargeE.routes.CheckYourAnswersController.onPageLoad(srn, startDate, accessType, version, idx).url
    val viewChargeGUrl: Int => String = idx => controllers.chargeG.routes.CheckYourAnswersController.onPageLoad(srn, startDate, accessType, version, idx).url

    val removeChargeDUrl: Int => String = idx =>
      if(request.isAmendment && deleteChargeHelper.isLastCharge(originalUa)) {
        controllers.chargeD.routes.RemoveLastChargeController.onPageLoad(srn, startDate, accessType, version, idx).url
      } else {
        controllers.chargeD.routes.DeleteMemberController.onPageLoad(srn, startDate, accessType, version, idx).url
      }

    val removeChargeEUrl: Int => String = idx =>
      if(request.isAmendment && deleteChargeHelper.isLastCharge(originalUa)) {
        controllers.chargeE.routes.RemoveLastChargeController.onPageLoad(srn, startDate, accessType, version, idx).url
      } else {
        controllers.chargeE.routes.DeleteMemberController.onPageLoad(srn, startDate, accessType, version, idx).url
      }

    val removeChargeGUrl: Int => String = idx =>
      if(request.isAmendment && deleteChargeHelper.isLastCharge(originalUa)) {
        controllers.chargeG.routes.RemoveLastChargeController.onPageLoad(srn, startDate, accessType, version, idx).url
      } else {
        controllers.chargeG.routes.DeleteMemberController.onPageLoad(srn, startDate, accessType, version, idx).url
      }

    chargeDMembers(searchResultsUa, viewChargeDUrl, removeChargeDUrl) ++
      chargeEMembers(searchResultsUa, viewChargeEUrl, removeChargeEUrl) ++
      chargeGMembers(searchResultsUa, viewChargeGUrl, removeChargeGUrl)
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

  case class MemberRow(name: String, rows: Seq[SummaryListRow], actions: Seq[Actions])

  private case class MemberSummary(index: Int,
                                   name: String,
                                   nino: String,
                                   chargeType: ChargeType,
                                   amount: BigDecimal,
                                   viewLink: String,
                                   removeLink: String) {
    def id = s"member-$index"
  }

  object MemberRow {

    implicit def writes: Writes[MemberRow] =
      ((JsPath \ "name").write[String] and
        (JsPath \ "rows").write[Seq[SummaryListRow]] and
        (JsPath \ "actions").write[Seq[Actions]]) (mr => Tuple3(mr.name, mr.rows, mr.actions))
  }

  private object MemberSummary {

    def apply(member: Member, chargeType: ChargeType): MemberSummary =
      MemberSummary(member.index, member.name, member.nino, chargeType, member.amount, member.viewLink, member.removeLink)
  }

}
