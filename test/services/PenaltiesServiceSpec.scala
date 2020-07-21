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
import config.FrontendAppConfig
import helpers.FormatHelper
import models.financialStatement.PsaFS
import models.financialStatement.PsaFSChargeType.{AFT_INITIAL_LFP, OTC_6_MONTH_LPP}
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.mockito.MockitoSugar
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.Results
import uk.gov.hmrc.viewmodels.SummaryList.{Key, Row, Value}
import uk.gov.hmrc.viewmodels.Text.Literal
import uk.gov.hmrc.viewmodels.{Html, _}
import utils.DateHelper
import utils.DateHelper.dateFormatterDMY
import viewmodels.Table
import viewmodels.Table.Cell

class PenaltiesServiceSpec extends SpecBase with ScalaFutures with BeforeAndAfterEach with MockitoSugar with Results {

  import PenaltiesServiceSpec._

  private val mockAppConfig: FrontendAppConfig = mock[FrontendAppConfig]
  private val penaltiesService = new PenaltiesService(mockAppConfig)

  val penaltyTables: Seq[JsObject] = Seq(
    Json.obj(
      "header" -> msg"penalties.period".withArgs("1 April", "30 June 2020"),
      "penaltyTable" -> Table(head = head, rows = rows(aftLink("2020-04-01")))
    ),
    Json.obj(
      "header" -> msg"penalties.period".withArgs("1 July", "30 September 2020"),
      "penaltyTable" -> Table(head = head, rows = rows(otcLink("2020-07-01")))
    )
  )

  override def beforeEach: Unit = {
    super.beforeEach
    when(mockAppConfig.minimumYear).thenReturn(year)
    DateHelper.setDate(Some(dateNow))
  }

  "getPsaFsJson" must {
    "return the penalty tables based on API response" in {
      penaltiesService.getPsaFsJson(psaFSResponse, srn, year) mustBe penaltyTables
    }
  }

  "isPaymentOverdue" must {
    "return true if amountDue is greater than 0 and due date is after today" in {
      penaltiesService.isPaymentOverdue(psaFS(BigDecimal(0.01), Some(dateNow.minusDays(1)))) mustBe true
    }

    "return false if amountDue is less than or equal to 0" in {
      penaltiesService.isPaymentOverdue(psaFS(BigDecimal(0.00), Some(dateNow.minusDays(1)))) mustBe false
    }

    "return false if amountDue is greater than 0 and due date is not defined" in {
      penaltiesService.isPaymentOverdue(psaFS(BigDecimal(0.01), None)) mustBe false
    }

    "return false if amountDue is greater than 0 and due date is today" in {
      penaltiesService.isPaymentOverdue(psaFS(BigDecimal(0.01), Some(dateNow))) mustBe false
    }
  }

  "chargeDetailsRows" must {
    "return the correct rows when all rows need to be displayed" in {
      val rows = Seq(totalAmount(), paymentAmount(), reviewAmount(), totalDueAmount(date = "15 July 2020"))
      penaltiesService.chargeDetailsRows(psaFSResponse.head) mustBe rows
    }

    "return the correct rows when payment row need not be displayed" in {
      val rows = Seq(totalAmount(), reviewAmount(), totalDueAmount())
      val psaData = psaFS(outStandingAmount = BigDecimal(80000.00))
      penaltiesService.chargeDetailsRows(psaData) mustBe rows
    }

    "return the correct rows when amountUnderReview row need not be displayed" in {
      val rows = Seq(totalAmount(), paymentAmount(), totalDueAmount())
      val psaData = psaFS(stoodOverAmount = BigDecimal(0.00))
      penaltiesService.chargeDetailsRows(psaData) mustBe rows
    }

    "return the correct rows when totalDue row need is displayed without date" in {
      val rows = Seq(totalAmount(), paymentAmount(), reviewAmount(), totalDueAmountWithoutDate())
      val psaData = psaFS(dueDate = None)
      penaltiesService.chargeDetailsRows(psaData) mustBe rows
    }
  }



}

object PenaltiesServiceSpec {

  val psaFSResponse: Seq[PsaFS] = Seq(
    PsaFS(
      chargeReference = "XY002610150184",
      chargeType = AFT_INITIAL_LFP,
      dueDate = Some(LocalDate.parse("2020-07-15")),
      totalAmount = 80000.00,
      outstandingAmount = 56049.08,
      stoodOverAmount = 25089.08,
      amountDue = 1029.05,
      periodStartDate =  LocalDate.parse("2020-04-01"),
      periodEndDate =  LocalDate.parse("2020-06-30"),
      pstr = "24000040IN"
    ),
    PsaFS(
      chargeReference = "XY002610150184",
      chargeType = OTC_6_MONTH_LPP,
      dueDate = Some(LocalDate.parse("2020-02-15")),
      totalAmount = 80000.00,
      outstandingAmount = 56049.08,
      stoodOverAmount = 25089.08,
      amountDue = 1029.05,
      periodStartDate =  LocalDate.parse("2020-07-01"),
      periodEndDate =  LocalDate.parse("2020-09-30"),
      pstr = "24000041IN"
    )
  )

  def psaFS(amountDue: BigDecimal = BigDecimal(1029.05), dueDate: Option[LocalDate] = Some(dateNow), totalAmount: BigDecimal = BigDecimal(80000.00),
            outStandingAmount: BigDecimal = BigDecimal(56049.08), stoodOverAmount: BigDecimal = BigDecimal(25089.08)): PsaFS =
    PsaFS("XY002610150184", AFT_INITIAL_LFP, dueDate, totalAmount, amountDue, outStandingAmount, stoodOverAmount, dateNow, dateNow, pstr)

  val year: Int = 2020
  val srn: String = "S2400000041"
  val pstr: String = "24000040IN"
  val zeroAmount: BigDecimal = BigDecimal(0.00)
  val dateNow: LocalDate = LocalDate.now()
  val formattedDateNow: String = dateNow.format(dateFormatterDMY)

  val head = Seq(
    Cell(msg"penalties.column.penalty", classes = Seq("govuk-!-width-one-quarter")),
    Cell(msg"penalties.column.amount", classes = Seq("govuk-!-width-one-quarter")),
    Cell(msg"penalties.column.chargeReference", classes = Seq("govuk-!-width-one-quarter")),
    Cell(msg"")
  )

  def rows(link: Html) = Seq(Seq(
    Cell(link, classes = Seq("govuk-!-width-one-quarter")),
    Cell(Literal("£1,029.05"), classes = Seq("govuk-!-width-one-quarter")),
    Cell(Literal("XY002610150184"), classes = Seq("govuk-!-width-one-quarter")),
    Cell(msg"penalties.status.paymentOverdue", classes = Seq("govuk-tag govuk-tag--red"))
  ))

  def aftLink(startDate: String): Html = Html(
    s"<a id=XY002610150184 href=${controllers.financialStatement.routes.ChargeDetailsController.onPageLoad(srn, startDate, "XY002610150184").url}>" +
      s"Accounting for Tax late filing penalty </a>")

  def otcLink(startDate: String): Html = Html(
    s"<a id=XY002610150184 href=${controllers.financialStatement.routes.ChargeDetailsController.onPageLoad(srn, startDate, "XY002610150184").url}>" +
      s"Overseas transfer charge late payment penalty (6 months) </a>")


    def totalAmount(amount: BigDecimal = BigDecimal(80000.00)): Row = Row(
      key = Key(Literal("Accounting for Tax late filing penalty"), classes = Seq("govuk-!-width-three-quarters")),
      value = Value(Literal(s"${FormatHelper.formatCurrencyAmountAsString(amount)}"), classes = Seq("govuk-!-width-one-quarter", "govuk-table__cell--numeric"))
    )

    def paymentAmount(amount: BigDecimal = BigDecimal(23950.92)): Row = Row(
      key = Key(msg"penalties.chargeDetails.payments", classes = Seq("govuk-!-width-three-quarters")),
      value = Value(Literal(s"${FormatHelper.formatCurrencyAmountAsString(amount)}"), classes = Seq("govuk-!-width-one-quarter", "govuk-table__cell--numeric"))
    )

    def reviewAmount(amount: BigDecimal = BigDecimal(25089.08)): Row = Row(
      key = Key(msg"penalties.chargeDetails.amountUnderReview", classes = Seq("govuk-!-width-three-quarters")),
      value = Value(Literal(s"${FormatHelper.formatCurrencyAmountAsString(amount)}"), classes = Seq("govuk-!-width-one-quarter", "govuk-table__cell--numeric"))
    )

    def totalDueAmount(amount: BigDecimal = BigDecimal(1029.05), date: String = formattedDateNow): Row = Row(
      key = Key(msg"penalties.chargeDetails.totalDueBy".withArgs(date), classes = Seq("govuk-table__header--numeric","govuk-!-padding-right-0")),
      value = Value(Literal(s"${FormatHelper.formatCurrencyAmountAsString(amount)}"), classes = Seq("govuk-!-width-one-quarter", "govuk-table__cell--numeric"))
    )

    def totalDueAmountWithoutDate(amount: BigDecimal = BigDecimal(1029.05)): Row = Row(
      key = Key(msg"penalties.chargeDetails.totalDue", classes = Seq("govuk-table__header--numeric","govuk-!-padding-right-0")),
      value = Value(Literal(s"${FormatHelper.formatCurrencyAmountAsString(amount)}"),
        classes = Seq("govuk-!-width-one-quarter", "govuk-table__cell--numeric"))
    )
}
