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

import base.SpecBase
import data.SampleData
import models.chargeG.MemberDetails
import models.Member
import models.UserAnswers
import pages.chargeG.ChargeAmountsPage
import pages.chargeG.MemberDetailsPage
import utils.AFTConstants.QUARTER_START_DATE
import models.LocalDateBinder._

class ChargeGServiceSpec extends SpecBase {

  val srn = "S1234567"
  val startDate: LocalDate = QUARTER_START_DATE

  val allMembers: UserAnswers = UserAnswers()
    .set(MemberDetailsPage(0), SampleData.memberGDetails)
    .toOption
    .get
    .set(ChargeAmountsPage(0), SampleData.chargeAmounts)
    .toOption
    .get
    .set(MemberDetailsPage(1), SampleData.memberGDetails2)
    .toOption
    .get
    .set(ChargeAmountsPage(1), SampleData.chargeAmounts2)
    .toOption
    .get

  val allMembersIncludingDeleted: UserAnswers = allMembers
    .set(MemberDetailsPage(2), SampleData.memberGDetailsDeleted)
    .toOption
    .get
    .set(ChargeAmountsPage(2), SampleData.chargeAmounts)
    .toOption
    .get

  def viewLink(index: Int): String = controllers.chargeG.routes.CheckYourAnswersController.onPageLoad(srn, startDate, index).url
  def removeLink(index: Int): String = controllers.chargeG.routes.DeleteMemberController.onPageLoad(srn, startDate, index).url

  def expectedMember(memberDetails: MemberDetails, index: Int) =
    Member(index,
           memberDetails.fullName,
           memberDetails.nino,
           SampleData.chargeAmount2,
           viewLink(index),
           removeLink(index),
           memberDetails.isDeleted)

  def expectedAllMembers: Seq[Member] = Seq(expectedMember(SampleData.memberGDetails, 0), expectedMember(SampleData.memberGDetails2, 1))

  def expectedMembersIncludingDeleted: Seq[Member] = expectedAllMembers ++ Seq(
    expectedMember(SampleData.memberGDetailsDeleted, 2)
  )

  ".getOverseasTransferMembers" must {
    "return all the members added in charge G" in {
      ChargeGService.getOverseasTransferMembers(allMembers, srn, startDate) mustBe expectedAllMembers
    }
  }

  ".getOverseasTransferMembersIncludingDeleted" must {
    "return all the members added in charge G" in {
      ChargeGService.getOverseasTransferMembersIncludingDeleted(allMembersIncludingDeleted, srn, startDate) mustBe expectedMembersIncludingDeleted
    }
  }

}
