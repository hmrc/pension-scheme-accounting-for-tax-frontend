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
import helpers.FormatHelper
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

  import MemberSearchServiceSpec._

  "Search" must {
    "return one valid result when searching with a valid name when case not matching" in {
      memberSearchService.search(ua, srn, startDate, memberDetailsD1.firstName.toLowerCase) mustBe
        searchResultsMemberDetailsChargeD(memberDetailsD1, BigDecimal("83.44"))
    }

    "return several valid results when searching across all 3 charge types with a valid name when case not matching" in {
      val expected = searchResultsMemberDetailsChargeD(memberDetailsD1, BigDecimal("83.44")) ++
        searchResultsMemberDetailsChargeD(memberDetailsD2, BigDecimal("83.44"), 1) ++
        searchResultsMemberDetailsChargeE(memberDetailsE1, BigDecimal("33.44")) ++
        searchResultsMemberDetailsChargeG(memberDetailsG2, BigDecimal("50.00"), 1)

      val actual = memberSearchService.search(ua, srn, startDate, memberDetailsD1.lastName.toLowerCase)

      actual.size mustBe expected.size

      actual.head mustBe expected.head
      actual(1) mustBe expected(1)
      actual(2) mustBe expected(2)

      actual(3) mustBe expected(3)
    }

    "return valid results when searching with a valid nino when case not matching" in {
      memberSearchService.search(ua, srn, startDate, memberDetailsD1.nino.toLowerCase) mustBe
        searchResultsMemberDetailsChargeD(memberDetailsD1, BigDecimal("83.44"))
    }

    "return no results when nothing matches" in {
      memberSearchService.search(ua, srn, startDate, "ZZ098765A") mustBe Nil
    }
  }

}

object MemberSearchServiceSpec {
  private val startDate = LocalDate.of(2020, 4, 1)
  private val startDateAsString = "2020-04-01"
  private val srn = "srn"

  private val memberSearchService = new MemberSearchService

  private val memberDetailsD1: MemberDetails = MemberDetails("Ann", "Bloggs", "AB123451C")
  private val memberDetailsD2: MemberDetails = MemberDetails("Joe", "Bloggs", "AB123452C")
  private val memberDetailsD3: MemberDetails = MemberDetails("Ann", "Smithers", "AB123451C", isDeleted = true)
  private val memberDetailsE1: MemberDetails = MemberDetails("Steph", "Bloggs", "AB123453C")
  private val memberDetailsE2: MemberDetails = MemberDetails("Brian", "Blessed", "AB123454C")
  private val memberDetailsG1: models.chargeG.MemberDetails = models.chargeG.MemberDetails("first", "last", LocalDate.now(), "AB123455C")
  private val memberDetailsG2: models.chargeG.MemberDetails = models.chargeG.MemberDetails("Joe", "Bloggs", LocalDate.now(), "AB123456C")

  private def searchResultsMemberDetailsChargeD(memberDetails: MemberDetails, totalAmount:BigDecimal, index:Int = 0) = Seq(
    MemberRow(
      memberDetails.fullName,
      Seq(
        Row(
          Key(Message("memberDetails.nino"), Seq("govuk-!-width-three-quarters")),
          Value(Literal(memberDetails.nino), Seq("govuk-!-width-one-quarter", "govuk-table__cell--numeric"))
        ),
        Row(
          Key(Message("aft.summary.search.chargeType"), Seq("govuk-!-width-three-quarters")),
          Value(Message("aft.summary.lifeTimeAllowance.description"), Seq("govuk-!-width-one-quarter", "govuk-table__cell--numeric"))
        ),
        Row(
          Key(Message("aft.summary.search.amount"), Seq("govuk-!-width-three-quarters")),

          Value(Literal(s"${FormatHelper.formatCurrencyAmountAsString(totalAmount)}"),
            classes = Seq("govuk-!-width-one-quarter", "govuk-table__cell--numeric"))

          //Value(Literal(totalAmount), Seq("govuk-!-width-one-quarter", "govuk-table__cell--numeric"))
        )
      ),
      Seq(
        Action(
          Message("site.view"),
          controllers.chargeD.routes.CheckYourAnswersController.onPageLoad(srn, startDateAsString, index).url,
          Some(Message("aft.summary.lifeTimeAllowance.visuallyHidden.row"))
        ),
        Action(
          Message("site.remove"),
          controllers.chargeD.routes.DeleteMemberController.onPageLoad(srn, startDateAsString, index).url,
          Some(Message("aft.summary.lifeTimeAllowance.visuallyHidden.row"))
        )
      )
    )
  )

  private def searchResultsMemberDetailsChargeE(memberDetails: MemberDetails, totalAmount:BigDecimal, index:Int = 0) = Seq(
    MemberRow(
      memberDetails.fullName,
      Seq(
        Row(
          Key(Message("memberDetails.nino"), Seq("govuk-!-width-three-quarters")),
          Value(Literal(memberDetails.nino), Seq("govuk-!-width-one-quarter", "govuk-table__cell--numeric"))
        ),
        Row(
          Key(Message("aft.summary.search.chargeType"), Seq("govuk-!-width-three-quarters")),
          Value(Message("aft.summary.annualAllowance.description"), Seq("govuk-!-width-one-quarter", "govuk-table__cell--numeric"))
        ),
        Row(
          Key(Message("aft.summary.search.amount"), Seq("govuk-!-width-three-quarters")),

          Value(Literal(s"${FormatHelper.formatCurrencyAmountAsString(totalAmount)}"),
            classes = Seq("govuk-!-width-one-quarter", "govuk-table__cell--numeric"))

          //Value(Literal(totalAmount), Seq("govuk-!-width-one-quarter", "govuk-table__cell--numeric"))
        )
      ),
      Seq(
        Action(
          Message("site.view"),
          controllers.chargeE.routes.CheckYourAnswersController.onPageLoad(srn, startDateAsString, index).url,
          Some(Message("aft.summary.annualAllowance.visuallyHidden.row"))
        ),
        Action(
          Message("site.remove"),
          controllers.chargeE.routes.DeleteMemberController.onPageLoad(srn, startDateAsString, index).url,
          Some(Message("aft.summary.annualAllowance.visuallyHidden.row"))
        )
      )
    )
  )

  private def searchResultsMemberDetailsChargeG(memberDetails: models.chargeG.MemberDetails, totalAmount:BigDecimal, index:Int = 0) = Seq(
    MemberRow(
      memberDetails.fullName,
      Seq(
        Row(
          Key(Message("memberDetails.nino"), Seq("govuk-!-width-three-quarters")),
          Value(Literal(memberDetails.nino), Seq("govuk-!-width-one-quarter", "govuk-table__cell--numeric"))
        ),
        Row(
          Key(Message("aft.summary.search.chargeType"), Seq("govuk-!-width-three-quarters")),
          Value(Message("aft.summary.overseasTransfer.description"), Seq("govuk-!-width-one-quarter", "govuk-table__cell--numeric"))
        ),
        Row(
          Key(Message("aft.summary.search.amount"), Seq("govuk-!-width-three-quarters")),
          Value(Literal(s"${FormatHelper.formatCurrencyAmountAsString(totalAmount)}"),
            classes = Seq("govuk-!-width-one-quarter", "govuk-table__cell--numeric"))
          //Value(Literal(totalAmount), Seq("govuk-!-width-one-quarter", "govuk-table__cell--numeric"))
        )
      ),
      Seq(
        Action(
          Message("site.view"),
          controllers.chargeG.routes.CheckYourAnswersController.onPageLoad(srn, startDateAsString, index).url,
          Some(Message("aft.summary.overseasTransfer.visuallyHidden.row"))
        ),
        Action(
          Message("site.remove"),
          controllers.chargeG.routes.DeleteMemberController.onPageLoad(srn, startDateAsString, index).url,
          Some(Message("aft.summary.overseasTransfer.visuallyHidden.row"))
        )
      )
    )
  )

  private def ua: UserAnswers =
    userAnswersWithSchemeNamePstrQuarter
      .setOrException(pages.chargeD.MemberDetailsPage(0), memberDetailsD1)
      .setOrException(pages.chargeD.MemberDetailsPage(1), memberDetailsD2)
      .setOrException(pages.chargeD.MemberDetailsPage(2), memberDetailsD3)
      .setOrException(pages.chargeD.ChargeDetailsPage(0), chargeDDetails)
      .setOrException(pages.chargeD.ChargeDetailsPage(1), chargeDDetails)
      .setOrException(pages.chargeD.ChargeDetailsPage(2), chargeDDetails)
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
}
