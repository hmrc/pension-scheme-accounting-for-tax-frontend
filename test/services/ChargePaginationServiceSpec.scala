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
import models.SponsoringEmployerType.SponsoringEmployerTypeIndividual
import models.chargeC.ChargeCDetails
import models.chargeD.ChargeDDetails
import models.chargeE.ChargeEDetails
import models.chargeG.ChargeAmounts
import models.{Member, UserAnswers, Employer, AmendedChargeStatus, MemberDetails}
import org.mockito.Matchers.any
import org.mockito.Mockito.{reset, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.mockito.MockitoSugar
import pages.chargeE.{ChargeDetailsPage, MemberDetailsPage, MemberStatusPage, MemberAFTVersionPage}
import pages.chargeG.ChargeAmountsPage
import play.api.libs.json.JsArray
import play.api.mvc.Call
import utils.AFTConstants.QUARTER_START_DATE

class ChargePaginationServiceSpec extends SpecBase with MockitoSugar with BeforeAndAfterEach {
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

  private def expectedEmployer(memberDetails: MemberDetails, index: Int, amount:BigDecimal): Employer =
    Employer(index, memberDetails.fullName, amount, viewUrl(index).url, removeUrl(index).url)

  private val mockDeleteChargeHelper: DeleteChargeHelper = mock[DeleteChargeHelper]
  private val mockAppConfig: FrontendAppConfig = mock[FrontendAppConfig]
  private val chargePaginationService: ChargePaginationService = new ChargePaginationService(mockAppConfig)

  override def beforeEach: Unit = {
    reset(mockDeleteChargeHelper, mockAppConfig)
    when(mockDeleteChargeHelper.isLastCharge(any())).thenReturn(false)
    when(mockAppConfig.membersPageSize).thenReturn(2)
  }

  private val viewUrl: Int => Call = index => Call("GET", s"/dummyViewUrl/$index")

  private val removeUrl: Int => Call = index => Call("GET", s"/dummyRemoveUrl/$index")

  def createPaginationStats(currentPage: Int, totalPages: Int): PaginationStats = {
    PaginationStats(currentPage = currentPage, startMember = 0, lastMember = 0, totalMembers = 0, totalPages = totalPages)
  }

  "ChargePaginationService pagerSeq" must {
    "return the sequence of numbers for the pager, where the current page number is 1" in {
        createPaginationStats(currentPage = 1, totalPages = 1)
          .pagerSeq mustBe Seq(1)
    }

    "return the sequence of numbers for the pager, where the current page number is 2" in {
      createPaginationStats(currentPage = 2, totalPages = 2)
        .pagerSeq mustBe Seq(1, 2)
    }

    "return the sequence of numbers for the pager, where the current page number is 4" in {
      createPaginationStats(currentPage = 4, totalPages = 5)
        .pagerSeq mustBe Seq(2, 3, 4, 5)
    }
  }

  "ChargePaginationService.totalPages" must {
    "give correct total pages where divide exactly" in {
      ChargePaginationService.totalPages(200, 25) mustBe 8
    }
    "give correct total pages where don't divide exactly" in {
      ChargePaginationService.totalPages(201, 25) mustBe 9
    }
    "give correct total pages where less than one page" in {
      ChargePaginationService.totalPages(24, 25) mustBe 1
    }
  }

  "ChargePaginationService.pageStartAndEnd" must {
    "give correct values for page 1 of 3" in {
      ChargePaginationService.pageStartAndEnd(
        pageNo = 1,
        totalMembers = 5,
        pageSize = 2
      ) mustBe (3, 5)
    }
    "give correct values for page 2 of 3" in {
      ChargePaginationService.pageStartAndEnd(
        pageNo = 2,
        totalMembers = 5,
        pageSize = 2
      ) mustBe (1, 3)
    }
    "give correct values for page 3 of 3" in {
      ChargePaginationService.pageStartAndEnd(
        pageNo = 3,
        totalMembers = 5,
        pageSize = 2
      ) mustBe (0, 1)
    }
    "give correct values for page 1 of 7" in { // ACT GOT (23,26)
      ChargePaginationService.pageStartAndEnd(
        pageNo = 1,
        totalMembers = 26,
        pageSize = 4
      ) mustBe (22, 26)
    }
    "give correct values for page 7 of 7" in { // ACT GOT (0,3)
      ChargePaginationService.pageStartAndEnd(
        pageNo = 7,
        totalMembers = 26,
        pageSize = 4
      ) mustBe (0, 2)
    }
  }

  "getItemsPaginated (using charge type E for testing)" must {
    "return pagination info in reverse order for page one for all the members added, excluding the deleted member" in {
      val expectedAllMembersMinusDeleted: Seq[Member] = Seq(
        expectedMember(SampleData.memberDetails6, index = 5, SampleData.chargeAmount1),
        expectedMember(SampleData.memberDetails5, index = 4, SampleData.chargeAmount1)
      )

      chargePaginationService.getItemsPaginated[ChargeEDetails](
        pageNo = 1,
        ua = allMembersChargeE,
        chargeRootNode = "chargeEDetails",
        amount = _.chargeAmount,
        viewUrl = viewUrl,
        removeUrl = removeUrl,
        membersOrEmployers = MembersOrEmployers.MEMBERS
      ) mustBe Some(
          PaginatedMembersInfo(
            itemsForCurrentPage = Left(expectedAllMembersMinusDeleted),
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

      chargePaginationService.getItemsPaginated[ChargeEDetails](
        pageNo = 2,
        ua = allMembersChargeE,
        chargeRootNode = "chargeEDetails",
        amount = _.chargeAmount,
        viewUrl = viewUrl,
        removeUrl = removeUrl,
        membersOrEmployers = MembersOrEmployers.MEMBERS
      ) mustBe Some(
        PaginatedMembersInfo(
          itemsForCurrentPage = Left(expectedAllMembersMinusDeleted),
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

      chargePaginationService.getItemsPaginated[ChargeEDetails](
        pageNo = 3,
        ua = allMembersChargeE,
        chargeRootNode = "chargeEDetails",
        amount = _.chargeAmount,
        viewUrl = viewUrl,
        removeUrl = removeUrl,
        membersOrEmployers = MembersOrEmployers.MEMBERS
      ) mustBe Some(
        PaginatedMembersInfo(
          itemsForCurrentPage = Left(expectedAllMembersMinusDeleted),
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

      chargePaginationService.getItemsPaginated[ChargeEDetails](
        pageNo = 4,
        ua = allMembersChargeE,
        chargeRootNode = "chargeEDetails",
        amount = _.chargeAmount,
        viewUrl = viewUrl,
        removeUrl = removeUrl,
        membersOrEmployers = MembersOrEmployers.MEMBERS
      ) mustBe None
    }
  }

  "getItemsPaginated (using charge type D)" must {
    "parse and return the sole member paginated" in {
      val ua = UserAnswers()
        .set(pages.chargeD.MemberStatusPage(0), AmendedChargeStatus.Added.toString).toOption.get
        .set(pages.chargeD.MemberAFTVersionPage(0), SampleData.version.toInt).toOption.get
        .set(pages.chargeD.MemberDetailsPage(0), SampleData.memberDetails).toOption.get
        .set(pages.chargeD.ChargeDetailsPage(0), SampleData.chargeDDetails).toOption.get

      val expectedMembers: Seq[Member] = Seq(
        expectedMember(SampleData.memberDetails, index = 0, SampleData.chargeAmount3)
      )

      val result = chargePaginationService.getItemsPaginated[ChargeDDetails](
        pageNo = 1,
        ua = ua,
        chargeRootNode = "chargeDDetails",
        amount = _.total,
        viewUrl = viewUrl,
        removeUrl = removeUrl,
        membersOrEmployers = MembersOrEmployers.MEMBERS
      ).map(_.membersForCurrentPage)

      result mustBe Some(expectedMembers)
    }
  }

  "getItemsPaginated (using charge type G)" must {
    "parse and return the sole member paginated" in {
      val ua = UserAnswers()
        .set(pages.chargeG.MemberStatusPage(0), AmendedChargeStatus.Added.toString).toOption.get
        .set(pages.chargeG.MemberAFTVersionPage(0), SampleData.version.toInt).toOption.get
        .set(pages.chargeG.MemberDetailsPage(0), SampleData.memberGDetails).toOption.get
        .set(pages.chargeG.ChargeAmountsPage(0), SampleData.chargeAmounts).toOption.get

      val expectedMembers: Seq[Member] = Seq(
        expectedMember(SampleData.memberDetails, index = 0, SampleData.chargeAmount2)
      )

      val result = chargePaginationService.getItemsPaginated[ChargeAmounts](
        pageNo = 1,
        ua = ua,
        chargeRootNode = "chargeGDetails",
        chargeDetailsNode = ChargeAmountsPage.toString,
        amount = _.amountTaxDue,
        viewUrl = viewUrl,
        removeUrl = removeUrl,
        membersOrEmployers = MembersOrEmployers.MEMBERS
      ).map(_.membersForCurrentPage)

      result mustBe Some(expectedMembers)
    }
  }

  "getItemsPaginated (using charge type C)" must {
    "parse and return the sole member paginated" in {
      val ua = UserAnswers()
        .set(pages.chargeC.MemberStatusPage(0), AmendedChargeStatus.Added.toString).toOption.get
        .set(pages.chargeC.MemberAFTVersionPage(0), SampleData.version.toInt).toOption.get
        .set(pages.chargeC.WhichTypeOfSponsoringEmployerPage(0), SponsoringEmployerTypeIndividual).toOption.get
        .set(pages.chargeC.SponsoringIndividualDetailsPage(0), SampleData.memberDetails).toOption.get
        .set(pages.chargeC.ChargeCDetailsPage(0), SampleData.chargeCDetails).toOption.get

      val expectedMembers: Seq[Employer] = Seq(
        expectedEmployer(SampleData.memberDetails, index = 0, SampleData.chargeAmount1)
      )

      val result = chargePaginationService.getItemsPaginated[ChargeCDetails](
        pageNo = 1,
        ua = ua,
        chargeRootNode = "chargeCDetails",
        amount = _.amountTaxDue,
        viewUrl = viewUrl,
        removeUrl = removeUrl,
        membersOrEmployers = MembersOrEmployers.EMPLOYERS
      ).map(_.employersForCurrentPage)
      result mustBe Some(expectedMembers)
    }
  }
}
