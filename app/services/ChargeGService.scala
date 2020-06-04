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
import AddMembersService.mapChargeXMembersToTable
import helpers.{DeleteChargeHelper, FormatHelper}
import models.AmendedChargeStatus.{Unknown, amendedChargeStatus}
import models.ChargeType.ChargeTypeOverseasTransfer
import models.LocalDateBinder._
import models.chargeG.{MemberDetails => ChargeGMemberDetails}
import models.requests.DataRequest
import models.viewModels.ViewAmendmentDetails
import models.{AccessType, Member, MemberDetails, UserAnswers}
import pages.chargeG.{ChargeAmountsPage, MemberAFTVersionPage, MemberStatusPage}
import play.api.i18n.Messages
import play.api.mvc.{AnyContent, Call}
import viewmodels.Table

class ChargeGService @Inject()(deleteChargeHelper: DeleteChargeHelper) {

  def getOverseasTransferMembers(ua: UserAnswers, srn: String, startDate: LocalDate, accessType: AccessType, version: Int)
                               (implicit request: DataRequest[AnyContent]): Seq[Member] = {
    ua.getAllMembersInCharge[MemberDetails](charge = "chargeGDetails").zipWithIndex.flatMap { case (member, index) =>
      ua.get(MemberStatusPage(index)) match {
        case Some(status) if status == "Deleted" => Nil
        case _ =>
          ua.get(ChargeAmountsPage(index)).map { chargeAmounts =>
            Member(
              index,
              member.fullName,
              member.nino,
              chargeAmounts.amountTaxDue,
              viewUrl(index, srn, startDate, accessType, version).url,
              removeUrl(index, srn, startDate, ua, accessType, version).url
            )
          }.toSeq
      }
    }
  }

  def getAllOverseasTransferAmendments(ua: UserAnswers)(implicit request: DataRequest[AnyContent]): Seq[ViewAmendmentDetails] = {
    ua.getAllMembersInCharge[ChargeGMemberDetails](charge = "chargeGDetails")
      .zipWithIndex
      .flatMap { memberDetails =>
        val (member, index) = memberDetails
        ua.get(ChargeAmountsPage(index)).map { chargeAmounts =>
          val currentVersion = request.aftVersion
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

  private def removeUrl(index: Int, srn: String, startDate: LocalDate, ua: UserAnswers,
                        accessType: AccessType, version: Int)(implicit request: DataRequest[AnyContent]): Call =
    if(request.isAmendment && deleteChargeHelper.isLastCharge(ua)) {
      controllers.chargeG.routes.RemoveLastChargeController.onPageLoad(srn, startDate, accessType, version, index)
    } else {
      controllers.chargeG.routes.DeleteMemberController.onPageLoad(srn, startDate, accessType, version, index)
    }

  def mapToTable(members: Seq[Member], canChange: Boolean)(implicit messages: Messages): Table =
    mapChargeXMembersToTable("chargeG", members, canChange)

}
