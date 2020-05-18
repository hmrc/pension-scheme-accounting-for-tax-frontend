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

import helpers.AddMembersHelper.mapChargeXMembersToTable
import models.AmendedChargeStatus.{Unknown, amendedChargeStatus}
import models.ChargeType.ChargeTypeLifetimeAllowance
import models.LocalDateBinder._
import models.requests.DataRequest
import models.viewModels.ViewAmendmentDetails
import models.{Member, MemberDetails, UserAnswers}
import pages.chargeD.{ChargeDetailsPage, MemberAFTVersionPage, MemberStatusPage}
import play.api.i18n.Messages
import play.api.mvc.{AnyContent, Call}
import viewmodels.Table

object ChargeDHelper {

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
          removeUrl(index, srn, startDate).url,
          member.isDeleted
        )
      }
    }

    members.flatten
  }

  def getAllLifetimeAllowanceAmendments(ua: UserAnswers)(implicit request: DataRequest[AnyContent]): Seq[ViewAmendmentDetails] = {
    ua.getAllMembersInCharge[MemberDetails]("chargeDDetails")
      .zipWithIndex
      .flatMap { memberDetails =>
        val (member, index) = memberDetails
        ua.get(ChargeDetailsPage(index)).map { chargeDetails =>
          val currentVersion = request.aftVersion
          val memberVersion = ua.get(MemberAFTVersionPage(index)).getOrElse(0)

          if (memberVersion == currentVersion) {
            Some(
              ViewAmendmentDetails(
                member.fullName,
                ChargeTypeLifetimeAllowance.toString,
                FormatHelper.formatCurrencyAmountAsString(chargeDetails.total),
                ua.get(MemberStatusPage(index)).map(amendedChargeStatus).getOrElse(Unknown)
              )
            )
          } else {
            None
          }
        }
      }
      .flatten
  }

  def getLifetimeAllowanceMembers(ua: UserAnswers, srn: String, startDate: LocalDate): Seq[Member] =
    getLifetimeAllowanceMembersIncludingDeleted(ua, srn, startDate).filterNot(_.isDeleted)

  def viewUrl(index: Int, srn: String, startDate: LocalDate): Call =
    controllers.chargeD.routes.CheckYourAnswersController.onPageLoad(srn, startDate, index)
  def removeUrl(index: Int, srn: String, startDate: LocalDate): Call =
    controllers.chargeD.routes.DeleteMemberController.onPageLoad(srn, startDate, index)

  def mapToTable(members: Seq[Member], canChange: Boolean)(implicit messages: Messages): Table =
    mapChargeXMembersToTable("chargeD", members, canChange)

}
