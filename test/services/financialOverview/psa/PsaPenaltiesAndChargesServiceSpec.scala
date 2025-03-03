/*
 * Copyright 2024 HM Revenue & Customs
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
import config.FrontendAppConfig
import connectors.cache.FinancialInfoCacheConnector
import connectors.{FinancialStatementConnector, ListOfSchemesConnector, MinimalConnector}
import controllers.financialOverview.psa.routes._
import data.SampleData._
import helpers.FormatHelper
import helpers.FormatHelper.formatCurrencyAmountAsString
import models.ChargeDetailsFilter.{Overdue, Upcoming}
import models.financialStatement.PenaltyType.ContractSettlementCharges
import models.financialStatement.PsaFSChargeType.{AFT_INITIAL_LFP, CONTRACT_SETTLEMENT, INTEREST_ON_CONTRACT_SETTLEMENT, SSC_30_DAY_LPP}
import models.financialStatement._
import models.viewModels.paymentsAndCharges.PaymentAndChargeStatus
import models.viewModels.paymentsAndCharges.PaymentAndChargeStatus.{InterestIsAccruing, PaymentOverdue}
import models.{ChargeDetailsFilter, Index, ListOfSchemes, ListSchemeDetails, SchemeDetails}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.mockito.MockitoSugar
import play.api.i18n.Messages
import play.api.libs.json.Json
import services.PenaltiesServiceSpec.dateNow
import services.SchemeService
import uk.gov.hmrc.govukfrontend.views.viewmodels.content.{HtmlContent, Text}
import uk.gov.hmrc.govukfrontend.views.viewmodels.summarylist.{Key, SummaryListRow, Value}
import uk.gov.hmrc.govukfrontend.views.viewmodels.table.{HeadCell, Table, TableRow}
import utils.DateHelper.{dateFormatterDMY, formatDateDMY}

import java.time.LocalDate
import scala.concurrent.{ExecutionContext, Future}

class PsaPenaltiesAndChargesServiceSpec extends SpecBase with MockitoSugar with BeforeAndAfterEach with ScalaFutures {

  import PsaPenaltiesAndChargesServiceSpec._

  private val mockSchemeService: SchemeService = mock[SchemeService]
  private val mockFSConnector: FinancialStatementConnector = mock[FinancialStatementConnector]
  private val mockFinancialInfoCacheConnector: FinancialInfoCacheConnector = mock[FinancialInfoCacheConnector]
  private val mockMinimalConnector: MinimalConnector = mock[MinimalConnector]
  private val mockListOfSchemesConnector = mock[ListOfSchemesConnector]
  private val dateNow: LocalDate = LocalDate.now()
  private val penaltiesCache: PenaltiesCache = PenaltiesCache(psaId, "psa-name", psaFsSeq)
  private val listOfSchemes: ListOfSchemes = ListOfSchemes("", "", Some(List(
    ListSchemeDetails(schemeName, srn, "", None, Some("24000040IN"), Some(pstr), None))))
  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
  private def config: FrontendAppConfig = mock[FrontendAppConfig]

  private val psaPenaltiesAndChargesService = new PsaPenaltiesAndChargesService(fsConnector = mockFSConnector,
    financialInfoCacheConnector = mockFinancialInfoCacheConnector, minimalConnector = mockMinimalConnector, config, mockListOfSchemesConnector)

  private def htmlChargeType(penaltyType: String,
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

    HtmlContent(
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
    HeadCell(Text(messages("psa.financial.overview.penalty")), classes = "govuk-!-width-one-half"),
    HeadCell(Text(messages("psa.financial.overview.charge.reference")), classes = "govuk-!-font-weight-bold"),
    HeadCell(Text(messages("financial.overview.payment.charge.amount")), classes = "govuk-!-font-weight-bold"),
    HeadCell(Text(messages("psa.financial.overview.payment.due")), classes = "govuk-!-font-weight-bold"),
    HeadCell(HtmlContent(
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
                 ): Seq[TableRow] = {
    val statusHtml = status match {
      case InterestIsAccruing => HtmlContent(s"<span class='govuk-tag govuk-tag--blue'>${status.toString}</span>")
      case PaymentOverdue => HtmlContent(s"<span class='govuk-tag govuk-tag--red'>${status.toString}</span>")
      case _ => if (paymentDue == "£0.00") {
        HtmlContent(s"<span class='govuk-visually-hidden'>${messages("paymentsAndCharges.chargeDetails.visuallyHiddenText.noPaymentDue")}</span>")
      } else {
        HtmlContent(s"<span class='govuk-visually-hidden'>${messages("paymentsAndCharges.chargeDetails.visuallyHiddenText.paymentIsDue")}</span>")
      }
    }

    Seq(
      TableRow(htmlChargeType(penaltyType, chargeReference, redirectUrl, visuallyHiddenText, schemeName, pstr), classes = "govuk-!-width-one-half"),
      TableRow(Text(s"$chargeReference"), classes = "govuk-!-width-one-quarter"),
      TableRow(Text(penaltyAmount), classes = "govuk-!-width-one-quarter"),
      TableRow(Text(paymentDue), classes = "govuk-!-width-one-quarter"),
      TableRow(statusHtml)
    )
  }

  private def penaltiesTable(rows: Seq[Seq[TableRow]]): Table =
    Table(head = Some(tableHead), rows = rows)

  override def beforeEach(): Unit = {
    super.beforeEach()
    when(mockSchemeService.retrieveSchemeDetails(any(), any())(any(), any()))
      .thenReturn(Future.successful(SchemeDetails(schemeDetails.schemeName, pstr, "Open", None)))
    when(mockFinancialInfoCacheConnector.fetch(any(), any()))
      .thenReturn(Future.successful(Some(Json.toJson(penaltiesCache))))
    when(mockListOfSchemesConnector.getListOfSchemes(any())(any(), any())).thenReturn(Future(Right(listOfSchemes)))
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
            pstr, psaFsSeq, Overdue, config)
          val row2 = psaPenaltiesAndChargesService.getPenaltiesAndCharges(
            pstr, psaFsSeq, Upcoming, config)

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
      val expected = psaPenaltiesAndChargesService.chargeAmount("£0.00", "£500.00", "£155.00")
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

        result mustBe chargeReferenceRow ++ penaltyAmountRow ++ clearingDetailsRow("2020-04-24") ++
          stoodOverAmountChargeDetailsRow ++ totalAmountDueChargeDetailsRow
      }
      "return the row for original charge amount, payments and credits, stood over amount and total amount due with paymDateOrCredDueDate None" in {
        val result =
          psaPenaltiesAndChargesService.chargeDetailsRows(psaFS2(), Overdue)

        result mustBe chargeReferenceRow ++ penaltyAmountRow ++ clearingDetailsRow("2020-06-30") ++
          stoodOverAmountChargeDetailsRow ++ totalAmountDueChargeDetailsRow
      }
    }

    "getClearedPenaltiesAndCharges" must {
      "return correct table" in {
        val result = psaPenaltiesAndChargesService.getClearedPenaltiesAndCharges(psaId, "2020", ContractSettlementCharges, psaFsCleared)

        val expectedTableHeader = Seq(
          HeadCell(
            HtmlContent(
              s"<span class='govuk-visually-hidden'>${messages("psa.financial.overview.penaltyOrCharge")}</span>"
            )),
          HeadCell(Text(Messages("psa.financial.overview.datePaid.table")), classes = "govuk-!-font-weight-bold"),
          HeadCell(Text(Messages("financial.overview.payment.charge.amount")), classes = "govuk-!-font-weight-bold")
        )

        val chargeLink = controllers.financialOverview.psa.routes.ClearedPenaltyOrChargeController.onPageLoad("2020", ContractSettlementCharges, Index(0))
        val expectedTableRows = Seq(
          TableRow(HtmlContent(
            s"<a id=XY002610150184 class=govuk-link href=$chargeLink>" +
              "Contract Settlement</a></br>" +
              schemeName + "</br>" +
              "XY002610150184</br>" +
              "1 April to 30 June 2020"
          ), classes = "govuk-!-width-one-half"),
          TableRow(HtmlContent(s"<p>12 May 2020</p>")),
          TableRow(HtmlContent(s"<p>£500.00</p>"))
        )

        val expectedTable = Table(head = Some(expectedTableHeader), rows = Seq(expectedTableRows))
        whenReady(result) {
          _ mustBe expectedTable
        }
      }
    }

  }

}

object PsaPenaltiesAndChargesServiceSpec {

  private val psaSourceChargeInfo: PsaSourceChargeInfo = PsaSourceChargeInfo(
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
    PsaFSDetail(0, "XY002610150184", AFT_INITIAL_LFP, dueDate, totalAmount, amountDue, outStandingAmount, stoodOverAmount,
      accruedInterestTotal = 0.00, dateNow, dateNow, pstr, None, None, Seq(DocumentLineItemDetail(
        clearingReason = Some(FSClearingReason.CLEARED_WITH_PAYMENT),
        clearingDate = Some(LocalDate.parse("2020-06-30")),
        paymDateOrCredDueDate = Some(LocalDate.parse("2020-04-24")),
        clearedAmountItem = BigDecimal(100.00))).toSeq)

  def psaFS2(
              amountDue: BigDecimal = BigDecimal(1029.05),
              dueDate: Option[LocalDate] = Some(LocalDate.parse("2022-03-18")),
              totalAmount: BigDecimal = BigDecimal(80000.00),
              outStandingAmount: BigDecimal = BigDecimal(56049.08),
              stoodOverAmount: BigDecimal = BigDecimal(25089.08)
            ): PsaFSDetail =
    PsaFSDetail(0, "XY002610150184", AFT_INITIAL_LFP, dueDate, totalAmount, amountDue, outStandingAmount, stoodOverAmount,
      accruedInterestTotal = 0.00, dateNow, dateNow, pstr, None, None, Seq(DocumentLineItemDetail(
        clearingReason = Some(FSClearingReason.CLEARED_WITH_PAYMENT),
        clearingDate = Some(LocalDate.parse("2020-06-30")),
        paymDateOrCredDueDate = None,
        clearedAmountItem = BigDecimal(100.00))).toSeq)


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
      psaSourceChargeInfo = None,
      documentLineItemDetails = Seq(DocumentLineItemDetail(
        clearingReason = Some(FSClearingReason.CLEARED_WITH_PAYMENT),
        clearingDate = Some(LocalDate.parse("2020-06-30")),
        paymDateOrCredDueDate = Some(LocalDate.parse("2020-04-24")),
        clearedAmountItem = BigDecimal(0.00))).toSeq
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
      psaSourceChargeInfo = None,
      documentLineItemDetails = Seq(DocumentLineItemDetail(
        clearingReason = Some(FSClearingReason.CLEARED_WITH_PAYMENT),
        clearingDate = Some(LocalDate.parse("2020-06-30")),
        paymDateOrCredDueDate = Some(LocalDate.parse("2020-04-24")),
        clearedAmountItem = BigDecimal(0.00))).toSeq
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
      psaSourceChargeInfo = Some(psaSourceChargeInfo),
      documentLineItemDetails = Seq(DocumentLineItemDetail(
        clearingReason = Some(FSClearingReason.CLEARED_WITH_PAYMENT),
        clearingDate = Some(LocalDate.parse("2020-06-30")),
        paymDateOrCredDueDate = Some(LocalDate.parse("2020-04-24")),
        clearedAmountItem = BigDecimal(0.00))).toSeq
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
      psaSourceChargeInfo = None,
      documentLineItemDetails = Nil
    )
  )

  val psaFsERSeq: Seq[PsaFSDetail] = Seq(
    PsaFSDetail(
      index = 0,
      chargeReference = "ER002610150184",
      chargeType = SSC_30_DAY_LPP,
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
      psaSourceChargeInfo = None,
      documentLineItemDetails = Nil
    )
  )

  val psaFsCleared: Seq[PsaFSDetail] = Seq(
    PsaFSDetail(
      index = 0,
      chargeReference = "XY002610150184",
      chargeType = CONTRACT_SETTLEMENT,
      dueDate = Some(LocalDate.parse("2020-11-15")),
      totalAmount = 500.00,
      outstandingAmount = 0.00,
      accruedInterestTotal = 0.00,
      stoodOverAmount = 0.00,
      amountDue = 0.00,
      periodStartDate = LocalDate.parse("2020-04-01"),
      periodEndDate = LocalDate.parse("2020-06-30"),
      pstr = "24000041IN",
      sourceChargeRefForInterest = None,
      psaSourceChargeInfo = None,
      documentLineItemDetails = Seq(DocumentLineItemDetail(
        clearedAmountItem = 500.00,
        clearingDate = Some(LocalDate.parse("2020-05-12")),
        paymDateOrCredDueDate = Some(LocalDate.parse("2020-05-12")),
        clearingReason = Some(FSClearingReason.CLEARED_WITH_PAYMENT)
      ))
    )
  )

  val psaFs: PsaFS = PsaFS(inhibitRefundSignal = false, psaFsSeq.toSeq)

  private def chargeReferenceRow(implicit messages: Messages): Seq[SummaryListRow] = {
    Seq(
      SummaryListRow(
        key = Key(Text(messages("psa.financial.overview.charge.reference")), classes = "govuk-!-padding-left-0 govuk-!-width-one-half"),
        value = Value(Text("XY002610150184"), classes = "govuk-!-width-one-quarter")),
    )
  }

  private def penaltyAmountRow(implicit messages: Messages): Seq[SummaryListRow] = {
    Seq(
      SummaryListRow(
        key = Key(Text(messages("psa.financial.overview.penaltyAmount")), classes = "govuk-!-padding-left-0 govuk-!-width-one-half"),
        value = Value(Text(s"${formatCurrencyAmountAsString(80000.00)}"), classes = "govuk-!-width-one-quarter"),
        actions = None
      ))
  }

  private def totalAmountDueChargeDetailsRow(implicit messages: Messages): Seq[SummaryListRow] = {
    Seq(
      SummaryListRow(
        key = Key(Text(messages("financialPaymentsAndCharges.paymentDue.overdue.dueDate", LocalDate.parse("2022-03-18").format(dateFormatterDMY))), classes = "govuk-!-padding-left-0 govuk-!-width-one-half"),
        value = Value(Text(s"${FormatHelper.formatCurrencyAmountAsString(1029.05)}"), classes = "govuk-!-width-one-quarter govuk-!-font-weight-bold")),
    )
  }

  private def stoodOverAmountChargeDetailsRow(implicit messages: Messages): Seq[SummaryListRow] = {
    Seq(
      SummaryListRow(
        key = Key(Text(messages("paymentsAndCharges.chargeDetails.stoodOverAmount")), classes = "govuk-!-padding-left-0 govuk-!-width-one-half"),
        value = Value(Text(s"-${formatCurrencyAmountAsString(25089.08)}"), classes = "govuk-!-width-one-quarter"),
        actions = None
      ))
  }

  private def clearingDetailsRow(date: String)(implicit messages: Messages): Seq[SummaryListRow] = {
    Seq(
      SummaryListRow(
        key = Key(Text(messages("financialPaymentsAndCharges.clearingReason.c1", formatDateDMY(LocalDate.parse(date)))), classes = "govuk-!-padding-left-0 govuk-!-width-one-half"),
        value = Value(Text(s"-${formatCurrencyAmountAsString(100)}"), classes = "govuk-!-width-one-quarter")),
    )
  }
}