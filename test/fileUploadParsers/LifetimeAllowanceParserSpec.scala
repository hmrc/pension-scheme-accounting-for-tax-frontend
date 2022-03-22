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
import fileUploadParsers.AnnualAllowanceParserSpec.mock
import fileUploadParsers.ParserErrorMessages.{HeaderInvalidOrFileIsEmpty, NotEnoughFields}
import forms.MemberDetailsFormProvider
import forms.chargeD.ChargeDetailsFormProvider
import models.UserAnswers
import models.chargeD.ChargeDDetails
import org.mockito.{Mockito, MockitoSugar}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.must.Matchers
import pages.chargeD.{ChargeDetailsPage, MemberDetailsPage}
import play.api.libs.json.Json

import java.time.LocalDate

class LifetimeAllowanceParserSpec extends SpecBase with Matchers with MockitoSugar with BeforeAndAfterEach {
  //scalastyle:off magic.number

  import LifetimeAllowanceParserSpec._

  override def beforeEach: Unit = {
    Mockito.reset(mockFrontendAppConfig)
    when(mockFrontendAppConfig.validLifeTimeAllowanceHeader).thenReturn(header)
  }

  "LifeTime allowance parser" must {
    "return charges in user answers when there are no validation errors" in {
      val GivingValidCSVFile = CsvLineSplitter.split(
        s"""$header
                            Joe,Bloggs,AB123456C,01/04/2020,268.28,0.00
                            Joe,Bliggs,AB123457C,01/04/2020,268.28,0.00"""
      )

      val chargeDetails = ChargeDDetails(LocalDate.of(2020, 4, 1), Some(BigDecimal(268.28)), None)
      val result = parser.parse(startDate, GivingValidCSVFile, UserAnswers())
      result mustBe Right(UserAnswers()
        .setOrException(MemberDetailsPage(0).path, Json.toJson(SampleData.memberDetails2))
        .setOrException(ChargeDetailsPage(0).path, Json.toJson(chargeDetails))
        .setOrException(MemberDetailsPage(1).path, Json.toJson(SampleData.memberDetails3))
        .setOrException(ChargeDetailsPage(1).path, Json.toJson(chargeDetails))
      )
    }

    "return validation error for incorrect header" in {
      val GivingIncorrectHeader = CsvLineSplitter.split("""test""")
      val result = parser.parse(startDate, GivingIncorrectHeader,UserAnswers())
      result mustBe Left(Seq(
        ParserValidationError(0, 0, HeaderInvalidOrFileIsEmpty)
      ))
    }

    "return validation error for empty file" in {
      val result = parser.parse(startDate, Nil,UserAnswers())
      result mustBe Left(Seq(
        ParserValidationError(0, 0, HeaderInvalidOrFileIsEmpty)
      ))
    }

    "return validation error for not enough fields" in {
      val GivingNotEnoughFields = CsvLineSplitter.split(
        s"""$header
                            one,two"""
      )
      val result = parser.parse(startDate,GivingNotEnoughFields,UserAnswers())
      result mustBe Left(Seq(
        ParserValidationError(1, 0, NotEnoughFields)
      ))
    }

    "return validation errors for member details when present" in {
      val GivingIncorrectMemberDetails = CsvLineSplitter.split(
        s"""$header
                            ,Bloggs,AB123456C,01/04/2020,268.28,0.00
                            Ann,,3456C,01/04/2020,268.28,0.00"""
      )
      val result = parser.parse(startDate, GivingIncorrectMemberDetails,UserAnswers())
      result mustBe Left(Seq(
        ParserValidationError(1, 0, "memberDetails.error.firstName.required", "firstName"),
        ParserValidationError(2, 1, "memberDetails.error.lastName.required", "lastName"),
        ParserValidationError(2, 2, "memberDetails.error.nino.invalid", "nino")
      ))
    }

    "return validation errors for charge details when present, including missing year and missing month" in {
      val GivingMissingYearAndMonth = CsvLineSplitter.split(
        s"""$header
                            Joe,Bloggs,AB123456C,01/04,268.28,0.00
                            Ann,Bliggs,AB123457C,01,268.28,0.00"""
      )

      val result = parser.parse(startDate, GivingMissingYearAndMonth,UserAnswers())
      result mustBe Left(Seq(
        ParserValidationError(1, 3, "dateOfEvent.error.incomplete", "dateOfEvent",Seq("year")),
        ParserValidationError(2, 3, "dateOfEvent.error.incomplete", "dateOfEvent",Seq("month","year"))
      ))
    }

    "return validation errors for member details AND charge details when both present" in {
      val GivingIncorrectMemberDetailsAndChargeDetails = CsvLineSplitter.split(
        s"""$header
                            ,Bloggs,AB123456C,01/04,268.28,0.00
                            Ann,,3456C,01,268.28,0.00"""
      )
      val result = parser.parse(startDate, GivingIncorrectMemberDetailsAndChargeDetails,UserAnswers())
      result mustBe Left(Seq(
        ParserValidationError(1, 0, "memberDetails.error.firstName.required", "firstName"),
        ParserValidationError(1, 3, "dateOfEvent.error.incomplete", "dateOfEvent",Seq("year")),
        ParserValidationError(2, 1, "memberDetails.error.lastName.required", "lastName"),
        ParserValidationError(2, 2, "memberDetails.error.nino.invalid", "nino"),
        ParserValidationError(2, 3, "dateOfEvent.error.incomplete", "dateOfEvent",Seq("month","year"))
      ))
    }

    "return validation errors for member details AND charge details when errors present in first row but not in second" in {
      val GivingIncorrectMemberDetailsAndChargeDetailsFirstRow = CsvLineSplitter.split(
        s"""$header
                            ,Bloggs,AB123456C,01/04,268.28,0.00
                            Joe,Bliggs,AB123457C,01/04/2020,268.28,0.00"""
      )

      val result = parser.parse(startDate, GivingIncorrectMemberDetailsAndChargeDetailsFirstRow,UserAnswers())
      result mustBe Left(Seq(
        ParserValidationError(1, 0, "memberDetails.error.firstName.required", "firstName"),
        ParserValidationError(1, 3, "dateOfEvent.error.incomplete", "dateOfEvent",Seq("year")),
      ))
    }

    "return validation errors when not enough fields" in {
      val GivingNotEnoughFields = CsvLineSplitter.split(
        s"""$header
                            Bloggs,AB123456C,2020268.28,2020-01-01,true"""
      )
      val result = parser.parse(startDate, GivingNotEnoughFields,UserAnswers())

      result mustBe Left(Seq(ParserValidationError(1, 0, "Enter all of the information for this member")))
    }
  }

}

object LifetimeAllowanceParserSpec {
  private val header = "First name,Last name,National Insurance number,Date,Tax due 25%,Tax due 55%"

  private val mockFrontendAppConfig = mock[FrontendAppConfig]

  private val memberDetailsFormProvider = new MemberDetailsFormProvider
  private val chargeDetailsFormProvider = new ChargeDetailsFormProvider

  private val parser = new LifetimeAllowanceParser(memberDetailsFormProvider, chargeDetailsFormProvider, mockFrontendAppConfig)
}
