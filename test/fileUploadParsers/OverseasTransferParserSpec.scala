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
import forms.chargeG.{ChargeAmountsFormProvider, ChargeDetailsFormProvider, MemberDetailsFormProvider}
import models.UserAnswers
import models.chargeG.{ChargeAmounts, ChargeDetails}
import org.mockito.MockitoSugar.mock
import org.mockito.{Mockito, MockitoSugar}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.must.Matchers
import pages.chargeG.{ChargeAmountsPage, ChargeDetailsPage, MemberDetailsPage}
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
      val chargeAmounts = ChargeAmounts(amountTransferred = BigDecimal(1.00), amountTaxDue = BigDecimal(2.00))
      val GivingValidCSVFile = CsvParser.split(
        s"""$header
                            first,last,AB123456C,01/04/2000,123123,01/04/2020,1.00,2.00
                            Joe,Bloggs,AB123456C,01/04/2000,123123,01/04/2020,1.00,2.00"""
      )
      val result = parser.parse(startDate, GivingValidCSVFile, UserAnswers())
      result mustBe Right(UserAnswers()
        .setOrException(MemberDetailsPage(0).path, Json.toJson(SampleData.memberGDetails))
        .setOrException(ChargeDetailsPage(0).path, Json.toJson(chargeDetails))
        .setOrException(ChargeAmountsPage(0).path, Json.toJson(chargeAmounts))
        .setOrException(MemberDetailsPage(1).path, Json.toJson(SampleData.memberGDetails2))
        .setOrException(ChargeDetailsPage(1).path, Json.toJson(chargeDetails))
        .setOrException(ChargeAmountsPage(1).path, Json.toJson(chargeAmounts))
      )
    }

    "return validation error for incorrect header" in {
      val GivingIncorrectHeader = CsvParser.split("""test""")
      val result = parser.parse(startDate, GivingIncorrectHeader,UserAnswers())
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
              val GivingNotEnoughFields = CsvParser.split(
                s"""$header
                            one,two"""
              )
              val result = parser.parse(startDate, GivingNotEnoughFields,UserAnswers())
              result mustBe Left(Seq(
                ParserValidationError(1, 0, "Not enough fields")
              ))
            }

                "return validation errors for member details when present" in {
                  val GivingInvalidMemberDetails = CsvParser.split(
                    s"""$header
                            ,last,AB123456C,01/04/2000,123123,01/04/2020,1.00,2.00
                            Joe,,123456C,01/04/2000,123123,01/04/2020,1.00,2.00"""
                  )

                  val result = parser.parse(startDate, GivingInvalidMemberDetails,UserAnswers())
                  result mustBe Left(Seq(
                    ParserValidationError(1, 0, "memberDetails.error.firstName.required", "firstName"),
                    ParserValidationError(2, 1, "memberDetails.error.lastName.required", "lastName"),
                    ParserValidationError(2, 2, "memberDetails.error.nino.invalid", "nino")
                  ))
                }

                   "return validation errors for charge details when present, including missing year and missing month" in {
                     val GivingInvalidChargeDetails = CsvParser.split(
                       s"""$header
                            first,last,AB123456C,01,123123,01/04,1.00,2.00
                            Joe,Bloggs,AB123456C,01/04,123123,01,1.00,2.00"""
                     )

                     val result = parser.parse(startDate, GivingInvalidChargeDetails,UserAnswers())
                     result mustBe Left(Seq(
                       ParserValidationError(1, 3, "dob.error.incomplete", "dob",Seq("month","year")),
                       ParserValidationError(1, 5, "chargeG.chargeDetails.qropsTransferDate.error.required.two", "qropsTransferDate",Seq("year")),
                       ParserValidationError(2, 3, "dob.error.incomplete", "dob",Seq("year")),
                       ParserValidationError(2, 5, "chargeG.chargeDetails.qropsTransferDate.error.required.two", "qropsTransferDate",Seq("month","year"))
                     ))
                   }

                       "return validation errors for member details AND charge details AND charge amounts when all present" in {
                         val GivingInvalidMemberDetailsAndChargeDetails = CsvParser.split(
                           s"""$header
                            ,last,AB123456C,01,123123,01/04,A,2.00
                            Joe,,123456C,01/04,123123,01,1.00,B"""
                         )

                         val result = parser.parse(startDate, GivingInvalidMemberDetailsAndChargeDetails,UserAnswers())
                         result mustBe Left(Seq(
                           ParserValidationError(1, 0, "memberDetails.error.firstName.required", "firstName"),
                           ParserValidationError(1, 3, "dob.error.incomplete", "dob",Seq("month","year")),
                           ParserValidationError(1, 5, "chargeG.chargeDetails.qropsTransferDate.error.required.two", "qropsTransferDate",Seq("year")),
                           ParserValidationError(1,6, "The amount transferred into the QROPS for last must be an amount of money, like 123 or 123.45", "amountTransferred"),
                           ParserValidationError(2, 1, "memberDetails.error.lastName.required", "lastName"),
                           ParserValidationError(2, 3, "dob.error.incomplete", "dob",Seq("year")),
                           ParserValidationError(2, 2, "memberDetails.error.nino.invalid", "nino"),
                           ParserValidationError(2, 5, "chargeG.chargeDetails.qropsTransferDate.error.required.two", "qropsTransferDate",Seq("month","year")),
                           ParserValidationError(2,7, "amountTaxDue.error.invalid", "amountTaxDue")
                         ))
                       }

                          "return validation errors for charge amounts when present" in {

                            val GivingInvalidChargeAmounts = CsvParser.split(
                              s"""$header
                            first,last,AB123456C,01/04/2000,123123,01/04/2020,,2.00
                            Joe,Bloggs,AB123456C,01/04/2000,123123,01/04/2020,1.00,A"""
                            )

                            val result = parser.parse(startDate, GivingInvalidChargeAmounts,UserAnswers())
                            result mustBe Left(Seq(
                              ParserValidationError(1, 6, "Enter the amount transferred into the QROPS for first last", "amountTransferred"),
                              ParserValidationError(2, 7, "amountTaxDue.error.invalid", "amountTaxDue")
                            ))
                          }

  }

}

object OverseasTransferParserSpec {
  private val header = "First name,Last name,National Insurance number,Date of birth,Reference number,Transfer date,Amount,Tax due"
  //First name,Last name,National Insurance number,Date of birth,Reference number,Transfer date,Amount,Tax due
  private val mockFrontendAppConfig = mock[FrontendAppConfig]

  private val memberDetailsFormProvider = new MemberDetailsFormProvider
  private val chargeDetailsFormProvider = new ChargeDetailsFormProvider
  private val chargeAmountsFormProvider = new ChargeAmountsFormProvider

  private val parser = new OverseasTransferParser(memberDetailsFormProvider, chargeDetailsFormProvider, chargeAmountsFormProvider, mockFrontendAppConfig)
}
