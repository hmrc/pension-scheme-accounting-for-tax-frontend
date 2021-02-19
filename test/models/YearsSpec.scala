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

package models

import forms.mappings.Mappings
import javax.inject.Inject
import org.mockito.Mockito.when
import org.scalatest.{FreeSpec, MustMatchers}
import org.scalatestplus.mockito.MockitoSugar
import play.api.data.Form
import org.mockito.Matchers.any
import play.api.libs.json.{JsString, Json}
import uk.gov.hmrc.viewmodels.Radios
import uk.gov.hmrc.viewmodels.Text.Literal
import viewmodels.Radios.Item

class YearsSpec extends FreeSpec with MustMatchers with MockitoSugar {


  private object DummyFormProvider extends Mappings {
    def apply(): Form[String] =
      Form(
        "value" -> text("required")
      )
  }

  val form = DummyFormProvider()


  //
  //private val mockForm = mock[Form[String]]
  //private val mockField = mock[play.api.data.Field]

  "writes" - {
    "must map correctly to string" in {
      val year = Year(2020)

      val result = Json.toJson(year)
      result mustBe JsString("2020")
    }
  }
  "AmendYears.values" - {
    "must return Seq of years in reverse order" in {
      val years = Seq(1,2,3)
      val expectedResult = Seq(Year(3), Year(2), Year(1))
      AmendYears.values(years) mustBe expectedResult
    }
  }

  "AmendYears.radios" - {
    "must return Seq of years as string in reverse order" in {
      //when(mockForm.apply(any())).thenReturn(mockField)
      //when(mockField.value).thenReturn(None)
      //when(mockField.indexes).thenReturn(Nil)
      val years = Seq(1,2)


      //val tt = List(Item(null,Literal(2),2,false), Item(null_1,Literal(1),1,false))

      val expectedResult = Seq(Radios.Radio.apply(Literal("2"), "2"), Radios.Radio(Literal("1"), "1"))
      AmendYears.radios(form, years) mustBe expectedResult
    }
  }

}
