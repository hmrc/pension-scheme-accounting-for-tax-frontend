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
import forms.MemberDetailsFormProvider
import forms.chargeE.ChargeDetailsFormProvider
import models.UserAnswers
import org.mockito.{Mockito, MockitoSugar}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.must.Matchers
import pages.chargeE.MemberDetailsPage

import java.time.LocalDate

class AnnualAllowanceParserSpec extends SpecBase with Matchers with MockitoSugar with BeforeAndAfterEach {
  //scalastyle:off magic.number

  import AnnualAllowanceParserSpec._

  override def beforeEach: Unit = {
    Mockito.reset(mockFrontendAppConfig)
    when(mockFrontendAppConfig.earliestDateOfNotice).thenReturn(LocalDate.of(1900,1,1))
  }

  "Annual allowance parser" must {
    "return charges in user answers when there are no validation errors" in {
      val result = parser.parse(emptyUa, validCsvFile)
      result.errors mustBe Nil
      result.ua.getOrException(MemberDetailsPage(0)) mustBe SampleData.memberDetails2
      result.ua.getOrException(MemberDetailsPage(1)) mustBe SampleData.memberDetails3
    }

    "return validation errors for member details when present" in {
      val result = parser.parse(emptyUa, invalidMemberDetailsCsvFile)
      result.errors mustBe List(
        ParserValidationErrors(0, Seq("memberDetails.error.firstName.required")),
        ParserValidationErrors(1, Seq("memberDetails.error.lastName.required", "memberDetails.error.nino.invalid"))
      )
    }

    "return validation errors for charge details when present" in {
      val result = parser.parse(emptyUa, invalidChargeDetailsCsvFile)
      result.errors mustBe List(
        ParserValidationErrors(0, Seq("chargeAmount.error.required")),
        ParserValidationErrors(1, Seq("dateNoticeReceived.error.invalid"))
      )
    }

    "return validation errors for member details AND charge details when both present" in {
      val result = parser.parse(emptyUa, invalidMemberDetailsAndChargeDetailsCsvFile)
      result.errors mustBe List(
        ParserValidationErrors(0, Seq("memberDetails.error.firstName.required", "chargeAmount.error.required")),
        ParserValidationErrors(1, Seq("memberDetails.error.lastName.required", "memberDetails.error.nino.invalid", "dateNoticeReceived.error.invalid"))
      )
    }

    "return validation errors for member details AND charge details when both present in first row but not in second" in {
      val result = parser.parse(emptyUa, invalidMemberDetailsAndChargeDetailsFirstRowCsvFile)
      result.errors mustBe List(
        ParserValidationErrors(0, Seq("memberDetails.error.firstName.required", "chargeAmount.error.required"))
      )
    }

    "return validation errors when not enough fields" in {
      val result = parser.parse(emptyUa, List("Bloggs,AB123456C,2020268.28,2020-01-01,true"))
      result.errors mustBe List(ParserValidationErrors(0, Seq("Not enough fields")))
    }
  }
}

object AnnualAllowanceParserSpec extends MockitoSugar {

  private val mockFrontendAppConfig = mock[FrontendAppConfig]

  private val validCsvFile = List(
    "Joe,Bloggs,AB123456C,2020,268.28,01/01/2020,yes",
    "Joe,Bliggs,AB123457C,2020,268.28,01/01/2020,yes"
  )
  private val invalidMemberDetailsCsvFile = List(
    ",Bloggs,AB123456C,2020,268.28,01/01/2020,yes",
    "Ann,,3456C,2020,268.28,01/01/2020,yes"
  )
  private val invalidChargeDetailsCsvFile = List(
    "Joe,Bloggs,AB123456C,2020,,01/01/2020,yes",
    "Ann,Bliggs,AB123457C,2020,268.28,01/13/2020,yes"
  )
  private val invalidMemberDetailsAndChargeDetailsCsvFile = List(
    ",Bloggs,AB123456C,2020,,01/01/2020,yes",
    "Ann,,3456C,2020,268.28,01/13/2020,yes"
  )

  private val invalidMemberDetailsAndChargeDetailsFirstRowCsvFile = List(
    ",Bloggs,AB123456C,2020,,01/01/2020,yes",
    "Joe,Bliggs,AB123457C,2020,268.28,01/01/2020,yes"
  )
  private val emptyUa = UserAnswers()
  private val formProviderMemberDetails = new MemberDetailsFormProvider
  private val formProviderChargeDetails = new ChargeDetailsFormProvider

  private val parser = new AnnualAllowanceParser(formProviderMemberDetails, formProviderChargeDetails, mockFrontendAppConfig)
}
