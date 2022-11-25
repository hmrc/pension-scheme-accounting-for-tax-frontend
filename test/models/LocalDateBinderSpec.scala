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

package models

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import play.api.mvc.PathBindable

import java.time.LocalDate

class LocalDateBinderSpec extends AnyWordSpec with Matchers with MockitoSugar with BeforeAndAfterEach {

  private val mockStringBinder = mock[PathBindable[String]]
  private val localDateBinder = LocalDateBinder.datePathBindable(mockStringBinder)
  private val localDate = LocalDate.of(2020, 1, 12)

  "LocalDateBinder" when {

    "on bind" must {
      "bind a valid string to date" in {
        when(mockStringBinder.bind(any(), any())).thenReturn(Right("2020-01-12"))
        localDateBinder.bind("date", "2020-01-12") mustBe Right(localDate)
      }

      "return LocalDate binding failed if not able to bind" in {
        when(mockStringBinder.bind(any(), any())).thenReturn(Left("error"))
        localDateBinder.bind("date", "invalid") mustBe Left("LocalDate binding failed")
      }
    }

    "on unbind" must {
      "unbind a valid date to string" in {
        when(mockStringBinder.unbind(any(), any())).thenReturn("2020-01-12")
        localDateBinder.unbind("date", localDate) mustBe "2020-01-12"
      }
    }
  }
}
