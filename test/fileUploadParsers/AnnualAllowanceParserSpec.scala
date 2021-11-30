/*
 * Copyright 2021 HM Revenue & Customs
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
import models.chargeE.ChargeEDetails
import models.{MemberDetails, UserAnswers, YearRange}
import org.mockito.MockitoSugar
import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.must.Matchers
import pages.chargeE.{AddMembersPage, AnnualAllowanceYearPage, ChargeDetailsPage, MemberDetailsPage}

import java.time.LocalDate

class AnnualAllowanceParserSpec extends SpecBase with Matchers with MockitoSugar with BeforeAndAfterEach {

  "Annual allowance parser" must {

    "return validation errors" when {

      "firstname, lastName is empty" in {
        val line = ",,AB123456C,2020,101.17,2020-01-01,true"
        assert(line, List("firstName.is.empty", "lastName.is.empty"))
      }

      "firstname, lastName is too long" in {
        val line = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA,AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA,AB123456C,2020,101.17,2020-01-01,true"
        assert(line, List("firstName.is.too.long", "lastName.is.too.long"))
      }

      "nino is empty" in {
        val line = "Joe,Bloggs,,2020,101.17,2020-01-01,true"
        assert(line, List("nino.is.empty"))
      }

      "nino is invalid" in {
        val line = "Joe,Bloggs,######,2020,101.17,2020-01-01,true"
        assert(line, List("nino.is.not.valid"))
      }

      "charge amount is empty" in {
        val line = "Joe,Bloggs,AB123456C,2020,,2020-01-01,true"
        assert(line, List("chargeAmount.is.empty"))
      }

      "charge type is not an integer" in {
        val line = "Joe,Bloggs,AB123456C,2020,###,2020-01-01,true"
        assert(line, List("chargeAmount.is.not.valid"))
      }

      "charge type has over 2 decimal places" in {
        val line = "Joe,Bloggs,AB123456C,2020,10.111,2020-01-01,true"
        assert(line, List("chargeAmount.has.to.many.decimal.places"))
      }

      def assert(line: String, error: List[String]) = {
        val userAnswers = UserAnswers()

        val lines = List(line)

        val updatedUserAnswers = AnnualAllowanceParser.parse(userAnswers, lines)

        val expected = List(
          ParserValidationErrors(0, error)
        )

        updatedUserAnswers.fold(
          a => a mustBe expected,
          b => fail(s"Should have failed to parse: $b")
        )
      }
    }

    "return updated UserAnswers" when {

      "parsing single line" in {
        val userAnswers = UserAnswers()

        val lines = List("Joe,Bloggs,AB123456C,2020,101.17,2020-01-01,true")

        val updatedUserAnswers = AnnualAllowanceParser.parse(userAnswers, lines)

        val expected = AddMember(
          UserAnswers().setOrException(AddMembersPage, true),
          0,
          "Joe",
          "Bloggs",
          "AB123456C",
          BigDecimal(101.17),
          LocalDate.parse("2020-01-01"),
          true,
          "2020")

        updatedUserAnswers.fold(_ => fail(), a => a.data mustBe expected.data)
      }

      "parsing multiple lines" in {
        val userAnswers = UserAnswers()

        val lines = List(
          "Joe,Bloggs,AB123456C,2020,101,2020-01-01,true",
          "Sarah,Smith,AB123456C,2021,208.10,2020-01-02,false",
          "Sam,Jones,AB123456C,2020,8.38,2020-01-04,false"
        )

        val updatedUserAnswers = AnnualAllowanceParser.parse(userAnswers, lines)

        val withMember1 = AddMember(
          UserAnswers().setOrException(AddMembersPage, true),
          0,
          "Joe",
          "Bloggs",
          "AB123456C",
          BigDecimal(101),
          LocalDate.parse("2020-01-01"),
          true,
          "2020")

        val withMember2 = AddMember(
          withMember1,
          1,
          "Sarah",
          "Smith",
          "AB123456C",
          BigDecimal(208.10),
          LocalDate.parse("2020-01-02"),
          false,
          "2021")

        val expected = AddMember(
          withMember2,
          2,
          "Sam",
          "Jones",
          "AB123456C",
          BigDecimal(8.38),
          LocalDate.parse("2020-01-04"),
          false,
          "2020")

          updatedUserAnswers.fold(a => fail(s"$a"), a => a.data mustBe expected.data)
      }

      def AddMember(userAnswers: UserAnswers,
                    index: Int,
                    fName: String,
                    lName: String,
                    nino: String,
                    amount: BigDecimal,
                    date: LocalDate,
                    isPaymentMandatory: Boolean,
                    yearRange: String) = {
        userAnswers
          .setOrException(MemberDetailsPage(index), MemberDetails(fName, lName, nino))
          .setOrException(
            ChargeDetailsPage(index), ChargeEDetails(amount, date, isPaymentMandatory))
          .setOrException(AnnualAllowanceYearPage(index), YearRange(yearRange))
      }
    }
  }
}