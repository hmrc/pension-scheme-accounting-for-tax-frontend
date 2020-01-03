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

import models.{Member, MemberDetails, UserAnswers}
import pages.chargeD.ChargeDetailsPage
import play.api.i18n.Messages
import play.api.mvc.Call
import uk.gov.hmrc.viewmodels.Html
import uk.gov.hmrc.viewmodels.Text.Literal
import utils.CheckYourAnswersHelper.formatBigDecimalAsString
import viewmodels.Table
import viewmodels.Table.Cell
import AddMembersService.mapChargeXMembersToTable

object ChargeDService {

  def getLifetimeAllowanceMembersIncludingDeleted(ua: UserAnswers, srn: String): Seq[Member] = {

    val members = for {
        (member, index) <- ua.getAllMembersInCharge[MemberDetails]("chargeDDetails").zipWithIndex
      } yield {
        ua.get(ChargeDetailsPage(index)).map { chargeDetails =>
          Member(
            index,
            member.fullName,
            member.nino,
            chargeDetails.total,
            viewUrl(index, srn).url,
            removeUrl(index, srn).url,
            member.isDeleted
          )
        }
      }

    members.flatten
  }

  def getLifetimeAllowanceMembers(ua: UserAnswers, srn: String): Seq[Member] =
    getLifetimeAllowanceMembersIncludingDeleted(ua, srn).filterNot(_.isDeleted)

  def viewUrl(index: Int, srn: String): Call = controllers.chargeD.routes.CheckYourAnswersController.onPageLoad(srn, index)
  def removeUrl(index: Int, srn: String): Call = controllers.chargeD.routes.DeleteMemberController.onPageLoad(srn, index)

  def mapToTable(members: Seq[Member])(implicit messages: Messages): Table =
    mapChargeXMembersToTable("chargeD", members)

}
