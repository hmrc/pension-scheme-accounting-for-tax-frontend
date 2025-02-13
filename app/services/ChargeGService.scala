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
import models.ChargeType.ChargeTypeOverseasTransfer
import models.LocalDateBinder._
import models.chargeG.{MemberDetails => ChargeGMemberDetails}
import models.viewModels.ViewAmendmentDetails
import models.{AccessType, Member, UserAnswers}
import pages.chargeG.{ChargeAmountsPage, MemberAFTVersionPage, MemberStatusPage}
import play.api.i18n.Messages
import play.api.mvc.Call
import services.AddMembersService.mapChargeXMembersToTable
import uk.gov.hmrc.govukfrontend.views.Aliases.Table

import java.time.LocalDate

class ChargeGService {

  def getAllOverseasTransferAmendments(ua: UserAnswers, currentVersion: Int): Seq[ViewAmendmentDetails] = {
    ua.getAllMembersInCharge[ChargeGMemberDetails](charge = "chargeGDetails")
      .zipWithIndex
      .flatMap { memberDetails =>
        val (member, index) = memberDetails
        ua.get(ChargeAmountsPage(index)).map { chargeAmounts =>
          val memberVersion = ua.get(MemberAFTVersionPage(index)).getOrElse(0)

          if (memberVersion == currentVersion) {
            Some(
              ViewAmendmentDetails(
                member.fullName,
                ChargeTypeOverseasTransfer.toString,
                FormatHelper.formatCurrencyAmountAsString(chargeAmounts.amountTaxDue),
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
    controllers.chargeG.routes.CheckYourAnswersController.onPageLoad(srn, startDate, accessType, version, index)

  def mapToTable(members: Seq[Member], canChange: Boolean)(implicit messages: Messages): Table =
    mapChargeXMembersToTable("chargeG", members, canChange)

}
