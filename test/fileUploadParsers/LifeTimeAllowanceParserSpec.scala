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

class LifeTimeAllowanceParserSpec extends SpecBase with Matchers with MockitoSugar with BeforeAndAfterEach {

  import LifeTimeAllowanceParserSpec._

  "LifeTime allowance parser" must {
    "return validation errors when present" in {
      val result = parser.parse(emptyUa, invalidCsvFile)
      result.errors mustBe List(ParserValidationErrors(0, Seq("memberDetails.error.firstName.required")))
    }

    "return charges in user answers when there are no validation errors" in {
      val result = parser.parse(emptyUa, validCsvFile)

      result.errors mustBe Nil
      result.ua.getOrException(MemberDetailsPage(0)) mustBe SampleData.memberDetails2
    }

    "return validation errors when not enough fields" in {
      val result = parser.parse(emptyUa, List("Bloggs,AB123456C,2020268.28,2020-01-01,true"))

      result.errors mustBe List(ParserValidationErrors(0, Seq("Not enough fields")))
    }
  }

}

object LifeTimeAllowanceParserSpec {

  private val validCsvFile = List("Joe,Bloggs,AB123456C,2020,268.28,2020-01-01,true")
  private val invalidCsvFile = List(",Bloggs,AB123456C,2020,268.28,2020-01-01,true")
  private val emptyUa = UserAnswers()
  private val formProvider = new MemberDetailsFormProvider

  private val parser = new LifeTimeAllowanceParser(formProvider)
}

