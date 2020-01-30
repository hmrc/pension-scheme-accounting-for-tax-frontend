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

import models.{Member, MemberDetails, NormalMode, UserAnswers}
import pages.chargeE.ChargeDetailsPage
import play.api.i18n.Messages
import play.api.mvc.Call
import services.AddMembersService.mapChargeXMembersToTable
import viewmodels.Table

object ChargeEService {

  def getAnnualAllowanceMembersIncludingDeleted(ua: UserAnswers, srn: String): Seq[Member] = {

    val members = for {
        (member, index) <- ua.getAllMembersInCharge[MemberDetails]("chargeEDetails").zipWithIndex
      } yield {
        ua.get(ChargeDetailsPage(index)).map { chargeDetails =>
          Member(
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

  def getAnnualAllowanceMembers(ua: UserAnswers, srn: String): Seq[Member] =
    getAnnualAllowanceMembersIncludingDeleted(ua, srn).filterNot(_.isDeleted)

  def viewUrl(index: Int, srn: String): Call = controllers.chargeE.routes.CheckYourAnswersController.onPageLoad(srn, index)
  def removeUrl(index: Int, srn: String): Call = controllers.chargeE.routes.DeleteMemberController.onPageLoad(srn, index)

  def mapToTable(members: Seq[Member], canChange: Boolean)(implicit messages: Messages): Table =
    mapChargeXMembersToTable("chargeE", members, canChange)

}
