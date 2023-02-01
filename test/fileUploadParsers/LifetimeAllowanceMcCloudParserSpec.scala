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
import data.SampleData
import data.SampleData.startDate
import forms.chargeD.ChargeDetailsFormProvider
import forms.mccloud.{ChargeAmountReportedFormProvider, EnterPstrFormProvider}
import forms.{MemberDetailsFormProvider, YesNoFormProvider}
import helpers.ParserHelper
import models.chargeD.ChargeDDetails
import models.{AFTQuarter, ChargeType, UserAnswers}
import org.mockito.Mockito
import org.mockito.Mockito.when
import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import pages.IsPublicServicePensionsRemedyPage
import pages.chargeD.{ChargeDetailsPage, MemberDetailsPage}
import pages.mccloud._

import java.time.LocalDate

class LifetimeAllowanceMcCloudParserSpec extends SpecBase
  with Matchers with MockitoSugar with BeforeAndAfterEach with ParserHelper {
  //scalastyle:off magic.number

  import LifetimeAllowanceMcCloudParserSpec._

  override def beforeEach(): Unit = {
    Mockito.reset(mockFrontendAppConfig)
    when(mockFrontendAppConfig.earliestDateOfNotice).thenReturn(LocalDate.of(1900, 1, 1))
    when(mockFrontendAppConfig.validLifetimeAllowanceMcCloudHeader).thenReturn(header)
  }

  "Lifetime allowance McCloud parser" must {
    "return charges in user answers when there are no validation errors" in {
      val validCsvFile: Seq[Array[String]] = CsvLineSplitter.split(
        s"""$header
Joe,Bloggs,AB123456C,01/04/2020,268.28,0.00,YES,NO,,31/03/2022,45.66,,,,,,,,,,,,
Joe,Bliggs,AB123457C,01/04/2020,100.50,0.00,YES,YES,24000017RN,30/06/2022,102.55,24000018RN,31/03/2022,99.88,24000019RN,30/06/2022,99.88,24000020RN,31/03/2022,99.88,24000021RN,31/03/2022,498.90
Joe,Blaggs,AB123458C,01/04/2020,68.28,0.00,NO,,,,,,,,,,,,,,,,"""
      )

      val chargeDetails1 = ChargeDDetails(LocalDate.of(2020, 4, 1), Some(BigDecimal(268.28)), Some(BigDecimal(0.0)))
      val chargeDetails2 = ChargeDDetails(LocalDate.of(2020, 4, 1), Some(BigDecimal(100.50)), Some(BigDecimal(0.00)))
      val chargeDetails3 = ChargeDDetails(LocalDate.of(2020, 4, 1), Some(BigDecimal(68.28)), Some(BigDecimal(0.00)))
      val result = parser.parse(startDate, validCsvFile, UserAnswers())

      result.isValid mustBe true
      val actualUA = result.toOption.value

      actualUA.get(MemberDetailsPage(0)) mustBe Some(SampleData.memberDetails2)
      actualUA.get(ChargeDetailsPage(0)) mustBe Some(chargeDetails1)
      actualUA.get(IsPublicServicePensionsRemedyPage(ChargeType.ChargeTypeLifetimeAllowance, Some(0))) mustBe Some(true)
      actualUA.get(IsChargeInAdditionReportedPage(ChargeType.ChargeTypeLifetimeAllowance, 0)) mustBe Some(true)
      actualUA.get(WasAnotherPensionSchemePage(ChargeType.ChargeTypeLifetimeAllowance, 0)) mustBe Some(false)
      actualUA.get(TaxQuarterReportedAndPaidPage(ChargeType.ChargeTypeLifetimeAllowance, 0, None)) mustBe
        Some(AFTQuarter(LocalDate.of(2022, 1, 1), LocalDate.of(2022, 3, 31)))
      actualUA.get(ChargeAmountReportedPage(ChargeType.ChargeTypeLifetimeAllowance, 0, None)) mustBe
        Some(BigDecimal(45.66))

      actualUA.get(MemberDetailsPage(1)) mustBe Some(SampleData.memberDetails3)
      actualUA.get(ChargeDetailsPage(1)) mustBe Some(chargeDetails2)
      actualUA.get(IsPublicServicePensionsRemedyPage(ChargeType.ChargeTypeLifetimeAllowance, Some(1))) mustBe Some(true)
      actualUA.get(IsChargeInAdditionReportedPage(ChargeType.ChargeTypeLifetimeAllowance, 1)) mustBe Some(true)
      actualUA.get(WasAnotherPensionSchemePage(ChargeType.ChargeTypeLifetimeAllowance, 1)) mustBe Some(true)
      Seq(
        Tuple3("24000017RN", AFTQuarter(LocalDate.of(2022, 4, 1), LocalDate.of(2022, 6, 30)), BigDecimal(102.55)),
        Tuple3("24000018RN", AFTQuarter(LocalDate.of(2022, 1, 1), LocalDate.of(2022, 3, 31)), BigDecimal(99.88)),
        Tuple3("24000019RN", AFTQuarter(LocalDate.of(2022, 4, 1), LocalDate.of(2022, 6, 30)), BigDecimal(99.88)),
        Tuple3("24000020RN", AFTQuarter(LocalDate.of(2022, 1, 1), LocalDate.of(2022, 3, 31)), BigDecimal(99.88)),
        Tuple3("24000021RN", AFTQuarter(LocalDate.of(2022, 1, 1), LocalDate.of(2022, 3, 31)), BigDecimal(498.90))
      ).zipWithIndex.map { case ((expectedPstr, expectedQuarter, expectedAmount), index) =>
        actualUA.get(EnterPstrPage(ChargeType.ChargeTypeLifetimeAllowance, 1, index)) mustBe Some(expectedPstr)
        actualUA.get(TaxQuarterReportedAndPaidPage(ChargeType.ChargeTypeLifetimeAllowance, 1, Some(index))) mustBe
          Some(expectedQuarter)
        actualUA.get(ChargeAmountReportedPage(ChargeType.ChargeTypeLifetimeAllowance, 1, Some(index))) mustBe
          Some(expectedAmount)
      }

      actualUA.get(MemberDetailsPage(2)) mustBe Some(SampleData.memberDetails4)
      actualUA.get(ChargeDetailsPage(2)) mustBe Some(chargeDetails3)
      actualUA.get(IsPublicServicePensionsRemedyPage(ChargeType.ChargeTypeLifetimeAllowance, Some(2))) mustBe Some(true)
      actualUA.get(IsChargeInAdditionReportedPage(ChargeType.ChargeTypeLifetimeAllowance, 2)) mustBe Some(false)
      actualUA.get(WasAnotherPensionSchemePage(ChargeType.ChargeTypeLifetimeAllowance, 2)) mustBe None
      actualUA.get(EnterPstrPage(ChargeType.ChargeTypeLifetimeAllowance, 2, 0)) mustBe None
    }

    "return correctly where McCloud errors - missing: isChargeInAdditionReported" in {
      val validCsvFile: Seq[Array[String]] = CsvLineSplitter.split(
        s"""$header
Joe,Bloggs,AB123456C,01/04/2020,268.28,0.00"""
      )

      val result = parser.parse(startDate, validCsvFile, UserAnswers())
      result.isValid mustBe false

      // TODO: Check - I think the field name "value" is not correct:
      result.swap.toList.flatten mustBe Seq(
        ParserValidationError(1, 6, messages("isChargeInAdditionReported.error.required",
          chargeTypeDescription(ChargeType.ChargeTypeLifetimeAllowance)), parser.McCloudFieldNames.isInAdditionToPrevious)
      )
    }

    "return correctly where McCloud errors - missing: wasAnotherPensionScheme, taxQuarterReportedAndPaid & chargeAmountReported" in {
      val validCsvFile: Seq[Array[String]] = CsvLineSplitter.split(
        s"""$header
Joe,Bloggs,AB123456C,01/04/2020,268.28,0.00,YES"""
      )

      val result = parser.parse(startDate, validCsvFile, UserAnswers())
      result.isValid mustBe false

      // TODO: Check - I think the field name "value" is not correct:
      result.swap.toList.flatten mustBe Seq(
        ParserValidationError(1, 7, messages("wasAnotherPensionScheme.error.required",
          chargeTypeDescription(ChargeType.ChargeTypeLifetimeAllowance)), parser.McCloudFieldNames.wasPaidByAnotherScheme),
        ParserValidationError(1, 9, "taxQuarterReportedAndPaid.error.required", parser.McCloudFieldNames.dateReportedAndPaid),
        ParserValidationError(1, 10, "chargeAmountReported.error.required", parser.McCloudFieldNames.chargeAmountReported)
      )
    }

    "return correctly when where McCloud errors - invalid & missing pstrs" in {
      val validCsvFile: Seq[Array[String]] = CsvLineSplitter.split(
        s"""$header
Joe,Bloggs,AB123456C,01/04/2020,268.28,0.00,YES,YES,4000017RN,30/06/2022,102.55,24000018RN,31/03/2022,99.88,24000019RN,,99.88,24000020RN,31/03/2022,invalid,,31/03/2022,498.90"""
      )

      val result = parser.parse(startDate, validCsvFile, UserAnswers())
      result.isValid mustBe false
      result.swap.toList.flatten mustBe Seq(
        ParserValidationError(1, 8, "enterPstr.error.invalid", parser.McCloudFieldNames.pstr, Seq("^[0-9]{8}[Rr][A-Za-z]{1}$")),
        ParserValidationError(1, 15, "taxQuarterReportedAndPaid.error.required", parser.McCloudFieldNames.dateReportedAndPaid),
        ParserValidationError(1, 19, "chargeAmountReported.error.invalid", parser.McCloudFieldNames.chargeAmountReported),
        ParserValidationError(1, 20, "enterPstr.error.required", parser.McCloudFieldNames.pstr)
      )
    }


    "return correctly where McCloud errors - invalid date" in {
      val validCsvFile: Seq[Array[String]] = CsvLineSplitter.split(
        s"""$header
Joe,Bloggs,AB123456C,01/04/2020,268.28,0.00,YES,NO,,invalid,45.66,,,,,,,,,,,,"""
      )

      val result = parser.parse(startDate, validCsvFile, UserAnswers())
      result.isValid mustBe false

      // TODO: Check - I think the field name "value" is not correct:
      result.swap.toList.flatten mustBe Seq(
        ParserValidationError(1, 9, "Invalid tax quarter reported and paid", parser.McCloudFieldNames.dateReportedAndPaid))
    }

    val extraErrorsExpectedForMcCloud: Int => Seq[ParserValidationError] = row => Seq(ParserValidationError(
      row = row,
      col = 6,
      error = messages("isChargeInAdditionReported.error.required", chargeTypeDescription(ChargeType.ChargeTypeLifetimeAllowance)),
      columnName = parser.McCloudFieldNames.isInAdditionToPrevious,
      args = Nil
    ))

    behave like lifetimeAllowanceParserWithMinimalFields(header, parser, extraErrorsExpectedForMcCloud)
  }
}

object LifetimeAllowanceMcCloudParserSpec extends MockitoSugar {
  private val header: String = "Test McCloud header"

  private val mockFrontendAppConfig = mock[FrontendAppConfig]
  private val formProviderMemberDetails = new MemberDetailsFormProvider
  private val formProviderChargeDetails = new ChargeDetailsFormProvider
  private val formProviderYesNo = new YesNoFormProvider
  private val formProviderEnterPstr = new EnterPstrFormProvider
  private val formProviderChargeAmountReported = new ChargeAmountReportedFormProvider

  private val parser = new LifetimeAllowanceMcCloudParser(
    formProviderMemberDetails, formProviderChargeDetails, mockFrontendAppConfig,
    formProviderYesNo, formProviderEnterPstr, formProviderChargeAmountReported)
}