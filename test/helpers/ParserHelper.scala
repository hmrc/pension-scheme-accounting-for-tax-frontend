/*
 * Copyright 2023 HM Revenue & Customs
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
import fileUploadParsers.ParserErrorMessages.{HeaderInvalidOrFileIsEmpty, NotEnoughFields}
import fileUploadParsers.{AnnualAllowanceParser, CsvLineSplitter, ParserValidationError}
import models.UserAnswers
import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar

//scalastyle:off magic.number
trait ParserHelper extends SpecBase with Matchers with MockitoSugar with BeforeAndAfterEach {
  // scalastyle:off method.length
  def annualAllowanceParserWithMinimalFields(header: String, parser: AnnualAllowanceParser): Unit = {
    "return validation error for incorrect header" in {
      val invalidHeader = CsvLineSplitter.split("""test""")
      val result = parser.parse(startDate, invalidHeader, UserAnswers())
      result.swap.toSeq.flatten mustBe Seq(
        ParserValidationError(0, 0, HeaderInvalidOrFileIsEmpty)
      )
    }

    "return validation error for empty file" in {
      val result = parser.parse(startDate, Nil, UserAnswers())
      result.isLeft mustBe true
      result.swap.toSeq.flatten.take(1) mustBe Seq(
        ParserValidationError(0, 0, HeaderInvalidOrFileIsEmpty)
      )
    }

    "return validation error for not enough fields" in {
      val validCsvFile: Seq[Array[String]] = CsvLineSplitter.split(
        s"""$header
one,two"""
      )
      val result = parser.parse(startDate, validCsvFile, UserAnswers())
      result.isLeft mustBe true
      result.swap.toSeq.flatten.take(1) mustBe Seq(
        ParserValidationError(1, 0, NotEnoughFields)
      )
    }

    "return validation errors for member " in {
      val GivingInvalidMemberDetailsCsv = CsvLineSplitter.split(
        s"""$header
  ,Bloggs,AB123456C,2020,268.28,01/01/2020,yes
  Ann,,3456C,2020,268.28,01/01/2020,yes"""
      )
      val result = parser.parse(startDate, GivingInvalidMemberDetailsCsv, UserAnswers())

      result.isLeft mustBe true
      result.swap.toSeq.flatten.take(3) mustBe Seq(
        ParserValidationError(1, 0, "memberDetails.error.firstName.required", "firstName"),
        ParserValidationError(2, 1, "memberDetails.error.lastName.required", "lastName"),
        ParserValidationError(2, 2, "memberDetails.error.nino.invalid", "nino")
      )
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

      result.isLeft mustBe true
      result.swap.toSeq.flatten.take(8) mustBe Seq(
        ParserValidationError(1, 4, "chargeAmount.error.required", "chargeAmount"),
        ParserValidationError(1, 5, "dateNoticeReceived.error.incomplete", "dateNoticeReceived", Seq("year")),
        ParserValidationError(1, 6, "error.boolean", "isPaymentMandatory"),
        ParserValidationError(1, 3, "annualAllowanceYear.fileUpload.error.required", AnnualAllowanceFieldNames.taxYear),
        ParserValidationError(2, 5, "dateNoticeReceived.error.incomplete", "dateNoticeReceived", Seq("month", "year")),
        ParserValidationError(2, 3, "annualAllowanceYear.fileUpload.error.invalid", AnnualAllowanceFieldNames.taxYear),
        ParserValidationError(3, 3, "annualAllowanceYear.fileUpload.error.future", AnnualAllowanceFieldNames.taxYear),
        ParserValidationError(4, 3, "annualAllowanceYear.fileUpload.error.past", AnnualAllowanceFieldNames.taxYear)
      )
    }

    "return validation errors for tax year only, including missing, invalid, future and past tax years" in {
      val GivingInvalidTaxYearCsvFile = CsvLineSplitter.split(
        s"""$header
                          Joe,Bloggs,AB123456C,,268.28,01/01/2020,yes
                          Ann,Bliggs,AB123457C,22,268.28,01/01/2020,yes
                          Joe,Blaggs,AB123454C,2021,268.28,01/01/2020,yes
                          Jim,Bloggs,AB123455C,2010,268.28,01/01/2020,yes"""
      )
      val result = parser.parse(startDate, GivingInvalidTaxYearCsvFile, UserAnswers())
      result.isLeft mustBe true
      result.swap.toSeq.flatten.take(4) mustBe Seq(
        ParserValidationError(1, 3, "annualAllowanceYear.fileUpload.error.required", AnnualAllowanceFieldNames.taxYear),
        ParserValidationError(2, 3, "annualAllowanceYear.fileUpload.error.invalid", AnnualAllowanceFieldNames.taxYear),
        ParserValidationError(3, 3, "annualAllowanceYear.fileUpload.error.future", AnnualAllowanceFieldNames.taxYear),
        ParserValidationError(4, 3, "annualAllowanceYear.fileUpload.error.past", AnnualAllowanceFieldNames.taxYear)
      )
    }

    "return validation errors for member details AND charge details when both present" in {
      val GivingInvalidMemberDetailsAndChargeDetailsCsvFile = CsvLineSplitter.split(
        s"""$header
                          ,Bloggs,AB123456C,2020,,01/01/2020,yes
                          Ann,,3456C,2020,268.28,01/13/2020,yes"""
      )

      val result = parser.parse(startDate, GivingInvalidMemberDetailsAndChargeDetailsCsvFile, UserAnswers())
      result.isLeft mustBe true
      result.swap.toSeq.flatten.take(5) mustBe Seq(
        ParserValidationError(1, 0, "memberDetails.error.firstName.required", "firstName"),
        ParserValidationError(1, 4, "chargeAmount.error.required", "chargeAmount"),
        ParserValidationError(2, 1, "memberDetails.error.lastName.required", "lastName"),
        ParserValidationError(2, 2, "memberDetails.error.nino.invalid", "nino"),
        ParserValidationError(2, 5, "dateNoticeReceived.error.invalid", "dateNoticeReceived")
      )
    }

    "return validation errors for member details AND charge details when errors present in first row but not in second" in {
      val GivingInvalidMemberDetailsAndChargeDetailsFirstRowCsvFile = CsvLineSplitter.split(
        s"""$header
                          ,Bloggs,AB123456C,2020,,01/01/2020,yes
                          Joe,Bliggs,AB123457C,2020,268.28,01/01/2020,yes"""
      )

      val result = parser.parse(startDate, GivingInvalidMemberDetailsAndChargeDetailsFirstRowCsvFile, UserAnswers())
      result.isLeft mustBe true
      result.swap.toSeq.flatten.take(2) mustBe Seq(
        ParserValidationError(1, 0, "memberDetails.error.firstName.required", "firstName"),
        ParserValidationError(1, 4, "chargeAmount.error.required", "chargeAmount")
      )
    }

    "return validation errors when not enough fields" in {
      val GivingNotEnoughFields = CsvLineSplitter.split(
        s"""$header
                          Bloggs,AB123456C,2020268.28,2020-01-01,true"""
      )
      val result = parser.parse(startDate, GivingNotEnoughFields, UserAnswers())
      result.isLeft mustBe true
      result.swap.toSeq.flatten.take(1) mustBe Seq(ParserValidationError(1, 0, NotEnoughFields))
    }
  }
}
