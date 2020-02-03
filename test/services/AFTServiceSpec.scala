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
import connectors.AFTConnector
import connectors.cache.UserAnswersCacheConnector
import data.SampleData
import data.SampleData._
import models.UserAnswers
import models.requests.DataRequest
import org.mockito.Matchers
import org.mockito.Matchers.any
import org.mockito.Mockito.{reset, times, verify, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.mockito.MockitoSugar
import play.api.libs.json.Json
import play.api.mvc.{AnyContentAsEmpty, Results}
import uk.gov.hmrc.domain.PsaId

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class AFTServiceSpec extends SpecBase with ScalaFutures with BeforeAndAfterEach with MockitoSugar with Results {
  private val mockAFTConnector: AFTConnector = mock[AFTConnector]
  private val mockUserAnswersCacheConnector: UserAnswersCacheConnector  = mock[UserAnswersCacheConnector]

  private def dataRequest(ua: UserAnswers): DataRequest[AnyContentAsEmpty.type] = DataRequest(fakeRequest, "", PsaId(SampleData.psaId), ua)

  override def beforeEach(): Unit = {
    reset(mockAFTConnector, mockUserAnswersCacheConnector)
  }

  "fileAFTReturn" must {
    "respond successfully when" in {
      val jsonReturnedByConnector = Json.obj()
      val emptyJson = Json.obj()
      when(mockAFTConnector.fileAFTReturn(any(), any())(any(), any()))
        .thenReturn(Future.successful(()))
      when(mockUserAnswersCacheConnector.save(any(), any())(any(), any()))
        .thenReturn(Future.successful(emptyJson))

      val aftService = new AFTService(mockAFTConnector, mockUserAnswersCacheConnector)
      whenReady(aftService.fileAFTReturn(pstr, userAnswersWithSchemeName)(implicitly, implicitly, dataRequest(userAnswersWithSchemeName))) { _ =>
        verify(mockAFTConnector, times(1)).fileAFTReturn(Matchers.eq(pstr), Matchers.eq(userAnswersWithSchemeName))(any(), any())

      }
    }
  }
}
