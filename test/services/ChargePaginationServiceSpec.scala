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
import models.SponsoringEmployerType.{SponsoringEmployerTypeIndividual, SponsoringEmployerTypeOrganisation}
import models.{Member, UserAnswers, Employer, AmendedChargeStatus, MemberDetails, ChargeType}
import org.mockito.Matchers.any
import org.mockito.Mockito.{reset, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.mockito.MockitoSugar
import pages.chargeE.{ChargeDetailsPage, MemberDetailsPage, MemberStatusPage, MemberAFTVersionPage}
import play.api.libs.json.JsArray
import play.api.mvc.Call
import uk.gov.hmrc.viewmodels.Text.{Message, Literal}
import utils.AFTConstants.QUARTER_START_DATE
import viewmodels.Link

class ChargePaginationServiceSpec extends SpecBase with MockitoSugar with BeforeAndAfterEach {
  //scalastyle:off magic.number

  private val srn = "S1234567"
  private val startDate: LocalDate = QUARTER_START_DATE

  private def addMembersChargeE(memberDetailsToAdd: Seq[(MemberDetails, AmendedChargeStatus)]): UserAnswers = {
    memberDetailsToAdd.foldLeft[UserAnswers](UserAnswers()) { (ua, memberDetailsAmendedChargeStatus) =>
      val memberNo = (ua.data \ "chargeEDetails" \ "members").asOpt[JsArray].map(_.value.size).getOrElse(0)
      ua.set(MemberStatusPage(memberNo), memberDetailsAmendedChargeStatus._2.toString).toOption.get
        .set(MemberAFTVersionPage(memberNo), SampleData.version.toInt).toOption.get
        .set(MemberDetailsPage(memberNo), memberDetailsAmendedChargeStatus._1).toOption.get.set(ChargeDetailsPage(memberNo), SampleData.chargeEDetails).toOption.get
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

  private def ps(currentPage: Int, totalPages: Int): PaginationStats = {
    PaginationStats(currentPage = currentPage, startMember = 0, lastMember = 0, totalMembers = 0, totalPages = totalPages, 0)
  }

  private val url: Int => Call = i => Call("GET", s"dummy/$i")

  private def prev(pageNo:Int):Link =
    Link(id = "nav-prev", url = url(pageNo).url, linkText = Message("paginationPreviousPage"), hiddenText = None)

  private def next(pageNo:Int):Link =
    Link(id = "nav-next", url = url(pageNo).url, linkText = Message("paginationNextPage"), hiddenText = None)

  private def number(pageNo:Int, includeTarget:Boolean = true):Link = {
    val u = if (includeTarget) {
      url(pageNo).url
    } else {
      ""
    }
    Link(id = s"nav-$pageNo", url = u, linkText = Literal(s"$pageNo"), hiddenText = None)
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
      ) mustBe Tuple2(3, 5)
    }
    "give correct values for page 2 of 3" in {
      ChargePaginationService.pageStartAndEnd(
        pageNo = 2,
        totalMembers = 5,
        pageSize = 2
      ) mustBe Tuple2(1, 3)
    }
    "give correct values for page 3 of 3" in {
      ChargePaginationService.pageStartAndEnd(
        pageNo = 3,
        totalMembers = 5,
        pageSize = 2
      ) mustBe Tuple2(0, 1)
    }
    "give correct values for page 1 of 7" in {
      ChargePaginationService.pageStartAndEnd(
        pageNo = 1,
        totalMembers = 26,
        pageSize = 4
      ) mustBe Tuple2(22, 26)
    }
    "give correct values for page 7 of 7" in {
      ChargePaginationService.pageStartAndEnd(
        pageNo = 7,
        totalMembers = 26,
        pageSize = 4
      ) mustBe Tuple2(0, 2)
    }
  }

  "ChargePaginationService pagerSeq" must {
    "return the sequence of numbers for the pager, where the current page number is 1" in {
      chargePaginationService.pagerSeq(ps(currentPage = 1, totalPages = 1)) mustBe Seq(1)
    }

    "return the sequence of numbers for the pager, where the current page number is 2" in {
      chargePaginationService.pagerSeq(ps(currentPage = 2, totalPages = 2)) mustBe Seq(1, 2)
    }

    "return the sequence of numbers for the pager, where the current page number is 4 and total pages is 5" in {
      chargePaginationService.pagerSeq(ps(currentPage = 4, totalPages = 5)) mustBe Seq(1, 2, 3, 4, 5)
    }

    "return the sequence of numbers for the pager, where the current page number is < 3 and total pages is 6" in {
      chargePaginationService.pagerSeq(ps(currentPage = 2, totalPages = 6)) mustBe Seq(1, 2, 3, 4, 5)
    }

    "return the sequence of numbers for the pager, where the current page number is > 7 and total pages is 10" in {
      chargePaginationService.pagerSeq(ps(currentPage = 8, totalPages = 10)) mustBe Seq(6, 7, 8, 9, 10)
    }

    "return the sequence of numbers for the pager, where the current page number is not in periphery and total pages is 10" in {
      chargePaginationService.pagerSeq(ps(currentPage = 6, totalPages = 10)) mustBe Seq(4, 5, 6, 7, 8)
    }

    "return the sequence of numbers for the pager, where the current page number is 4 and total pages is 6" in {
      chargePaginationService.pagerSeq(ps(currentPage = 4, totalPages = 6)) mustBe Seq(2, 3, 4, 5, 6)
    }
  }

  "pagerNavSeq" must {
    "return Nil if only one page" in {
      chargePaginationService.pagerNavSeq(ps(currentPage = 1, totalPages = 1), url) mustBe Nil
    }

    "include previous and next link and no target for current page" in {
      chargePaginationService.pagerNavSeq(ps(currentPage = 4, totalPages = 6), url) mustBe Seq(
        prev(3), number(2), number(3), number(4, includeTarget = false), number(5), number(6), next(5)
      )
    }

    "not include previous link if first page" in {
      chargePaginationService.pagerNavSeq(ps(currentPage = 1, totalPages = 6), url) mustBe Seq(
        number(1, includeTarget = false), number(2), number(3), number(4), number(5), next(2)
      )
    }

    "not include next link if last page" in {
      chargePaginationService.pagerNavSeq(ps(currentPage = 6, totalPages = 6), url) mustBe Seq(
        prev(5), number(2), number(3), number(4), number(5), number(6, includeTarget = false)
      )
    }
  }


  "getItemsPaginated (using charge type E for testing)" must {
    "return pagination info in reverse order for page one for all the members added, excluding the deleted member" in {
      val expectedAllMembersMinusDeleted: Seq[Member] = Seq(
        expectedMember(SampleData.memberDetails6, index = 5, SampleData.chargeAmount1),
        expectedMember(SampleData.memberDetails5, index = 4, SampleData.chargeAmount1)
      )

      chargePaginationService.getItemsPaginated(
        pageNo = 1,
        ua = allMembersChargeE,
        viewUrl = viewUrl,
        removeUrl = removeUrl,
        chargeType = ChargeType.ChargeTypeAnnualAllowance
      ) mustBe Some(
          PaginatedMembersInfo(
            itemsForCurrentPage = Left(expectedAllMembersMinusDeleted),
            paginationStats = PaginationStats(
              currentPage = 1,
              startMember = 1,
              lastMember = 2,
              totalMembers = 5,
              totalPages = 3,
              totalAmount = BigDecimal(167.20)
            )
          )
        )
    }

    "return pagination info in reverse order for page two for all the members added, excluding the deleted member" in {
      val expectedAllMembersMinusDeleted: Seq[Member] = Seq(
        expectedMember(SampleData.memberDetails4, index = 3, SampleData.chargeAmount1),
        expectedMember(SampleData.memberDetails3, index = 2, SampleData.chargeAmount1)
      )

      chargePaginationService.getItemsPaginated(
        pageNo = 2,
        ua = allMembersChargeE,
        viewUrl = viewUrl,
        removeUrl = removeUrl,
        chargeType = ChargeType.ChargeTypeAnnualAllowance
      ) mustBe Some(
        PaginatedMembersInfo(
          itemsForCurrentPage = Left(expectedAllMembersMinusDeleted),
          paginationStats = PaginationStats(
            currentPage = 2,
            startMember = 3,
            lastMember = 4,
            totalMembers = 5,
            totalPages = 3,
            totalAmount = BigDecimal(167.20)
          )
        )
      )
    }

    "return pagination info in reverse order for page three for all the members added, excluding the deleted member" in {
      val expectedAllMembersMinusDeleted: Seq[Member] = Seq(
        expectedMember(SampleData.memberDetails, index = 0, SampleData.chargeAmount1)
      )

      chargePaginationService.getItemsPaginated(
        pageNo = 3,
        ua = allMembersChargeE,
        viewUrl = viewUrl,
        removeUrl = removeUrl,
        chargeType = ChargeType.ChargeTypeAnnualAllowance
      ) mustBe Some(
        PaginatedMembersInfo(
          itemsForCurrentPage = Left(expectedAllMembersMinusDeleted),
          paginationStats = PaginationStats(
            currentPage = 3,
            startMember = 5,
            lastMember = 5,
            totalMembers = 5,
            totalPages = 3,
            totalAmount = BigDecimal(167.20)
          )
        )
      )
    }


    "return none when there are no members" in {
      chargePaginationService.getItemsPaginated(
        pageNo = 1,
        ua = UserAnswers(),
        viewUrl = viewUrl,
        removeUrl = removeUrl,
        chargeType = ChargeType.ChargeTypeAnnualAllowance
      ) mustBe None
    }

    "return none when beyond page limit" in {
      chargePaginationService.getItemsPaginated(
        pageNo = 4,
        ua = allMembersChargeE,
        viewUrl = viewUrl,
        removeUrl = removeUrl,
        chargeType = ChargeType.ChargeTypeAnnualAllowance
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

      val result = chargePaginationService.getItemsPaginated(
        pageNo = 1,
        ua = ua,
        viewUrl = viewUrl,
        removeUrl = removeUrl,
        chargeType = ChargeType.ChargeTypeLifetimeAllowance
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

      val result = chargePaginationService.getItemsPaginated(
        pageNo = 1,
        ua = ua,
        viewUrl = viewUrl,
        removeUrl = removeUrl,
        chargeType = ChargeType.ChargeTypeOverseasTransfer
      ).map(_.membersForCurrentPage)

      result mustBe Some(expectedMembers)
    }
  }

  "getItemsPaginated (using charge type C)" must {
    "parse and return the two members (one individual, one organisation) paginated" in {
      val ua = UserAnswers()
        .set(pages.chargeC.MemberStatusPage(0), AmendedChargeStatus.Added.toString).toOption.get
        .set(pages.chargeC.MemberAFTVersionPage(0), SampleData.version.toInt).toOption.get
        .set(pages.chargeC.WhichTypeOfSponsoringEmployerPage(0), SponsoringEmployerTypeIndividual).toOption.get
        .set(pages.chargeC.SponsoringIndividualDetailsPage(0), SampleData.memberDetails).toOption.get
        .set(pages.chargeC.ChargeCDetailsPage(0), SampleData.chargeCDetails).toOption.get

        .set(pages.chargeC.MemberStatusPage(1), AmendedChargeStatus.Added.toString).toOption.get
        .set(pages.chargeC.MemberAFTVersionPage(1), SampleData.version.toInt).toOption.get
        .set(pages.chargeC.WhichTypeOfSponsoringEmployerPage(1), SponsoringEmployerTypeOrganisation).toOption.get
        .set(pages.chargeC.SponsoringOrganisationDetailsPage(1), SampleData.sponsoringOrganisationDetails).toOption.get
        .set(pages.chargeC.ChargeCDetailsPage(1), SampleData.chargeCDetails).toOption.get

      val expectedMembers: Seq[Employer] = Seq(
        Employer(index = 1, SampleData.companyName, SampleData.chargeAmount1, viewUrl(1).url, removeUrl(1).url),
        expectedEmployer(SampleData.memberDetails, index = 0, SampleData.chargeAmount1)
      )

      val result = chargePaginationService.getItemsPaginated(
        pageNo = 1,
        ua = ua,
        viewUrl = viewUrl,
        removeUrl = removeUrl,
        chargeType = ChargeType.ChargeTypeAuthSurplus
      ).map(_.employersForCurrentPage)
      result mustBe Some(expectedMembers)
    }

    "return none when there are no members" in {

      chargePaginationService.getItemsPaginated(
        pageNo = 1,
        ua = UserAnswers(),
        viewUrl = viewUrl,
        removeUrl = removeUrl,
        chargeType = ChargeType.ChargeTypeAuthSurplus
      ) mustBe None
    }
  }
}
