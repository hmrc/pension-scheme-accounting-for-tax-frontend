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

package helpers

import java.time.LocalDate

import models.{Member, MemberDetails, UserAnswers}
import pages.chargeD.ChargeDetailsPage
import play.api.i18n.Messages
import play.api.mvc.Call
import AddMembersHelper.mapChargeXMembersToTable
import com.google.inject.Inject
import viewmodels.Table
import models.LocalDateBinder._
import utils.DeleteChargeHelper

class ChargeDHelper @Inject()(deleteChargeHelper: DeleteChargeHelper) {

  def getLifetimeAllowanceMembersIncludingDeleted(ua: UserAnswers, srn: String, startDate: LocalDate): Seq[Member] = {

    val members = for {
        (member, index) <- ua.getAllMembersInCharge[MemberDetails]("chargeDDetails").zipWithIndex
      } yield {
        ua.get(ChargeDetailsPage(index)).map { chargeDetails =>
          Member(
            index,
            member.fullName,
            member.nino,
            chargeDetails.total,
            viewUrl(index, srn, startDate).url,
            removeUrl(index, srn, startDate, ua).url,
            member.isDeleted
          )
        }
      }

    members.flatten
  }

  def getLifetimeAllowanceMembers(ua: UserAnswers, srn: String, startDate: LocalDate): Seq[Member] =
    getLifetimeAllowanceMembersIncludingDeleted(ua, srn, startDate).filterNot(_.isDeleted)

  def removeUrl(index: Int, srn: String, startDate: LocalDate, ua: UserAnswers): Call =
    if(deleteChargeHelper.isLastCharge(ua)) {
      controllers.chargeD.routes.RemoveLastChargeController.onPageLoad(srn, startDate, index)
    } else {
      controllers.chargeD.routes.DeleteMemberController.onPageLoad(srn, startDate, index)
    }

  def viewUrl(index: Int, srn: String, startDate: LocalDate): Call = controllers.chargeD.routes.CheckYourAnswersController.onPageLoad(srn, startDate, index)

  def mapToTable(members: Seq[Member], canChange:Boolean)(implicit messages: Messages): Table =
    mapChargeXMembersToTable("chargeD", members, canChange)

}
