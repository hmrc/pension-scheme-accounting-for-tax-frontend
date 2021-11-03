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
import data.SampleData.{accessType, versionInt}
import helpers.{DeleteChargeHelper, FormatHelper}
import models.AmendedChargeStatus.{Added, Deleted}
import models.ChargeType.ChargeTypeAnnualAllowance
import models.LocalDateBinder._
import models.viewModels.ViewAmendmentDetails
import models.{UserAnswers, MemberDetails, Member, AmendedChargeStatus}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, when}
import org.scalatest.BeforeAndAfterEach
import org.mockito.MockitoSugar
import pages.chargeE.{ChargeDetailsPage, MemberDetailsPage, MemberStatusPage, MemberAFTVersionPage}
import play.api.libs.json.JsArray
import utils.AFTConstants.QUARTER_START_DATE
class ChargeEServiceSpec extends SpecBase with MockitoSugar with BeforeAndAfterEach {

  val srn = "S1234567"
  val startDate: LocalDate = QUARTER_START_DATE

  private def addMembers(memberDetailsToAdd: Seq[(MemberDetails, AmendedChargeStatus)]): UserAnswers = {
    memberDetailsToAdd.foldLeft[UserAnswers](UserAnswers()) { (ua, yyy) =>
      val memberNo = (ua.data \ "chargeEDetails" \ "members").asOpt[JsArray].map(_.value.size).getOrElse(0)
      ua.set(MemberStatusPage(memberNo), yyy._2.toString).toOption.get
        .set(MemberAFTVersionPage(memberNo), SampleData.version.toInt).toOption.get
        .set(MemberDetailsPage(memberNo), yyy._1).toOption.get.set(ChargeDetailsPage(memberNo), SampleData.chargeEDetails).toOption.get
    }
  }

  val allMembers: UserAnswers =
    addMembers(
      Seq(
        (SampleData.memberDetails, AmendedChargeStatus.Added),
        (SampleData.memberDetails2, AmendedChargeStatus.Deleted),
        (SampleData.memberDetails3, AmendedChargeStatus.Added),
        (SampleData.memberDetails4, AmendedChargeStatus.Added),
        (SampleData.memberDetails5, AmendedChargeStatus.Added),
        (SampleData.memberDetails6, AmendedChargeStatus.Added)
      )
    )

  def viewLink(index: Int): String = controllers.chargeE.routes.CheckYourAnswersController.onPageLoad(srn, startDate, accessType, versionInt, index).url
  def removeLink(index: Int): String = controllers.chargeE.routes.DeleteMemberController.onPageLoad(srn, startDate, accessType, versionInt, index).url
  def expectedMember(memberDetails: MemberDetails, index: Int): Member =
    Member(index, memberDetails.fullName, memberDetails.nino, SampleData.chargeAmount1, viewLink(index), removeLink(index))

  //scalastyle.off: magic.number
  def expectedAllMembersMinusDeleted: Seq[Member] = Seq(
    expectedMember(SampleData.memberDetails, 0),
    expectedMember(SampleData.memberDetails3, 2),
    expectedMember(SampleData.memberDetails4, 3),
    expectedMember(SampleData.memberDetails5, 4),
    expectedMember(SampleData.memberDetails6, 5)
  )

  val mockDeleteChargeHelper: DeleteChargeHelper = mock[DeleteChargeHelper]
  val chargeEHelper: ChargeEService = new ChargeEService(mockDeleteChargeHelper)

  override def beforeEach: Unit = {
    reset(mockDeleteChargeHelper)
    when(mockDeleteChargeHelper.isLastCharge(any())).thenReturn(false)
  }

  ".getAnnualAllowanceMembers" must {
    "return all the members added in charge E" in {
      chargeEHelper.getAnnualAllowanceMembers(allMembers, srn, startDate,
        accessType, versionInt)(request()) mustBe expectedAllMembersMinusDeleted
    }
   }

  "getAllAnnualAllowanceAmendments" must {
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
        ),
        ViewAmendmentDetails(
          SampleData.memberDetails3.fullName, ChargeTypeAnnualAllowance.toString,
          FormatHelper.formatCurrencyAmountAsString(SampleData.chargeEDetails.chargeAmount),
          Added
        ),
        ViewAmendmentDetails(
          SampleData.memberDetails4.fullName, ChargeTypeAnnualAllowance.toString,
          FormatHelper.formatCurrencyAmountAsString(SampleData.chargeEDetails.chargeAmount),
          Added
        ),
        ViewAmendmentDetails(
          SampleData.memberDetails5.fullName, ChargeTypeAnnualAllowance.toString,
          FormatHelper.formatCurrencyAmountAsString(SampleData.chargeEDetails.chargeAmount),
          Added
        ),
        ViewAmendmentDetails(
          SampleData.memberDetails6.fullName, ChargeTypeAnnualAllowance.toString,
          FormatHelper.formatCurrencyAmountAsString(SampleData.chargeEDetails.chargeAmount),
          Added
        )
      )
      chargeEHelper.getAllAnnualAllowanceAmendments(allMembers, versionInt) mustBe expectedRows
    }
  }

}
