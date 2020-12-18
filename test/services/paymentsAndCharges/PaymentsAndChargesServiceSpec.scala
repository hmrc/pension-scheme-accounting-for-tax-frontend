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

import base.SpecBase
import controllers.chargeB.{routes => _}
import helpers.FormatHelper
import models.ChargeDetailsFilter
import models.financialStatement.SchemeFSChargeType._
import models.financialStatement.{SchemeFS, SchemeFSChargeType}
import models.viewModels.paymentsAndCharges.PaymentAndChargeStatus.{InterestIsAccruing, NoStatus, PaymentOverdue}
import models.viewModels.paymentsAndCharges.{PaymentAndChargeStatus, PaymentsAndChargesTable}
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.mockito.MockitoSugar
import play.api.i18n.Messages
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

class PaymentsAndChargesServiceSpec
  extends SpecBase
    with MockitoSugar
    with BeforeAndAfterEach {

  import PaymentsAndChargesServiceSpec._

  private def htmlChargeType(
                              chargeType: String,
                              chargeReference: String,
                              redirectUrl: String,
                              visuallyHiddenText: String
                            ): String = {
    val linkId =
      chargeReference match {
        case "To be assigned" => "to-be-assigned"
        case "None" => "none"
        case _ => chargeReference
      }

      s"<a id=$linkId class=govuk-link href=" +
        s"$redirectUrl>" +
        s"$chargeType " +
        s"<span class=govuk-visually-hidden>$visuallyHiddenText</span> </a>"
  }

  private val tableHead = Seq(
    Cell(msg"paymentsAndCharges.chargeType.table"),
    Cell(msg"paymentsAndCharges.totalDue.table", classes = Seq( "govuk-!-font-weight-bold")),
    Cell(msg"paymentsAndCharges.chargeReference.table", classes = Seq("govuk-!-font-weight-bold")),
    Cell(Html(s"<span class='govuk-visually-hidden'>${messages("paymentsAndCharges.chargeDetails.paymentStatus")}</span>"))
  )

  private def paymentTable(rows: Seq[Seq[Table.Cell]]): PaymentsAndChargesTable =
    PaymentsAndChargesTable(
      caption = messages("paymentsAndCharges.caption", startDate, endDate),
      table = Table(
        head = tableHead,
        rows = rows,
        attributes = Map("role" -> "table"),
        classes= Seq("hmrc-responsive-table")
      )
    )

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
      Cell(Html(s"""<span class=hmrc-responsive-table__heading aria-hidden=true>${messages("paymentsAndCharges.chargeType.table")}</span>${htmlChargeType(chargeType, chargeReference, redirectUrl, visuallyHiddenText)}""")),
      Cell(Html(s"""<span class=hmrc-responsive-table__heading aria-hidden=true>${messages("paymentsAndCharges.totalDue.table")}</span>${amountDue}""")),
      Cell(Html(s"""<span class=hmrc-responsive-table__heading aria-hidden=true>${messages("paymentsAndCharges.chargeReference.table")}</span>${chargeReference}""")),
      Cell(statusHtml)
    )
  }

  private val paymentsAndChargesService = new PaymentsAndChargesService()

  "getPaymentsAndCharges" must {

    Seq(PSS_AFT_RETURN, PSS_OTC_AFT_RETURN).foreach {
      chargeType =>
        s"return payments and charges table with two rows for the charge and interest accrued for $chargeType" in {

          def chargeLink(chargeDetailsFilter: ChargeDetailsFilter): String =
            chargeDetailsFilter match {
              case ChargeDetailsFilter.All =>
                controllers.paymentsAndCharges.routes.PaymentsAndChargeDetailsController
                  .onPageLoad(srn, QUARTER_START_DATE.toString, "0").url
              case ChargeDetailsFilter.Upcoming =>
                controllers.paymentsAndCharges.routes.PaymentsAndChargeDetailsController
                  .onPageLoadUpcoming(srn, QUARTER_START_DATE.toString, "0").url
              case ChargeDetailsFilter.Overdue =>
                controllers.paymentsAndCharges.routes.PaymentsAndChargeDetailsController
                  .onPageLoadOverdue(srn, QUARTER_START_DATE.toString, "0").url
            }

          def interestLink(chargeDetailsFilter: ChargeDetailsFilter): String =
            chargeDetailsFilter match {
              case ChargeDetailsFilter.All =>
                controllers.paymentsAndCharges.routes.PaymentsAndChargesInterestController
                  .onPageLoad(srn, QUARTER_START_DATE.toString, "0").url
              case ChargeDetailsFilter.Upcoming =>
                controllers.paymentsAndCharges.routes.PaymentsAndChargesInterestController
                  .onPageLoadUpcoming(srn, QUARTER_START_DATE.toString, "0").url
              case ChargeDetailsFilter.Overdue =>
                controllers.paymentsAndCharges.routes.PaymentsAndChargesInterestController
                  .onPageLoadOverdue(srn, QUARTER_START_DATE.toString, "0").url
            }

          def expectedTable(chargeLink: String, interestLink: String) = Seq(
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
            )))

          val result1 = paymentsAndChargesService.getPaymentsAndCharges(
            srn = srn,
            schemeFS = paymentsAndChargesForAGivenPeriod(chargeType).head._2,
            year = 2020,
            chargeDetailsFilter = ChargeDetailsFilter.All
          )

          val result2 = paymentsAndChargesService.getPaymentsAndCharges(
            srn = srn,
            schemeFS = paymentsAndChargesForAGivenPeriod(chargeType).head._2,
            year = 2020,
            chargeDetailsFilter = ChargeDetailsFilter.Upcoming
          )

          val result3 = paymentsAndChargesService.getPaymentsAndCharges(
            srn = srn,
            schemeFS = paymentsAndChargesForAGivenPeriod(chargeType).head._2,
            year = 2020,
            chargeDetailsFilter = ChargeDetailsFilter.Overdue
          )

          result1 mustBe expectedTable(chargeLink(ChargeDetailsFilter.All), interestLink(ChargeDetailsFilter.All))
          result2 mustBe expectedTable(chargeLink(ChargeDetailsFilter.Upcoming), interestLink(ChargeDetailsFilter.Upcoming))
          result3 mustBe expectedTable(chargeLink(ChargeDetailsFilter.Overdue), interestLink(ChargeDetailsFilter.Overdue))
        }
    }

    "return payments and charges table with no rows for credit" in {

      val totalAmount = -56432.00
      val expectedTable = Seq(paymentTable(Seq.empty))

      val result = paymentsAndChargesService.getPaymentsAndCharges(
        srn,
        paymentsAndChargesForAGivenPeriod(PSS_OTC_AFT_RETURN, totalAmount, amountDue = 0.00).head._2,
        2020,
        ChargeDetailsFilter.All
      )

      result mustBe expectedTable
    }

    "return payments and charges table with row where there is no amount due" in {

      val expectedTable = Seq(
        paymentTable(
          Seq(
            row(
              chargeType = PSS_OTC_AFT_RETURN.toString,
              chargeReference = "AYU3494534632",
              amountDue = FormatHelper.formatCurrencyAmountAsString(0.00),
              status = Html(""),
              redirectUrl = controllers.paymentsAndCharges.routes.PaymentsAndChargeDetailsController
                .onPageLoad(srn, QUARTER_START_DATE.toString, index = "0")
                .url,
              visuallyHiddenText = messages(s"paymentsAndCharges.visuallyHiddenText", "AYU3494534632")
            )
          )))

      val result =
        paymentsAndChargesService.getPaymentsAndCharges(
          srn,
          paymentsAndChargesForAGivenPeriod(PSS_OTC_AFT_RETURN, amountDue = 0.00).head._2,
          2020,
          ChargeDetailsFilter.All
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

  "getUpcomingCharges" must {
    "only return charges with a dueDate in the future" in {
      DateHelper.setDate(Some(LocalDate.of(2020, 6, 1)))

      val charges = Seq(
        createCharge(PSS_AFT_RETURN, 123.00, 456.00),
        createCharge(PSS_OTC_AFT_RETURN, 123.00, 456.00, Some(LocalDate.parse("2020-08-15")))
      )

      paymentsAndChargesService.getUpcomingCharges(charges).size mustBe 1
      paymentsAndChargesService.getUpcomingCharges(charges).head.chargeType mustBe PSS_OTC_AFT_RETURN
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
}

object PaymentsAndChargesServiceSpec {
  val srn = "S1234567"
  val startDate: String = QUARTER_START_DATE.format(dateFormatterStartDate)
  val endDate: String = QUARTER_END_DATE.format(dateFormatterDMY)

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
