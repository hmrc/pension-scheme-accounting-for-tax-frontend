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

package services.financialOverview.scheme

import base.SpecBase
import config.FrontendAppConfig
import connectors.FinancialStatementConnector
import connectors.cache.FinancialInfoCacheConnector
import controllers.chargeB.{routes => _}
import controllers.financialOverview.scheme.routes._
import data.SampleData._
import helpers.FormatHelper
import helpers.FormatHelper.formatCurrencyAmountAsString
import models.ChargeDetailsFilter
import models.ChargeDetailsFilter._
import models.financialStatement.PaymentOrChargeType.AccountingForTaxCharges
import models.financialStatement.SchemeFSChargeType._
import models.financialStatement._
import models.viewModels.paymentsAndCharges.PaymentAndChargeStatus
import models.viewModels.paymentsAndCharges.PaymentAndChargeStatus.{InterestIsAccruing, PaymentOverdue}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.mockito.MockitoSugar
import play.api.i18n.Messages
import play.api.libs.json.Json
import services.SchemeService
import services.financialOverview.psa.PenaltiesCache
import uk.gov.hmrc.govukfrontend.views.Aliases.Text
import uk.gov.hmrc.govukfrontend.views.viewmodels.table.{HeadCell, TableRow}
import utils.AFTConstants._
import utils.DateHelper
import utils.DateHelper.{dateFormatterDMY, dateFormatterStartDate, formatDateDMY}
import uk.gov.hmrc.govukfrontend.views.viewmodels.content.HtmlContent
import uk.gov.hmrc.govukfrontend.views.viewmodels.summarylist.{Key, SummaryListRow, Value}
import uk.gov.hmrc.govukfrontend.views.viewmodels.table.{HeadCell, Table, TableRow}

import java.time.LocalDate
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class PaymentsAndChargesServiceSpec extends SpecBase with MockitoSugar with BeforeAndAfterEach with ScalaFutures {

  import PaymentsAndChargesServiceSpec._

  private def htmlChargeType(
                              isInterestAccrued: Boolean,
                              chargeType: String,
                              chargeReference: String,
                              redirectUrl: String,
                              period: String,
                              visuallyHiddenText: String,
                            ): HtmlContent = {
    val linkId = if (isInterestAccrued) {
      s"$chargeReference-interest"
    } else {
      chargeReference
    }

//    HtmlContent
    HtmlContent(
      s"<a id=$linkId class=govuk-link href=" +
        s"$redirectUrl>" +
        s"$chargeType " +
        s"<span class=govuk-visually-hidden>$visuallyHiddenText</span> </a>" +
        s"<p class=govuk-hint>" +
        s"$period</p>")
  }

  private val tableHead = Seq(
    HeadCell(Text(messages("paymentsAndCharges.chargeType.table")), classes = "govuk-!-width-one-half"),
    HeadCell(Text(messages("paymentsAndCharges.chargeReference.table")), classes = "govuk-!-font-weight-bold"),
    HeadCell(Text(messages("paymentsAndCharges.chargeDetails.originalChargeAmount")), classes = "govuk-!-font-weight-bold"),
    HeadCell(Text(messages("paymentsAndCharges.paymentDue.table")), classes = "govuk-!-font-weight-bold"),
    HeadCell(HtmlContent(
      s"<span class='govuk-visually-hidden'>${messages("paymentsAndCharges.chargeDetails.paymentStatus")}</span>"
    ))
  )

  private def paymentTable(rows: Seq[Seq[TableRow]]): Table =
    Table(head = Some(tableHead), rows = rows, attributes = Map("role" -> "table"))


  private def row(isInterestAccrued: Boolean,
                  chargeType: String,
                  displayChargeReference: String,
                  originalChargeAmount: String,
                  paymentDue: String,
                  status: PaymentAndChargeStatus,
                  redirectUrl: String,
                  visuallyHiddenText: String
                 ): Seq[TableRow] = {
    val period = "Quarter: 1 April to 30 June 2020"
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
      TableRow(htmlChargeType(isInterestAccrued, chargeType, "AYU3494534632", redirectUrl, period, visuallyHiddenText),
        classes = "govuk-!-width-one-third"),
      TableRow(Text(s"$displayChargeReference"), classes = "govuk-!-padding-right-7"),
      if (originalChargeAmount.isEmpty) {
        TableRow(HtmlContent(s"""<span class=govuk-visually-hidden>${messages("paymentsAndCharges.chargeDetails.visuallyHiddenText")}</span>"""))
      } else {
        TableRow(Text(originalChargeAmount), classes = "govuk-!-padding-right-7 table-nowrap")
      },
      TableRow(Text(paymentDue), classes = "govuk-!-padding-right-5 table-nowrap"),
      TableRow(statusHtml)
    )
  }

  val mockSchemeService: SchemeService = mock[SchemeService]
  val config: FrontendAppConfig = mock[FrontendAppConfig]

  val mockFSConnector: FinancialStatementConnector = mock[FinancialStatementConnector]
  val mockFIConnector: FinancialInfoCacheConnector = mock[FinancialInfoCacheConnector]

  private val paymentsAndChargesService = new PaymentsAndChargesService(mockSchemeService, mockFSConnector, mockFIConnector)

  // scalastyle:off method.length
  "getPaymentsAndCharges" must {
    Seq(PSS_AFT_RETURN, PSS_OTC_AFT_RETURN).foreach {
      chargeType =>
        s"return payments and charges table with two rows for the charge and interest accrued for $chargeType" in {

          val chargeLink: ChargeDetailsFilter => String = chargeDetailsFilter =>
            PaymentsAndChargeDetailsController.onPageLoad(srn, QUARTER_START_DATE.toString, "1", AccountingForTaxCharges,
              Some(versionInt), Some("2016-12-17"), chargeDetailsFilter).url

          val interestLink: ChargeDetailsFilter => String = chargeDetailsFilter =>
            PaymentsAndChargesInterestController.onPageLoad(srn, QUARTER_START_DATE.toString, "1", AccountingForTaxCharges,
              Some(versionInt), Some("2016-12-17"), chargeDetailsFilter).url

          def expectedTable(chargeLink: String, interestLink: String): Table =
            paymentTable(Seq(
              row(
                isInterestAccrued = false,
                chargeType = chargeType.toString + s" submission $versionInt",
                displayChargeReference = "AYU3494534632",
                originalChargeAmount = FormatHelper.formatCurrencyAmountAsString(56432.00),
                paymentDue = FormatHelper.formatCurrencyAmountAsString(1029.05),
                status = PaymentAndChargeStatus.PaymentOverdue,
                redirectUrl = chargeLink,
                visuallyHiddenText = messages(s"paymentsAndCharges.visuallyHiddenText", "AYU3494534632")
              ),
              row(
                isInterestAccrued = true,
                chargeType = if (chargeType == PSS_AFT_RETURN) {
                  PSS_AFT_RETURN_INTEREST.toString + s" submission $versionInt"
                } else {
                  PSS_OTC_AFT_RETURN_INTEREST.toString + s" submission $versionInt"
                },
                displayChargeReference = messages("paymentsAndCharges.chargeReference.toBeAssigned"),
                originalChargeAmount = "",
                paymentDue = FormatHelper.formatCurrencyAmountAsString(153.00),
                status = PaymentAndChargeStatus.InterestIsAccruing,
                redirectUrl = interestLink,
                visuallyHiddenText = messages(s"paymentsAndCharges.interest.visuallyHiddenText")
              )
            ))

          val result1 = paymentsAndChargesService.getPaymentsAndCharges(
            srn = srn,
            schemeFSDetail = paymentsAndChargesForAGivenPeriod(chargeType).head._2,
            chargeDetailsFilter = Overdue,
            config = config
          )

          val result2 = paymentsAndChargesService.getPaymentsAndCharges(
            srn = srn,
            schemeFSDetail = paymentsAndChargesForAGivenPeriod(chargeType).head._2,
            chargeDetailsFilter = Upcoming,
            config = config
          )
          result1 mustBe expectedTable(chargeLink(Overdue), interestLink(Overdue))
          result2 mustBe expectedTable(chargeLink(Upcoming), interestLink(Upcoming))
        }
    }

    "return payments and charges table with no rows for credit" in {

      val totalAmount = -56432.00
      val expectedTable = paymentTable(Seq.empty)

      val result = paymentsAndChargesService.getPaymentsAndCharges(
        srn,
        paymentsAndChargesForAGivenPeriod(PSS_OTC_AFT_RETURN, totalAmount, amountDue = 0.00).head._2,
        Overdue,
        config = config
      )

      result mustBe expectedTable
    }
  }

  "getChargeDetailsForSelectedCharge" must {
    "return the row for original charge amount, payments and credits, stood over amount and total amount due" in {
      val result =
        paymentsAndChargesService.getChargeDetailsForSelectedCharge(createCharge(index = 1, PSS_AFT_RETURN, totalAmount = 56432.00, amountDue = 1029.05), Upcoming, Some(submittedDate))

      result mustBe dateSubmittedRow ++ chargeReferenceRow ++ originalAmountChargeDetailsRow ++
        clearingChargeDetailsRow ++ stoodOverAmountChargeDetailsRow ++ totalAmountDueChargeDetailsRow
    }

    "return the row for original charge amount, payments and credits, stood over amount and total amount due with payment or credit due date not exist" in {
      val result =
        paymentsAndChargesService.getChargeDetailsForSelectedCharge(createCharge(index = 1, PSS_AFT_RETURN, totalAmount = 56432.00, amountDue = 1029.05, item = item2), Upcoming, Some(submittedDate))

      result mustBe dateSubmittedRow ++ chargeReferenceRow ++ originalAmountChargeDetailsRow ++
        clearingChargeDetailsWithClearingDateRow ++ stoodOverAmountChargeDetailsRow ++ totalAmountDueChargeDetailsRow
    }
  }

  "isPaymentOverdue" must {
    "return true if the amount due is positive and due date is before today" in {
      val schemeFSDetail: SchemeFSDetail = schemeFSResponseAftAndOTC.seqSchemeFSDetail.head
      paymentsAndChargesService.isPaymentOverdue(schemeFSDetail) mustBe true
    }

    "return false if the amount due is negative and due date is before today" in {
      val schemeFSDetail: SchemeFSDetail = schemeFSResponseAftAndOTC.seqSchemeFSDetail.head.copy(amountDue = BigDecimal(0.00))
      paymentsAndChargesService.isPaymentOverdue(schemeFSDetail) mustBe false
    }

    "return true if the amount due is positive and due date is today" in {
      val schemeFSDetail: SchemeFSDetail = schemeFSResponseAftAndOTC.seqSchemeFSDetail.head.copy(dueDate = Some(LocalDate.now()))
      paymentsAndChargesService.isPaymentOverdue(schemeFSDetail) mustBe false
    }

    "return true if the amount due is positive and due date is none" in {
      val schemeFSDetail: SchemeFSDetail = schemeFSResponseAftAndOTC.seqSchemeFSDetail.head.copy(dueDate = None)
      paymentsAndChargesService.isPaymentOverdue(schemeFSDetail) mustBe false
    }
  }

  "getUpcomingChargesScheme" must {
    "only return charges with a dueDate in the future" in {
      DateHelper.setDate(Some(LocalDate.of(2020, 6, 1)))

      val charges = Seq(
        createCharge(index = 1, PSS_AFT_RETURN, 123.00, 456.00),
        createCharge(index = 2, PSS_OTC_AFT_RETURN, 123.00, 456.00, Some(LocalDate.parse("2020-08-15")))
      )

      val result = paymentsAndChargesService.extractUpcomingCharges(charges)
      result.size mustBe 1
      result.head.chargeType mustBe PSS_OTC_AFT_RETURN
    }
  }

  "getOverdueCharges" must {
    "only return charges with a dueDate in the past" in {
      DateHelper.setDate(Some(LocalDate.of(2020, 6, 1)))

      val charges = Seq(
        createCharge(index = 1, PSS_AFT_RETURN, 123.00, 456.00),
        createCharge(index = 2, PSS_OTC_AFT_RETURN, 123.00, 456.00, Some(LocalDate.parse("2020-08-15")))
      )

      paymentsAndChargesService.getOverdueCharges(charges).size mustBe 1
      paymentsAndChargesService.getOverdueCharges(charges).head.chargeType mustBe PSS_AFT_RETURN
    }
  }

  "getDueCharges" must {
    "only return charges with amountDue > = £0" in {
      DateHelper.setDate(Some(LocalDate.of(2020, 6, 1)))

      val charges = Seq(
        createCharge(index = 1, PSS_AFT_RETURN, 123.00, 456.00),
        createCharge(index = 2, PSS_AFT_RETURN, 123.00, -456.00),
        createCharge(index = 3, PSS_OTC_AFT_RETURN, 123.00, 0.00)
      )

      paymentsAndChargesService.getDueCharges(charges).size mustBe 2
      paymentsAndChargesService.getDueCharges(charges).head.chargeType mustBe PSS_AFT_RETURN
    }
  }

  "getInterestCharges" must {
    "only return charges with accruedInterest >= 0" in {
      DateHelper.setDate(Some(LocalDate.of(2020, 6, 1)))

      val charges = Seq(
        createCharge(index = 1, PSS_AFT_RETURN, 123.00, 456.00),
        createCharge(index = 2, PSS_AFT_RETURN_INTEREST, 123.00, 456.00, accruedInterestTotal = Some(0.00)),
        createCharge(index = 3, PSS_OTC_AFT_RETURN, 123.00, 0.00, accruedInterestTotal = Some(-12.00))
      )

      paymentsAndChargesService.getInterestCharges(charges).size mustBe 2
      paymentsAndChargesService.getInterestCharges(charges).head.chargeType mustBe PSS_AFT_RETURN
    }
  }

  "getReturnLinkBasedOnJourney" must {
    "return schemeName if journey is All" in {
      paymentsAndChargesService.getReturnLinkBasedOnJourney(All, schemeName) mustBe schemeName
    }

    "return schemeName if journey is Overdue" in {
      paymentsAndChargesService.getReturnLinkBasedOnJourney(Overdue, schemeName) mustBe "your Overdue payments and charges"
    }

    "return schemeName if journey is Upcoming" in {
      paymentsAndChargesService.getReturnLinkBasedOnJourney(Upcoming, schemeName) mustBe "your due payments and charges"
    }
  }

  "getPaymentsForJourney" must {
    "return payload from cache is srn and logged in id match the payload" in {
      when(mockFIConnector.fetch(any(), any()))
        .thenReturn(Future.successful(Some(Json.toJson(paymentsCache))))
      whenReady(paymentsAndChargesService.getPaymentsForJourney(psaId, srn, All)) {
        _ mustBe paymentsCache
      }
    }

    "call FS API and save to cache if srn does not match the retrieved payload from cache" in {
      when(mockFIConnector.fetch(any(), any())).thenReturn(Future.successful(Some(Json.toJson(paymentsCache.copy(srn = "wrong-srn")))))
      when(mockSchemeService.retrieveSchemeDetails(any(), any(), any())(any(), any())).thenReturn(Future.successful(schemeDetails))
      when(mockFSConnector.getSchemeFS(any())(any(), any())).thenReturn(Future.successful(SchemeFS(seqSchemeFSDetail = Seq(chargeWithCredit(index = 1)))))
      when(mockFIConnector.save(any())(any(), any())).thenReturn(Future.successful(Json.obj()))
      whenReady(paymentsAndChargesService.getPaymentsForJourney(psaId, srn, All)) {
        _ mustBe paymentsCache.copy(schemeFSDetail = Seq(chargeWithCredit(index = 1)))
      }
    }

    "call FS API and save to cache if logged in id does not match the retrieved payload from cache" in {
      when(mockFIConnector.fetch(any(), any())).thenReturn(Future.successful(Some(Json.toJson(paymentsCache.copy(loggedInId = "wrong-id")))))
      when(mockSchemeService.retrieveSchemeDetails(any(), any(), any())(any(), any())).thenReturn(Future.successful(schemeDetails))
      when(mockFSConnector.getSchemeFS(any())(any(), any())).thenReturn(Future.successful(SchemeFS(seqSchemeFSDetail = Seq(chargeWithCredit(index = 1)))))
      when(mockFIConnector.save(any())(any(), any())).thenReturn(Future.successful(Json.obj()))
      whenReady(paymentsAndChargesService.getPaymentsForJourney(psaId, srn, All)) {
        _ mustBe paymentsCache.copy(schemeFSDetail = Seq(chargeWithCredit(index = 1)))
      }
    }

    "call FS API and save to cache if retrieved payload from cache is not in Payments format" in {
      when(mockFIConnector.fetch(any(), any())).thenReturn(Future.successful(Some(Json.toJson(PenaltiesCache(psaId, "name", Nil)))))
      when(mockSchemeService.retrieveSchemeDetails(any(), any(), any())(any(), any())).thenReturn(Future.successful(schemeDetails))
      when(mockFSConnector.getSchemeFS(any())(any(), any())).thenReturn(Future.successful(schemeFSResponseAftAndOTC))
      when(mockFIConnector.save(any())(any(), any())).thenReturn(Future.successful(Json.obj()))
      whenReady(paymentsAndChargesService.getPaymentsForJourney(psaId, srn, All)) {
        _ mustBe paymentsCache
      }
    }

    "call FS API and save to cache if there is no existing payload stored in cache" in {
      when(mockFIConnector.fetch(any(), any())).thenReturn(Future.successful(None))
      when(mockSchemeService.retrieveSchemeDetails(any(), any(), any())(any(), any())).thenReturn(Future.successful(schemeDetails))
      when(mockFSConnector.getSchemeFS(any())(any(), any())).thenReturn(Future.successful(schemeFSResponseAftAndOTC))
      when(mockFIConnector.save(any())(any(), any())).thenReturn(Future.successful(Json.obj()))
      whenReady(paymentsAndChargesService.getPaymentsForJourney(psaId, srn, All)) {
        _ mustBe paymentsCache
      }
    }

    "return upcoming charges only for upcoming filter" in {
      DateHelper.setDate(Some(LocalDate.parse("2020-01-31")))
      when(mockFIConnector.fetch(any(), any()))
        .thenReturn(Future.successful(Some(Json.toJson(paymentsCache))))
      whenReady(paymentsAndChargesService.getPaymentsForJourney(psaId, srn, Upcoming)) {
        _ mustBe paymentsCache
      }
    }

    "return overdue charges only for overdue filter" in {
      DateHelper.setDate(Some(LocalDate.parse("2020-12-31")))
      when(mockFIConnector.fetch(any(), any()))
        .thenReturn(Future.successful(Some(Json.toJson(paymentsCache))))
      whenReady(paymentsAndChargesService.getPaymentsForJourney(psaId, srn, Overdue)) {
        _ mustBe paymentsCache
      }
    }
  }
}

object PaymentsAndChargesServiceSpec {
  val srn = "S1234567"
  val startDate: String = QUARTER_START_DATE.format(dateFormatterStartDate)
  val endDate: String = QUARTER_END_DATE.format(dateFormatterDMY)
  val paymentsCache: PaymentsCache = PaymentsCache(psaId, srn, schemeDetails, schemeFSResponseAftAndOTC.seqSchemeFSDetail)
  val item: DocumentLineItemDetail = DocumentLineItemDetail(150.00, Some(LocalDate.parse("2020-05-14")), Some(LocalDate.parse("2020-04-24")), Some(FSClearingReason.CLEARED_WITH_PAYMENT))
  val item2: DocumentLineItemDetail = DocumentLineItemDetail(150.00, Some(LocalDate.parse("2020-04-15")), None, Some(FSClearingReason.CLEARED_WITH_PAYMENT))

  private def createCharge(index: Int,
                           chargeType: SchemeFSChargeType,
                           totalAmount: BigDecimal,
                           amountDue: BigDecimal,
                           dueDate: Option[LocalDate] = Some(LocalDate.parse("2020-05-15")),
                           accruedInterestTotal: Option[BigDecimal] = Some(153.00),
                           item: DocumentLineItemDetail = item
                          ): SchemeFSDetail = {
    SchemeFSDetail(
      index = index,
      chargeReference = "AYU3494534632",
      chargeType = chargeType,
      dueDate = dueDate,
      totalAmount = totalAmount,
      outstandingAmount = 56049.08,
      stoodOverAmount = 25089.08,
      amountDue = amountDue,
      accruedInterestTotal = accruedInterestTotal.get,
      periodStartDate = Some(QUARTER_START_DATE),
      periodEndDate = Some(QUARTER_END_DATE),
      formBundleNumber = None,
      version = Some(1),
      receiptDate = Some(LocalDate.parse("2016-12-17")),
      sourceChargeRefForInterest = None,
      sourceChargeInfo = None,
      documentLineItemDetails = Seq(item)
    )
  }

  private def chargeWithCredit(index: Int) = SchemeFSDetail(
    index = index,
    chargeReference = "AYU3494534632",
    chargeType = PSS_AFT_RETURN,
    dueDate = Some(LocalDate.parse("2020-05-15")),
    totalAmount = -20000.00,
    outstandingAmount = 0.00,
    stoodOverAmount = 0.00,
    amountDue = 0.00,
    accruedInterestTotal = 0.00,
    periodStartDate = Some(QUARTER_START_DATE),
    periodEndDate = Some(QUARTER_END_DATE),
    formBundleNumber = None,
    version = None,
    receiptDate = None,
    sourceChargeRefForInterest = None,
    sourceChargeInfo = None,
    documentLineItemDetails = Nil
  )

  private def paymentsAndChargesForAGivenPeriod(chargeType: SchemeFSChargeType,
                                                totalAmount: BigDecimal = 56432.00,
                                                amountDue: BigDecimal = 1029.05): Seq[(LocalDate, Seq[SchemeFSDetail])] = Seq(
    (
      LocalDate.parse(QUARTER_START_DATE.toString),
      Seq(
        createCharge(index = 1, chargeType, totalAmount, amountDue)
      )
    )
  )

  private def dateSubmittedRow(implicit messages: Messages): Seq[SummaryListRow] = {
    Seq(
      SummaryListRow(
        key = Key(Text(messages("financialPaymentsAndCharges.dateSubmitted")), classes = "govuk-!-padding-left-0 govuk-!-width-one-half"),
        value = Value(
          Text(s"${DateHelper.formatDateDMYString(submittedDate)}"), classes = "govuk-!-width-one-quarter")
      ))
  }

  private def chargeReferenceRow(implicit messages: Messages): Seq[SummaryListRow]  = {
    Seq(
      SummaryListRow(
        key = Key(
          content = Text(messages("financialPaymentsAndCharges.chargeReference")),
          classes = "govuk-!-padding-left-0 govuk-!-width-one-half"
        ),
        value = Value(
          content = Text("AYU3494534632"),
          classes = "govuk-!-width-one-quarter"),
        actions = None
      ))
  }

  private def originalAmountChargeDetailsRow(implicit messages: Messages): Seq[SummaryListRow] = {
    Seq(
      SummaryListRow(
        key = Key(
          content = Text(messages("paymentsAndCharges.chargeDetails.originalChargeAmount")),
          classes = "govuk-!-padding-left-0 govuk-!-width-one-half"
        ),
        value = Value(
          content = Text(s"${formatCurrencyAmountAsString(56432.00)}"),
          classes = "govuk-!-width-one-quarter"
        ),
        actions = None
      ))
  }

  private def stoodOverAmountChargeDetailsRow(implicit messages: Messages): Seq[SummaryListRow] = {
    Seq(
      SummaryListRow(
        key = Key(
          content = Text(messages("paymentsAndCharges.chargeDetails.stoodOverAmount")),
          classes = "govuk-!-padding-left-0 govuk-!-width-one-half"
        ),
        value = Value(
          content = Text(s"-${formatCurrencyAmountAsString(25089.08)}"),
          classes = "govuk-!-width-one-quarter"
        ),
        actions = None
      ))
  }

  private def totalAmountDueChargeDetailsRow(implicit messages: Messages): Seq[SummaryListRow] = {
    Seq(
      SummaryListRow(
        key = Key(
          content = Text(messages("financialPaymentsAndCharges.paymentDue.upcoming.dueDate", LocalDate.parse("2020-05-15").format(dateFormatterDMY))),
          classes = "govuk-!-padding-left-0 govuk-!-width-one-half"
        ),
        value = Value(
          Text(s"${FormatHelper.formatCurrencyAmountAsString(1029.05)}"),
          classes = "govuk-!-padding-left-0 govuk-!-width--one-half govuk-!-font-weight-bold"
        ),
        actions = None
      ))
  }

  private def clearingChargeDetailsRow(implicit messages: Messages): Seq[SummaryListRow] = {
    Seq(
      SummaryListRow(
        key = Key(
          content = Text(messages("financialPaymentsAndCharges.clearingReason.c1", formatDateDMY(LocalDate.parse("2020-04-24")))),
          classes = "govuk-!-padding-left-0 govuk-!-width-one-half"
        ),
        value = Value(
          content = Text(s"-${formatCurrencyAmountAsString(150)}"),
          classes = "govuk-!-width-one-quarter"
        ),
        actions = None
      ))
  }

  private def clearingChargeDetailsWithClearingDateRow(implicit messages: Messages): Seq[SummaryListRow] = {
    Seq(
      SummaryListRow(
        key = Key(
          content = Text(messages("financialPaymentsAndCharges.clearingReason.c1", formatDateDMY(LocalDate.parse("2020-04-15")))),
          classes = "govuk-!-padding-left-0 govuk-!-width-one-half"
        ),
        value = Value(
          content = Text(s"-${formatCurrencyAmountAsString(150)}"),
          classes = "govuk-!-width-one-quarter"
        ),
        actions = None
      ))
  }
}
