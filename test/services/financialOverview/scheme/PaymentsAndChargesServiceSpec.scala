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
import connectors.FinancialStatementConnector
import connectors.cache.FinancialInfoCacheConnector
import controllers.chargeB.{routes => _}
import controllers.financialOverview.scheme.routes._
import data.SampleData._
import helpers.FormatHelper
import models.ChargeDetailsFilter
import models.ChargeDetailsFilter._
import models.financialStatement.PaymentOrChargeType.AccountingForTaxCharges
import models.financialStatement.SchemeFSChargeType._
import models.financialStatement._
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
import uk.gov.hmrc.govukfrontend.views.viewmodels.content.HtmlContent
import uk.gov.hmrc.govukfrontend.views.viewmodels.summarylist.{Key, SummaryListRow, Value}
import uk.gov.hmrc.govukfrontend.views.viewmodels.table.{HeadCell, Table, TableRow}
import utils.AFTConstants._
import utils.DateHelper
import utils.DateHelper.{dateFormatterDMY, dateFormatterStartDate, formatDateDMY}

import java.time.LocalDate
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class PaymentsAndChargesServiceSpec extends SpecBase with MockitoSugar with BeforeAndAfterEach with ScalaFutures {

  import PaymentsAndChargesServiceSpec._

  private def htmlChargeType(
                              isInterestAccrued: Boolean,
                              chargeType: String,
                              chargeReference: String,
                              displayChargeReference: String,
                              redirectUrl: String,
                              visuallyHiddenText: String,
                            ): HtmlContent = {
    val linkId = if (isInterestAccrued) {
      s"$chargeReference-interest"
    } else {
      chargeReference
    }

    val period = s"${if (isInterestAccrued) "Quarter: " else ""}1 April to 30 June 2020"

    HtmlContent(
      s"<a id=$linkId class=govuk-link href=" +
        s"$redirectUrl>" +
        s"$chargeType " +
        s"<span class=govuk-visually-hidden>$visuallyHiddenText</span></a>" +
        s"<p class=govuk-hint>$displayChargeReference</br>" +
        s"$period")
  }

  private val tableHead = Seq(
    HeadCell(
      HtmlContent(
        s"<span class='govuk-visually-hidden'>${messages("psa.financial.overview.penaltyOrCharge")}</span>"
      )),
    HeadCell(Text(Messages("paymentsAndCharges.dateDue.table")), classes = "govuk-!-font-weight-bold table-nowrap"),
    HeadCell(Text(Messages("paymentsAndCharges.chargeDetails.originalChargeAmount.new")), classes = "govuk-!-font-weight-bold table-nowrap"),
    HeadCell(Text(Messages("paymentsAndCharges.paymentDue.table")), classes = "govuk-!-font-weight-bold table-nowrap"),
    HeadCell(Text(Messages("paymentsAndCharges.interestAccruing.table")), classes = "govuk-!-font-weight-bold table-nowrap")
  )

  private def paymentTable(rows: Seq[Seq[TableRow]]): Table =
    Table(head = Some(tableHead), rows = rows, attributes = Map("role" -> "table"))


  private def row(isInterestAccrued: Boolean,
                  chargeType: String,
                  chargeReference: String = "AYU3494534632",
                  displayChargeReference: String,
                  dateDue: String,
                  originalChargeAmount: String,
                  paymentDue: String,
                  redirectUrl: String,
                  visuallyHiddenText: String,
                  accruedInterestTotal: String = "153.00"
                 ): Seq[TableRow] = {

    Seq(
      TableRow(htmlChargeType(isInterestAccrued, chargeType, chargeReference, displayChargeReference, redirectUrl, visuallyHiddenText),
        classes = "govuk-!-width-one-third"),
      TableRow(Text(s"$dateDue"), classes = "govuk-!-padding-right-7, table-nowrap"),
      if (originalChargeAmount.isEmpty) {
        TableRow(HtmlContent(s"""<span class=govuk-visually-hidden>${messages("paymentsAndCharges.chargeDetails.visuallyHiddenText")}</span>"""))
      } else {
        TableRow(Text(originalChargeAmount), classes = "govuk-table__cell govuk-!-padding-right-7 table-nowrap")
      },
      TableRow(Text(paymentDue), classes = "govuk-table__cell govuk-!-padding-right-7 table-nowrap"),
      TableRow(Text(accruedInterestTotal), classes = "govuk-table__cell govuk-table__cell--numeric table-nowrap")
    )
  }

  val mockSchemeService: SchemeService = mock[SchemeService]

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
                isInterestAccrued      = false,
                chargeType             = chargeType.toString + s" submission $versionInt",
                displayChargeReference = "AYU3494534632",
                dateDue                = formatDateDMY(LocalDate.parse("2020-05-15")),
                originalChargeAmount   = FormatHelper.formatCurrencyAmountAsString(56432.00),
                paymentDue             = FormatHelper.formatCurrencyAmountAsString(1029.05),
                redirectUrl            = chargeLink,
                visuallyHiddenText     = messages(s"paymentsAndCharges.visuallyHiddenText", "AYU3494534632"),
              ),
              row(
                isInterestAccrued      = true,
                chargeType             = if (chargeType == PSS_AFT_RETURN) {
                  PSS_AFT_RETURN_INTEREST.toString + s" submission $versionInt"
                } else {
                  PSS_OTC_AFT_RETURN_INTEREST.toString + s" submission $versionInt"
                },
                displayChargeReference = messages("paymentsAndCharges.chargeReference.toBeAssigned"),
                dateDue                = formatDateDMY(LocalDate.parse("2020-05-15")),
                originalChargeAmount   = "",
                paymentDue             = FormatHelper.formatCurrencyAmountAsString(153.00),
                redirectUrl            = interestLink,
                visuallyHiddenText     = messages(s"paymentsAndCharges.interest.visuallyHiddenText"),
              )
            ))

          val result1 = paymentsAndChargesService.getPaymentsAndCharges(
            srn = srn,
            schemeFSDetail = paymentsAndChargesForAGivenPeriod(chargeType).head._2,
            chargeDetailsFilter = Overdue
          )

          val result2 = paymentsAndChargesService.getPaymentsAndCharges(
            srn = srn,
            schemeFSDetail = paymentsAndChargesForAGivenPeriod(chargeType).head._2,
            chargeDetailsFilter = Upcoming
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
        Overdue
      )

      result mustBe expectedTable
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
      whenReady(paymentsAndChargesService.getPaymentsForJourney(psaId, srn, All, isLoggedInAsPsa = true)) {
        _ mustBe paymentsCache
      }
    }

    "call FS API and save to cache if srn does not match the retrieved payload from cache" in {
      when(mockFIConnector.fetch(any(), any())).thenReturn(Future.successful(Some(Json.toJson(paymentsCache.copy(srn = "wrong-srn")))))
      when(mockSchemeService.retrieveSchemeDetails(any(), any())(any(), any())).thenReturn(Future.successful(schemeDetails))
      when(mockFSConnector.getSchemeFS(any(), any(), any())(any(), any())).thenReturn(Future.successful(SchemeFS(seqSchemeFSDetail = Seq(chargeWithCredit(index = 1)))))
      when(mockFIConnector.save(any())(any(), any())).thenReturn(Future.successful(Json.obj()))
      whenReady(paymentsAndChargesService.getPaymentsForJourney(psaId, srn, All, isLoggedInAsPsa = true)) {
        _ mustBe paymentsCache.copy(schemeFSDetail = Seq(chargeWithCredit(index = 1)))
      }
    }

    "call FS API and save to cache if logged in id does not match the retrieved payload from cache" in {
      when(mockFIConnector.fetch(any(), any())).thenReturn(Future.successful(Some(Json.toJson(paymentsCache.copy(loggedInId = "wrong-id")))))
      when(mockSchemeService.retrieveSchemeDetails(any(), any())(any(), any())).thenReturn(Future.successful(schemeDetails))
      when(mockFSConnector.getSchemeFS(any(), any(), any())(any(), any())).thenReturn(Future.successful(SchemeFS(seqSchemeFSDetail = Seq(chargeWithCredit(index = 1)))))
      when(mockFIConnector.save(any())(any(), any())).thenReturn(Future.successful(Json.obj()))
      whenReady(paymentsAndChargesService.getPaymentsForJourney(psaId, srn, All, isLoggedInAsPsa = true)) {
        _ mustBe paymentsCache.copy(schemeFSDetail = Seq(chargeWithCredit(index = 1)))
      }
    }

    "call FS API and save to cache if retrieved payload from cache is not in Payments format" in {
      when(mockFIConnector.fetch(any(), any())).thenReturn(Future.successful(Some(Json.toJson(PenaltiesCache(psaId, "name", Nil)))))
      when(mockSchemeService.retrieveSchemeDetails(any(), any())(any(), any())).thenReturn(Future.successful(schemeDetails))
      when(mockFSConnector.getSchemeFS(any(), any(), any())(any(), any())).thenReturn(Future.successful(schemeFSResponseAftAndOTC))
      when(mockFIConnector.save(any())(any(), any())).thenReturn(Future.successful(Json.obj()))
      whenReady(paymentsAndChargesService.getPaymentsForJourney(psaId, srn, All, isLoggedInAsPsa = true)) {
        _ mustBe paymentsCache
      }
    }

    "call FS API and save to cache if there is no existing payload stored in cache" in {
      when(mockFIConnector.fetch(any(), any())).thenReturn(Future.successful(None))
      when(mockSchemeService.retrieveSchemeDetails(any(), any())(any(), any())).thenReturn(Future.successful(schemeDetails))
      when(mockFSConnector.getSchemeFS(any(), any(), any())(any(), any())).thenReturn(Future.successful(schemeFSResponseAftAndOTC))
      when(mockFIConnector.save(any())(any(), any())).thenReturn(Future.successful(Json.obj()))
      whenReady(paymentsAndChargesService.getPaymentsForJourney(psaId, srn, All, isLoggedInAsPsa = true)) {
        _ mustBe paymentsCache
      }
    }

    "return upcoming charges only for upcoming filter" in {
      DateHelper.setDate(Some(LocalDate.parse("2020-01-31")))
      when(mockFIConnector.fetch(any(), any()))
        .thenReturn(Future.successful(Some(Json.toJson(paymentsCache))))
      whenReady(paymentsAndChargesService.getPaymentsForJourney(psaId, srn, Upcoming, isLoggedInAsPsa = true)) {
        val upcomingPaymentsCache = PaymentsCache(psaId, srn, schemeDetails, unpaidCharges)
        _ mustBe upcomingPaymentsCache
      }
    }

    "return overdue charges only for overdue filter" in {
      DateHelper.setDate(Some(LocalDate.parse("2020-12-31")))
      when(mockFIConnector.fetch(any(), any()))
        .thenReturn(Future.successful(Some(Json.toJson(paymentsCache))))
      whenReady(paymentsAndChargesService.getPaymentsForJourney(psaId, srn, Overdue, isLoggedInAsPsa = true)) {
        val overduePaymentsCache = PaymentsCache(psaId, srn, schemeDetails, unpaidCharges)
        _ mustBe overduePaymentsCache
      }
    }
  }

  "setPeriodNew" must {
    "return correct string for AFT charge type" in {
      val result = paymentsAndChargesService.setPeriodNew(SchemeFSChargeType.PSS_AFT_RETURN, Some(QUARTER_START_DATE), Some(QUARTER_END_DATE))
      result mustBe "1 April to 30 June 2020"
    }
    "return correct string for Contract Settlement charge type" in {
      val result = paymentsAndChargesService.setPeriodNew(SchemeFSChargeType.CONTRACT_SETTLEMENT, Some(QUARTER_START_DATE), Some(QUARTER_END_DATE))
      result mustBe "1 April to 30 June 2020"
    }
    "return correct string for Excess Relief charge type" in {
      val result = paymentsAndChargesService.setPeriodNew(SchemeFSChargeType.EXCESS_RELIEF_PAID, Some(QUARTER_START_DATE), Some(QUARTER_END_DATE))
      result mustBe "1 April 2020 to 30 June 2020"
    }
  }

  "getChargeDetailsForSelectedCharge" must {
    "return expected Summary List Rows" in {
      val schemeFSDetails = schemeFSResponseAftAndOTC.seqSchemeFSDetail.head
      val result = paymentsAndChargesService.getChargeDetailsForSelectedCharge(schemeFSDetails, schemeDetails, isClearedCharge = true)
      val pstrRow = Seq(
        SummaryListRow(
          key = Key(Text(Messages("pension.scheme.tax.reference.new")), classes = "govuk-!-padding-left-0 govuk-!-width-one-half"),
          value = Value(Text(s"${schemeDetails.pstr}"), classes = "govuk-!-width-one-half")
        ))

      val chargeReferenceRow = Seq(
        SummaryListRow(
          key = Key(Text(Messages("financialPaymentsAndCharges.chargeReference")), classes = "govuk-!-padding-left-0 govuk-!-width-one-half"),
          value = Value(Text(schemeFSDetails.chargeReference), classes = "govuk-!-width-one-quarter")
        ))

      val taxPeriod = Seq(
        SummaryListRow(
          key = Key(Text(Messages("pension.scheme.interest.tax.period.new")), classes = "govuk-!-padding-left-0 govuk-!-width-one-half"),
          value = Value(Text("1 April to 30 June 2020"), classes = "govuk-!-width-one-half")
        ))

      result mustBe pstrRow ++ chargeReferenceRow ++ taxPeriod
    }
  }
}

object PaymentsAndChargesServiceSpec {
  val srn = "S1234567"
  val startDate: String = QUARTER_START_DATE.format(dateFormatterStartDate)
  val endDate: String = QUARTER_END_DATE.format(dateFormatterDMY)
  val unpaidCharges: Seq[SchemeFSDetail] = schemeFSResponseAftAndOTC.seqSchemeFSDetail.dropRight(1)
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

}