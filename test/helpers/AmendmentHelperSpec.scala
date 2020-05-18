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
import models.AmendedChargeStatus.{Added, Deleted, Updated}
import models.ChargeType.{ChargeTypeAnnualAllowance, ChargeTypeAuthSurplus, ChargeTypeDeRegistration, ChargeTypeLifetimeAllowance, ChargeTypeLumpSumDeath, ChargeTypeOverseasTransfer, ChargeTypeShortService}
import models.SponsoringEmployerType.SponsoringEmployerTypeIndividual
import models.chargeA.{ChargeDetails => ChargeADetails}
import models.chargeB.ChargeBDetails
import models.chargeF.{ChargeDetails => ChargeFDetails}
import models.requests.DataRequest
import models.viewModels.ViewAmendmentDetails
import models.{UserAnswers, chargeA}
import org.mockito.Matchers.any
import org.mockito.Mockito
import org.mockito.Mockito.when
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.mockito.MockitoSugar
import pages.chargeA.{ChargeDetailsPage => ChargeADetailsPage}
import pages.chargeB.ChargeBDetailsPage
import pages.chargeC.{ChargeCDetailsPage, SponsoringIndividualDetailsPage, WhichTypeOfSponsoringEmployerPage, MemberAFTVersionPage => MemberCAFTVersionPage, MemberStatusPage => MemberCStatusPage}
import pages.chargeD.{ChargeDetailsPage => ChargeDDetailsPage, MemberAFTVersionPage => MemberDAFTVersionPage, MemberDetailsPage => MemberDDetailsPage, MemberStatusPage => MemberDStatusPage}
import pages.chargeE.{ChargeDetailsPage => ChargeEDetailsPage, MemberAFTVersionPage => MemberEAFTVersionPage, MemberDetailsPage => MemberEDetailsPage, MemberStatusPage => MemberEStatusPage}
import pages.chargeF.{ChargeDetailsPage => ChargeFDetailsPage}
import pages.chargeG.{ChargeAmountsPage, MemberAFTVersionPage => MemberGAFTVersionPage, MemberDetailsPage => MemberGDetailsPage, MemberStatusPage => MemberGStatusPage}
import play.api.libs.json.Json
import play.api.mvc.AnyContent
import play.twirl.api.Html
import uk.gov.hmrc.domain.PsaId
import uk.gov.hmrc.viewmodels.SummaryList.{Key, Row, Value}
import uk.gov.hmrc.viewmodels.Text.Literal
import uk.gov.hmrc.viewmodels._

import scala.concurrent.Future

class AmendmentHelperSpec extends SpecBase with MockitoSugar with BeforeAndAfterEach {

  val chargeCHelper: ChargeCHelper = mock[ChargeCHelper]
  val chargeDHelper: ChargeDHelper = mock[ChargeDHelper]
  val chargeEHelper: ChargeEHelper = mock[ChargeEHelper]
  val chargeGHelper: ChargeGHelper = mock[ChargeGHelper]
  private val amendmentHelper = new AmendmentHelper(chargeCHelper, chargeDHelper, chargeEHelper, chargeGHelper)

  override def beforeEach: Unit = {
    super.beforeEach
    Mockito.reset(chargeCHelper, chargeDHelper, chargeEHelper, chargeGHelper)
    when(chargeCHelper.getAllAuthSurplusAmendments(any())(any())).thenReturn(Nil)
    when(chargeDHelper.getAllLifetimeAllowanceAmendments(any())(any())).thenReturn(Nil)
    when(chargeEHelper.getAllAnnualAllowanceAmendments(any())(any())).thenReturn(Nil)
    when(chargeGHelper.getAllOverseasTransferAmendments(any())(any())).thenReturn(Nil)

  }

  "getTotalAmount" must {

    "return sum of the total amounts of all charges for UK/NonUK" in {
      val ua = UserAnswers()
        .setOrException(pages.chargeE.TotalChargeAmountPage, BigDecimal(100.00))
        .setOrException(pages.chargeC.TotalChargeAmountPage, BigDecimal(1000.00))
        .setOrException(pages.chargeD.TotalChargeAmountPage, BigDecimal(1000.00))
        .setOrException(pages.chargeF.ChargeDetailsPage, ChargeFDetails(LocalDate.now(), BigDecimal(2500.00)))
        .setOrException(pages.chargeA.ChargeDetailsPage, chargeA.ChargeDetails(3, None, None, BigDecimal(3500.00)))
        .setOrException(pages.chargeB.ChargeBDetailsPage, ChargeBDetails(3, BigDecimal(5000.00)))
        .setOrException(pages.chargeG.TotalChargeAmountPage, BigDecimal(34220.00))

      amendmentHelper.getTotalAmount(ua) mustEqual Tuple2(BigDecimal(13100.00), BigDecimal(34220.00))
    }
  }

  "amendmentSummaryRows" must {
    val previousVersion = 2
    val currentVersion = 3
    val previousTotalAmount = BigDecimal(5000.00)
    val currentTotalAmount = BigDecimal(8000.00)
    val differenceAmount = BigDecimal(3000.00)

    "return all the summary list rows" in {
      val result = amendmentHelper.amendmentSummaryRows(currentTotalAmount, previousTotalAmount, currentVersion, previousVersion)

      result mustBe Seq(
        Row(
          key = Key(msg"confirmSubmitAFTReturn.total.for".withArgs(previousVersion), classes = Seq("govuk-!-width-three-quarters")),
          value = Value(Literal(s"${FormatHelper.formatCurrencyAmountAsString(previousTotalAmount)}"),
                        classes = Seq("govuk-!-width-one-quarter", "govuk-table__cell--numeric")),
          actions = Nil
        ),
        Row(
          key = Key(msg"confirmSubmitAFTReturn.total.for.draft", classes = Seq("govuk-!-width-three-quarters")),
          value = Value(
            Literal(s"${FormatHelper.formatCurrencyAmountAsString(currentTotalAmount)}"),
            classes = Seq("govuk-!-width-one-quarter", "govuk-table__cell--numeric")
          ),
          actions = Nil
        ),
        Row(
          key = Key(msg"confirmSubmitAFTReturn.difference", classes = Seq("govuk-!-width-three-quarters")),
          value = Value(Literal(s"${FormatHelper.formatCurrencyAmountAsString(differenceAmount)}"),
                        classes = Seq("govuk-!-width-one-quarter", "govuk-table__cell--numeric")),
          actions = Nil
        )
      )
    }
  }

  "getAllAmendments" when {
    implicit val dataRequest: DataRequest[AnyContent] = DataRequest(fakeRequest, "", PsaId(SampleData.psaId), UserAnswers(), SampleData.sessionData())

    "called with chargeA" must {

      "return all the added amendments" in {
        val currentUa = UserAnswers().setOrException(ChargeADetailsPage, ChargeADetails(2, Some(100.00), Some(200.00), 300.00))
        val previousUa = UserAnswers()
        val expectedRows = Seq(
          ViewAmendmentDetails(messages("allAmendments.numberOfMembers", 2),
                               ChargeTypeShortService.toString,
                               FormatHelper.formatCurrencyAmountAsString(BigDecimal(300.00)),
                               Added))

        amendmentHelper.getAllAmendments(currentUa, previousUa) mustBe expectedRows
      }

      "return all the deleted amendments" in {
        val currentUa = UserAnswers().setOrException(ChargeADetailsPage, ChargeADetails(2, Some(0), Some(0), 0))
        val previousUa = UserAnswers().setOrException(ChargeADetailsPage, ChargeADetails(2, Some(100.00), Some(200.00), 300.00))

        val expectedRows = Seq(
          ViewAmendmentDetails(messages("allAmendments.numberOfMembers", 2),
                               ChargeTypeShortService.toString,
                               FormatHelper.formatCurrencyAmountAsString(BigDecimal(300.00)),
                               Deleted))

        amendmentHelper.getAllAmendments(currentUa, previousUa) mustBe expectedRows
      }

      "return all the updated amendments" in {
        val currentUa = UserAnswers().setOrException(ChargeADetailsPage, ChargeADetails(2, Some(200.00), Some(200.00), 400.00))
        val previousUa = UserAnswers().setOrException(ChargeADetailsPage, ChargeADetails(2, Some(100.00), Some(200.00), 300.00))

        val expectedRows = Seq(
          ViewAmendmentDetails(messages("allAmendments.numberOfMembers", 2),
                               ChargeTypeShortService.toString,
                               FormatHelper.formatCurrencyAmountAsString(BigDecimal(400.00)),
                               Updated))

        amendmentHelper.getAllAmendments(currentUa, previousUa) mustBe expectedRows
      }
    }

    "called with chargeB" must {

      "return all the added amendments" in {
        val currentUa = UserAnswers().setOrException(ChargeBDetailsPage, ChargeBDetails(2, 300.00))
        val previousUa = UserAnswers()
        val expectedRows = Seq(
          ViewAmendmentDetails(messages("allAmendments.numberOfMembers", 2),
                               ChargeTypeLumpSumDeath.toString,
                               FormatHelper.formatCurrencyAmountAsString(BigDecimal(300.00)),
                               Added))

        amendmentHelper.getAllAmendments(currentUa, previousUa) mustBe expectedRows
      }

      "return all the deleted amendments" in {
        val currentUa = UserAnswers().setOrException(ChargeBDetailsPage, ChargeBDetails(2, 0))
        val previousUa = UserAnswers().setOrException(ChargeBDetailsPage, ChargeBDetails(2, 300.00))

        val expectedRows = Seq(
          ViewAmendmentDetails(messages("allAmendments.numberOfMembers", 2),
                               ChargeTypeLumpSumDeath.toString,
                               FormatHelper.formatCurrencyAmountAsString(BigDecimal(300.00)),
                               Deleted))

        amendmentHelper.getAllAmendments(currentUa, previousUa) mustBe expectedRows
      }

      "return all the updated amendments" in {
        val currentUa = UserAnswers().setOrException(ChargeBDetailsPage, ChargeBDetails(2, 400.00))
        val previousUa = UserAnswers().setOrException(ChargeBDetailsPage, ChargeBDetails(2, 300.00))

        val expectedRows = Seq(
          ViewAmendmentDetails(messages("allAmendments.numberOfMembers", 2),
                               ChargeTypeLumpSumDeath.toString,
                               FormatHelper.formatCurrencyAmountAsString(BigDecimal(400.00)),
                               Updated))

        amendmentHelper.getAllAmendments(currentUa, previousUa) mustBe expectedRows
      }
    }

    "called with chargeF" must {

      "return all the added amendments" in {
        val currentUa = UserAnswers().setOrException(ChargeFDetailsPage, ChargeFDetails(LocalDate.now(), 300.00))
        val previousUa = UserAnswers()
        val expectedRows = Seq(
          ViewAmendmentDetails(messages("allAmendments.noMembers"),
                               ChargeTypeDeRegistration.toString,
                               FormatHelper.formatCurrencyAmountAsString(BigDecimal(300.00)),
                               Added))

        amendmentHelper.getAllAmendments(currentUa, previousUa) mustBe expectedRows
      }

      "return all the deleted amendments" in {
        val currentUa = UserAnswers().setOrException(ChargeFDetailsPage, ChargeFDetails(LocalDate.now(), 0))
        val previousUa = UserAnswers().setOrException(ChargeFDetailsPage, ChargeFDetails(LocalDate.now(), 300.00))

        val expectedRows = Seq(
          ViewAmendmentDetails(messages("allAmendments.noMembers"),
                               ChargeTypeDeRegistration.toString,
                               FormatHelper.formatCurrencyAmountAsString(BigDecimal(300.00)),
                               Deleted))

        amendmentHelper.getAllAmendments(currentUa, previousUa) mustBe expectedRows
      }

      "return all the updated amendments" in {
        val currentUa = UserAnswers().setOrException(ChargeFDetailsPage, ChargeFDetails(LocalDate.now(), 400.00))
        val previousUa = UserAnswers().setOrException(ChargeFDetailsPage, ChargeFDetails(LocalDate.now(), 300.00))

        val expectedRows = Seq(
          ViewAmendmentDetails(messages("allAmendments.noMembers"),
                               ChargeTypeDeRegistration.toString,
                               FormatHelper.formatCurrencyAmountAsString(BigDecimal(400.00)),
                               Updated))

        amendmentHelper.getAllAmendments(currentUa, previousUa) mustBe expectedRows
      }
    }

    "called with all the charges" must {

      "return all the amendment rows" in {
        val currentUa = UserAnswers()
          .setOrException(ChargeFDetailsPage, ChargeFDetails(LocalDate.now(), 400.00))
          .setOrException(ChargeBDetailsPage, ChargeBDetails(2, 400.00))
          .setOrException(ChargeADetailsPage, ChargeADetails(2, Some(200.00), Some(200.00), 400.00))
          .setOrException(MemberGStatusPage(0), "Deleted")
          .setOrException(MemberGAFTVersionPage(0), SampleData.version.toInt)
          .setOrException(MemberGDetailsPage(0), SampleData.memberGDetails)
          .setOrException(ChargeAmountsPage(0), SampleData.chargeAmounts)
          .setOrException(MemberEStatusPage(0), "New")
          .setOrException(MemberEAFTVersionPage(0), SampleData.version.toInt)
          .setOrException(MemberEDetailsPage(0), SampleData.memberDetails)
          .setOrException(ChargeEDetailsPage(0), SampleData.chargeEDetails)
          .setOrException(MemberDStatusPage(0), "Changed")
          .setOrException(MemberDAFTVersionPage(0), SampleData.version.toInt)
          .setOrException(MemberDDetailsPage(0), SampleData.memberDetails)
          .setOrException(ChargeDDetailsPage(0), SampleData.chargeDDetails)
          .setOrException(MemberCAFTVersionPage(0), SampleData.version.toInt)
          .setOrException(MemberCStatusPage(0), "New")
          .setOrException(WhichTypeOfSponsoringEmployerPage(0), SponsoringEmployerTypeIndividual)
          .setOrException(SponsoringIndividualDetailsPage(0), SampleData.sponsoringIndividualDetails)
          .setOrException(ChargeCDetailsPage(0), SampleData.chargeCDetails)

        val expectedRows = Seq(
          ViewAmendmentDetails(
            messages("allAmendments.numberOfMembers", 2),
            ChargeTypeShortService.toString,
            FormatHelper.formatCurrencyAmountAsString(BigDecimal(400.00)),
            Added
          ),
          ViewAmendmentDetails(
            messages("allAmendments.numberOfMembers", 2),
            ChargeTypeLumpSumDeath.toString,
            FormatHelper.formatCurrencyAmountAsString(BigDecimal(400.00)),
            Added
          ),
          ViewAmendmentDetails(
            messages("allAmendments.noMembers"),
            ChargeTypeDeRegistration.toString,
            FormatHelper.formatCurrencyAmountAsString(BigDecimal(400.00)),
            Added
          ),
          ViewAmendmentDetails(
            SampleData.sponsoringIndividualDetails.fullName,
            ChargeTypeAuthSurplus.toString,
            FormatHelper.formatCurrencyAmountAsString(SampleData.chargeCDetails.amountTaxDue),
            Added
          ),
          ViewAmendmentDetails(
            SampleData.memberDetails.fullName,
            ChargeTypeLifetimeAllowance.toString,
            FormatHelper.formatCurrencyAmountAsString(SampleData.chargeDDetails.total),
            Updated
          ),
          ViewAmendmentDetails(
            SampleData.memberDetails.fullName,
            ChargeTypeAnnualAllowance.toString,
            FormatHelper.formatCurrencyAmountAsString(SampleData.chargeEDetails.chargeAmount),
            Added
          ),
          ViewAmendmentDetails(
            SampleData.memberGDetails.fullName,
            ChargeTypeOverseasTransfer.toString,
            FormatHelper.formatCurrencyAmountAsString(SampleData.chargeAmounts.amountTaxDue),
            Deleted
          )
        )

        when(chargeCHelper.getAllAuthSurplusAmendments(any())(any())).thenReturn(Seq(expectedRows(3)))
        when(chargeDHelper.getAllLifetimeAllowanceAmendments(any())(any())).thenReturn(Seq(expectedRows(4)))
        when(chargeEHelper.getAllAnnualAllowanceAmendments(any())(any())).thenReturn(Seq(expectedRows(5)))
        when(chargeGHelper.getAllOverseasTransferAmendments(any())(any())).thenReturn(Seq(expectedRows(6)))

        amendmentHelper.getAllAmendments(currentUa, UserAnswers()) mustBe expectedRows
      }
    }
  }
}
