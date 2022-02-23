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
      val chargeDetails = ChargeDDetails(LocalDate.of(2020, 4, 1), Some(BigDecimal(268.28)), None)
      val result = parser.parse(startDate, validCsvFile, UserAnswers())
      result mustBe Right(UserAnswers()
        .setOrException(MemberDetailsPage(0).path, Json.toJson(SampleData.memberDetails2))
        .setOrException(ChargeDetailsPage(0).path, Json.toJson(chargeDetails))
        .setOrException(MemberDetailsPage(1).path, Json.toJson(SampleData.memberDetails3))
        .setOrException(ChargeDetailsPage(1).path, Json.toJson(chargeDetails))
      )
    }

    "return validation error for incorrect header" in {
      val result = parser.parse(startDate, Seq("test"),UserAnswers())
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
      val result = parser.parse(startDate, Seq(header, "one,two"),UserAnswers())
      result mustBe Left(Seq(
        ParserValidationError(1, 0, "Not enough fields")
      ))
    }

    "return validation errors for member details when present" in {
      val result = parser.parse(startDate, invalidMemberDetailsCsvFile,UserAnswers())
      result mustBe Left(Seq(
        ParserValidationError(1, 0, "memberDetails.error.firstName.required"),
        ParserValidationError(2, 1, "memberDetails.error.lastName.required"),
        ParserValidationError(2, 2, "memberDetails.error.nino.invalid")
      ))
    }

    "return validation errors for charge details when present, including missing year and missing month" in {
      val result = parser.parse(startDate, invalidChargeDetailsCsvFile,UserAnswers())
      result mustBe Left(Seq(
        ParserValidationError(1, 3, "dateOfEvent.error.incomplete"),
        ParserValidationError(2, 3, "dateOfEvent.error.incomplete")
      ))
    }

    "return validation errors for member details AND charge details when both present" in {
      val result = parser.parse(startDate, invalidMemberDetailsAndChargeDetailsCsvFile,UserAnswers())
      result mustBe Left(Seq(
        ParserValidationError(1, 0, "memberDetails.error.firstName.required"),
        ParserValidationError(1, 3, "dateOfEvent.error.incomplete"),
        ParserValidationError(2, 1, "memberDetails.error.lastName.required"),
        ParserValidationError(2, 2, "memberDetails.error.nino.invalid"),
        ParserValidationError(2, 3, "dateOfEvent.error.incomplete")
      ))
    }

    "return validation errors for member details AND charge details when errors present in first row but not in second" in {
      val result = parser.parse(startDate, invalidMemberDetailsAndChargeDetailsFirstRowCsvFile,UserAnswers())
      result mustBe Left(Seq(
        ParserValidationError(1, 0, "memberDetails.error.firstName.required"),
        ParserValidationError(1, 3, "dateOfEvent.error.incomplete"),
      ))
    }

    "return validation errors when not enough fields" in {
      val result = parser.parse(startDate, Seq(header, "Bloggs,AB123456C,2020268.28,2020-01-01,true"),UserAnswers())

      result mustBe Left(Seq(ParserValidationError(1, 0, "Not enough fields")))
    }
  }

}

object LifetimeAllowanceParserSpec {
  private val header = "First name,Last name,National Insurance number,Date,Tax due 25%,Tax due 55%"

  private val mockFrontendAppConfig = mock[FrontendAppConfig]

  private val validCsvFile = Seq(
    header,
    "Joe,Bloggs,AB123456C,01/04/2020,268.28,0.00",
    "Joe,Bliggs,AB123457C,01/04/2020,268.28,0.00"
  )
  private val invalidMemberDetailsCsvFile = Seq(
    header,
    ",Bloggs,AB123456C,01/04/2020,268.28,0.00",
    "Ann,,3456C,01/04/2020,268.28,0.00"
  )
  private val invalidChargeDetailsCsvFile = Seq(
    header,
    "Joe,Bloggs,AB123456C,01/04,268.28,0.00",
    "Ann,Bliggs,AB123457C,01,268.28,0.00"
  )
  private val invalidMemberDetailsAndChargeDetailsCsvFile = Seq(
    header,
    ",Bloggs,AB123456C,01/04,268.28,0.00",
    "Ann,,3456C,01,268.28,0.00"
  )

  private val invalidMemberDetailsAndChargeDetailsFirstRowCsvFile = Seq(
    header,
    ",Bloggs,AB123456C,01/04,268.28,0.00",
    "Joe,Bliggs,AB123457C,01/04/2020,268.28,0.00"
  )

  private val memberDetailsFormProvider = new MemberDetailsFormProvider
  private val chargeDetailsFormProvider = new ChargeDetailsFormProvider

  private val parser = new LifetimeAllowanceParser(memberDetailsFormProvider, chargeDetailsFormProvider, mockFrontendAppConfig)
}