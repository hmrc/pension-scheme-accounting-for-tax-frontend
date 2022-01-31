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
import forms.chargeG.{ChargeDetailsFormProvider, MemberDetailsFormProvider}
import models.chargeG.ChargeDetails
import org.mockito.MockitoSugar.mock
import org.mockito.{Mockito, MockitoSugar}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.must.Matchers
import pages.chargeG.{ChargeDetailsPage, MemberDetailsPage}
import play.api.libs.json.Json

import java.time.LocalDate

class OverseasTransferParserSpec extends SpecBase with Matchers with MockitoSugar with BeforeAndAfterEach {
  //scalastyle:off magic.number

  import OverseasTransferParserSpec._

  override def beforeEach: Unit = {
    Mockito.reset(mockFrontendAppConfig)
    when(mockFrontendAppConfig.validOverseasTransferHeader).thenReturn(header)
  }

  "Overseas transfer parser" must {
    "return charges in user answers when there are no validation errors" in {
      val chargeDetails = ChargeDetails(qropsReferenceNumber = "123123", qropsTransferDate = LocalDate.of(2020,4,1))
      val result = parser.parse(startDate, validCsvFile)
      result mustBe Right(Seq(
        CommitItem(MemberDetailsPage(0).path, Json.toJson(SampleData.memberGDetails)),
        CommitItem(ChargeDetailsPage(0).path, Json.toJson(chargeDetails)),
        CommitItem(MemberDetailsPage(1).path, Json.toJson(SampleData.memberGDetails2)),
        CommitItem(ChargeDetailsPage(1).path, Json.toJson(chargeDetails)),
      ))
    }

//    "return validation error for incorrect header" in {
//      val result = parser.parse(startDate, Seq("test"))
//      result mustBe Left(Seq(
//        ParserValidationError(0, 0, "Header invalid")
//      ))
//    }
//
//    "return validation error for empty file" in {
//      val result = parser.parse(startDate, Nil)
//      result mustBe Left(Seq(
//        ParserValidationError(0, 0, "File is empty")
//      ))
//    }
//
//    "return validation error for not enough fields" in {
//      val result = parser.parse(startDate, Seq(header, "one,two"))
//      result mustBe Left(Seq(
//        ParserValidationError(1, 0, "Not enough fields")
//      ))
//    }
//
//    "return validation errors for member details when present" in {
//      val result = parser.parse(startDate, invalidMemberDetailsCsvFile)
//      result mustBe Left(Seq(
//        ParserValidationError(1, 0, "memberDetails.error.firstName.required"),
//        ParserValidationError(2, 1, "memberDetails.error.lastName.required"),
//        ParserValidationError(2, 2, "memberDetails.error.nino.invalid")
//      ))
//    }
//
//    "return validation errors for charge details when present, including missing year and missing month" in {
//      val result = parser.parse(startDate, invalidChargeDetailsCsvFile)
//      result mustBe Left(Seq(
//        ParserValidationError(1, 3, "dateOfEvent.error.incomplete"),
//        ParserValidationError(2, 3, "dateOfEvent.error.incomplete")
//      ))
//    }
//
//    "return validation errors for member details AND charge details when both present" in {
//      val result = parser.parse(startDate, invalidMemberDetailsAndChargeDetailsCsvFile)
//      result mustBe Left(Seq(
//        ParserValidationError(1, 0, "memberDetails.error.firstName.required"),
//        ParserValidationError(1, 3, "dateOfEvent.error.incomplete"),
//        ParserValidationError(2, 1, "memberDetails.error.lastName.required"),
//        ParserValidationError(2, 2, "memberDetails.error.nino.invalid"),
//        ParserValidationError(2, 3, "dateOfEvent.error.incomplete")
//      ))
//    }
//
//    "return validation errors for member details AND charge details when errors present in first row but not in second" in {
//      val result = parser.parse(startDate, invalidMemberDetailsAndChargeDetailsFirstRowCsvFile)
//      result mustBe Left(Seq(
//        ParserValidationError(1, 0, "memberDetails.error.firstName.required"),
//        ParserValidationError(1, 3, "dateOfEvent.error.incomplete"),
//      ))
//    }
//
//    "return validation errors when not enough fields" in {
//      val result = parser.parse(startDate, Seq(header, "Bloggs,AB123456C,2020268.28,2020-01-01,true"))
//
//      result mustBe Left(Seq(ParserValidationError(1, 0, "Not enough fields")))
//    }
  }

}

object OverseasTransferParserSpec {
  private val header = "First name,Last name,National Insurance number,Date of birth,Reference number,Transfer date,Amount,Tax due"
  //First name,Last name,National Insurance number,Date of birth,Reference number,Transfer date,Amount,Tax due
  private val mockFrontendAppConfig = mock[FrontendAppConfig]

  private val validCsvFile = Seq(
    header,
    "first,last,AB123456C,01/04/2000,123123,01/04/2020,0.00,0.00",
    "Joe,Bloggs,AB123456C,01/04/2000,123123,01/04/2020,0.00,0.00"
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

  private val parser = new OverseasTransferParser(memberDetailsFormProvider, chargeDetailsFormProvider, mockFrontendAppConfig)
}
