/*
 * Copyright 2023 HM Revenue & Customs
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
import controllers.fileUpload.FileUploadHeaders.AnnualAllowanceFieldNames
import data.SampleData
import data.SampleData.startDate
import forms.chargeE.ChargeDetailsFormProvider
import forms.mccloud.{ChargeAmountReportedFormProvider, EnterPstrFormProvider}
import forms.{MemberDetailsFormProvider, YesNoFormProvider}
import helpers.ParserHelper
import models.chargeE.ChargeEDetails
import models.{AFTQuarter, ChargeType, UserAnswers, YearRange}
import org.mockito.Mockito
import org.mockito.Mockito.when
import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import pages.IsPublicServicePensionsRemedyPage
import pages.chargeE.{AnnualAllowanceYearPage, ChargeDetailsPage, MemberDetailsPage}
import pages.mccloud._
import utils.DateHelper

import java.time.LocalDate

class AnnualAllowanceMcCloudParserSpec extends SpecBase
  with Matchers with MockitoSugar with BeforeAndAfterEach with ParserHelper {
  //scalastyle:off magic.number

  import AnnualAllowanceMcCloudParserSpec._

  override def beforeEach(): Unit = {
    Mockito.reset(mockFrontendAppConfig)
    when(mockFrontendAppConfig.earliestDateOfNotice).thenReturn(LocalDate.of(1900, 1, 1))
    when(mockFrontendAppConfig.validAnnualAllowanceMcCloudHeader).thenReturn(header)
  }


  "Annual allowance McCloud parser" must {

//    "return charges in user answers when there are no validation errors" in {
//
//      DateHelper.setDate(Some(LocalDate.of(2023, 12, 12)))
//
//      val validCsvFile: Seq[Array[String]] = CsvLineSplitter.split(
//        s"""$header
//Joe,Bloggs,AB123456C,2020 to 2021,268.28,01/01/2020,YES,YES,NO,,31/03/2022,45.66,,,,,,,,,,,,
//Joe,Bliggs,AB123457C,2020 to 2021,100.50,01/01/2020,NO,YES,YES,24000017RN,30/06/2022,102.55,24000018RN,31/03/2022,99.88,24000019RN,30/06/2022,99.88,24000020RN,31/03/2022,99.88,24000021RN,31/03/2022,498.90
//Joe,Blaggs,AB123458C,2020 to 2021,68.28,01/01/2020,YES,NO,,,,,,,,,,,,,,,,"""
//      )
//
//      val chargeDetails1 = ChargeEDetails(BigDecimal(268.28), LocalDate.of(2020, 1, 1), isPaymentMandatory = true)
//      val chargeDetails2 = ChargeEDetails(BigDecimal(100.50), LocalDate.of(2020, 1, 1), isPaymentMandatory = false)
//      val chargeDetails3 = ChargeEDetails(BigDecimal(68.28), LocalDate.of(2020, 1, 1), isPaymentMandatory = true)
//      val result = parser.parse(startDate, validCsvFile, UserAnswers())
//
//      result.isValid mustBe true
//      val actualUA = result.toOption.value
//
//      actualUA.get(MemberDetailsPage(0)) mustBe Some(SampleData.memberDetails2)
//      actualUA.get(ChargeDetailsPage(0)) mustBe Some(chargeDetails1)
//      actualUA.get(AnnualAllowanceYearPage(0)) mustBe Some(YearRange("2020"))
//      actualUA.get(IsPublicServicePensionsRemedyPage(ChargeType.ChargeTypeAnnualAllowance, Some(0))) mustBe Some(true)
//      actualUA.get(IsChargeInAdditionReportedPage(ChargeType.ChargeTypeAnnualAllowance, 0)) mustBe Some(true)
//      actualUA.get(WasAnotherPensionSchemePage(ChargeType.ChargeTypeAnnualAllowance, 0)) mustBe Some(false)
//      actualUA.get(TaxQuarterReportedAndPaidPage(ChargeType.ChargeTypeAnnualAllowance, 0, None)) mustBe
//        Some(AFTQuarter(LocalDate.of(2022,1,1), LocalDate.of(2022,3,31)))
//
//      actualUA.get(TaxYearReportedAndPaidPage(ChargeType.ChargeTypeAnnualAllowance, 1, Some(0))) mustBe
//        Some(YearRange("2022"))
//      actualUA.get(ChargeAmountReportedPage(ChargeType.ChargeTypeAnnualAllowance, 0, None)) mustBe
//        Some(BigDecimal(45.66))
//
//      actualUA.get(MemberDetailsPage(1)) mustBe Some(SampleData.memberDetails3)
//      actualUA.get(ChargeDetailsPage(1)) mustBe Some(chargeDetails2)
//      actualUA.get(AnnualAllowanceYearPage(1)) mustBe Some(YearRange("2020"))
//      actualUA.get(IsPublicServicePensionsRemedyPage(ChargeType.ChargeTypeAnnualAllowance, Some(1))) mustBe Some(true)
//      actualUA.get(IsChargeInAdditionReportedPage(ChargeType.ChargeTypeAnnualAllowance, 1)) mustBe Some(true)
//      actualUA.get(WasAnotherPensionSchemePage(ChargeType.ChargeTypeAnnualAllowance, 1)) mustBe Some(true)
//      Seq(
//        Tuple3("24000017RN", AFTQuarter(LocalDate.of(2022, 4, 1), LocalDate.of(2022, 6, 30)), BigDecimal(102.55)),
//        Tuple3("24000018RN", AFTQuarter(LocalDate.of(2022, 1, 1), LocalDate.of(2022, 3, 31)), BigDecimal(99.88)),
//        Tuple3("24000019RN", AFTQuarter(LocalDate.of(2022, 4, 1), LocalDate.of(2022, 6, 30)), BigDecimal(99.88)),
//        Tuple3("24000020RN", AFTQuarter(LocalDate.of(2022, 1, 1), LocalDate.of(2022, 3, 31)), BigDecimal(99.88)),
//        Tuple3("24000021RN", AFTQuarter(LocalDate.of(2022, 1, 1), LocalDate.of(2022, 3, 31)), BigDecimal(498.90))
//      ).zipWithIndex.map{ case ((expectedPstr, expectedQuarter, expectedAmount), index) =>
//        actualUA.get(EnterPstrPage(ChargeType.ChargeTypeAnnualAllowance, 1, index)) mustBe Some(expectedPstr)
//        actualUA.get(TaxQuarterReportedAndPaidPage(ChargeType.ChargeTypeAnnualAllowance, 1, Some(index))) mustBe
//          Some(expectedQuarter)
//        actualUA.get(TaxYearReportedAndPaidPage(ChargeType.ChargeTypeAnnualAllowance, 1, Some(index))) mustBe
//          Some(YearRange(expectedQuarter.startDate.getYear.toString))
//        actualUA.get(ChargeAmountReportedPage(ChargeType.ChargeTypeAnnualAllowance, 1, Some(index))) mustBe
//          Some(expectedAmount)
//      }
//
//      actualUA.get(MemberDetailsPage(2)) mustBe Some(SampleData.memberDetails4)
//      actualUA.get(ChargeDetailsPage(2)) mustBe Some(chargeDetails3)
//      actualUA.get(AnnualAllowanceYearPage(2)) mustBe Some(YearRange("2020"))
//      actualUA.get(IsPublicServicePensionsRemedyPage(ChargeType.ChargeTypeAnnualAllowance, Some(2))) mustBe Some(true)
//      actualUA.get(IsChargeInAdditionReportedPage(ChargeType.ChargeTypeAnnualAllowance, 2)) mustBe Some(false)
//      actualUA.get(WasAnotherPensionSchemePage(ChargeType.ChargeTypeAnnualAllowance, 2)) mustBe None
//      actualUA.get(EnterPstrPage(ChargeType.ChargeTypeAnnualAllowance, 2, 0)) mustBe None
//    }

//    "return correctly where McCloud errors - missing: isChargeInAdditionReported" in {
//      val validCsvFile: Seq[Array[String]] = CsvLineSplitter.split(
//        s"""$header
//Joe,Bloggs,AB123456C,2020 to 2021,268.28,01/01/2020,NO"""
//      )
//
//      val result = parser.parse(startDate, validCsvFile, UserAnswers())
//      result.isValid mustBe false
//
//      result.swap.toList.flatten mustBe Seq(
//        ParserValidationError(1, 7, messages("isChargeInAdditionReported.error.required",
//          chargeTypeDescription(ChargeType.ChargeTypeAnnualAllowance)), parser.McCloudFieldNames.isInAdditionToPrevious)
//      )
//    }

//    "return correctly where McCloud errors - missing: wasAnotherPensionScheme, taxQuarterReportedAndPaid & chargeAmountReported" in {
//      val validCsvFile: Seq[Array[String]] = CsvLineSplitter.split(
//        s"""$header
//Joe,Bloggs,AB123456C,2020 to 2021,268.28,01/01/2020,NO,YES"""
//      )
//
//      val result = parser.parse(startDate, validCsvFile, UserAnswers())
//      result.isValid mustBe false
//
//      result.swap.toList.flatten mustBe Seq(
//        ParserValidationError(1, 8, messages("wasAnotherPensionScheme.error.required",
//          chargeTypeDescription(ChargeType.ChargeTypeAnnualAllowance)), parser.McCloudFieldNames.wasPaidByAnotherScheme),
//        ParserValidationError(1, 10, "taxQuarterReportedAndPaid.error.required", parser.McCloudFieldNames.dateReportedAndPaid),
//        ParserValidationError(1, 11, "chargeAmountReported.error.required", parser.McCloudFieldNames.chargeAmountReported)
//      )
//    }

//    "return correctly when where McCloud errors - invalid & missing pstrs" in {
//      val validCsvFile: Seq[Array[String]] = CsvLineSplitter.split(
//        s"""$header
//Joe,Bliggs,AB123457C,2020 to 2021,100.50,01/01/2020,NO,YES,YES,4000017RN,30/06/2022,102.55,24000018RN,31/03/2022,99.88,24000019RN,,99.88,24000020RN,31/03/2022,invalid,,31/03/2022,498.90"""
//      )
//
//      val result = parser.parse(startDate, validCsvFile, UserAnswers())
//      result.isValid mustBe false
//      result.swap.toList.flatten mustBe Seq(
//        ParserValidationError(1, 9, "enterPstr.error.invalid", parser.McCloudFieldNames.pstr, Seq("^[0-9]{8}[Rr][A-Za-z]{1}$")),
//        ParserValidationError(1, 16, "taxQuarterReportedAndPaid.error.required", parser.McCloudFieldNames.dateReportedAndPaid),
//        ParserValidationError(1, 20, "chargeAmountReported.error.invalid", parser.McCloudFieldNames.chargeAmountReported),
//        ParserValidationError(1, 21, "enterPstr.error.required", parser.McCloudFieldNames.pstr)
//      )
//    }


    "return correctly where McCloud errors - invalid date" in {
      val validCsvFile: Seq[Array[String]] = CsvLineSplitter.split(
        s"""$header
Joe,Bloggs,AB123456C,2020 to 2021,268.28,01/01/2020,YES,YES,NO,,invalid,45.66,,,,,,,,,,,,"""
      )

      val result = parser.parse(startDate, validCsvFile, UserAnswers())
      result.isValid mustBe false

      result.swap.toList.flatten mustBe Seq(
        ParserValidationError(1, 10, "Invalid tax quarter reported and paid", parser.McCloudFieldNames.dateReportedAndPaid))
    }

  "return correctly where McCloud errors - invalid Tax Year" in {
    val validCsvFile: Seq[Array[String]] = CsvLineSplitter.split(
      s"""$header
Joe,Bloggs,AB123456C,2020-2021,268.28,01/01/2020,NO,NO,NO,,,,,,,,,,,,,,,"""
    )

    val result = parser.parse(startDate, validCsvFile, UserAnswers())
    result.isValid mustBe false

    result.swap.toList.flatten mustBe Seq(
      ParserValidationError(1, 3, "annualAllowanceYear.fileUpload.error.invalid", AnnualAllowanceFieldNames.taxYear))
  }

//    val extraErrorsExpectedForMcCloud: Int => Seq[ParserValidationError] = row => Seq(ParserValidationError(
//      row = row,
//      col = 7,
//      error = messages("isChargeInAdditionReported.error.required", chargeTypeDescription(ChargeType.ChargeTypeAnnualAllowance)),
//      columnName = parser.McCloudFieldNames.isInAdditionToPrevious,
//      args = Nil
//    ))
//
//    behave like annualAllowanceParserWithMinimalFields(header, parser, extraErrorsExpectedForMcCloud)
  }

}

object AnnualAllowanceMcCloudParserSpec extends MockitoSugar {
  private val header: String = "Test McCloud header"

  private val mockFrontendAppConfig = mock[FrontendAppConfig]
  private val formProviderMemberDetails = new MemberDetailsFormProvider
  private val formProviderChargeDetails = new ChargeDetailsFormProvider
  private val formProviderYesNo = new YesNoFormProvider
  private val formProviderEnterPstr = new EnterPstrFormProvider
  private val formProviderChargeAmountReported = new ChargeAmountReportedFormProvider

  private val parser = new AnnualAllowanceMcCloudParser(
    formProviderMemberDetails, formProviderChargeDetails, mockFrontendAppConfig,
    formProviderYesNo, formProviderEnterPstr, formProviderChargeAmountReported)
}