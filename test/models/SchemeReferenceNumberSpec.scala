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
import org.mockito.MockitoSugar
import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.mvc.PathBindable

class SchemeReferenceNumberSpec extends AnyWordSpec with Matchers with MockitoSugar with BeforeAndAfterEach {

  private val mockStringBinder = mock[PathBindable[String]]
  private val srnPathBinder = SchemeReferenceNumber.srnPathBindable(mockStringBinder)

  "SchemeReferenceNumber" when {

    "on bind" must {
      "bind a valid string to srn" in {
        when(mockStringBinder.bind(any(), any())).thenReturn(Right("S0000000000"))
        srnPathBinder.bind("srn", "S0000000000") mustBe Right(SchemeReferenceNumber("S0000000000"))
      }

      "fail to bind an invalid srn" in {
        when(mockStringBinder.bind(any(), any())).thenReturn(Right("invalid"))
        srnPathBinder.bind("srn", "invalid") mustBe Left("SchemeReferenceNumber binding failed")
      }
    }

    "on unbind" must {
      "unbind a valid srn to string" in {
        when(mockStringBinder.unbind(any(), any())).thenReturn("S0000000000")
        srnPathBinder.unbind("srn", SchemeReferenceNumber("S0000000000")) mustBe "S0000000000"
      }
    }

    "implicit conversion from String" must {
      "return the correct SchemeReferenceNumber" in {
        val x: SchemeReferenceNumber = "S0000000000"
        x mustEqual SchemeReferenceNumber("S0000000000")
      }
    }

    "implicit conversion to SchemeReferenceNumber" must {
      "return the correct String" in {
        val x: String = SchemeReferenceNumber("S0000000000")
        x mustEqual "S0000000000"
      }
    }
  }
}
