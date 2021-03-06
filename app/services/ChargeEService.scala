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
import helpers.{DeleteChargeHelper, FormatHelper}
import models.AmendedChargeStatus.{amendedChargeStatus, Unknown}
import models.ChargeType.ChargeTypeAnnualAllowance
import models.LocalDateBinder._
import models.requests.DataRequest
import models.viewModels.ViewAmendmentDetails
import models.{Member, AccessType, UserAnswers, MemberDetails}
import pages.chargeE.{ChargeDetailsPage, MemberAFTVersionPage, MemberStatusPage}
import play.api.i18n.Messages
import play.api.mvc.{Call, AnyContent}
import services.AddMembersService.mapChargeXMembersToTable
import viewmodels.Table

class ChargeEService @Inject()(deleteChargeHelper: DeleteChargeHelper) {


  def getAnnualAllowanceMembers(ua: UserAnswers, srn: String, startDate: LocalDate, accessType: AccessType, version: Int)
                                 (implicit request: DataRequest[AnyContent]): Seq[Member] = {
    ua.getAllMembersInCharge[MemberDetails](charge = "chargeEDetails").zipWithIndex.flatMap { case (member, index) =>
      ua.get(MemberStatusPage(index)) match {
        case Some(status) if status == "Deleted" => Nil
        case _ =>
          ua.get(ChargeDetailsPage(index)).map { chargeDetails =>
            Member(
              index,
              member.fullName,
              member.nino,
              chargeDetails.chargeAmount,
              viewUrl(index, srn, startDate, accessType, version).url,
              removeUrl(index, srn, startDate, ua, accessType, version).url
            )
          }.toSeq
      }
    }
  }

  def getAllAnnualAllowanceAmendments(ua: UserAnswers, currentVersion: Int): Seq[ViewAmendmentDetails] = {
    ua.getAllMembersInCharge[MemberDetails]("chargeEDetails")
      .zipWithIndex
      .flatMap { memberDetails =>
        val (member, index) = memberDetails
        ua.get(ChargeDetailsPage(index)).map { chargeDetails =>
          val memberVersion = ua.get(MemberAFTVersionPage(index)).getOrElse(0)

          if (memberVersion == currentVersion) {
            Some(
              ViewAmendmentDetails(
                member.fullName,
                ChargeTypeAnnualAllowance.toString,
                FormatHelper.formatCurrencyAmountAsString(chargeDetails.chargeAmount),
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
    controllers.chargeE.routes.CheckYourAnswersController.onPageLoad(srn, startDate, accessType, version, index)

  private def removeUrl(index: Int, srn: String, startDate: LocalDate, ua: UserAnswers,
                        accessType: AccessType, version: Int)(implicit request: DataRequest[AnyContent]): Call =
    if(request.isAmendment && deleteChargeHelper.isLastCharge(ua)) {
      controllers.chargeE.routes.RemoveLastChargeController.onPageLoad(srn, startDate, accessType, version, index)
    } else {
      controllers.chargeE.routes.DeleteMemberController.onPageLoad(srn, startDate, accessType, version, index)
    }

  def mapToTable(members: Seq[Member], canChange: Boolean)(implicit messages: Messages): Table =
    mapChargeXMembersToTable("chargeE", members, canChange)

}
