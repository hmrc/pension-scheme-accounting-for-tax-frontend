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
import data.SampleData
import data.SampleData.startDate
import forms.MemberDetailsFormProvider
import forms.chargeD.ChargeDetailsFormProvider
import models.chargeD.ChargeDDetails
import org.mockito.MockitoSugar
import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.must.Matchers
import pages.chargeD.{ChargeDetailsPage, MemberDetailsPage}
import play.api.libs.json.Json

import java.time.LocalDate

class LifetimeAllowanceParserSpec extends SpecBase with Matchers with MockitoSugar with BeforeAndAfterEach {
  //scalastyle:off magic.number
  import LifetimeAllowanceParserSpec._

  "LifeTime allowance parser" must {
    "return charges in user answers when there are no validation errors" in {
      val chargeDetails = ChargeDDetails(LocalDate.of(2020,4,1), Some(BigDecimal(268.28)), None)
      val result = parser.parse(startDate, validCsvFile)
      result mustBe Right(Seq(
        CommitItem(MemberDetailsPage(0).path, Json.toJson(SampleData.memberDetails2)),
        CommitItem(ChargeDetailsPage(0).path, Json.toJson(chargeDetails)),
        CommitItem(MemberDetailsPage(1).path, Json.toJson(SampleData.memberDetails3)),
        CommitItem(ChargeDetailsPage(1).path, Json.toJson(chargeDetails)),
      ))
    }

    "return validation errors for member details when present" in {
      val result = parser.parse(startDate, invalidMemberDetailsCsvFile)
      result mustBe Left(Seq(
        ParserValidationErrors(0, Seq("memberDetails.error.firstName.required")),
        ParserValidationErrors(1, Seq("memberDetails.error.lastName.required", "memberDetails.error.nino.invalid"))
      ))
    }

    "return validation errors for charge details when present, including missing year and missing month" in {
      val result = parser.parse(startDate, invalidChargeDetailsCsvFile)
      result mustBe Left(Seq(
        ParserValidationErrors(0, Seq("dateOfEvent.error.incomplete")),
        ParserValidationErrors(1, Seq("dateOfEvent.error.incomplete"))
      ))
    }

    "return validation errors for member details AND charge details when both present" in {
      val result = parser.parse(startDate, invalidMemberDetailsAndChargeDetailsCsvFile)
      result mustBe Left(Seq(
        ParserValidationErrors(0, Seq("memberDetails.error.firstName.required", "dateOfEvent.error.incomplete")),
        ParserValidationErrors(1, Seq("memberDetails.error.lastName.required", "memberDetails.error.nino.invalid", "dateOfEvent.error.incomplete"))
      ))
    }

    "return validation errors for member details AND charge details when errors present in first row but not in second" in {
      val result = parser.parse(startDate, invalidMemberDetailsAndChargeDetailsFirstRowCsvFile)
      result mustBe Left(Seq(
        ParserValidationErrors(0, Seq("memberDetails.error.firstName.required", "dateOfEvent.error.incomplete")),
      ))
    }

    "return validation errors when not enough fields" in {
      val result = parser.parse(startDate, Seq("Bloggs,AB123456C,2020268.28,2020-01-01,true"))

      result mustBe Left(Seq(ParserValidationErrors(0, Seq("Not enough fields"))))
    }
  }

}

object LifetimeAllowanceParserSpec {
  private val validCsvFile = Seq(
    "Joe,Bloggs,AB123456C,01/04/2020,268.28,0.00",
    "Joe,Bliggs,AB123457C,01/04/2020,268.28,0.00"
  )
  private val invalidMemberDetailsCsvFile = Seq(
    ",Bloggs,AB123456C,01/04/2020,268.28,0.00",
    "Ann,,3456C,01/04/2020,268.28,0.00"
  )
  private val invalidChargeDetailsCsvFile = Seq(
    "Joe,Bloggs,AB123456C,01/04,268.28,0.00",
    "Ann,Bliggs,AB123457C,01,268.28,0.00"
  )
  private val invalidMemberDetailsAndChargeDetailsCsvFile = Seq(
    ",Bloggs,AB123456C,01/04,268.28,0.00",
    "Ann,,3456C,01,268.28,0.00"
  )

  private val invalidMemberDetailsAndChargeDetailsFirstRowCsvFile = Seq(
    ",Bloggs,AB123456C,01/04,268.28,0.00",
    "Joe,Bliggs,AB123457C,01/04/2020,268.28,0.00"
  )

  private val memberDetailsFormProvider = new MemberDetailsFormProvider
  private val chargeDetailsFormProvider = new ChargeDetailsFormProvider

  private val parser = new LifetimeAllowanceParser(memberDetailsFormProvider, chargeDetailsFormProvider)
}
