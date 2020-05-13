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
import config.FrontendAppConfig
import connectors.cache.UserAnswersCacheConnector
import connectors.AFTConnector
import helpers.ChargeCHelper
import helpers.ChargeDHelper
import helpers.ChargeEHelper
import helpers.ChargeGHelper
import helpers.FormatHelper
import javax.inject.Singleton
import models.ChargeType
import models.Member
import models.UserAnswers
import pages.chargeC.ChargeCDetailsPage
import play.api.i18n.Messages
import play.api.libs.json._
import uk.gov.hmrc.viewmodels.SummaryList.Action
import uk.gov.hmrc.viewmodels.SummaryList.Key
import uk.gov.hmrc.viewmodels.SummaryList.Row
import uk.gov.hmrc.viewmodels.SummaryList.Value
import uk.gov.hmrc.viewmodels.Text.Literal
import utils.AFTSummaryHelper
import config.FrontendAppConfig
import play.api.data.Form
import play.api.i18n.Messages
import uk.gov.hmrc.viewmodels.Text.Literal
import uk.gov.hmrc.viewmodels.{Text, _}
import utils.DateHelper._
import viewmodels.Radios.Radio
import viewmodels.{Hint, LabelClasses, Radios}

import scala.language.implicitConversions

@Singleton
class MemberSearchService @Inject()(
    aftConnector: AFTConnector,
    userAnswersCacheConnector: UserAnswersCacheConnector,
    config: FrontendAppConfig,
    aftSummaryHelper: AFTSummaryHelper
) {
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

  def search(ua: UserAnswers, srn: String, startDate: LocalDate, searchText:String)(implicit messages: Messages):Seq[Row] = {
    listOfRows(listOfMembers(ua, srn, startDate))
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

  def listOfRows(listOfMembers:Seq[MemberSummary]): Seq[Row] = {
    listOfMembers.map { data =>
      Row(
        key = Key(msg"aft.summary.${data.chargeType.toString}.row", classes = Seq("govuk-!-width-three-quarters")),
        value = Value(Literal(s"${FormatHelper.formatCurrencyAmountAsString(data.amount)}"), classes = Seq("govuk-!-width-one-quarter", "govuk-table__cell--numeric")),
        actions = if (data.amount > BigDecimal(0)) {
          List(
            Action(
              content = msg"site.view",
              href = data.viewLink,
              visuallyHiddenText = Some(msg"aft.summary.${data.chargeType.toString}.visuallyHidden.row")
            )
          )
        } else {
          Nil
        }
      )
    }
  }

}
