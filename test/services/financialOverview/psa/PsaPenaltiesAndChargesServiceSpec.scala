/*
 * Copyright 2022 HM Revenue & Customs
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

package services.financialOverview.psa

import base.SpecBase
import connectors.cache.FinancialInfoCacheConnector
import connectors.{FinancialStatementConnector, MinimalConnector}
import controllers.financialOverview.psa.routes._
import data.SampleData._
import helpers.FormatHelper
import helpers.FormatHelper.formatCurrencyAmountAsString
import models.ChargeDetailsFilter.{Overdue, Upcoming}
import models.financialStatement.PsaFSChargeType.{AFT_INITIAL_LFP, CONTRACT_SETTLEMENT, INTEREST_ON_CONTRACT_SETTLEMENT}
import models.financialStatement._
import models.viewModels.paymentsAndCharges.PaymentAndChargeStatus
import models.viewModels.paymentsAndCharges.PaymentAndChargeStatus.{InterestIsAccruing, PaymentOverdue}
import models.{ChargeDetailsFilter, SchemeDetails}
import org.mockito.ArgumentMatchers.any
import org.mockito.MockitoSugar
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import play.api.libs.json.Json
import services.PenaltiesServiceSpec.dateNow
import services.SchemeService
import uk.gov.hmrc.viewmodels.SummaryList.{Key, Row, Value}
import uk.gov.hmrc.viewmodels.Text.Literal
import uk.gov.hmrc.viewmodels.{Html, SummaryList}
import utils.DateHelper.dateFormatterDMY
import viewmodels.Radios.MessageInterpolators
import viewmodels.Table
import viewmodels.Table.Cell

import java.time.LocalDate
import scala.collection.Seq
import scala.concurrent.{ExecutionContext, Future}

class PsaPenaltiesAndChargesServiceSpec extends SpecBase with MockitoSugar with BeforeAndAfterEach with ScalaFutures {

  import PsaPenaltiesAndChargesServiceSpec._

  val mockSchemeService: SchemeService = mock[SchemeService]
  val mockFSConnector: FinancialStatementConnector = mock[FinancialStatementConnector]
  val mockFinancialInfoCacheConnector: FinancialInfoCacheConnector = mock[FinancialInfoCacheConnector]
  val mockMinimalConnector: MinimalConnector = mock[MinimalConnector]
  val dateNow: LocalDate = LocalDate.now()
  val penaltiesCache: PenaltiesCache = PenaltiesCache(psaId, "psa-name", psaFsSeq)
  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

  private val psaPenaltiesAndChargesService = new PsaPenaltiesAndChargesService(fsConnector = mockFSConnector,
    financialInfoCacheConnector = mockFinancialInfoCacheConnector, schemeService = mockSchemeService, minimalConnector = mockMinimalConnector)

  private def htmlChargeType( penaltyType: String,
                              chargeReference: String,
                              redirectUrl: String,
                              visuallyHiddenText: String,
                              schemeName: String,
                              pstr: String
                            ) = {
    val linkId =
      chargeReference match {
        case "To be assigned" => "to-be-assigned"
        case "None" => "none"
        case _ => chargeReference
      }

    Html(
      s"<a id=$linkId class=govuk-link href=" +
        s"$redirectUrl>" +
        s"$penaltyType " +
        s"<span class=govuk-visually-hidden>$visuallyHiddenText</span> </a>" +
        s"<p class=govuk-hint>" +
        s"$schemeName </br>" +
        s"($pstr)"
    )
  }

  private val tableHead = Seq(
      Cell(msg"psa.financial.overview.penalty", classes = Seq("govuk-!-width-one-half")),
      Cell(msg"psa.financial.overview.charge.reference", classes = Seq("govuk-!-font-weight-bold")),
      Cell(msg"psa.financial.overview.payment.amount", classes = Seq("govuk-!-font-weight-bold")),
      Cell(msg"psa.financial.overview.payment.due", classes = Seq("govuk-!-font-weight-bold")),
      Cell(Html(
        s"<span class='govuk-visually-hidden'>${messages("paymentsAndCharges.chargeDetails.paymentStatus")}</span>"
      ))
    )

  private def row(penaltyType: String,
                  chargeReference: String,
                  penaltyAmount: String,
                  paymentDue: String,
                  status: PaymentAndChargeStatus,
                  redirectUrl: String,
                  schemeName: String,
                  pstr: String,
                  visuallyHiddenText: String
                 ): Seq[Table.Cell] = {
    val statusHtml = status match {
      case InterestIsAccruing => Html(s"<span class='govuk-tag govuk-tag--blue'>${status.toString}</span>")
      case PaymentOverdue => Html(s"<span class='govuk-tag govuk-tag--red'>${status.toString}</span>")
      case _ => if (paymentDue == "£0.00") {
        Html(s"<span class='govuk-visually-hidden'>${messages("paymentsAndCharges.chargeDetails.visuallyHiddenText.noPaymentDue")}</span>")
      } else {
        Html(s"<span class='govuk-visually-hidden'>${messages("paymentsAndCharges.chargeDetails.visuallyHiddenText.paymentIsDue")}</span>")
      }
    }

    Seq(
      Cell(htmlChargeType(penaltyType, chargeReference, redirectUrl, visuallyHiddenText, schemeName, pstr), classes = Seq("govuk-!-width-one-half")),
      Cell(Literal(s"$chargeReference"), classes = Seq("govuk-!-width-one-quarter")),
      Cell(Literal(penaltyAmount), classes = Seq("govuk-!-width-one-quarter")),
      Cell(Literal(paymentDue), classes = Seq("govuk-!-width-one-quarter")),
      Cell(statusHtml)
    )
  }


  private def penaltiesTable(rows: Seq[Seq[Table.Cell]]): Table =
    Table(head = tableHead, rows = rows)

  override def beforeEach: Unit = {
    super.beforeEach

    when(mockSchemeService.retrieveSchemeDetails(any(), any(), any())(any(), any()))
      .thenReturn(Future.successful(SchemeDetails(schemeDetails.schemeName, pstr, "Open", None)))
    when(mockFinancialInfoCacheConnector.fetch(any(), any()))
      .thenReturn(Future.successful(Some(Json.toJson(penaltiesCache))))
  }

  "getPenaltiesAndCharges" must {

    Seq(CONTRACT_SETTLEMENT).foreach {
      chargeType =>

        s"return payments and charges table with two rows for the charge and interest accrued for $chargeType" in {

          val penaltyLink: ChargeDetailsFilter => String = chargeDetailsFilter =>
            PsaPenaltiesAndChargeDetailsController.onPageLoad(pstr, "0", chargeDetailsFilter).url
          val interestLink: ChargeDetailsFilter => String = chargeDetailsFilter =>
            PsaPaymentsAndChargesInterestController.onPageLoad(pstr, "0", chargeDetailsFilter).url

          def expectedTable(penaltyLink: String, interestLink: String): Table =
            penaltiesTable(Seq(
              row(
                penaltyType = chargeType.toString,
                chargeReference = "XY002610150184",
                penaltyAmount = FormatHelper.formatCurrencyAmountAsString(500.00),
                paymentDue = FormatHelper.formatCurrencyAmountAsString(500.00),
                status = PaymentAndChargeStatus.PaymentOverdue,
                redirectUrl = penaltyLink,
                schemeName = schemeName,
                pstr = pstr,
                visuallyHiddenText = messages(s"paymentsAndCharges.visuallyHiddenText", "XY002610150184")
              ),
              row(
                penaltyType = INTEREST_ON_CONTRACT_SETTLEMENT.toString,
                chargeReference = messages("paymentsAndCharges.chargeReference.toBeAssigned"),
                penaltyAmount = "",
                paymentDue = FormatHelper.formatCurrencyAmountAsString(155.00),
                status = PaymentAndChargeStatus.InterestIsAccruing,
                redirectUrl = interestLink,
                schemeName = schemeName,
                pstr = pstr,
                visuallyHiddenText = messages(s"paymentsAndCharges.interest.visuallyHiddenText"),
              )
            ))

          val row1 = psaPenaltiesAndChargesService.getPenaltiesAndCharges(
            pstr, psaFsSeq, Overdue)
          val row2 = psaPenaltiesAndChargesService.getPenaltiesAndCharges(
            pstr, psaFsSeq, Upcoming)

          row1 map {
            _ mustBe expectedTable(penaltyLink(Overdue), interestLink(Overdue))
          }

          row2 map {
            _ mustBe expectedTable(penaltyLink(Upcoming), interestLink(Upcoming))
          }
        }
    }


  }

  "retrievePsaChargesAmount" must {
    "return true if the amount due is positive and due date is before today" in {
      val expected = Tuple3("£0.00", "£500.00", "£155.00")
      psaPenaltiesAndChargesService.retrievePsaChargesAmount(psaFsSeq) mustEqual expected
    }

    "getPenaltiesForJourney" must {
      "return payload from cache for psaId and match the payload" in {
        when(mockFinancialInfoCacheConnector.fetch(any(), any()))
          .thenReturn(Future.successful(Some(Json.toJson(penaltiesCache))))
        whenReady(psaPenaltiesAndChargesService.getPenaltiesForJourney(psaId, ChargeDetailsFilter.All)) {
          _ mustBe penaltiesCache
        }
      }
    }

    "isPaymentOverdue" must {
      "return true if amountDue is greater than 0 and due date is after today" in {
        psaPenaltiesAndChargesService.isPaymentOverdue(psaFS(BigDecimal(0.01), Some(dateNow.minusDays(1)))) mustBe true
      }

      "return false if amountDue is less than or equal to 0" in {
        psaPenaltiesAndChargesService.isPaymentOverdue(psaFS(BigDecimal(0.00), Some(dateNow.minusDays(1)))) mustBe false
      }

      "return false if amountDue is greater than 0 and due date is not defined" in {
        psaPenaltiesAndChargesService.isPaymentOverdue(psaFS(BigDecimal(0.01), None)) mustBe false
      }

      "return false if amountDue is greater than 0 and due date is today" in {
        psaPenaltiesAndChargesService.isPaymentOverdue(psaFS(BigDecimal(0.01), Some(dateNow))) mustBe false
      }
    }

    "chargeDetailsRows" must {
      "return the row for original charge amount, payments and credits, stood over amount and total amount due" in {
        val result =
          psaPenaltiesAndChargesService.chargeDetailsRows(psaFS(), Overdue)

        result mustBe chargeReferenceRow ++ penaltyAmountRow ++
          stoodOverAmountChargeDetailsRow ++ totalAmountDueChargeDetailsRow
      }
    }

  }

}

object PsaPenaltiesAndChargesServiceSpec {

  private val sourceChargeInfo : SourceChargeInfo = SourceChargeInfo(
    index = 1,
    chargeType = CONTRACT_SETTLEMENT,
    periodStartDate = LocalDate.parse("2020-04-01"),
    periodEndDate = LocalDate.parse("2020-06-30")
  )

  def psaFS(
             amountDue: BigDecimal = BigDecimal(1029.05),
             dueDate: Option[LocalDate] = Some(LocalDate.parse("2022-03-18")),
             totalAmount: BigDecimal = BigDecimal(80000.00),
             outStandingAmount: BigDecimal = BigDecimal(56049.08),
             stoodOverAmount: BigDecimal = BigDecimal(25089.08)
           ): PsaFSDetail =
    PsaFSDetail( 0, "XY002610150184", AFT_INITIAL_LFP, dueDate, totalAmount, amountDue, outStandingAmount, stoodOverAmount,
      accruedInterestTotal = 0.00, dateNow, dateNow, pstr, None, None, Seq(DocumentLineItemDetail(
        clearingReason = Some(FSClearingReason.CLEARED_WITH_PAYMENT),
        clearingDate = Some(LocalDate.parse("2020-06-30")),
        clearedAmountItem = BigDecimal(0.00))))


  def createPsaFSCharge(chargeReference: String): PsaFSDetail =
    PsaFSDetail(
      index = 0,
      chargeReference = chargeReference,
      chargeType = AFT_INITIAL_LFP,
      dueDate = Some(LocalDate.parse("2020-07-15")),
      totalAmount = 80000.00,
      outstandingAmount = 56049.08,
      stoodOverAmount = 25089.08,
      accruedInterestTotal = 0.00,
      amountDue = 1029.05,
      periodStartDate = LocalDate.parse("2020-04-01"),
      periodEndDate = LocalDate.parse("2020-06-30"),
      pstr = "24000041IN",
      sourceChargeRefForInterest = None,
      sourceChargeInfo = None,
      documentLineItemDetails = Seq(DocumentLineItemDetail(
        clearingReason = Some(FSClearingReason.CLEARED_WITH_PAYMENT),
        clearingDate = Some(LocalDate.parse("2020-06-30")),
        clearedAmountItem = BigDecimal(0.00)))
    )

  def psaFSResponse(amountDue: BigDecimal = BigDecimal(0.01), dueDate: LocalDate = dateNow): Seq[PsaFSDetail] = Seq(
    PsaFSDetail(
      index = 1,
      chargeReference = "XY002610150184",
      chargeType = AFT_INITIAL_LFP,
      dueDate = Some(dueDate),
      totalAmount = 80000.00,
      outstandingAmount = 56049.08,
      stoodOverAmount = 25089.08,
      accruedInterestTotal = 0.00,
      amountDue = amountDue,
      periodStartDate = LocalDate.parse("2020-04-01"),
      periodEndDate = LocalDate.parse("2020-06-30"),
      pstr = "24000041IN",
      sourceChargeRefForInterest = None,
      sourceChargeInfo = None,
      documentLineItemDetails = Seq(DocumentLineItemDetail(
        clearingReason= Some(FSClearingReason.CLEARED_WITH_PAYMENT),
        clearingDate = Some(LocalDate.parse("2020-06-30")),
        clearedAmountItem = BigDecimal(0.00)))
    ),
    PsaFSDetail(
      index = 2,
      chargeReference = "XY002610150184",
      chargeType = CONTRACT_SETTLEMENT,
      dueDate = Some(dueDate),
      totalAmount = 80000.00,
      outstandingAmount = 56049.08,
      stoodOverAmount = 25089.08,
      accruedInterestTotal = 0.00,
      amountDue = amountDue,
      periodStartDate = LocalDate.parse("2020-04-01"),
      periodEndDate = LocalDate.parse("2020-06-30"),
      pstr = "24000041IN",
      sourceChargeRefForInterest = None,
      sourceChargeInfo = Some(sourceChargeInfo),
      documentLineItemDetails = Seq(DocumentLineItemDetail(
        clearingReason= Some(FSClearingReason.CLEARED_WITH_PAYMENT),
        clearingDate = Some(LocalDate.parse("2020-06-30")),
        clearedAmountItem = BigDecimal(0.00)))
    )
  )

  val pstr: String = "24000041IN"
  val schemeName = "Big Scheme"

  val psaFsSeq: Seq[PsaFSDetail] = Seq(
    PsaFSDetail(
      index = 0,
      chargeReference = "XY002610150184",
      chargeType = CONTRACT_SETTLEMENT,
      dueDate = Some(LocalDate.parse("2020-11-15")),
      totalAmount = 500.00,
      outstandingAmount = 0.00,
      accruedInterestTotal = 155.00,
      stoodOverAmount = 0.00,
      amountDue = 500.00,
      periodStartDate = LocalDate.parse("2020-04-01"),
      periodEndDate = LocalDate.parse("2020-06-30"),
      pstr = "24000041IN",
      sourceChargeRefForInterest = None,
      sourceChargeInfo = None,
      documentLineItemDetails = Nil
    )
  )
  val psaFs: PsaFS = PsaFS (inhibitRefundSignal = false, psaFsSeq)

  private def chargeReferenceRow: Seq[SummaryList.Row] = {
    Seq(
      Row(
        key = Key(
          content = msg"psa.financial.overview.charge.reference",
          classes = Seq("govuk-!-padding-left-0", "govuk-!-width-one-half")
        ),
        value = Value(
          content = Literal("XY002610150184"),
          classes =
            Seq("govuk-!-width-one-quarter")
        ),
        actions = Nil
      ))
  }

  private def penaltyAmountRow: Seq[SummaryList.Row] = {
    Seq(
      Row(
        key = Key(
          content = msg"psa.financial.overview.penaltyAmount",
          classes = Seq("govuk-!-padding-left-0", "govuk-!-width-one-half")
        ),
        value = Value(
          content = Literal(s"${formatCurrencyAmountAsString(80000.00)}"),
          classes =
            Seq("govuk-!-width-one-quarter")
        ),
        actions = Nil
      ))
  }

  private def totalAmountDueChargeDetailsRow: Seq[SummaryList.Row] = {
    Seq(
      Row(
        key = Key(
          content = msg"financialPaymentsAndCharges.paymentDue.overdue.dueDate".withArgs(LocalDate.parse("2022-03-18").format(dateFormatterDMY)),
          classes = Seq("govuk-!-padding-left-0", "govuk-!-width-one-half")
        ),
        value = Value(
          Literal(s"${FormatHelper.formatCurrencyAmountAsString(1029.05)}"),
          classes = Seq("govuk-!-width-one-quarter","govuk-!-font-weight-bold")
        ),
        actions = Nil
      ))
  }

  private def stoodOverAmountChargeDetailsRow: Seq[SummaryList.Row] = {
    Seq(
      Row(
        key = Key(
          content = msg"paymentsAndCharges.chargeDetails.stoodOverAmount",
          classes = Seq("govuk-!-padding-left-0", "govuk-!-width-one-half")
        ),
        value = Value(
          content = Literal(s"-${formatCurrencyAmountAsString(25089.08)}"),
          classes = Seq("govuk-!-width-one-quarter")
        ),
        actions = Nil
      ))
  }
}