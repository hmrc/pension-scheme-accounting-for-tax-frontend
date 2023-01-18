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
import forms.MemberDetailsFormProvider
import forms.chargeE.ChargeDetailsFormProvider
import helpers.ParserHelper
import models.{ChargeType, UserAnswers}
import models.chargeE.ChargeEDetails
import org.mockito.Mockito
import org.mockito.Mockito.when
import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import pages.IsPublicServicePensionsRemedyPage
import pages.chargeE.{AnnualAllowanceYearPage, ChargeDetailsPage, MemberDetailsPage}
import pages.mccloud.IsChargeInAdditionReportedPage
import play.api.libs.json.Json

import java.time.LocalDate

class AnnualAllowanceNonMcCloudParserSpec extends SpecBase
  with Matchers with MockitoSugar with BeforeAndAfterEach with ParserHelper {
  //scalastyle:off magic.number

  import AnnualAllowanceNonMcCloudParserSpec._

  override def beforeEach(): Unit = {
    Mockito.reset(mockFrontendAppConfig)
    when(mockFrontendAppConfig.earliestDateOfNotice).thenReturn(LocalDate.of(1900, 1, 1))
    when(mockFrontendAppConfig.validAnnualAllowanceNonMcCloudHeader).thenReturn(header)
  }

  "Annual allowance parser" must {
    "return charges in user answers when there are no validation errors" in {
      val validCsvFile: Seq[Array[String]] = CsvLineSplitter.split(
        s"""$header
    Joe,Bloggs,AB123456C,2020,268.28,01/01/2020,yes
    Joe,Bliggs,AB123457C,2020,268.28,01/01/2020,yes"""
      )
      val chargeDetails = ChargeEDetails(BigDecimal(268.28), LocalDate.of(2020, 1, 1), isPaymentMandatory = true)
      val result = parser.parse(startDate, validCsvFile, UserAnswers())
      result mustBe Right(UserAnswers()
        .setOrException(MemberDetailsPage(0).path, Json.toJson(SampleData.memberDetails2))
        .setOrException(ChargeDetailsPage(0).path, Json.toJson(chargeDetails))
        .setOrException(AnnualAllowanceYearPage(0).path, Json.toJson("2020"))
        .setOrException(IsPublicServicePensionsRemedyPage(ChargeType.ChargeTypeAnnualAllowance, Some(0)), false)

        .setOrException(MemberDetailsPage(1).path, Json.toJson(SampleData.memberDetails3))
        .setOrException(ChargeDetailsPage(1).path, Json.toJson(chargeDetails))
        .setOrException(AnnualAllowanceYearPage(1).path, Json.toJson("2020"))
        .setOrException(IsPublicServicePensionsRemedyPage(ChargeType.ChargeTypeAnnualAllowance, Some(1)), false)
      )
    }

    behave like annualAllowanceParserWithMinimalFields(header, parser)

  }
}

object AnnualAllowanceNonMcCloudParserSpec extends MockitoSugar {
  private val header: String = "Test header"

  private val mockFrontendAppConfig = mock[FrontendAppConfig]

  private val formProviderMemberDetails = new MemberDetailsFormProvider
  private val formProviderChargeDetails = new ChargeDetailsFormProvider

  private val parser = new AnnualAllowanceNonMcCloudParser(formProviderMemberDetails, formProviderChargeDetails, mockFrontendAppConfig)
}