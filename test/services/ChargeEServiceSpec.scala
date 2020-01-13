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

import base.SpecBase
import data.SampleData
import models.{Member, MemberDetails, NormalMode, UserAnswers}
import pages.chargeE.{ChargeDetailsPage, MemberDetailsPage}

class ChargeEServiceSpec extends SpecBase {

  val srn = "S1234567"

  val allMembers: UserAnswers = UserAnswers().set(MemberDetailsPage(0), SampleData.memberDetails).toOption.get
    .set(ChargeDetailsPage(0), SampleData.chargeEDetails).toOption.get
    .set(MemberDetailsPage(1), SampleData.memberDetails2).toOption.get
    .set(ChargeDetailsPage(1), SampleData.chargeEDetails).toOption.get

  val allMembersIncludingDeleted: UserAnswers = allMembers
    .set(MemberDetailsPage(2), SampleData.memberDetailsDeleted).toOption.get
    .set(ChargeDetailsPage(2), SampleData.chargeEDetails).toOption.get

  def viewLink(index: Int): String = controllers.chargeE.routes.CheckYourAnswersController.onPageLoad(srn, index).url
  def removeLink(index: Int): String = controllers.chargeE.routes.DeleteMemberController.onPageLoad(srn, index).url
  def expectedMember(memberDetails: MemberDetails, index: Int) =
    Member(index, memberDetails.fullName, memberDetails.nino, SampleData.chargeAmount1, viewLink(index), removeLink(index), memberDetails.isDeleted)

  def expectedAllMembers: Seq[Member] = Seq(
    expectedMember(SampleData.memberDetails, 0),
    expectedMember(SampleData.memberDetails2, 1))

  def expectedMembersIncludingDeleted: Seq[Member] = expectedAllMembers ++ Seq(
    expectedMember(SampleData.memberDetailsDeleted, 2)
  )

  ".getAnnualAllowanceMembers" must {
    "return all the members added in charge E" in {
      ChargeEService.getAnnualAllowanceMembers(allMembers, srn) mustBe expectedAllMembers
    }
  }

  ".getAnnualAllowanceMembersIncludingDeleted" must {
    "return all the members added in charge E" in {
      ChargeEService.getAnnualAllowanceMembersIncludingDeleted(allMembersIncludingDeleted, srn) mustBe expectedMembersIncludingDeleted
    }
  }

}
