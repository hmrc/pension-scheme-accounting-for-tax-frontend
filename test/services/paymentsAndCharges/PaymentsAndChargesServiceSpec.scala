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

package services.paymentsAndCharges

import base.SpecBase
import connectors.FinancialStatementConnector
import connectors.cache.FinancialInfoCacheConnector
import controllers.chargeB.{routes => _}
import controllers.financialStatement.paymentsAndCharges.routes.{PaymentsAndChargeDetailsController, PaymentsAndChargesInterestController}
import data.SampleData.{psaId, schemeDetails, schemeFSResponseAftAndOTC}
import helpers.FormatHelper
import models.ChargeDetailsFilter
import models.ChargeDetailsFilter._
import models.financialStatement.PaymentOrChargeType.AccountingForTaxCharges
import models.financialStatement.SchemeFSChargeType._
import models.financialStatement.{SchemeFS, SchemeFSChargeType}
import models.viewModels.paymentsAndCharges.PaymentAndChargeStatus
import models.viewModels.paymentsAndCharges.PaymentAndChargeStatus.{InterestIsAccruing, NoStatus, PaymentOverdue}
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
import utils.DateHelper.{dateFormatterDMY, dateFormatterStartDate}
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
                              visuallyHiddenText: String
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
        s"<span class=govuk-visually-hidden>$visuallyHiddenText</span> </a>")
  }

  private val tableHead = Seq(
    Cell(msg"paymentsAndCharges.chargeType.table"),
    Cell(msg"paymentsAndCharges.totalDue.table", classes = Seq( "govuk-!-font-weight-bold")),
    Cell(msg"paymentsAndCharges.chargeReference.table", classes = Seq("govuk-!-font-weight-bold")),
    Cell(Html(s"<span class='govuk-visually-hidden'>${messages("paymentsAndCharges.chargeDetails.paymentStatus")}</span>"))
  )

  private def paymentTable(rows: Seq[Seq[Table.Cell]]): Table =
    Table(head = tableHead, rows = rows, attributes = Map("role" -> "table"))

  private def row(chargeType: String,
                  chargeReference: String,
                  amountDue: String,
                  status: Html,
                  redirectUrl: String,
                  visuallyHiddenText: String,
                  paymentAndChargeStatus: PaymentAndChargeStatus = NoStatus
                 ): Seq[Table.Cell] = {
    val statusHtml = paymentAndChargeStatus match {
      case InterestIsAccruing => Html(s"<span class='govuk-tag govuk-tag--blue'>${paymentAndChargeStatus.toString}</span>")
      case PaymentOverdue => Html(s"<span class='govuk-tag govuk-tag--red'>${paymentAndChargeStatus.toString}</span>")
      case _ => if (amountDue == "£0.00") {
        Html(s"<span class='govuk-visually-hidden'>${messages("paymentsAndCharges.chargeDetails.visuallyHiddenText.noPaymentDue")}</span>")
      } else {
        Html(s"<span class='govuk-visually-hidden'>${messages("paymentsAndCharges.chargeDetails.visuallyHiddenText.paymentIsDue")}</span>")
      }
    }

    Seq(
      Cell(htmlChargeType(chargeType, chargeReference, redirectUrl, visuallyHiddenText), classes = Seq("govuk-!-width-two-thirds-quarter")),
      Cell(Literal(amountDue), classes = Seq("govuk-!-width-one-quarter")),
      Cell(Literal(s"$chargeReference"), classes = Seq("govuk-!-width-one-quarter")),
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
            PaymentsAndChargeDetailsController.onPageLoad(srn, QUARTER_START_DATE.toString, "0", AccountingForTaxCharges, chargeDetailsFilter).url

          val interestLink: ChargeDetailsFilter => String = chargeDetailsFilter =>
            PaymentsAndChargesInterestController.onPageLoad(srn, QUARTER_START_DATE.toString, "0", AccountingForTaxCharges, chargeDetailsFilter).url

          def expectedTable(chargeLink: String, interestLink: String): Table =
            paymentTable(Seq(
              row(
                chargeType = chargeType.toString,
                chargeReference = "AYU3494534632",
                amountDue = FormatHelper.formatCurrencyAmountAsString(1029.05),
                status = Html(s"<span class='govuk-tag govuk-tag--red'>${PaymentAndChargeStatus.PaymentOverdue.toString}</span>"),
                redirectUrl = chargeLink,
                visuallyHiddenText = messages(s"paymentsAndCharges.visuallyHiddenText", "AYU3494534632"),
                paymentAndChargeStatus = PaymentAndChargeStatus.PaymentOverdue
              ),
              row(
                chargeType = if (chargeType == PSS_AFT_RETURN) PSS_AFT_RETURN_INTEREST.toString else PSS_OTC_AFT_RETURN_INTEREST.toString,
                chargeReference = messages("paymentsAndCharges.chargeReference.toBeAssigned"),
                amountDue = FormatHelper.formatCurrencyAmountAsString(153.00),
                status = Html(s"<span class='govuk-tag govuk-tag--blue'>${PaymentAndChargeStatus.InterestIsAccruing.toString}</span>"),
                redirectUrl = interestLink,
                visuallyHiddenText = messages(s"paymentsAndCharges.interest.visuallyHiddenText"),
                paymentAndChargeStatus = PaymentAndChargeStatus.InterestIsAccruing
              )
            ))

          val result1 = paymentsAndChargesService.getPaymentsAndCharges(
            srn = srn,
            schemeFS = paymentsAndChargesForAGivenPeriod(chargeType).head._2,
            chargeDetailsFilter = All,
            paymentOrChargeType = AccountingForTaxCharges
          )

          val result2 = paymentsAndChargesService.getPaymentsAndCharges(
            srn = srn,
            schemeFS = paymentsAndChargesForAGivenPeriod(chargeType).head._2,
            chargeDetailsFilter = Upcoming,
            paymentOrChargeType = AccountingForTaxCharges
          )

          val result3 = paymentsAndChargesService.getPaymentsAndCharges(
            srn = srn,
            schemeFS = paymentsAndChargesForAGivenPeriod(chargeType).head._2,
            chargeDetailsFilter = Overdue,
            paymentOrChargeType = AccountingForTaxCharges
          )

          result1 mustBe expectedTable(chargeLink(All), interestLink(All))
          result2 mustBe expectedTable(chargeLink(Upcoming), interestLink(Upcoming))
          result3 mustBe expectedTable(chargeLink(Overdue), interestLink(Overdue))
        }
    }

    "return payments and charges table with no rows for credit" in {

      val totalAmount = -56432.00
      val expectedTable = paymentTable(Seq.empty)

      val result = paymentsAndChargesService.getPaymentsAndCharges(
        srn,
        paymentsAndChargesForAGivenPeriod(PSS_OTC_AFT_RETURN, totalAmount, amountDue = 0.00).head._2,
        All,
        AccountingForTaxCharges
      )

      result mustBe expectedTable
    }

    "return payments and charges table with row where there is no amount due" in {

      val expectedTable: Table =
        paymentTable(
          Seq(
            row(
              chargeType = PSS_OTC_AFT_RETURN.toString,
              chargeReference = "AYU3494534632",
              amountDue = FormatHelper.formatCurrencyAmountAsString(0.00),
              status = Html(""),
              redirectUrl = controllers.financialStatement.paymentsAndCharges.routes.PaymentsAndChargeDetailsController
                .onPageLoad(srn, QUARTER_START_DATE.toString, index = "0", AccountingForTaxCharges, All)
                .url,
              visuallyHiddenText = messages(s"paymentsAndCharges.visuallyHiddenText", "AYU3494534632")
            )
          ))

      val result =
        paymentsAndChargesService.getPaymentsAndCharges(
          srn,
          paymentsAndChargesForAGivenPeriod(PSS_OTC_AFT_RETURN, amountDue = 0.00).head._2,
          All,
          AccountingForTaxCharges
        )

      result mustBe expectedTable
    }
  }

  "getChargeDetailsForSelectedCharge" must {
    "return the row for original charge amount, payments and credits, stood over amount and total amount due" in {
      val result =
        paymentsAndChargesService.getChargeDetailsForSelectedCharge(createCharge(PSS_AFT_RETURN, totalAmount = 56432.00, amountDue = 1029.05))

      result mustBe originalAmountRow ++ paymentsAndCreditsRow ++ stoodOverAmountRow ++ totalAmountDueRow
    }

    "return the row for original charge amount credit, no payments and credits , no stood over amount and no total amount due" in {
      val result = paymentsAndChargesService.getChargeDetailsForSelectedCharge(chargeWithCredit)

      result mustBe Seq(
        Row(
          key =
            Key(msg"paymentsAndCharges.chargeDetails.originalChargeAmount", classes = Seq("govuk-!-padding-left-0", "govuk-!-width-three-quarters")),
          value = Value(
            Literal(s"${FormatHelper.formatCurrencyAmountAsString(20000.00)} ${messages("paymentsAndCharges.credit")}"),
            classes = Nil
          ),
          actions = Nil
        ))
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

  "isPaymentOverdue" must {
    "return true if the amount due is positive and due date is before today" in {
      val schemeFS: SchemeFS = schemeFSResponseAftAndOTC.head
      paymentsAndChargesService.isPaymentOverdue(schemeFS) mustBe true
    }

    "return false if the amount due is negative and due date is before today" in {
      val schemeFS: SchemeFS = schemeFSResponseAftAndOTC.head.copy(amountDue = BigDecimal(0.00))
      paymentsAndChargesService.isPaymentOverdue(schemeFS) mustBe false
    }

    "return true if the amount due is positive and due date is today" in {
      val schemeFS: SchemeFS = schemeFSResponseAftAndOTC.head.copy(dueDate = Some(LocalDate.now()))
      paymentsAndChargesService.isPaymentOverdue(schemeFS) mustBe false
    }

    "return true if the amount due is positive and due date is none" in {
      val schemeFS: SchemeFS = schemeFSResponseAftAndOTC.head.copy(dueDate = None)
      paymentsAndChargesService.isPaymentOverdue(schemeFS) mustBe false
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
      whenReady(paymentsAndChargesService.getPaymentsForJourney(psaId, srn, All)){ _ mustBe paymentsCache.copy(schemeFS = Seq(chargeWithCredit)) }
    }

    "call FS API and save to cache if logged in id does not match the retrieved payload from cache" in {
      when(mockFIConnector.fetch(any(), any())).thenReturn(Future.successful(Some(Json.toJson(paymentsCache.copy(loggedInId = "wrong-id")))))
      when(mockSchemeService.retrieveSchemeDetails(any(), any(), any())(any(), any())).thenReturn(Future.successful(schemeDetails))
      when(mockFSConnector.getSchemeFS(any())(any(), any())).thenReturn(Future.successful(Seq(chargeWithCredit)))
      when(mockFIConnector.save(any())(any(), any())).thenReturn(Future.successful(Json.obj()))
      whenReady(paymentsAndChargesService.getPaymentsForJourney(psaId, srn, All)){ _ mustBe paymentsCache.copy(schemeFS = Seq(chargeWithCredit)) }
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
  private def createCharge(
                            chargeType: SchemeFSChargeType,
                            totalAmount: BigDecimal,
                            amountDue: BigDecimal,
                            dueDate: Option[LocalDate] = Some(LocalDate.parse("2020-05-15"))
                          ): SchemeFS = {
    SchemeFS(
      chargeReference = "AYU3494534632",
      chargeType = chargeType,
      dueDate = dueDate,
      totalAmount = totalAmount,
      outstandingAmount = 56049.08,
      stoodOverAmount = 25089.08,
      amountDue = amountDue,
      accruedInterestTotal = 153.00,
      periodStartDate = QUARTER_START_DATE,
      periodEndDate = QUARTER_END_DATE
    )
  }

  private def chargeWithCredit = SchemeFS(
    chargeReference = "AYU3494534632",
    chargeType = PSS_AFT_RETURN,
    dueDate = Some(LocalDate.parse("2020-05-15")),
    totalAmount = -20000.00,
    outstandingAmount = 0.00,
    stoodOverAmount = 0.00,
    amountDue = 0.00,
    accruedInterestTotal = 0.00,
    periodStartDate = QUARTER_START_DATE,
    periodEndDate = QUARTER_END_DATE
  )

  private def paymentsAndChargesForAGivenPeriod(chargeType: SchemeFSChargeType,
                                                totalAmount: BigDecimal = 56432.00,
                                                amountDue: BigDecimal = 1029.05): Seq[(LocalDate, Seq[SchemeFS])] = Seq(
    (
      LocalDate.parse(QUARTER_START_DATE.toString),
      Seq(
        createCharge(chargeType, totalAmount, amountDue)
      )
    )
  )

  private def originalAmountRow: Seq[SummaryList.Row] = {
    Seq(
      Row(
        key = Key(msg"paymentsAndCharges.chargeDetails.originalChargeAmount", classes = Seq("govuk-!-padding-left-0", "govuk-!-width-three-quarters")),
        value = Value(
          Literal(s"${FormatHelper.formatCurrencyAmountAsString(56432.00)}"),
          classes = Seq("govuk-!-width-one-quarter", "govuk-table__cell--numeric")
        ),
        actions = Nil
      ))
  }

  private def paymentsAndCreditsRow: Seq[SummaryList.Row] = {
    Seq(
      Row(
        key = Key(msg"paymentsAndCharges.chargeDetails.payments", classes = Seq("govuk-!-padding-left-0", "govuk-!-width-three-quarters")),
        value = Value(
          Literal(s"-${FormatHelper.formatCurrencyAmountAsString(30313.87)}"),
          classes = Seq("govuk-!-width-one-quarter", "govuk-table__cell--numeric")
        ),
        actions = Nil
      ))
  }

  private def stoodOverAmountRow: Seq[SummaryList.Row] = {
    Seq(
      Row(
        key = Key(msg"paymentsAndCharges.chargeDetails.stoodOverAmount", classes = Seq("govuk-!-padding-left-0", "govuk-!-width-three-quarters")),
        value = Value(
          Literal(s"-${FormatHelper.formatCurrencyAmountAsString(25089.08)}"),
          classes = Seq("govuk-!-width-one-quarter", "govuk-table__cell--numeric")
        ),
        actions = Nil
      ))
  }

  private def totalAmountDueRow: Seq[SummaryList.Row] = {
    Seq(
      Row(
        key = Key(
          msg"paymentsAndCharges.chargeDetails.amountDue".withArgs(LocalDate.parse("2020-05-15").format(dateFormatterDMY)),
          classes = Seq("govuk-table__cell--numeric", "govuk-!-padding-right-0", "govuk-!-width-three-quarters", "govuk-!-font-weight-bold")
        ),
        value = Value(
          Literal(s"${FormatHelper.formatCurrencyAmountAsString(1029.05)}"),
          classes = Seq("govuk-!-width-one-quarter", "govuk-table__cell--numeric", "govuk-!-font-weight-bold")
        ),
        actions = Nil
      ))
  }
}
