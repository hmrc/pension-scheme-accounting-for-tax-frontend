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

package helpers

import base.SpecBase
import controllers.fileUpload.FileUploadHeaders.AnnualAllowanceFieldNames
import data.SampleData.startDate
import fileUploadParsers.ParserErrorMessages.HeaderInvalidOrFileIsEmpty
import fileUploadParsers.{AnnualAllowanceParser, CsvLineSplitter, LifetimeAllowanceParser, ParserValidationError}
import models.{ChargeType, UserAnswers}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import play.api.i18n.Messages

//scalastyle:off magic.number
trait ParserHelper extends SpecBase with Matchers with MockitoSugar with BeforeAndAfterEach {

  protected def chargeTypeDescription(chargeType: ChargeType)(implicit messages: Messages): String =
    Messages(s"chargeType.description.${chargeType.toString}")

  protected def combine(a: Seq[ParserValidationError], extraExpected: Int => Seq[ParserValidationError]): Seq[ParserValidationError] = {
    val distinctRows: Seq[Int] = a.groupBy(_.row).keys.toSeq
    val b: Seq[ParserValidationError] = distinctRows.flatMap { r =>
      extraExpected(r)
    }
    (a ++ b).sortWith((a, b) => a.row < b.row)
  }

  // scalastyle:off method.length
  def annualAllowanceParserWithMinimalFields(header: String,
                                             parser: AnnualAllowanceParser,
                                             extraExpected: Int => Seq[ParserValidationError] = _ => Nil): Unit = {
    "return validation error for incorrect header" in {
      val invalidHeader = CsvLineSplitter.split("""test""")
      val result = parser.parse(startDate, invalidHeader, UserAnswers())
      result.swap.toList.flatten mustBe Seq(
        ParserValidationError(0, 0, HeaderInvalidOrFileIsEmpty)
      )
    }

    "return validation error for empty file" in {
      val result = parser.parse(startDate, Nil, UserAnswers())
      result.isInvalid mustBe true
      result.swap.toList.flatten.take(1) mustBe Seq(
        ParserValidationError(0, 0, HeaderInvalidOrFileIsEmpty)
      )
    }

    "return validation errors for member " in {
      val GivingInvalidMemberDetailsCsv = CsvLineSplitter.split(
        s"""$header
  ,Bloggs,AB123456C,2020 to 2021,268.28,01/01/2020,yes
  Ann,,3456C,2020 to 2021,268.28,01/01/2020,yes"""
      )
      val result = parser.parse(startDate, GivingInvalidMemberDetailsCsv, UserAnswers())

      result.isInvalid mustBe true
      val expectedResult = combine(Seq(
        ParserValidationError(1, 0, "memberDetails.error.firstName.required", "firstName"),
        ParserValidationError(2, 1, "memberDetails.error.lastName.required", "lastName"),
        ParserValidationError(2, 2, "memberDetails.error.nino.invalid", "nino")
      ), extraExpected)

      result.swap.toList.flatten.take(expectedResult.size) mustBe expectedResult
    }

    "return validation errors for charge details, including missing, invalid, future and past tax years" in {

      val GivingInvalidChargeDetailsCsvFile = CsvLineSplitter.split(
        s"""$header
      Joe,Bloggs,AB123456C,,,01/01,nah
      Ann,Bliggs,AB123457C,22,268.28,01,yes
      Joe,Blaggs,AB123454C,2021,268.28,01/01/2020,yes
      Jim,Bloggs,AB123455C,2010,268.28,01/01/2020,yes"""
      )
      val result = parser.parse(startDate, GivingInvalidChargeDetailsCsvFile, UserAnswers())

      result.isInvalid mustBe true

      val expectedResult = combine(Seq(
        ParserValidationError(1, 4, "chargeAmount.error.required", "chargeAmount"),
        ParserValidationError(1, 5, "dateNoticeReceived.error.incomplete", "dateNoticeReceived", Seq("year")),
        ParserValidationError(1, 6, "error.boolean", "isPaymentMandatory"),
        ParserValidationError(1, 3, "annualAllowanceYear.fileUpload.error.required", AnnualAllowanceFieldNames.taxYear),
        ParserValidationError(2, 5, "dateNoticeReceived.error.incomplete", "dateNoticeReceived", Seq("month", "year")),
        ParserValidationError(2, 3, "annualAllowanceYear.fileUpload.error.invalid", AnnualAllowanceFieldNames.taxYear),
        ParserValidationError(3, 3, "annualAllowanceYear.fileUpload.error.future", AnnualAllowanceFieldNames.taxYear),
        ParserValidationError(4, 3, "annualAllowanceYear.fileUpload.error.past", AnnualAllowanceFieldNames.taxYear)
      ), extraExpected)

      result.swap.toList.flatten.take(expectedResult.size) mustBe expectedResult
    }

    "return validation errors for tax year only, including missing, invalid, future and past tax years" in {
      val GivingInvalidTaxYearCsvFile = CsvLineSplitter.split(
        s"""$header
                              Joe,Bloggs,AB123456C,,268.28,01/01/2020,yes
                              Ann,Bliggs,AB123457C,22,268.28,01/01/2020,yes
                              Steven,Miggs,AB123457C,2020-2022,268.28,01/01/2020,yes
                              Joe,Blaggs,AB123454C,2027,268.28,01/01/2020,yes
                              Jim,Bloggs,AB123455C,2010,268.28,01/01/2020,yes"""
      )
      val result = parser.parse(startDate, GivingInvalidTaxYearCsvFile, UserAnswers())
      result.isInvalid mustBe true
      val expectedResult = combine(Seq(ParserValidationError(1, 3, "annualAllowanceYear.fileUpload.error.required", AnnualAllowanceFieldNames.taxYear),
        ParserValidationError(2, 3, "annualAllowanceYear.fileUpload.error.invalid", AnnualAllowanceFieldNames.taxYear),
        ParserValidationError(3, 3, "annualAllowanceYear.fileUpload.error.invalid", AnnualAllowanceFieldNames.taxYear),
        ParserValidationError(4, 3, "annualAllowanceYear.fileUpload.error.future", AnnualAllowanceFieldNames.taxYear),
        ParserValidationError(5, 3, "annualAllowanceYear.fileUpload.error.past", AnnualAllowanceFieldNames.taxYear)
      ), extraExpected)
      result.swap.toList.flatten.take(expectedResult.size) mustBe expectedResult

    }

    "return validation errors for member details AND charge details when both present" in {
      val GivingInvalidMemberDetailsAndChargeDetailsCsvFile = CsvLineSplitter.split(
        s"""$header
                              ,Bloggs,AB123456C,2020 to 2021,,01/01/2020,yes
                              Ann,,3456C,2020 to 2021,268.28,01/13/2020,yes"""
      )

      val result = parser.parse(startDate, GivingInvalidMemberDetailsAndChargeDetailsCsvFile, UserAnswers())
      result.isInvalid mustBe true
      val expectedResult = combine(Seq(
        ParserValidationError(1, 0, "memberDetails.error.firstName.required", "firstName"),
        ParserValidationError(1, 4, "chargeAmount.error.required", "chargeAmount"),
        ParserValidationError(2, 1, "memberDetails.error.lastName.required", "lastName"),
        ParserValidationError(2, 2, "memberDetails.error.nino.invalid", "nino"),
        ParserValidationError(2, 5, "dateNoticeReceived.error.invalid", "dateNoticeReceived")
      ), extraExpected)
      result.swap.toList.flatten.take(expectedResult.size) mustBe expectedResult
    }

    "return validation errors for member details AND charge details when errors present in first row but not in second" in {
      val GivingInvalidMemberDetailsAndChargeDetailsFirstRowCsvFile = CsvLineSplitter.split(
        s"""$header
                              ,Bloggs,AB123456C,2020 to 2021,,01/01/2020,yes
                              Joe,Bliggs,AB123457C,2020 to 2021,268.28,01/01/2020,yes"""
      )

      val result = parser.parse(startDate, GivingInvalidMemberDetailsAndChargeDetailsFirstRowCsvFile, UserAnswers())
      result.isInvalid mustBe true
      val expectedResult = combine(Seq(
        ParserValidationError(1, 0, "memberDetails.error.firstName.required", "firstName"),
        ParserValidationError(1, 4, "chargeAmount.error.required", "chargeAmount")
      ), extraExpected)
      result.swap.toList.flatten.take(expectedResult.size) mustBe expectedResult
    }
  }

  def lifetimeAllowanceParserWithMinimalFields(header: String,
                                               parser: LifetimeAllowanceParser,
                                               extraExpected: Int => Seq[ParserValidationError] = _ => Nil): Unit = {
    "return validation error for incorrect header" in {
      val GivingIncorrectHeader = CsvLineSplitter.split("""test""")
      val result = parser.parse(startDate, GivingIncorrectHeader, UserAnswers())
      result.swap.toList.flatten.take(1) mustBe Seq(
        ParserValidationError(0, 0, HeaderInvalidOrFileIsEmpty)
      )
    }

    "return validation error for empty file" in {
      val result = parser.parse(startDate, Nil, UserAnswers())
      result.isInvalid mustBe true
      result.swap.toList.flatten.take(1) mustBe Seq(
        ParserValidationError(0, 0, HeaderInvalidOrFileIsEmpty)
      )
    }

    "return validation errors for member details when present" in {
      val GivingIncorrectMemberDetails = CsvLineSplitter.split(
        s"""$header
                          ,Bloggs,AB123456C,01/04/2020,268.28,0.00
                          Ann,,3456C,01/04/2020,268.28,0.00"""
      )
      val result = parser.parse(startDate, GivingIncorrectMemberDetails, UserAnswers())
      result.isInvalid mustBe true
      val expectedResult = combine(Seq(
        ParserValidationError(1, 0, "memberDetails.error.firstName.required", "firstName"),
        ParserValidationError(2, 1, "memberDetails.error.lastName.required", "lastName"),
        ParserValidationError(2, 2, "memberDetails.error.nino.invalid", "nino")
      ), extraExpected)
      result.swap.toList.flatten.take(expectedResult.size) mustBe expectedResult
    }

    "return validation errors for charge details when present, including missing year and missing month" in {
      val GivingMissingYearAndMonth = CsvLineSplitter.split(
        s"""$header
                          Joe,Bloggs,AB123456C,01/04,268.28,0.00
                          Ann,Bliggs,AB123457C,01,268.28,0.00"""
      )

      val result = parser.parse(startDate, GivingMissingYearAndMonth, UserAnswers())
      result.isInvalid mustBe true
      val expectedResult = combine(Seq(
        ParserValidationError(1, 3, "dateOfEvent.error.incomplete", "dateOfEvent", Seq("year")),
        ParserValidationError(2, 3, "dateOfEvent.error.incomplete", "dateOfEvent", Seq("month", "year"))
      ), extraExpected)
      result.swap.toList.flatten.take(expectedResult.size) mustBe expectedResult
    }

    "return validation errors for member details AND charge details when both present" in {
      val GivingIncorrectMemberDetailsAndChargeDetails = CsvLineSplitter.split(
        s"""$header
                          ,Bloggs,AB123456C,01/04,268.28,0.00
                          Ann,,3456C,01,268.28,0.00"""
      )
      val result = parser.parse(startDate, GivingIncorrectMemberDetailsAndChargeDetails, UserAnswers())
      result.isInvalid mustBe true
      val expectedResult = combine(Seq(
        ParserValidationError(1, 0, "memberDetails.error.firstName.required", "firstName"),
        ParserValidationError(1, 3, "dateOfEvent.error.incomplete", "dateOfEvent", Seq("year")),
        ParserValidationError(2, 1, "memberDetails.error.lastName.required", "lastName"),
        ParserValidationError(2, 2, "memberDetails.error.nino.invalid", "nino"),
        ParserValidationError(2, 3, "dateOfEvent.error.incomplete", "dateOfEvent", Seq("month", "year"))
      ), extraExpected)
      result.swap.toList.flatten.take(expectedResult.size) mustBe expectedResult
    }

    "return validation errors for member details AND charge details when errors present in first row but not in second" in {
      val GivingIncorrectMemberDetailsAndChargeDetailsFirstRow = CsvLineSplitter.split(
        s"""$header
                          ,Bloggs,AB123456C,01/04,268.28,0.00
                          Joe,Bliggs,AB123457C,01/04/2020,268.28,0.00"""
      )

      val result = parser.parse(startDate, GivingIncorrectMemberDetailsAndChargeDetailsFirstRow, UserAnswers())
      result.isInvalid mustBe true
      val expectedResult = combine(Seq(
        ParserValidationError(1, 0, "memberDetails.error.firstName.required", "firstName"),
        ParserValidationError(1, 3, "dateOfEvent.error.incomplete", "dateOfEvent", Seq("year")),
      ), extraExpected)
      result.swap.toList.flatten.take(expectedResult.size) mustBe expectedResult
    }
  }

}
