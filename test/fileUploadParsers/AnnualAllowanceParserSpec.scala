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

package fileUploadParsers

import base.SpecBase
import config.FrontendAppConfig
import controllers.fileUpload.FileUploadHeaders.AnnualAllowanceFieldNames
import data.SampleData
import data.SampleData.startDate
import fileUploadParsers.AnnualAllowanceParserSpec.invalidMemberDetailsCsvFile
import forms.MemberDetailsFormProvider
import forms.chargeE.ChargeDetailsFormProvider
import models.UserAnswers
import models.chargeE.ChargeEDetails
import org.mockito.{Mockito, MockitoSugar}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.must.Matchers
import pages.chargeE.{AnnualAllowanceYearPage, ChargeDetailsPage, MemberDetailsPage}
import play.api.libs.json.Json

import java.time.LocalDate

class AnnualAllowanceParserSpec extends SpecBase with Matchers with MockitoSugar with BeforeAndAfterEach {
  //scalastyle:off magic.number

  import AnnualAllowanceParserSpec._

  override def beforeEach: Unit = {
    Mockito.reset(mockFrontendAppConfig)
    when(mockFrontendAppConfig.earliestDateOfNotice).thenReturn(LocalDate.of(1900, 1, 1))
    when(mockFrontendAppConfig.validAnnualAllowanceHeader).thenReturn(header)
  }

  "Annual allowance parser" must {
    "return charges in user answers when there are no validation errors" in {
      val validCsvFile: Seq[Array[String]] = CsvParser.split(
        s"""$header
    Joe,Bloggs,AB123456C,2020,268.28,01/01/2020,yes
    Joe,Bliggs,AB123457C,2020,268.28,01/01/2020,yes"""
      )
      val chargeDetails = ChargeEDetails(BigDecimal(268.28), LocalDate.of(2020, 1, 1), isPaymentMandatory = true)
      val result = parser.parse(startDate, validCsvFile, UserAnswers())
      result mustBe Right(UserAnswers()
        .setOrException(MemberDetailsPage(0).path, Json.toJson(SampleData.memberDetails2))
        .setOrException(ChargeDetailsPage(0).path, Json.toJson(chargeDetails))
        .setOrException(AnnualAllowanceYearPage(0).path, Json.toJson("2020"))
        .setOrException(MemberDetailsPage(1).path, Json.toJson(SampleData.memberDetails3))
        .setOrException(ChargeDetailsPage(1).path, Json.toJson(chargeDetails))
        .setOrException(AnnualAllowanceYearPage(1).path, Json.toJson("2020"))
      )
    }

    "return validation error for incorrect header" in {
      val invalidHeader = CsvParser.split("""test""")
      val result = parser.parse(startDate, invalidHeader,UserAnswers())
      result mustBe Left(Seq(
        ParserValidationError(0, 0, "Header invalid or File is empty")
      ))
    }

    "return validation error for empty file" in {
      val result = parser.parse(startDate, Nil,UserAnswers())
      result mustBe Left(Seq(
        ParserValidationError(0, 0, "Header invalid or File is empty")
      ))
    }

    "return validation error for not enough fields" in {
      val validCsvFile: Seq[Array[String]] = CsvParser.split(
        s"""$header
    one,two"""
      )
      val result = parser.parse(startDate, validCsvFile,UserAnswers())
      result mustBe Left(Seq(
        ParserValidationError(1, 0, "Not enough fields")
      ))
    }

   "return validation errors for member details" in {
     val invalidMemberDetailsCsv= CsvParser.split(
       s"""$header
    ,Bloggs,AB123456C,2020,268.28,01/01/2020,yes
    "Ann,,3456C,2020,268.28,01/01/2020,yes"""
     )
      val result = parser.parse(startDate, invalidMemberDetailsCsv,UserAnswers())
      result mustBe Left(Seq(
        ParserValidationError(1, 0, "memberDetails.error.firstName.required", "firstName"),
        ParserValidationError(2, 1, "memberDetails.error.lastName.required", "lastName"),
        ParserValidationError(2, 2, "memberDetails.error.nino.invalid", "nino")
      ))
    }

/*    "return validation errors for charge details, including missing, invalid, future and past tax years" in {
      val result = parser.parse(startDate, invalidChargeDetailsCsvFile,UserAnswers())
      result mustBe Left(Seq(
        ParserValidationError(1, 4, "chargeAmount.error.required", "chargeAmount"),
        ParserValidationError(1, 5, "dateNoticeReceived.error.incomplete", "dateNoticeReceived",Seq("year")),
        ParserValidationError(1, 6, "error.boolean", "isPaymentMandatory"),
        ParserValidationError(1, 3, "annualAllowanceYear.fileUpload.error.required", AnnualAllowanceFieldNames.taxYear),
        ParserValidationError(2, 5, "dateNoticeReceived.error.incomplete", "dateNoticeReceived",Seq("month","year")),
        ParserValidationError(2, 3, "annualAllowanceYear.fileUpload.error.invalid", AnnualAllowanceFieldNames.taxYear),
        ParserValidationError(3, 3, "annualAllowanceYear.fileUpload.error.future", AnnualAllowanceFieldNames.taxYear),
        ParserValidationError(4, 3, "annualAllowanceYear.fileUpload.error.past", AnnualAllowanceFieldNames.taxYear)
      )
      )
    }*/

/*    "return validation errors for tax year only, including missing, invalid, future and past tax years" in {
      val result = parser.parse(startDate, invalidTaxYearCsvFile,UserAnswers())
      result mustBe Left(Seq(
        ParserValidationError(1, 3, "annualAllowanceYear.fileUpload.error.required", AnnualAllowanceFieldNames.taxYear),
        ParserValidationError(2, 3, "annualAllowanceYear.fileUpload.error.invalid", AnnualAllowanceFieldNames.taxYear),
        ParserValidationError(3, 3, "annualAllowanceYear.fileUpload.error.future", AnnualAllowanceFieldNames.taxYear),
        ParserValidationError(4, 3, "annualAllowanceYear.fileUpload.error.past", AnnualAllowanceFieldNames.taxYear)
      ))
    }*/

/*    "return validation errors for member details AND charge details when both present" in {
      val result = parser.parse(startDate, invalidMemberDetailsAndChargeDetailsCsvFile,UserAnswers())
      result mustBe Left(Seq(
        ParserValidationError(1, 0, "memberDetails.error.firstName.required", "firstName"),
        ParserValidationError(1, 4, "chargeAmount.error.required", "chargeAmount"),
        ParserValidationError(2, 1, "memberDetails.error.lastName.required", "lastName"),
        ParserValidationError(2, 2, "memberDetails.error.nino.invalid", "nino"),
        ParserValidationError(2, 5, "dateNoticeReceived.error.invalid", "dateNoticeReceived")
      ))
    }*/

/*    "return validation errors for member details AND charge details when errors present in first row but not in second" in {
      val result = parser.parse(startDate, invalidMemberDetailsAndChargeDetailsFirstRowCsvFile,UserAnswers())
      result mustBe Left(Seq(
        ParserValidationError(1, 0, "memberDetails.error.firstName.required", "firstName"),
        ParserValidationError(1, 4, "chargeAmount.error.required", "chargeAmount")
      ))
    }*/

/*    "return validation errors when not enough fields" in {
      val result = parser.parse(startDate, Seq(header, "Bloggs,AB123456C,2020268.28,2020-01-01,true"),UserAnswers())
      result mustBe Left(Seq(ParserValidationError(1, 0, "Not enough fields")))
    }*/
  }
}

object AnnualAllowanceParserSpec extends MockitoSugar {
  private val header:String = "First name,Last name,National Insurance number,Tax year,Charge amount,Date,Payment type mandatory"

  private val mockFrontendAppConfig = mock[FrontendAppConfig]

  private val invalidMemberDetailsCsvFile = Seq(
    s"""$header
    ,Bloggs,AB123456C,2020,268.28,01/01/2020,yes
    "Ann,,3456C,2020,268.28,01/01/2020,yes"""
  )
  private val invalidChargeDetailsCsvFile = Seq(
    header,
    "Joe,Bloggs,AB123456C,,,01/01,nah",
    "Ann,Bliggs,AB123457C,22,268.28,01,yes",
    "Joe,Blaggs,AB123454C,2021,268.28,01/01/2020,yes",
    "Jim,Bloggs,AB123455C,2010,268.28,01/01/2020,yes"
  )
  private val invalidTaxYearCsvFile = Seq(
    header,
    "Joe,Bloggs,AB123456C,,268.28,01/01/2020,yes",
    "Ann,Bliggs,AB123457C,22,268.28,01/01/2020,yes",
    "Joe,Blaggs,AB123454C,2021,268.28,01/01/2020,yes",
    "Jim,Bloggs,AB123455C,2010,268.28,01/01/2020,yes"
  )
  private val invalidMemberDetailsAndChargeDetailsCsvFile = Seq(
    header,
    ",Bloggs,AB123456C,2020,,01/01/2020,yes",
    "Ann,,3456C,2020,268.28,01/13/2020,yes"
  )

  private val invalidMemberDetailsAndChargeDetailsFirstRowCsvFile = Seq(
    header,
    ",Bloggs,AB123456C,2020,,01/01/2020,yes",
    "Joe,Bliggs,AB123457C,2020,268.28,01/01/2020,yes"
  )
  private val formProviderMemberDetails = new MemberDetailsFormProvider
  private val formProviderChargeDetails = new ChargeDetailsFormProvider

  private val parser = new AnnualAllowanceParser(formProviderMemberDetails, formProviderChargeDetails, mockFrontendAppConfig)
}