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

import base.SpecBase
import data.SampleData
import models.AmendedChargeStatus.{Added, Deleted}
import models.ChargeType.ChargeTypeAnnualAllowance
import models.LocalDateBinder._
import models.requests.DataRequest
import models.viewModels.ViewAmendmentDetails
import models.{AmendedChargeStatus, Member, MemberDetails, UserAnswers}
import org.mockito.Matchers.any
import org.mockito.Mockito.{reset, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.mockito.MockitoSugar
import pages.chargeE.{ChargeDetailsPage, MemberAFTVersionPage, MemberDetailsPage, MemberStatusPage}
import play.api.mvc.AnyContent
import uk.gov.hmrc.domain.PsaId
import utils.AFTConstants.QUARTER_START_DATE
import utils.DeleteChargeHelper

class ChargeEHelperSpec extends SpecBase with MockitoSugar with BeforeAndAfterEach {

  val srn = "S1234567"
  val startDate: LocalDate = QUARTER_START_DATE

  val allMembers: UserAnswers = UserAnswers()
    .set(MemberStatusPage(0), AmendedChargeStatus.Added.toString).toOption.get
    .set(MemberAFTVersionPage(0), SampleData.version.toInt).toOption.get
    .set(MemberDetailsPage(0), SampleData.memberDetails).toOption.get
    .set(ChargeDetailsPage(0), SampleData.chargeEDetails).toOption.get
    .set(MemberStatusPage(1), AmendedChargeStatus.Deleted.toString).toOption.get
    .set(MemberAFTVersionPage(1), SampleData.version.toInt).toOption.get
    .set(MemberDetailsPage(1), SampleData.memberDetails2).toOption.get
    .set(ChargeDetailsPage(1), SampleData.chargeEDetails).toOption.get

  val allMembersIncludingDeleted: UserAnswers = allMembers
    .set(MemberDetailsPage(2), SampleData.memberDetailsDeleted).toOption.get
    .set(ChargeDetailsPage(2), SampleData.chargeEDetails).toOption.get

  def viewLink(index: Int): String = controllers.chargeE.routes.CheckYourAnswersController.onPageLoad(srn, startDate, index).url
  def removeLink(index: Int): String = controllers.chargeE.routes.DeleteMemberController.onPageLoad(srn, startDate, index).url
  def expectedMember(memberDetails: MemberDetails, index: Int): Member =
    Member(index, memberDetails.fullName, memberDetails.nino, SampleData.chargeAmount1, viewLink(index), removeLink(index), memberDetails.isDeleted)

  def expectedAllMembers: Seq[Member] = Seq(
    expectedMember(SampleData.memberDetails, 0),
    expectedMember(SampleData.memberDetails2, 1))

  def expectedMembersIncludingDeleted: Seq[Member] = expectedAllMembers ++ Seq(
    expectedMember(SampleData.memberDetailsDeleted, 2)
  )

  val mockDeleteChargeHelper: DeleteChargeHelper = mock[DeleteChargeHelper]
  val chargeEHelper: ChargeEHelper = new ChargeEHelper(mockDeleteChargeHelper)

  override def beforeEach: Unit = {
    reset(mockDeleteChargeHelper)
    when(mockDeleteChargeHelper.isLastCharge(any())).thenReturn(false)
  }

  ".getAnnualAllowanceMembers" must {
    "return all the members added in charge E" in {
      chargeEHelper.getAnnualAllowanceMembers(allMembers, srn, startDate)(request()) mustBe expectedAllMembers
    }
  }

  ".getAnnualAllowanceMembersIncludingDeleted" must {
    "return all the members added in charge E" in {
      chargeEHelper.getAnnualAllowanceMembersIncludingDeleted(allMembersIncludingDeleted, srn, startDate)(request()) mustBe expectedMembersIncludingDeleted
    }
  }

  "getAllAnnualAllowanceAmendments" must {
    implicit val dataRequest: DataRequest[AnyContent] = DataRequest(fakeRequest, "", PsaId(SampleData.psaId), UserAnswers(), SampleData.sessionData())

    "return all the amendments for annual allowance charge" in {
      val expectedRows = Seq(
        ViewAmendmentDetails(
          SampleData.memberDetails.fullName, ChargeTypeAnnualAllowance.toString,
          FormatHelper.formatCurrencyAmountAsString(SampleData.chargeEDetails.chargeAmount),
          Added
        ),
        ViewAmendmentDetails(
          SampleData.memberDetails2.fullName, ChargeTypeAnnualAllowance.toString,
          FormatHelper.formatCurrencyAmountAsString(SampleData.chargeEDetails.chargeAmount),
          Deleted
        )
      )
      chargeEHelper.getAllAnnualAllowanceAmendments(allMembers) mustBe expectedRows
    }
  }

}
