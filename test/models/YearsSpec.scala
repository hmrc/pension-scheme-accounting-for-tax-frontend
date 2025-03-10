/*
 * Copyright 2024 HM Revenue & Customs
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

package models

import config.FrontendAppConfig
import forms.mappings.Mappings
import org.mockito.Mockito.when
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import play.api.data.Form
import play.api.libs.json.{JsString, Json}
import uk.gov.hmrc.govukfrontend.views.Aliases.{RadioItem, Text}
import utils.DateHelper


import java.time.LocalDate

class YearsSpec extends AnyFreeSpec with Matchers with MockitoSugar {

  private val form = {
    object DummyFormProvider extends Mappings {
      def apply(): Form[String] =
        Form(
          "value" -> text("required")
        )
    }
    DummyFormProvider()
  }

  private val mockConfig = mock[FrontendAppConfig]
  private val minYear = 2018

  //scalastyle.off: magic.number
  private def setDate(): Unit =
    DateHelper.setDate(Some(LocalDate.of(2020, 12, 12)))

  "writes" - {
    "must map correctly to string" in {
      val year = Year(2020)

      val result = Json.toJson(year)
      result mustBe JsString("2020")
    }
  }

  "StartYears.values" - {
    "must return Seq of years in reverse order" in {
      setDate()
      when(mockConfig.minimumYear).thenReturn(minYear)
      val expectedResult = Seq(Year(2020), Year(2019), Year(2018))
      StartYears.values(mockConfig) mustBe expectedResult
    }
  }

  "StartYears.radios" - {
    "must return Seq of radio items" in {
      setDate()
      when(mockConfig.minimumYear).thenReturn(minYear)
      val expectedResult = Seq(
        RadioItem(Text("2020"), Some("value"), Some("2020"), None, None, None, false, None, false, Map()),
        RadioItem(Text("2019"), Some("value_1"), Some("2019"), None, None, None, false, None, false, Map()),
        RadioItem(Text("2018"), Some("value_2"), Some("2018"), None, None, None, false, None, false, Map())
      )
      StartYears.radios(form)(mockConfig) mustBe expectedResult
    }
  }

  "AmendYears.values" - {
    "must return Seq of years in reverse order" in {
      val years = Seq(1, 2, 3)
      val expectedResult = Seq(Year(3), Year(2), Year(1))
      AmendYears.values(years) mustBe expectedResult
    }
  }

  "AmendYears.radios" - {
    "must return Seq of radio items" in {
      val years = Seq(1, 2)
      val expectedResult = Seq(
        RadioItem(Text("2"), Some("value"), Some("2"), None, None, None, false, None, false, Map()),
        RadioItem(Text("1"), Some("value_1"), Some("1"), None, None, None, false, None, false, Map())
      )
      AmendYears.radios(form, years) mustBe expectedResult

    }

  }

}
