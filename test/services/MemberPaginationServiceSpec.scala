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
import helpers.DeleteChargeHelper
import models.chargeD.ChargeDDetails
import models.chargeE.ChargeEDetails
import models.chargeG.ChargeAmounts
import models.{UserAnswers, MemberDetails, Member, AmendedChargeStatus}
import org.mockito.Matchers.any
import org.mockito.Mockito.{reset, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.mockito.MockitoSugar
import pages.chargeE.{ChargeDetailsPage, MemberDetailsPage, MemberStatusPage, MemberAFTVersionPage}
import pages.chargeG.ChargeAmountsPage
import play.api.libs.json.JsArray
import play.api.mvc.Call
import utils.AFTConstants.QUARTER_START_DATE

class MemberPaginationServiceSpec extends SpecBase with MockitoSugar with BeforeAndAfterEach {
  //scalastyle:off magic.number

  private val srn = "S1234567"
  private val startDate: LocalDate = QUARTER_START_DATE

  private def addMembersChargeE(memberDetailsToAdd: Seq[(MemberDetails, AmendedChargeStatus)]): UserAnswers = {
    memberDetailsToAdd.foldLeft[UserAnswers](UserAnswers()) { (ua, yyy) =>
      val memberNo = (ua.data \ "chargeEDetails" \ "members").asOpt[JsArray].map(_.value.size).getOrElse(0)
      ua.set(MemberStatusPage(memberNo), yyy._2.toString).toOption.get
        .set(MemberAFTVersionPage(memberNo), SampleData.version.toInt).toOption.get
        .set(MemberDetailsPage(memberNo), yyy._1).toOption.get.set(ChargeDetailsPage(memberNo), SampleData.chargeEDetails).toOption.get
    }
  }

  private val allMembersChargeE: UserAnswers =
    addMembersChargeE(
      Seq(
        (SampleData.memberDetails, AmendedChargeStatus.Added),
        (SampleData.memberDetails2, AmendedChargeStatus.Deleted),
        (SampleData.memberDetails3, AmendedChargeStatus.Added),
        (SampleData.memberDetails4, AmendedChargeStatus.Added),
        (SampleData.memberDetails5, AmendedChargeStatus.Added),
        (SampleData.memberDetails6, AmendedChargeStatus.Added)
      )
    )

  private def expectedMember(memberDetails: MemberDetails, index: Int, amount:BigDecimal): Member =
    Member(index, memberDetails.fullName, memberDetails.nino, amount, viewUrl(index).url, removeUrl(index).url)

  private val mockDeleteChargeHelper: DeleteChargeHelper = mock[DeleteChargeHelper]
  private val mockAppConfig: FrontendAppConfig = mock[FrontendAppConfig]
  private val memberPaginationService: MemberPaginationService = new MemberPaginationService(mockAppConfig)

  override def beforeEach: Unit = {
    reset(mockDeleteChargeHelper, mockAppConfig)
    when(mockDeleteChargeHelper.isLastCharge(any())).thenReturn(false)
    when(mockAppConfig.membersPageSize).thenReturn(2)
  }

  private val viewUrl: Int => Call = index => Call("GET", s"/dummyViewUrl/$index")

  private val removeUrl: Int => Call = index => Call("GET", s"/dummyRemoveUrl/$index")

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
    "return pagination info in reverse order for page one for all the members added, excluding the deleted member" in {
      val expectedAllMembersMinusDeleted: Seq[Member] = Seq(
        expectedMember(SampleData.memberDetails6, index = 5, SampleData.chargeAmount1),
        expectedMember(SampleData.memberDetails5, index = 4, SampleData.chargeAmount1)
      )

      memberPaginationService.getMembersPaginated[ChargeEDetails](
        pageNo = 1,
        ua = allMembersChargeE,
        chargeRootNode = "chargeEDetails",
        amount = _.chargeAmount,
        viewUrl = viewUrl,
        removeUrl = removeUrl
      ) mustBe Some(
          PaginatedMembersInfo(
            membersForCurrentPage = expectedAllMembersMinusDeleted,
            paginationStats = PaginationStats(
              currentPage = 1,
              startMember = 1,
              lastMember = 2,
              totalMembers = 5,
              totalPages = 3
            )
          )
        )
    }

    "return pagination info in reverse order for page two for all the members added, excluding the deleted member" in {
      val expectedAllMembersMinusDeleted: Seq[Member] = Seq(
        expectedMember(SampleData.memberDetails4, index = 3, SampleData.chargeAmount1),
        expectedMember(SampleData.memberDetails3, index = 2, SampleData.chargeAmount1)
      )

      memberPaginationService.getMembersPaginated[ChargeEDetails](
        pageNo = 2,
        ua = allMembersChargeE,
        chargeRootNode = "chargeEDetails",
        amount = _.chargeAmount,
        viewUrl = viewUrl,
        removeUrl = removeUrl
      ) mustBe Some(
        PaginatedMembersInfo(
          membersForCurrentPage = expectedAllMembersMinusDeleted,
          paginationStats = PaginationStats(
            currentPage = 2,
            startMember = 3,
            lastMember = 4,
            totalMembers = 5,
            totalPages = 3
          )
        )
      )
    }

    "return pagination info in reverse order for page three for all the members added, excluding the deleted member" in {
      val expectedAllMembersMinusDeleted: Seq[Member] = Seq(
        expectedMember(SampleData.memberDetails, index = 0, SampleData.chargeAmount1)
      )

      memberPaginationService.getMembersPaginated[ChargeEDetails](
        pageNo = 3,
        ua = allMembersChargeE,
        chargeRootNode = "chargeEDetails",
        amount = _.chargeAmount,
        viewUrl = viewUrl,
        removeUrl = removeUrl
      ) mustBe Some(
        PaginatedMembersInfo(
          membersForCurrentPage = expectedAllMembersMinusDeleted,
          paginationStats = PaginationStats(
            currentPage = 3,
            startMember = 5,
            lastMember = 5,
            totalMembers = 5,
            totalPages = 3
          )
        )
      )
    }

    "return none when beyond page limit" in {

      memberPaginationService.getMembersPaginated[ChargeEDetails](
        pageNo = 4,
        ua = allMembersChargeE,
        chargeRootNode = "chargeEDetails",
        amount = _.chargeAmount,
        viewUrl = viewUrl,
        removeUrl = removeUrl
      ) mustBe None
    }
  }

  "getMembersPaginated (using charge type D)" must {
    "parse and return the sole member paginated" in {
      val ua = UserAnswers()
        .set(pages.chargeD.MemberStatusPage(0), AmendedChargeStatus.Added.toString).toOption.get
        .set(pages.chargeD.MemberAFTVersionPage(0), SampleData.version.toInt).toOption.get
        .set(pages.chargeD.MemberDetailsPage(0), SampleData.memberDetails).toOption.get
        .set(pages.chargeD.ChargeDetailsPage(0), SampleData.chargeDDetails).toOption.get

      val expectedMembers: Seq[Member] = Seq(
        expectedMember(SampleData.memberDetails, index = 0, SampleData.chargeAmount3)
      )

      val result = memberPaginationService.getMembersPaginated[ChargeDDetails](
        pageNo = 1,
        ua = ua,
        chargeRootNode = "chargeDDetails",
        amount = _.total,
        viewUrl = viewUrl,
        removeUrl = removeUrl
      ).map(_.membersForCurrentPage)

      result mustBe Some(expectedMembers)
    }
  }

  "getMembersPaginated (using charge type G)" must {
    "parse and return the sole member paginated" in {
      val ua = UserAnswers()
        .set(pages.chargeG.MemberStatusPage(0), AmendedChargeStatus.Added.toString).toOption.get
        .set(pages.chargeG.MemberAFTVersionPage(0), SampleData.version.toInt).toOption.get
        .set(pages.chargeG.MemberDetailsPage(0), SampleData.memberGDetails).toOption.get
        .set(pages.chargeG.ChargeAmountsPage(0), SampleData.chargeAmounts).toOption.get

      val expectedMembers: Seq[Member] = Seq(
        expectedMember(SampleData.memberDetails, index = 0, SampleData.chargeAmount2)
      )

      val result = memberPaginationService.getMembersPaginated[ChargeAmounts](
        pageNo = 1,
        ua = ua,
        chargeRootNode = "chargeGDetails",
        chargeDetailsNode = ChargeAmountsPage.toString,
        amount = _.amountTaxDue,
        viewUrl = viewUrl,
        removeUrl = removeUrl
      ).map(_.membersForCurrentPage)

      result mustBe Some(expectedMembers)
    }
  }
}
