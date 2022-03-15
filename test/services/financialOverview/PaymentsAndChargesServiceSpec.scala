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

package services.financialOverview

import base.SpecBase
import connectors.FinancialStatementConnector
import connectors.cache.FinancialInfoCacheConnector
import controllers.chargeB.{routes => _}
import controllers.financialOverview.routes.{PaymentsAndChargeDetailsController, PaymentsAndChargesInterestController}
import data.SampleData._
import helpers.FormatHelper
import helpers.FormatHelper.formatCurrencyAmountAsString
import models.ChargeDetailsFilter
import models.ChargeDetailsFilter._
import models.financialStatement.PaymentOrChargeType.AccountingForTaxCharges
import models.financialStatement.SchemeFSChargeType._
import models.financialStatement.{DocumentLineItemDetail, SchemeFSDetail, SchemeFSChargeType, SchemeFSClearingReason}
import models.viewModels.paymentsAndCharges.PaymentAndChargeStatus
import models.viewModels.paymentsAndCharges.PaymentAndChargeStatus.{InterestIsAccruing, PaymentOverdue}
import org.mockito.ArgumentMatchers.any
import org.mockito.MockitoSugar
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import play.api.libs.json.Json
import services.{PenaltiesCache, SchemeService}
import uk.gov.hmrc.viewmodels.SummaryList.{Key, Row, Value}
import uk.gov.hmrc.viewmodels.Text.Literal
import uk.gov.hmrc.viewmodels._
import utils.AFTConstants._
import utils.DateHelper
import utils.DateHelper.{dateFormatterDMY, dateFormatterStartDate, formatDateDMY}
import viewmodels.Table
import viewmodels.Table.Cell

import java.time.LocalDate
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class PaymentsAndChargesServiceSpec extends SpecBase with MockitoSugar with BeforeAndAfterEach with ScalaFutures {

  import PaymentsAndChargesServiceSpec._

  private def htmlChargeType(
                              chargeType: String,
                              chargeReference: String,
                              redirectUrl: String,
                              period: String,
                              visuallyHiddenText: String,
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
        s"$chargeType " +
        s"<span class=govuk-visually-hidden>$visuallyHiddenText</span> </a>" +
        s"<p class=govuk-hint>" +
        s"$period</p>")
  }

  private val tableHead = Seq(
    Cell(msg"paymentsAndCharges.chargeType.table", classes = Seq("govuk-!-width-one-half")),
    Cell(msg"paymentsAndCharges.chargeReference.table", classes = Seq("govuk-!-font-weight-bold")),
    Cell(msg"paymentsAndCharges.chargeDetails.originalChargeAmount", classes = Seq("govuk-!-font-weight-bold")),
    Cell(msg"paymentsAndCharges.paymentDue.table", classes = Seq("govuk-!-font-weight-bold")),
    Cell(Html(
      s"<span class='govuk-visually-hidden'>${messages("paymentsAndCharges.chargeDetails.paymentStatus")}</span>"
    ))
  )

  private def paymentTable(rows: Seq[Seq[Table.Cell]]): Table =
    Table(head = tableHead, rows = rows, attributes = Map("role" -> "table"))

  private def row(chargeType: String,
                  chargeReference: String,
                  originalChargeAmount: String,
                  paymentDue: String,
                  status: PaymentAndChargeStatus,
                  redirectUrl: String,
                  period: String,
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
      Cell(htmlChargeType(chargeType, chargeReference, redirectUrl, period, visuallyHiddenText), classes = Seq("govuk-!-width-one-half")),
      Cell(Literal(s"$chargeReference"), classes = Seq("govuk-!-width-one-quarter")),
      Cell(Literal(originalChargeAmount), classes = Seq("govuk-!-width-one-quarter")),
      Cell(Literal(paymentDue), classes = Seq("govuk-!-width-one-quarter")),
      Cell(statusHtml)
    )
  }

  val mockSchemeService: SchemeService = mock[SchemeService]
  val mockFSConnector: FinancialStatementConnector = mock[FinancialStatementConnector]
  val mockFIConnector: FinancialInfoCacheConnector = mock[FinancialInfoCacheConnector]

  private val paymentsAndChargesService = new PaymentsAndChargesService(mockSchemeService, mockFSConnector, mockFIConnector)

  "getPaymentsAndCharges" must {

    Seq(PSS_AFT_RETURN, PSS_OTC_AFT_RETURN).foreach {
      chargeType =>
        s"return payments and charges table with two rows for the charge and interest accrued for $chargeType" in {

          val chargeLink: ChargeDetailsFilter => String = chargeDetailsFilter =>
            PaymentsAndChargeDetailsController.onPageLoad(srn, pstr, QUARTER_START_DATE.toString, "0", AccountingForTaxCharges,
              Some(versionInt), Some("2016-12-17"), chargeDetailsFilter).url

          val interestLink: ChargeDetailsFilter => String = chargeDetailsFilter =>
            PaymentsAndChargesInterestController.onPageLoad(srn, pstr, QUARTER_START_DATE.toString, "0", AccountingForTaxCharges,
              Some(versionInt), Some("2016-12-17"), chargeDetailsFilter).url

          def expectedTable(chargeLink: String, interestLink: String): Table =
            paymentTable(Seq(
              row(
                chargeType = chargeType.toString + s" submission $versionInt",
                chargeReference = "AYU3494534632",
                originalChargeAmount = FormatHelper.formatCurrencyAmountAsString(56432.00),
                paymentDue = FormatHelper.formatCurrencyAmountAsString(1029.05),
                status = PaymentAndChargeStatus.PaymentOverdue,
                redirectUrl = chargeLink,
                period = "Quarter: 1 April to 30 June 2020",
                visuallyHiddenText = messages(s"paymentsAndCharges.visuallyHiddenText", "AYU3494534632")
              ),
              row(
                chargeType = if (chargeType == PSS_AFT_RETURN)
                  PSS_AFT_RETURN_INTEREST.toString + s" submission $versionInt"
                else
                  PSS_OTC_AFT_RETURN_INTEREST.toString + s" submission $versionInt",
                chargeReference = messages("paymentsAndCharges.chargeReference.toBeAssigned"),
                originalChargeAmount = "",
                paymentDue = FormatHelper.formatCurrencyAmountAsString(153.00),
                status = PaymentAndChargeStatus.InterestIsAccruing,
                redirectUrl = interestLink,
                period = "Quarter: 1 April to 30 June 2020",
                visuallyHiddenText = messages(s"paymentsAndCharges.interest.visuallyHiddenText"),
              )
            ))

          val result1 = paymentsAndChargesService.getPaymentsAndCharges(
            srn = srn,
            pstr = pstr,
            schemeFSDetail = paymentsAndChargesForAGivenPeriod(chargeType).head._2,
            mapChargeTypesVersionAndDate = mapChargeTypesVersionAndDate,
            chargeDetailsFilter = Overdue
          )

          val result2 = paymentsAndChargesService.getPaymentsAndCharges(
            srn = srn,
            pstr = pstr,
            schemeFSDetail = paymentsAndChargesForAGivenPeriod(chargeType).head._2,
            mapChargeTypesVersionAndDate = mapChargeTypesVersionAndDate,
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
        pstr,
        paymentsAndChargesForAGivenPeriod(PSS_OTC_AFT_RETURN, totalAmount, amountDue = 0.00).head._2,
        mapChargeTypesVersionAndDate,
        Overdue
      )

      result mustBe expectedTable
    }
  }

  "getChargeDetailsForSelectedCharge" must {
    "return the row for original charge amount, payments and credits, stood over amount and total amount due" in {
      val result =
        paymentsAndChargesService.getChargeDetailsForSelectedCharge(createCharge(PSS_AFT_RETURN, totalAmount = 56432.00, amountDue = 1029.05), Upcoming, Some(submittedDate))

      result mustBe dateSubmittedRow ++ chargeReferenceRow ++ originalAmountChargeDetailsRow ++
        clearingChargeDetailsRow ++ stoodOverAmountChargeDetailsRow ++ totalAmountDueChargeDetailsRow
    }
  }

  "isPaymentOverdue" must {
    "return true if the amount due is positive and due date is before today" in {
      val schemeFSDetail: SchemeFSDetail = schemeFSResponseAftAndOTC.head
      paymentsAndChargesService.isPaymentOverdue(schemeFSDetail) mustBe true
    }

    "return false if the amount due is negative and due date is before today" in {
      val schemeFSDetail: SchemeFSDetail = schemeFSResponseAftAndOTC.head.copy(amountDue = BigDecimal(0.00))
      paymentsAndChargesService.isPaymentOverdue(schemeFSDetail) mustBe false
    }

    "return true if the amount due is positive and due date is today" in {
      val schemeFSDetail: SchemeFSDetail = schemeFSResponseAftAndOTC.head.copy(dueDate = Some(LocalDate.now()))
      paymentsAndChargesService.isPaymentOverdue(schemeFSDetail) mustBe false
    }

    "return true if the amount due is positive and due date is none" in {
      val schemeFSDetail: SchemeFSDetail = schemeFSResponseAftAndOTC.head.copy(dueDate = None)
      paymentsAndChargesService.isPaymentOverdue(schemeFSDetail) mustBe false
    }
  }

  "getUpcomingChargesScheme" must {
    "only return charges with a dueDate in the future" in {
      DateHelper.setDate(Some(LocalDate.of(2020, 6, 1)))

      val charges = Seq(
        createCharge(PSS_AFT_RETURN, 123.00, 456.00),
        createCharge(PSS_OTC_AFT_RETURN, 123.00, 456.00, Some(LocalDate.parse("2020-08-15")))
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
        createCharge(PSS_AFT_RETURN, 123.00, 456.00),
        createCharge(PSS_OTC_AFT_RETURN, 123.00, 456.00, Some(LocalDate.parse("2020-08-15")))
      )

      paymentsAndChargesService.getOverdueCharges(charges).size mustBe 1
      paymentsAndChargesService.getOverdueCharges(charges).head.chargeType mustBe PSS_AFT_RETURN
    }
  }

  "getDueCharges" must {
    "only return charges with amountDue > = £0" in {
      DateHelper.setDate(Some(LocalDate.of(2020, 6, 1)))

      val charges = Seq(
        createCharge(PSS_AFT_RETURN, 123.00, 456.00),
        createCharge(PSS_AFT_RETURN, 123.00, -456.00),
        createCharge(PSS_OTC_AFT_RETURN, 123.00, 0.00)
      )

      paymentsAndChargesService.getDueCharges(charges).size mustBe 2
      paymentsAndChargesService.getDueCharges(charges).head.chargeType mustBe PSS_AFT_RETURN
    }
  }

  "getInterestCharges" must {
    "only return charges with accruedInterest >= 0" in {
      DateHelper.setDate(Some(LocalDate.of(2020, 6, 1)))

      val charges = Seq(
        createCharge(PSS_AFT_RETURN, 123.00, 456.00),
        createCharge(PSS_AFT_RETURN_INTEREST, 123.00, 456.00, accruedInterestTotal = Some(0.00)),
        createCharge(PSS_OTC_AFT_RETURN, 123.00, 0.00, accruedInterestTotal = Some(-12.00))
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
      paymentsAndChargesService.getReturnLinkBasedOnJourney(Overdue, schemeName) mustBe "your overdue payments and charges"
    }

    "return schemeName if journey is Upcoming" in {
      paymentsAndChargesService.getReturnLinkBasedOnJourney(Upcoming, schemeName) mustBe "your due payments and charges"
    }
  }

  "getPaymentsForJourney" must {
    "return payload from cache is srn and logged in id match the payload" in {
      when(mockFIConnector.fetch(any(), any()))
        .thenReturn(Future.successful(Some(Json.toJson(paymentsCache))))
      whenReady(paymentsAndChargesService.getPaymentsForJourney(psaId, srn, All)){ _ mustBe paymentsCache }
    }

    "call FS API and save to cache if srn does not match the retrieved payload from cache" in {
      when(mockFIConnector.fetch(any(), any())).thenReturn(Future.successful(Some(Json.toJson(paymentsCache.copy(srn = "wrong-srn")))))
      when(mockSchemeService.retrieveSchemeDetails(any(), any(), any())(any(), any())).thenReturn(Future.successful(schemeDetails))
      when(mockFSConnector.getSchemeFS(any())(any(), any())).thenReturn(Future.successful(Seq(chargeWithCredit)))
      when(mockFIConnector.save(any())(any(), any())).thenReturn(Future.successful(Json.obj()))
      whenReady(paymentsAndChargesService.getPaymentsForJourney(psaId, srn, All)){ _ mustBe paymentsCache.copy(schemeFSDetail = Seq(chargeWithCredit)) }
    }

    "call FS API and save to cache if logged in id does not match the retrieved payload from cache" in {
      when(mockFIConnector.fetch(any(), any())).thenReturn(Future.successful(Some(Json.toJson(paymentsCache.copy(loggedInId = "wrong-id")))))
      when(mockSchemeService.retrieveSchemeDetails(any(), any(), any())(any(), any())).thenReturn(Future.successful(schemeDetails))
      when(mockFSConnector.getSchemeFS(any())(any(), any())).thenReturn(Future.successful(Seq(chargeWithCredit)))
      when(mockFIConnector.save(any())(any(), any())).thenReturn(Future.successful(Json.obj()))
      whenReady(paymentsAndChargesService.getPaymentsForJourney(psaId, srn, All)){ _ mustBe paymentsCache.copy(schemeFSDetail = Seq(chargeWithCredit)) }
    }

    "call FS API and save to cache if retrieved payload from cache is not in Payments format" in {
      when(mockFIConnector.fetch(any(), any())).thenReturn(Future.successful(Some(Json.toJson(PenaltiesCache(psaId, "name", Nil)))))
      when(mockSchemeService.retrieveSchemeDetails(any(), any(), any())(any(), any())).thenReturn(Future.successful(schemeDetails))
      when(mockFSConnector.getSchemeFS(any())(any(), any())).thenReturn(Future.successful(schemeFSResponseAftAndOTC))
      when(mockFIConnector.save(any())(any(), any())).thenReturn(Future.successful(Json.obj()))
      whenReady(paymentsAndChargesService.getPaymentsForJourney(psaId, srn, All)){ _ mustBe paymentsCache }
    }

    "call FS API and save to cache if there is no existing payload stored in cache" in {
      when(mockFIConnector.fetch(any(), any())).thenReturn(Future.successful(None))
      when(mockSchemeService.retrieveSchemeDetails(any(), any(), any())(any(), any())).thenReturn(Future.successful(schemeDetails))
      when(mockFSConnector.getSchemeFS(any())(any(), any())).thenReturn(Future.successful(schemeFSResponseAftAndOTC))
      when(mockFIConnector.save(any())(any(), any())).thenReturn(Future.successful(Json.obj()))
      whenReady(paymentsAndChargesService.getPaymentsForJourney(psaId, srn, All)){ _ mustBe paymentsCache }
    }

    "return upcoming charges only for upcoming filter" in {
      DateHelper.setDate(Some(LocalDate.parse("2020-01-31")))
      when(mockFIConnector.fetch(any(), any()))
        .thenReturn(Future.successful(Some(Json.toJson(paymentsCache))))
      whenReady(paymentsAndChargesService.getPaymentsForJourney(psaId, srn, Upcoming)){ _ mustBe paymentsCache }
    }

    "return overdue charges only for overdue filter" in {
      DateHelper.setDate(Some(LocalDate.parse("2020-12-31")))
      when(mockFIConnector.fetch(any(), any()))
        .thenReturn(Future.successful(Some(Json.toJson(paymentsCache))))
      whenReady(paymentsAndChargesService.getPaymentsForJourney(psaId, srn, Overdue)){ _ mustBe paymentsCache }
    }
  }
}

object PaymentsAndChargesServiceSpec {
  val srn = "S1234567"
  val startDate: String = QUARTER_START_DATE.format(dateFormatterStartDate)
  val endDate: String = QUARTER_END_DATE.format(dateFormatterDMY)
  val paymentsCache: PaymentsCache = PaymentsCache(psaId, srn, schemeDetails, schemeFSResponseAftAndOTC)
  val item: DocumentLineItemDetail = DocumentLineItemDetail(150.00, Some(LocalDate.parse("2020-05-14")), Some(SchemeFSClearingReason.CLEARED_WITH_PAYMENT))
  private def createCharge(
                            chargeType: SchemeFSChargeType,
                            totalAmount: BigDecimal,
                            amountDue: BigDecimal,
                            dueDate: Option[LocalDate] = Some(LocalDate.parse("2020-05-15")),
                            accruedInterestTotal: Option[BigDecimal] = Some(153.00)
                          ): SchemeFSDetail = {
    SchemeFSDetail(
      chargeReference = "AYU3494534632",
      chargeType = chargeType,
      dueDate = dueDate,
      totalAmount = totalAmount,
      outstandingAmount = 56049.08,
      stoodOverAmount = 25089.08,
      amountDue = amountDue,
      accruedInterestTotal = accruedInterestTotal.get,
      periodStartDate = QUARTER_START_DATE,
      periodEndDate = QUARTER_END_DATE,
      formBundleNumber = None,
      sourceChargeRefForInterest = None,
      documentLineItemDetails = Seq(item)
    )
  }

  private def chargeWithCredit = SchemeFSDetail(
    chargeReference = "AYU3494534632",
    chargeType = PSS_AFT_RETURN,
    dueDate = Some(LocalDate.parse("2020-05-15")),
    totalAmount = -20000.00,
    outstandingAmount = 0.00,
    stoodOverAmount = 0.00,
    amountDue = 0.00,
    accruedInterestTotal = 0.00,
    periodStartDate = QUARTER_START_DATE,
    periodEndDate = QUARTER_END_DATE,
    formBundleNumber = None,
    sourceChargeRefForInterest = None,
    documentLineItemDetails = Nil
  )

  private def paymentsAndChargesForAGivenPeriod(chargeType: SchemeFSChargeType,
                                                totalAmount: BigDecimal = 56432.00,
                                                amountDue: BigDecimal = 1029.05): Seq[(LocalDate, Seq[SchemeFSDetail])] = Seq(
    (
      LocalDate.parse(QUARTER_START_DATE.toString),
      Seq(
        createCharge(chargeType, totalAmount, amountDue)
      )
    )
  )

  private def dateSubmittedRow: Seq[SummaryList.Row] = {
    Seq(
      Row(
        key = Key(msg"financialPaymentsAndCharges.dateSubmitted", classes = Seq("govuk-!-padding-left-0", "govuk-!-width-one-half")),
        value = Value(
          Literal(s"${DateHelper.formatDateDMYString(submittedDate)}"),
          classes = Seq("govuk-!-width-one-quarter")
        ),
        actions = Nil
      ))
  }

  private def chargeReferenceRow: Seq[SummaryList.Row] = {
    Seq(
      Row(
        key = Key(
          content = msg"financialPaymentsAndCharges.chargeReference",
          classes = Seq("govuk-!-padding-left-0", "govuk-!-width-one-half")
        ),
        value = Value(
          content = Literal("AYU3494534632"),
          classes =
            Seq("govuk-!-width-one-quarter")
        ),
        actions = Nil
      ))
  }

  private def originalAmountChargeDetailsRow: Seq[SummaryList.Row] = {
    Seq(
      Row(
        key = Key(
          content = msg"paymentsAndCharges.chargeDetails.originalChargeAmount",
          classes = Seq("govuk-!-padding-left-0", "govuk-!-width-one-half")
        ),
        value = Value(
          content = Literal(s"${formatCurrencyAmountAsString(56432.00)}"),
          classes =
            Seq("govuk-!-width-one-quarter")
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

  private def totalAmountDueChargeDetailsRow: Seq[SummaryList.Row] = {
    Seq(
      Row(
        key = Key(
          content = msg"financialPaymentsAndCharges.paymentDue.upcoming.dueDate".withArgs(LocalDate.parse("2020-05-15").format(dateFormatterDMY)),
          classes = Seq("govuk-!-padding-left-0", "govuk-!-width-one-half")
        ),
        value = Value(
          Literal(s"${FormatHelper.formatCurrencyAmountAsString(1029.05)}"),
          classes = Seq("govuk-!-padding-left-0", "govuk-!-width--one-half", "govuk-!-font-weight-bold")
        ),
        actions = Nil
      ))
  }

  private def clearingChargeDetailsRow: Seq[SummaryList.Row] = {
    Seq(
      Row(
        key = Key(
          content = msg"financialPaymentsAndCharges.clearingReason.c1".withArgs(formatDateDMY(LocalDate.parse("2020-05-14"))),
          classes = Seq("govuk-!-padding-left-0", "govuk-!-width-one-half")
        ),
        value = Value(
          content = Literal(s"-${formatCurrencyAmountAsString(150)}"),
          classes = Seq("govuk-!-width-one-quarter")
        ),
        actions = Nil
    ))
  }

  private def mapChargeTypesVersionAndDate: Map[SchemeFSChargeType, (Option[Int], Option[LocalDate])] = {
    val localDate = LocalDate.parse(submittedDate)
    Map(
      (PSS_AFT_RETURN, (Some(1),Some(localDate))),
      (PSS_OTC_AFT_RETURN,  (Some(1),Some(localDate))),
      (PSS_CHARGE, (Some(1),Some(localDate))),
      (CONTRACT_SETTLEMENT, (Some(1),Some(localDate))))
  }
}
