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
import config.FrontendAppConfig
import data.SampleData
import data.SampleData.{accessType, versionInt}
import helpers.DeleteChargeHelper
import models.chargeE.ChargeEDetails
import models.{Member, AmendedChargeStatus, MemberDetails, UserAnswers, AccessType}
import org.mockito.Matchers.any
import org.mockito.Mockito.{reset, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.mockito.MockitoSugar
import pages.chargeE.{ChargeDetailsPage, MemberDetailsPage, MemberStatusPage, MemberAFTVersionPage}
import play.api.libs.json.JsArray
import play.api.mvc.Call
import utils.AFTConstants.QUARTER_START_DATE

class MemberPaginationServiceSpec extends SpecBase with MockitoSugar with BeforeAndAfterEach {
  //scalastyle:off magic.number

  private val srn = "S1234567"
  private val startDate: LocalDate = QUARTER_START_DATE

  private def addMembers(memberDetailsToAdd: Seq[(MemberDetails, AmendedChargeStatus)]): UserAnswers = {
    memberDetailsToAdd.foldLeft[UserAnswers](UserAnswers()) { (ua, yyy) =>
      val memberNo = (ua.data \ "chargeEDetails" \ "members").asOpt[JsArray].map(_.value.size).getOrElse(0)
      ua.set(MemberStatusPage(memberNo), yyy._2.toString).toOption.get
        .set(MemberAFTVersionPage(memberNo), SampleData.version.toInt).toOption.get
        .set(MemberDetailsPage(memberNo), yyy._1).toOption.get.set(ChargeDetailsPage(memberNo), SampleData.chargeEDetails).toOption.get
    }
  }

  private val allMembers: UserAnswers =
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

  private def expectedMember(memberDetails: MemberDetails, index: Int): Member =
    Member(index, memberDetails.fullName, memberDetails.nino, SampleData.chargeAmount1,
      viewUrl(index, srn, startDate, accessType, versionInt).url,
      removeUrl(index, srn, startDate, UserAnswers(), accessType, versionInt).url
    )

  private val mockDeleteChargeHelper: DeleteChargeHelper = mock[DeleteChargeHelper]
  private val mockAppConfig: FrontendAppConfig = mock[FrontendAppConfig]
  private val memberPaginationService: MemberPaginationService = new MemberPaginationService(mockAppConfig)

  override def beforeEach: Unit = {
    reset(mockDeleteChargeHelper, mockAppConfig)
    when(mockDeleteChargeHelper.isLastCharge(any())).thenReturn(false)
    when(mockAppConfig.membersPageSize).thenReturn(2)
  }

  private val viewUrl: (Int, String, LocalDate, AccessType, Int) => Call =
    (index, srn, startDate, accessType, version) => Call("GET", s"/dummyViewUrl/$index/$srn/$startDate/$accessType/$version")

  private val removeUrl: (Int, String, LocalDate, UserAnswers, AccessType, Int) => Call =
    (index, srn, startDate, ua, accessType, version) => Call("GET", s"/dummyRemoveUrl/$index/$srn/$startDate/$accessType/$version")

  "MemberPaginationService.totalPages" must {
    "give correct total pages where divide exactly" in {
      MemberPaginationService.totalPages(200, 25) mustBe 8
    }
    "give correct total pages where don't divide exactly" in {
      MemberPaginationService.totalPages(201, 25) mustBe 9
    }
    "give correct total pages where less than one page" in {
      MemberPaginationService.totalPages(24, 25) mustBe 1
    }
  }

  "getMembersPaginated (using charge type E for testing)" must {
    "return pagination info for page one for all the members added, excluding the deleted member" in {
      val expectedAllMembersMinusDeleted: Seq[Member] = Seq(
        expectedMember(SampleData.memberDetails, index = 0),
        expectedMember(SampleData.memberDetails3, index = 2)
      )

      memberPaginationService.getMembersPaginated[ChargeEDetails](
        "chargeEDetails", _.chargeAmount, viewUrl, removeUrl, pageNo = 1)(allMembers, srn, startDate,
        accessType, versionInt) mustBe Some(
          PaginatedMembersInfo(
            members = expectedAllMembersMinusDeleted,
            startMember = 1,
            lastMember = 2,
            totalMembers = 5,
            totalPages = 3)
        )
    }

    "return pagination info for page two for all the members added, excluding the deleted member" in {
      val expectedAllMembersMinusDeleted: Seq[Member] = Seq(
        expectedMember(SampleData.memberDetails4, index = 3),
        expectedMember(SampleData.memberDetails5, index = 4)
      )
      memberPaginationService.getMembersPaginated[ChargeEDetails](
        "chargeEDetails", _.chargeAmount, viewUrl, removeUrl, pageNo = 2)(allMembers, srn, startDate,
        accessType, versionInt) mustBe Some(
        PaginatedMembersInfo(
          members = expectedAllMembersMinusDeleted,
          startMember = 3,
          lastMember = 4,
          totalMembers = 5,
          totalPages = 3)
      )
    }

    "return pagination info for page three for all the members added, excluding the deleted member" in {
      val expectedAllMembersMinusDeleted: Seq[Member] = Seq(
        expectedMember(SampleData.memberDetails6, index = 5)
      )
      memberPaginationService.getMembersPaginated[ChargeEDetails](
        "chargeEDetails", _.chargeAmount, viewUrl, removeUrl, pageNo = 3)(allMembers, srn, startDate,
        accessType, versionInt) mustBe Some(
        PaginatedMembersInfo(
          members = expectedAllMembersMinusDeleted,
          startMember = 5,
          lastMember = 5,
          totalMembers = 5,
          totalPages = 3)
      )
    }

    "return none when beyond page limit" in {
      memberPaginationService.getMembersPaginated[ChargeEDetails](
        "chargeEDetails", _.chargeAmount, viewUrl, removeUrl, pageNo = 4)(allMembers, srn, startDate,
        accessType, versionInt) mustBe None
    }
  }
}
