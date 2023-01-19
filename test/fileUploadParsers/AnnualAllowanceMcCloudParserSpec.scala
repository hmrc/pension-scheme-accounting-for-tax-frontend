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
import forms.chargeE.ChargeDetailsFormProvider
import forms.{MemberDetailsFormProvider, YesNoFormProvider}
import helpers.ParserHelper
import models.chargeE.ChargeEDetails
import models.{ChargeType, UserAnswers, YearRange}
import org.mockito.Mockito
import org.mockito.Mockito.when
import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import pages.IsPublicServicePensionsRemedyPage
import pages.chargeE.{AnnualAllowanceYearPage, ChargeDetailsPage, MemberDetailsPage}
import pages.mccloud.{AddAnotherPensionSchemePage, IsChargeInAdditionReportedPage, WasAnotherPensionSchemePage}

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

    "return charges in user answers when there are no validation errors" in {
      val validCsvFile: Seq[Array[String]] = CsvLineSplitter.split(
        s"""$header
Joe,Bloggs,AB123456C,2020,268.28,01/01/2020,YES,YES,NO,,12/02/22,45.66,,,,,,,,,,,,
Joe,Bliggs,AB123457C,2020,100.50,01/01/2020,NO,YES,YES,24000017IN,01/01/22,102.55,24000018IN,01/01/22,99.88,24000019IN,01/01/22,99.88,24000020IN,01/01/22,99.88,24000021IN,01/01/22,498.9
Joe,Blaggs,AB123458C,2020,68.28,01/01/2020,YES,NO,,,,,,,,,,,,,,,,"""
      )

      val chargeDetails1 = ChargeEDetails(BigDecimal(268.28), LocalDate.of(2020, 1, 1), isPaymentMandatory = true)
      val chargeDetails2 = ChargeEDetails(BigDecimal(100.50), LocalDate.of(2020, 1, 1), isPaymentMandatory = false)
      val chargeDetails3 = ChargeEDetails(BigDecimal(68.28), LocalDate.of(2020, 1, 1), isPaymentMandatory = true)
      val result = parser.parse(startDate, validCsvFile, UserAnswers())
      result.isRight mustBe true
      val actualUA = result.toOption.value

      actualUA.get(MemberDetailsPage(0)) mustBe Some(SampleData.memberDetails2)
      actualUA.get(ChargeDetailsPage(0)) mustBe Some(chargeDetails1)
      actualUA.get(AnnualAllowanceYearPage(0)) mustBe Some(YearRange("2020"))
      actualUA.get(IsPublicServicePensionsRemedyPage(ChargeType.ChargeTypeAnnualAllowance, Some(0))) mustBe Some(true)
      actualUA.get(IsChargeInAdditionReportedPage(ChargeType.ChargeTypeAnnualAllowance, 0)) mustBe Some(true)
      actualUA.get(WasAnotherPensionSchemePage(ChargeType.ChargeTypeAnnualAllowance, 0)) mustBe Some(false)

      actualUA.get(MemberDetailsPage(1)) mustBe Some(SampleData.memberDetails3)
      actualUA.get(ChargeDetailsPage(1)) mustBe Some(chargeDetails2)
      actualUA.get(AnnualAllowanceYearPage(1)) mustBe Some(YearRange("2020"))
      actualUA.get(IsPublicServicePensionsRemedyPage(ChargeType.ChargeTypeAnnualAllowance, Some(1))) mustBe Some(true)
      actualUA.get(IsChargeInAdditionReportedPage(ChargeType.ChargeTypeAnnualAllowance, 1)) mustBe Some(true)
      actualUA.get(WasAnotherPensionSchemePage(ChargeType.ChargeTypeAnnualAllowance, 1)) mustBe Some(true)

      actualUA.get(MemberDetailsPage(2)) mustBe Some(SampleData.memberDetails4)
      actualUA.get(ChargeDetailsPage(2)) mustBe Some(chargeDetails3)
      actualUA.get(AnnualAllowanceYearPage(2)) mustBe Some(YearRange("2020"))
      actualUA.get(IsPublicServicePensionsRemedyPage(ChargeType.ChargeTypeAnnualAllowance, Some(2))) mustBe Some(true)
      actualUA.get(IsChargeInAdditionReportedPage(ChargeType.ChargeTypeAnnualAllowance, 2)) mustBe Some(false)
      actualUA.get(WasAnotherPensionSchemePage(ChargeType.ChargeTypeAnnualAllowance, 2)) mustBe None
    }

    val extraErrorsExpectedForMcCloud: Int => Seq[ParserValidationError] = row => Seq(ParserValidationError(
      row = row,
      col = 7,
      error = messages("isChargeInAdditionReported.error.required", chargeTypeDescription(ChargeType.ChargeTypeAnnualAllowance)),
      columnName = parser.McCloudFieldNames.allSingleBooleanFields,
      args = Nil
    ))

    behave like annualAllowanceParserWithMinimalFields(header, parser, extraErrorsExpectedForMcCloud)
  }
}

object AnnualAllowanceMcCloudParserSpec extends MockitoSugar {
  private val header: String = "Test McCloud header"

  private val mockFrontendAppConfig = mock[FrontendAppConfig]
  private val formProviderMemberDetails = new MemberDetailsFormProvider
  private val formProviderChargeDetails = new ChargeDetailsFormProvider
  private val formProviderYesNo = new YesNoFormProvider

  private val parser = new AnnualAllowanceMcCloudParser(
    formProviderMemberDetails, formProviderChargeDetails, mockFrontendAppConfig, formProviderYesNo)
}