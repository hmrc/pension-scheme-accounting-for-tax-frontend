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

import base.SpecBase
import data.SampleData
import data.SampleData.{versionInt, accessType}
import helpers.{DeleteChargeHelper, FormatHelper}
import models.AmendedChargeStatus.{Updated, Deleted}
import models.ChargeType.ChargeTypeLifetimeAllowance
import models.LocalDateBinder._
import models.viewModels.ViewAmendmentDetails
import models.{Member, AmendedChargeStatus, UserAnswers, MemberDetails}
import org.mockito.Matchers.any
import org.mockito.Mockito.{reset, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.mockito.MockitoSugar
import pages.chargeD.{ChargeDetailsPage, MemberAFTVersionPage, MemberDetailsPage, MemberStatusPage}
import utils.AFTConstants.QUARTER_START_DATE
class ChargeDServiceSpec extends SpecBase with MockitoSugar with BeforeAndAfterEach {

  val srn = "S1234567"
  val startDate: LocalDate = QUARTER_START_DATE

  val allMembers: UserAnswers = UserAnswers()
    .set(MemberStatusPage(0), AmendedChargeStatus.Deleted.toString).toOption.get
    .set(MemberAFTVersionPage(0), SampleData.version.toInt).toOption.get
    .set(MemberDetailsPage(0), SampleData.memberDetails).toOption.get
    .set(ChargeDetailsPage(0), SampleData.chargeDDetails).toOption.get
    .set(MemberStatusPage(1), AmendedChargeStatus.Updated.toString).toOption.get
    .set(MemberAFTVersionPage(1), SampleData.version.toInt).toOption.get
    .set(MemberDetailsPage(1), SampleData.memberDetails2).toOption.get
    .set(ChargeDetailsPage(1), SampleData.chargeDDetails).toOption.get

  val allMembersIncludingDeleted: UserAnswers = allMembers
    .set(MemberDetailsPage(2), SampleData.memberDetails).toOption.get
    .set(ChargeDetailsPage(2), SampleData.chargeDDetails).toOption.get

  def viewLink(index: Int): String = controllers.chargeD.routes.CheckYourAnswersController.onPageLoad(srn, startDate, accessType, versionInt, index).url
  def removeLink(index: Int): String = controllers.chargeD.routes.DeleteMemberController.onPageLoad(srn, startDate, accessType, versionInt, index).url
  def expectedMember(memberDetails: MemberDetails, index: Int): Member =
    Member(index, memberDetails.fullName, memberDetails.nino, SampleData.chargeAmount1 + SampleData.chargeAmount2, viewLink(index), removeLink(index))

  def expectedAllMembersMinusDeleted: Seq[Member] = Seq(
    expectedMember(SampleData.memberDetails2, 1))

  val mockDeleteChargeHelper: DeleteChargeHelper = mock[DeleteChargeHelper]
  val chargeDHelper: ChargeDService = new ChargeDService(mockDeleteChargeHelper)

  override def beforeEach: Unit = {
    reset(mockDeleteChargeHelper)
    when(mockDeleteChargeHelper.isLastCharge(any())).thenReturn(false)
  }

  ".getAnnualAllowanceMembers" must {
    "return all the members added in charge E" in {
      chargeDHelper.getLifetimeAllowanceMembers(allMembers, srn, startDate, accessType, versionInt)(request()) mustBe expectedAllMembersMinusDeleted
    }
  }

  "getAllLifetimeAllowanceAmendments" must {

    "return all the amendments for lifetime allowance charge" in {
      val expectedRows = Seq(
        ViewAmendmentDetails(
          SampleData.memberDetails.fullName, ChargeTypeLifetimeAllowance.toString,
          FormatHelper.formatCurrencyAmountAsString(SampleData.chargeDDetails.total),
          Deleted
        ),
        ViewAmendmentDetails(
          SampleData.memberDetails2.fullName, ChargeTypeLifetimeAllowance.toString,
          FormatHelper.formatCurrencyAmountAsString(SampleData.chargeDDetails.total),
          Updated
        )
      )
      chargeDHelper.getAllLifetimeAllowanceAmendments(allMembers, versionInt) mustBe expectedRows
    }
  }

}
