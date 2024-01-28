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

import helpers.FormatHelper
import models.AmendedChargeStatus.{Unknown, amendedChargeStatus}
import models.ChargeType.ChargeTypeLifetimeAllowance
import models.LocalDateBinder._
import models.viewModels.ViewAmendmentDetails
import models.{AccessType, Member, MemberDetails, UserAnswers}
import pages.chargeD.{ChargeDetailsPage, MemberAFTVersionPage, MemberStatusPage}
import play.api.i18n.Messages
import play.api.mvc.Call
import services.AddMembersService.mapChargeXMembersToTable
import viewmodels.Table

import java.time.LocalDate

class ChargeDService {

  def getAllLifetimeAllowanceAmendments(ua: UserAnswers, currentVersion: Int): Seq[ViewAmendmentDetails] = {
    ua.getAllMembersInCharge[MemberDetails]("chargeDDetails")
      .zipWithIndex
      .flatMap { memberDetails =>
        val (member, index) = memberDetails
        ua.get(ChargeDetailsPage(index)).map { chargeDetails =>
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

  def viewUrl(index: Int, srn: String, startDate: LocalDate, accessType: AccessType, version: Int): Call =
    controllers.chargeD.routes.CheckYourAnswersController.onPageLoad(srn, startDate, accessType, version, index)

  def mapToTable(members: Seq[Member], canChange: Boolean)(implicit messages: Messages): Table =
    mapChargeXMembersToTable("chargeD", members, canChange)

}
