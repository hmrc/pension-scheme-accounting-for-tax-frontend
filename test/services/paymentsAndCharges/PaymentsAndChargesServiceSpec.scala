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

package services.paymentsAndCharges

import java.time.LocalDate

import base.SpecBase
import controllers.chargeB.{routes => _}
import helpers.FormatHelper
import models.financialStatement.SchemeFSChargeType.{PSS_AFT_RETURN, PSS_AFT_RETURN_INTEREST, PSS_OTC_AFT_RETURN, PSS_OTC_AFT_RETURN_INTEREST}
import models.financialStatement.{SchemeFS, SchemeFSChargeType}
import models.viewModels.paymentsAndCharges.{PaymentAndChargeStatus, PaymentsAndChargesTable}
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.mockito.MockitoSugar
import play.api.i18n.Messages
import uk.gov.hmrc.viewmodels.SummaryList.{Key, Row, Value}
import uk.gov.hmrc.viewmodels.Text.Literal
import uk.gov.hmrc.viewmodels._
import utils.AFTConstants._
import utils.DateHelper.{dateFormatterDMY, dateFormatterStartDate}
import viewmodels.Table
import viewmodels.Table.Cell

class PaymentsAndChargesServiceSpec extends SpecBase with MockitoSugar with BeforeAndAfterEach {
  import PaymentsAndChargesServiceSpec._

  private def htmlChargeType(chargeType: String, chargeReference: String, redirectUrl: String, visuallyHiddenText: String) = {
    val linkId =
      chargeReference match {
        case "To be assigned" => "id-to-be-assigned"
        case "None" => "id-none"
        case _ => chargeReference
      }
    Html(
      s"<a id=$linkId class=govuk-link href=" +
        s"$redirectUrl>" +
        s"$chargeType " +
        s"<span class=govuk-visually-hidden>$visuallyHiddenText</span> </a>")
  }

  private val tableHead = Seq(
    Cell(msg"paymentsAndCharges.chargeType.table", classes = Seq("govuk-!-width-two-thirds-quarter")),
    Cell(msg"paymentsAndCharges.totalDue.table", classes = Seq("govuk-!-width-one-quarter", "govuk-!-font-weight-bold")),
    Cell(msg"paymentsAndCharges.chargeReference.table", classes = Seq("govuk-!-width-one-quarter", "govuk-!-font-weight-bold")),
    Cell(msg"", classes = Seq("govuk-!-font-weight-bold"))
  )

  private def paymentTable(rows: Seq[Seq[Table.Cell]]): PaymentsAndChargesTable = {
    PaymentsAndChargesTable(
      caption = messages("paymentsAndCharges.caption", startDate, endDate),
      table = Table(
        head = tableHead,
        rows = rows,
        attributes = Map("role" -> "grid", "aria-describedby" -> messages("paymentsAndCharges.caption", startDate, endDate))
      )
    )
  }

  private def row(chargeType: String,
                  chargeReference: String,
                  amountDue: String,
                  status: Html,
                  redirectUrl: String,
                  visuallyHiddenText: String): Seq[Table.Cell] = Seq(
    Cell(htmlChargeType(chargeType, chargeReference, redirectUrl, visuallyHiddenText), classes = Seq("govuk-!-width-two-thirds-quarter")),
    Cell(Literal(amountDue), classes = Seq("govuk-!-width-one-quarter")),
    Cell(Literal(s"$chargeReference"), classes = Seq("govuk-!-width-one-quarter")),
    Cell(status, classes = Nil)
  )

  private val paymentsAndChargesService = new PaymentsAndChargesService

  "getPaymentsAndChargesSeqOfTables" must {

    Seq(PSS_AFT_RETURN, PSS_OTC_AFT_RETURN).foreach { chargeType =>
      s"return payments and charges table with two rows for the charge and interest accrued for $chargeType" in {
        val expectedTable = Seq(
          paymentTable(Seq(
            row(
              chargeType.toString,
              "AYU3494534632",
              FormatHelper.formatCurrencyAmountAsString(1029.05),
              Html(s"<span class='govuk-tag govuk-tag--red'>${PaymentAndChargeStatus.PaymentOverdue.toString}</span>"),
              controllers.paymentsAndCharges.routes.PaymentsAndChargeDetailsController
                .onPageLoad(srn, QUARTER_START_DATE.toString, "AYU3494534632")
                .url,
              messages(s"paymentsAndCharges.visuallyHiddenText", "AYU3494534632")
            ),
            row(
              if (chargeType == PSS_AFT_RETURN) PSS_AFT_RETURN_INTEREST.toString else PSS_OTC_AFT_RETURN_INTEREST.toString,
              messages("paymentsAndCharges.chargeReference.toBeAssigned"),
              FormatHelper.formatCurrencyAmountAsString(153.00),
              Html(s"<span class='govuk-tag govuk-tag--blue'>${PaymentAndChargeStatus.InterestIsAccruing.toString}</span>"),
              controllers.paymentsAndCharges.routes.PaymentsAndChargesInterestController
                .onPageLoad(srn, QUARTER_START_DATE.toString, "AYU3494534632")
                .url,
              messages(s"paymentsAndCharges.interest.visuallyHiddenText")
            )
          )))

        val result = paymentsAndChargesService.getPaymentsAndChargesSeqOfTables(paymentsAndChargesForAGivenPeriod(chargeType), srn)

        result mustBe expectedTable
      }
    }

    "return payments and charges table with row for credit" in {
      val totalAmount = -56432.00
      val redirectUrl = controllers.paymentsAndCharges.routes.PaymentsAndChargeDetailsController
        .onPageLoad(srn, QUARTER_START_DATE.toString, "AYU3494534632")
        .url
      val expectedTable = Seq(
        paymentTable(
          Seq(
            row(
              PSS_OTC_AFT_RETURN.toString,
              chargeReference = messages("paymentsAndCharges.chargeReference.None"),
              amountDue = messages("paymentsAndCharges.amountDue.in.credit"),
              Html(""),
              redirectUrl,
              messages(s"paymentsAndCharges.credit.visuallyHiddenText")
            )
          )))

      val result = paymentsAndChargesService.getPaymentsAndChargesSeqOfTables(
        paymentsAndChargesForAGivenPeriod(PSS_OTC_AFT_RETURN, totalAmount, amountDue = 0.00),
        srn)

      result mustBe expectedTable
    }

    "return payments and charges table with row where there is no amount due" in {
      val redirectUrl = controllers.paymentsAndCharges.routes.PaymentsAndChargeDetailsController
        .onPageLoad(srn, QUARTER_START_DATE.toString, chargeReference = "AYU3494534632")
        .url
      val expectedTable = Seq(
        paymentTable(
          Seq(
            row(
              PSS_OTC_AFT_RETURN.toString,
              chargeReference = "AYU3494534632",
              amountDue = FormatHelper.formatCurrencyAmountAsString(0.00),
              Html(""),
              redirectUrl,
              messages(s"paymentsAndCharges.visuallyHiddenText", "AYU3494534632")
            )
          )))

      val result =
        paymentsAndChargesService.getPaymentsAndChargesSeqOfTables(paymentsAndChargesForAGivenPeriod(PSS_OTC_AFT_RETURN, amountDue = 0.00), srn)

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
}

object PaymentsAndChargesServiceSpec {
  val srn = "S1234567"
  val startDate: String = QUARTER_START_DATE.format(dateFormatterStartDate)
  val endDate: String = QUARTER_END_DATE.format(dateFormatterDMY)
  private def createCharge(chargeType: SchemeFSChargeType, totalAmount: BigDecimal, amountDue: BigDecimal): SchemeFS = {
    SchemeFS(
      chargeReference = "AYU3494534632",
      chargeType = chargeType,
      dueDate = Some(LocalDate.parse("2020-05-15")),
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

  private def originalAmountRow(implicit messages: Messages): Seq[SummaryList.Row] = {
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
