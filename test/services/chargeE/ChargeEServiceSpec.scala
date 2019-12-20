/*
 * Copyright 2019 HM Revenue & Customs
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

package services.chargeE

import base.SpecBase
import data.SampleData
import models.{MemberDetails, NormalMode, UserAnswers}
import models.chargeE.AnnualAllowanceMember
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
  def removeLink(index: Int): String = controllers.chargeE.routes.DeleteMemberController.onPageLoad(NormalMode, srn, index).url
  def expectedMember(memberDetails: MemberDetails, index: Int) =
    AnnualAllowanceMember(index, memberDetails.fullName, memberDetails.nino, SampleData.chargeAmount1, viewLink(index), removeLink(index), memberDetails.isDeleted)

  def expectedAllMembers: Seq[AnnualAllowanceMember] = Seq(
    expectedMember(SampleData.memberDetails, 0),
    expectedMember(SampleData.memberDetails2, 1))

  def expectedMembersIncludingDeleted: Seq[AnnualAllowanceMember] = expectedAllMembers ++ Seq(
    expectedMember(SampleData.memberDetailsDeleted, 2)
  )

  ".getAnnualAllowanceMembers" must {
    "return all the members added in charge E" in {
      ChargeEService.getAnnualAllowanceMembersIncludingDeleted(allMembers, srn) mustBe expectedAllMembers
    }
  }

  ".getAnnualAllowanceMembersIncludingDeleted" must {
    "return all the members added in charge E" in {
      ChargeEService.getAnnualAllowanceMembersIncludingDeleted(allMembersIncludingDeleted, srn) mustBe expectedMembersIncludingDeleted
    }
  }

}
