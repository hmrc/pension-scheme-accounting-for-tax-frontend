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
import data.SampleData
import data.SampleData.startDate
import forms.MemberDetailsFormProvider
import forms.chargeE.ChargeDetailsFormProvider
import models.chargeE.ChargeEDetails
import org.mockito.{Mockito, MockitoSugar}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.must.Matchers
import pages.chargeE.{ChargeDetailsPage, MemberDetailsPage}
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
      val chargeDetails = ChargeEDetails(BigDecimal(268.28), LocalDate.of(2020, 1, 1), isPaymentMandatory = true)
      val result = parser.parse(startDate, validCsvFile)
      result mustBe Right(Seq(
        CommitItem(MemberDetailsPage(0).path, Json.toJson(SampleData.memberDetails2)),
        CommitItem(ChargeDetailsPage(0).path, Json.toJson(chargeDetails)),
        CommitItem(MemberDetailsPage(1).path, Json.toJson(SampleData.memberDetails3)),
        CommitItem(ChargeDetailsPage(1).path, Json.toJson(chargeDetails)),
      ))
    }

    "return validation error for incorrect header" in {
      val result = parser.parse(startDate, Seq("test"))
      result mustBe Left(Seq(
        ParserValidationError(0, 0, "Header invalid")
      ))
    }

    "return validation error for empty file" in {
      val result = parser.parse(startDate, Nil)
      result mustBe Left(Seq(
        ParserValidationError(0, 0, "File is empty")
      ))
    }

    "return validation error for not enough fields" in {
      val result = parser.parse(startDate, Seq(header, "one,two"))
      result mustBe Left(Seq(
        ParserValidationError(1, 0, "Not enough fields")
      ))
    }

    "return validation errors for member details" in {
      val result = parser.parse(startDate, invalidMemberDetailsCsvFile)
      result mustBe Left(Seq(
        ParserValidationError(1, 0, "memberDetails.error.firstName.required"),
        ParserValidationError(2, 1, "memberDetails.error.lastName.required"),
        ParserValidationError(2, 2, "memberDetails.error.nino.invalid")
      ))
    }

    "return validation errors for charge details, including missing, invalid, future and past tax years" in {
      val result = parser.parse(startDate, invalidChargeDetailsCsvFile)
      result mustBe Left(Seq(
        ParserValidationError(1, 4, "chargeAmount.error.required"),
        ParserValidationError(1, 5, "dateNoticeReceived.error.incomplete"),
        ParserValidationError(1, 6, "error.boolean"),
        ParserValidationError(1, 3, "annualAllowanceYear.fileUpload.error.required"),
        ParserValidationError(2, 5, "dateNoticeReceived.error.incomplete"),
        ParserValidationError(2, 3, "annualAllowanceYear.fileUpload.error.invalid"),
        ParserValidationError(3, 3, "annualAllowanceYear.fileUpload.error.future"),
        ParserValidationError(4, 3, "annualAllowanceYear.fileUpload.error.past")
      )
      )
    }

    "return validation errors for tax year only, including missing, invalid, future and past tax years" in {
      val result = parser.parse(startDate, invalidTaxYearCsvFile)
      result mustBe Left(Seq(
        ParserValidationError(1, 3, "annualAllowanceYear.fileUpload.error.required"),
        ParserValidationError(2, 3, "annualAllowanceYear.fileUpload.error.invalid"),
        ParserValidationError(3, 3, "annualAllowanceYear.fileUpload.error.future"),
        ParserValidationError(4, 3, "annualAllowanceYear.fileUpload.error.past")
      ))
    }

    "return validation errors for member details AND charge details when both present" in {
      val result = parser.parse(startDate, invalidMemberDetailsAndChargeDetailsCsvFile)
      result mustBe Left(Seq(
        ParserValidationError(1, 0, "memberDetails.error.firstName.required"),
        ParserValidationError(1, 4, "chargeAmount.error.required"),
        ParserValidationError(2, 1, "memberDetails.error.lastName.required"),
        ParserValidationError(2, 2, "memberDetails.error.nino.invalid"),
        ParserValidationError(2, 5, "dateNoticeReceived.error.invalid")
      ))
    }

    "return validation errors for member details AND charge details when errors present in first row but not in second" in {
      val result = parser.parse(startDate, invalidMemberDetailsAndChargeDetailsFirstRowCsvFile)
      result mustBe Left(Seq(
        ParserValidationError(1, 0, "memberDetails.error.firstName.required"),
        ParserValidationError(1, 4, "chargeAmount.error.required")
      ))
    }

    "return validation errors when not enough fields" in {
      val result = parser.parse(startDate, Seq(header, "Bloggs,AB123456C,2020268.28,2020-01-01,true"))
      result mustBe Left(Seq(ParserValidationError(1, 0, "Not enough fields")))
    }
  }
}

object AnnualAllowanceParserSpec extends MockitoSugar {
  private val header:String = "First name,Last name,National Insurance number,Tax year,Charge amount,Date,Payment type mandatory"

  private val mockFrontendAppConfig = mock[FrontendAppConfig]

  private val validCsvFile = Seq(
    header,
    "Joe,Bloggs,AB123456C,2020,268.28,01/01/2020,yes",
    "Joe,Bliggs,AB123457C,2020,268.28,01/01/2020,yes"
  )
  private val invalidMemberDetailsCsvFile = Seq(
    header,
    ",Bloggs,AB123456C,2020,268.28,01/01/2020,yes",
    "Ann,,3456C,2020,268.28,01/01/2020,yes"
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
