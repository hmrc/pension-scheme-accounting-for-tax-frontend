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
import data.SampleData._
import models.chargeB.ChargeBDetails
import models.MemberDetails
import models.UserAnswers
import models.YearRange
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.mockito.MockitoSugar
import play.api.mvc.Results
import services.MemberSearchService.MemberRow
import uk.gov.hmrc.viewmodels.SummaryList.Action
import uk.gov.hmrc.viewmodels.SummaryList.Key
import uk.gov.hmrc.viewmodels.SummaryList.Row
import uk.gov.hmrc.viewmodels.SummaryList.Value
import uk.gov.hmrc.viewmodels.Text.Literal
import uk.gov.hmrc.viewmodels.Text.Message

class MemberSearchServiceSpec extends SpecBase with ScalaFutures with BeforeAndAfterEach with MockitoSugar with Results {

  private val memberSearchService = new MemberSearchService

  private val chargeBDetails: ChargeBDetails = ChargeBDetails(4, chargeAmount1)
  private val memberDetailsD1: MemberDetails = MemberDetails("Ann", "Bloggs", "AB123451C")
  private val memberDetailsD2: MemberDetails = MemberDetails("Joe", "Bloggs", "AB123452C")
  private val memberDetailsE1: MemberDetails = MemberDetails("Steph", "Bloggs", "AB123453C")
  private val memberDetailsE2: MemberDetails = MemberDetails("Brian", "Blessed", "AB123454C")
  private val memberDetailsG1: models.chargeG.MemberDetails = models.chargeG.MemberDetails("first", "last", LocalDate.now(), "AB123455C")
  private val memberDetailsG2: models.chargeG.MemberDetails = models.chargeG.MemberDetails("Joe", "Bloggs", LocalDate.now(), "AB123456C")
//  private val memberDetailsDeleted: MemberDetails = MemberDetails("Jill", "Bloggs", "AB123457C", isDeleted = true)
//  private val memberGDetailsDeleted: models.chargeG.MemberDetails = models.chargeG.MemberDetails("Jill", "Bloggs", LocalDate.now(), "AB123458C", isDeleted = true)

  private val memberDetailsAnn = Seq(
    MemberRow(
      memberDetailsD1.fullName,
      Seq(
        Row(
          Key(Message("memberDetails.nino"), Seq("govuk-!-width-three-quarters")),
          Value(Literal("AB123451C"), Seq("govuk-!-width-one-quarter", "govuk-table__cell--numeric")),
          Seq()
        ),
        Row(
          Key(Message("aft.summary.search.chargeType"), Seq("govuk-!-width-three-quarters")),
          Value(Message("aft.summary.lifeTimeAllowance.description"), Seq("govuk-!-width-one-quarter", "govuk-table__cell--numeric")),
          Seq()
        ),
        Row(
          Key(Message("aft.summary.search.amount"), Seq("govuk-!-width-three-quarters")),
          Value(Literal("83.44"), Seq("govuk-!-width-one-quarter", "govuk-table__cell--numeric")),
          Seq()
        )
      ),
      Seq(
        Action(
          Message("site.view"),
          "/manage-pension-scheme-accounting-for-tax/srn/new-return/2020-04-01/lifetime-allowance-charge/1/check-your-answers",
          Some(Message("aft.summary.lifeTimeAllowance.visuallyHidden.row")),
          Seq(),
          Map()
        ),
        Action(
          Message("site.remove"),
          "/manage-pension-scheme-accounting-for-tax/srn/new-return/2020-04-01/lifetime-allowance-charge/1/remove-charge",
          Some(Message("aft.summary.lifeTimeAllowance.visuallyHidden.row")),
          Seq(),
          Map()
        )
      )
    )
  )

  private def ua: UserAnswers =
    userAnswersWithSchemeNamePstrQuarter
      .setOrException(pages.chargeD.MemberDetailsPage(0), memberDetailsD1)
      .setOrException(pages.chargeD.MemberDetailsPage(1), memberDetailsD2)
      .setOrException(pages.chargeD.ChargeDetailsPage(0), chargeDDetails)
      .setOrException(pages.chargeD.ChargeDetailsPage(1), chargeDDetails)
      .setOrException(pages.chargeD.TotalChargeAmountPage, BigDecimal(66.88))
      .setOrException(pages.chargeE.MemberDetailsPage(0), memberDetailsE1)
      .setOrException(pages.chargeE.MemberDetailsPage(1), memberDetailsE2)
      .setOrException(pages.chargeE.AnnualAllowanceYearPage(0), YearRange.currentYear)
      .setOrException(pages.chargeE.AnnualAllowanceYearPage(1), YearRange.currentYear)
      .setOrException(pages.chargeE.ChargeDetailsPage(0), chargeEDetails)
      .setOrException(pages.chargeE.ChargeDetailsPage(1), chargeEDetails)
      .setOrException(pages.chargeE.TotalChargeAmountPage, BigDecimal(66.88))
      .setOrException(pages.chargeG.MemberDetailsPage(0), memberDetailsG1)
      .setOrException(pages.chargeG.MemberDetailsPage(1), memberDetailsG2)
      .setOrException(pages.chargeG.ChargeAmountsPage(0), chargeAmounts)
      .setOrException(pages.chargeG.ChargeAmountsPage(1), chargeAmounts2)
      .setOrException(pages.chargeG.TotalChargeAmountPage, BigDecimal(66.88))

  "Search" must {
    "return valid results when searching with a valid name" in {
      val name = memberDetailsD1.firstName
      val expected = memberDetailsAnn
      memberSearchService.search(ua, "srn", LocalDate.of(2020, 4, 1), name) mustBe expected
    }

    "return valid results when searching with a valid nino" in {
      val nino = memberDetailsD1.nino
      val expected = memberDetailsAnn
      memberSearchService.search(ua, "srn", LocalDate.of(2020, 4, 1), nino) mustBe expected
    }

    "return no results when nothing matches" in {
      val nino = "ZZ098765A"

      memberSearchService.search(ua, "srn", LocalDate.of(2020, 4, 1), nino) mustBe Nil
    }
  }

}
