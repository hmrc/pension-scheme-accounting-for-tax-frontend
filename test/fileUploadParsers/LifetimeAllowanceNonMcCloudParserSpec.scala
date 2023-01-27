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
import cats.data.Validated.Valid
import config.FrontendAppConfig
import data.SampleData
import data.SampleData.startDate
import fileUploadParsers.AnnualAllowanceNonMcCloudParserSpec.mock
import forms.MemberDetailsFormProvider
import forms.chargeD.ChargeDetailsFormProvider
import helpers.ParserHelper
import models.{ChargeType, UserAnswers}
import models.chargeD.ChargeDDetails
import org.mockito.Mockito
import org.mockito.Mockito.when
import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import pages.IsPublicServicePensionsRemedyPage
import pages.chargeD.{ChargeDetailsPage, MemberDetailsPage}
import play.api.libs.json.Json

import java.time.LocalDate

class LifetimeAllowanceNonMcCloudParserSpec extends SpecBase
  with Matchers with MockitoSugar with BeforeAndAfterEach with ParserHelper{
  //scalastyle:off magic.number

  import LifetimeAllowanceNonMcCloudParserSpec._

  override def beforeEach(): Unit = {
    Mockito.reset(mockFrontendAppConfig)
    when(mockFrontendAppConfig.validLifetimeAllowanceHeader).thenReturn(header)
  }

  "LifeTime allowance parser" must {
    "return charges in user answers when there are no validation errors" in {
      val GivingValidCSVFile = CsvLineSplitter.split(
        s"""$header
                            Joe,Bloggs,AB123456C,01/04/2020,268.28,0
                            Joe,Bliggs,AB123457C,01/04/2020,0,268.28"""
      )

      val chargeDetails1 = ChargeDDetails(LocalDate.of(2020, 4, 1), Some(BigDecimal(268.28)), Some(BigDecimal(0.0)))
      val chargeDetails2 = ChargeDDetails(LocalDate.of(2020, 4, 1), Some(BigDecimal(0.0)), Some(BigDecimal(268.28)))
      val result = parser.parse(startDate, GivingValidCSVFile, UserAnswers())
      result mustBe Valid(UserAnswers()
        .setOrException(MemberDetailsPage(0).path, Json.toJson(SampleData.memberDetails2))
        .setOrException(ChargeDetailsPage(0).path, Json.toJson(chargeDetails1))
        .setOrException(IsPublicServicePensionsRemedyPage(ChargeType.ChargeTypeLifetimeAllowance, Some(0)), false)
        .setOrException(MemberDetailsPage(1).path, Json.toJson(SampleData.memberDetails3))
        .setOrException(ChargeDetailsPage(1).path, Json.toJson(chargeDetails2))
        .setOrException(IsPublicServicePensionsRemedyPage(ChargeType.ChargeTypeLifetimeAllowance, Some(1)), false)
      )
    }

    behave like lifetimeAllowanceParserWithMinimalFields(header, parser)
  }
}

object LifetimeAllowanceNonMcCloudParserSpec {
  private val header = "Test header"

  private val mockFrontendAppConfig = mock[FrontendAppConfig]

  private val memberDetailsFormProvider = new MemberDetailsFormProvider
  private val chargeDetailsFormProvider = new ChargeDetailsFormProvider

  private val parser = new LifetimeAllowanceNonMcCloudParser(memberDetailsFormProvider, chargeDetailsFormProvider, mockFrontendAppConfig)
}
