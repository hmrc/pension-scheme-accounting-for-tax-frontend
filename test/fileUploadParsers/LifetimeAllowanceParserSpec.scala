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
import forms.MemberDetailsFormProvider
import models.UserAnswers
import org.mockito.MockitoSugar
import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.must.Matchers
import pages.chargeD.MemberDetailsPage
import play.api.libs.json.Json

class LifetimeAllowanceParserSpec extends SpecBase with Matchers with MockitoSugar with BeforeAndAfterEach {

  import LifetimeAllowanceParserSpec._

  "LifeTime allowance parser" must {
    "return validation errors when present" in {
      val result = parser.parse(invalidCsvFile)
      result.errors mustBe List(
        ParserValidationErrors(0, Seq("memberDetails.error.firstName.required")),
        ParserValidationErrors(1, Seq("memberDetails.error.lastName.required", "memberDetails.error.nino.invalid"))
      )
    }

    "return charges in user answers when there are no validation errors" in {
      val result = parser.parse(validCsvFile)

      result.errors mustBe Nil
      result.commitItems mustBe Seq(
        CommitItem(MemberDetailsPage(0).path, Json.toJson(SampleData.memberDetails2)),
        CommitItem(MemberDetailsPage(1).path, Json.toJson(SampleData.memberDetails3))
      )
    }

    "return validation errors when not enough fields" in {
      val result = parser.parse(List("Bloggs,AB123456C,2020268.28,2020-01-01,true"))

      result.errors mustBe List(ParserValidationErrors(0, Seq("Not enough fields")))
    }
  }

}

object LifetimeAllowanceParserSpec {

  private val validCsvFile = List("Joe,Bloggs,AB123456C,2020,268.28,2020-01-01,true", "Joe,Bliggs,AB123457C,2020,268.28,2020-01-01,true")
  private val invalidCsvFile = List(",Bloggs,AB123456C,2020,268.28,2020-01-01,true", "Ann,,3456C,2020,268.28,2020-01-01,true")
  private val emptyUa = UserAnswers()
  private val formProvider = new MemberDetailsFormProvider

  private val parser = new LifetimeAllowanceParser(formProvider)
}

