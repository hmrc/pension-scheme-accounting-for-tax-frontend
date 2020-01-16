/*
 * Copyright 2020 HM Revenue & Customs
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

package services

import base.SpecBase
import connectors.SchemeDetailsConnector
import data.SampleData
import org.mockito.Matchers
import org.mockito.Matchers.any
import org.mockito.Mockito.when
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.mockito.MockitoSugar

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class SchemeServiceSpec extends SpecBase with MockitoSugar with ScalaFutures {
  private val mockSchemeDetailsConnector = mock[SchemeDetailsConnector]
  "retrieveSchemeDetails" must {
    "return scheme details" in {
      when(mockSchemeDetailsConnector
        .getSchemeDetails(Matchers.eq(SampleData.psaId),
          Matchers.eq("srn"),
          Matchers.eq(SampleData.srn))(any(), any()))
        .thenReturn(Future.successful(SampleData.schemeDetails))
      val schemeService = new SchemeService(mockSchemeDetailsConnector)
      val result = schemeService.retrieveSchemeDetails(SampleData.psaId, SampleData.srn)
      whenReady(result) { resultSchemeDetails =>
        resultSchemeDetails mustBe SampleData.schemeDetails
      }
    }
  }
}
