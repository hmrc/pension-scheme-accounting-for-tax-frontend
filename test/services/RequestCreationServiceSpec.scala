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
import connectors.MinimalPsaConnector
import connectors.cache.UserAnswersCacheConnector
import data.SampleData
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.mockito.MockitoSugar
import uk.gov.hmrc.domain.PsaId
import SampleData._
import models.AccessMode
import models.SessionAccessData
import models.SessionData
import models.UserAnswers
import models.requests.OptionalDataRequest
import org.scalatest.BeforeAndAfterEach
import play.api.mvc.AnyContent
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import org.mockito.Matchers._
import org.mockito.Mockito.{reset, when}
import play.api.libs.json.JsObject
import play.api.libs.json.JsString
import play.api.libs.json.Json

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class RequestCreationServiceSpec extends SpecBase with MockitoSugar with ScalaFutures with BeforeAndAfterEach {
  private val mockAftConnector: AFTConnector = mock[AFTConnector]
  private val mockUserAnswersCacheConnector: UserAnswersCacheConnector = mock[UserAnswersCacheConnector]
  private val mockSchemeService: SchemeService = mock[SchemeService]
  private val mockMinimalPsaConnector: MinimalPsaConnector = mock[MinimalPsaConnector]
  private implicit val req: FakeRequest[AnyContentAsEmpty.type] = FakeRequest("", "")
  private val psaIdInstance = PsaId(psaId)

  private val sessionId = "???"
  private val internalId = s"$srn$startDate"

  private val jsObject = Json.obj( "one" -> "two")
  private val optionUA = Some(UserAnswers(jsObject))
  val name = None
  private val sessionAccessData = SessionAccessData(version = 1, accessMode = AccessMode.PageAccessModeViewOnly)
  private val optionSD = Some(SessionData(sessionId, name, sessionAccessData))

  private val requestCreationService = new RequestCreationService(mockAftConnector, mockUserAnswersCacheConnector, mockSchemeService, mockMinimalPsaConnector)

  override def beforeEach(): Unit = {
    reset(mockAftConnector, mockUserAnswersCacheConnector, mockSchemeService, mockMinimalPsaConnector)
    when(mockUserAnswersCacheConnector.fetch(any())(any(),any())).thenReturn(Future.successful(Some(jsObject)))
    when(mockUserAnswersCacheConnector.getSessionData(any())(any(),any())).thenReturn(Future.successful(optionSD))
  }

  "Request creation service" must {
    "create a request with  both user answers and session data" in {
      whenReady(requestCreationService.createRequest[AnyContent](psaIdInstance, srn, startDate)) { result =>
        val expectedResult = OptionalDataRequest(req, internalId, psaIdInstance, optionUA, optionSD)
        result mustBe expectedResult
      }
    }

    "create a request with no user answers but session data" in {
      when(mockUserAnswersCacheConnector.fetch(any())(any(),any())).thenReturn(Future.successful(None))
      whenReady(requestCreationService.createRequest[AnyContent](psaIdInstance, srn, startDate)) { result =>
        val expectedResult = OptionalDataRequest(req, internalId, psaIdInstance, None, optionSD)
        result mustBe expectedResult
      }
    }

    "create a request with no session data" in {
      when(mockUserAnswersCacheConnector.getSessionData(any())(any(),any())).thenReturn(Future.successful(None))
      whenReady(requestCreationService.createRequest[AnyContent](psaIdInstance, srn, startDate)) { result =>
        val expectedResult = OptionalDataRequest(req, internalId, psaIdInstance, None, None)
        result mustBe expectedResult
      }
    }
  }
}
