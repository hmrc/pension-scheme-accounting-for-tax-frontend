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
import helpers.{DeleteChargeHelper, FormatHelper}
import models.AmendedChargeStatus.{Deleted, Updated}
import models.ChargeType.ChargeTypeOverseasTransfer
import models.LocalDateBinder._
import models.chargeG.MemberDetails
import models.requests.DataRequest
import models.viewModels.ViewAmendmentDetails
import models.{Member, UserAnswers}
import org.mockito.Matchers.any
import org.mockito.Mockito.{reset, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.mockito.MockitoSugar
import pages.chargeG.{ChargeAmountsPage, MemberAFTVersionPage, MemberDetailsPage, MemberStatusPage}
import play.api.mvc.AnyContent
import uk.gov.hmrc.domain.PsaId
import utils.AFTConstants.QUARTER_START_DATE
class ChargeGServiceSpec extends SpecBase with MockitoSugar with BeforeAndAfterEach {

  val srn = "S1234567"
  val startDate: LocalDate = QUARTER_START_DATE

  val allMembers: UserAnswers = UserAnswers()
    .set(MemberStatusPage(0), "Deleted").toOption.get
    .set(MemberAFTVersionPage(0), SampleData.version.toInt).toOption.get
    .set(MemberDetailsPage(0), SampleData.memberGDetails).toOption.get
    .set(ChargeAmountsPage(0), SampleData.chargeAmounts).toOption.get
    .set(MemberStatusPage(1), "Changed").toOption.get
    .set(MemberAFTVersionPage(1), SampleData.version.toInt).toOption.get
    .set(MemberDetailsPage(1), SampleData.memberGDetails2).toOption.get
    .set(ChargeAmountsPage(1), SampleData.chargeAmounts2).toOption.get

  val allMembersIncludingDeleted: UserAnswers = allMembers
    .set(MemberDetailsPage(2), SampleData.memberGDetails).toOption.get
    .set(ChargeAmountsPage(2), SampleData.chargeAmounts).toOption.get

  def viewLink(index: Int): String = controllers.chargeG.routes.CheckYourAnswersController.onPageLoad(srn, startDate, index).url
  def removeLink(index: Int): String = controllers.chargeG.routes.DeleteMemberController.onPageLoad(srn, startDate, index).url
  def expectedMember(memberDetails: MemberDetails, index: Int): Member =
    Member(index, memberDetails.fullName, memberDetails.nino, SampleData.chargeAmount2, viewLink(index), removeLink(index))

  def expectedAllMembers: Seq[Member] = Seq(
    expectedMember(SampleData.memberGDetails, 0),
    expectedMember(SampleData.memberGDetails2, 1))

  val mockDeleteChargeHelper: DeleteChargeHelper = mock[DeleteChargeHelper]
  val chargeGHelper: ChargeGService = new ChargeGService(mockDeleteChargeHelper)

  override def beforeEach: Unit = {
    reset(mockDeleteChargeHelper)
    when(mockDeleteChargeHelper.isLastCharge(any())).thenReturn(false)
  }

  ".getOverseasTransferMembers" must {
    "return all the members added in charge G" in {
      chargeGHelper.getOverseasTransferMembers(allMembers, srn, startDate)(request()) mustBe expectedAllMembers
    }
  }

  "getAllOverseasTransferAmendments" must {
    implicit val dataRequest: DataRequest[AnyContent] = DataRequest(fakeRequest, "", PsaId(SampleData.psaId), UserAnswers(), SampleData.sessionData())

    "return all the amendments for overseas transfer charge" in {
      val expectedRows = Seq(
        ViewAmendmentDetails(
          SampleData.memberGDetails.fullName, ChargeTypeOverseasTransfer.toString,
          FormatHelper.formatCurrencyAmountAsString(SampleData.chargeAmounts.amountTaxDue),
          Deleted
        ),
        ViewAmendmentDetails(
          SampleData.memberGDetails2.fullName, ChargeTypeOverseasTransfer.toString,
          FormatHelper.formatCurrencyAmountAsString(SampleData.chargeAmounts.amountTaxDue),
          Updated
        )
      )
      chargeGHelper.getAllOverseasTransferAmendments(allMembers) mustBe expectedRows
    }
  }

}
