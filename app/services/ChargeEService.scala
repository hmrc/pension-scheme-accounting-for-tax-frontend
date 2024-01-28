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
import models.ChargeType.ChargeTypeAnnualAllowance
import models.viewModels.ViewAmendmentDetails
import models.{MemberDetails, UserAnswers}
import pages.chargeE.{ChargeDetailsPage, MemberAFTVersionPage, MemberStatusPage}
import play.api.libs.json.Reads._

class ChargeEService {

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

}
