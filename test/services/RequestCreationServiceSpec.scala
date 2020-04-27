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
import org.mockito.Mockito.reset
import org.scalatest.BeforeAndAfterEach
import play.api.mvc.AnyContent
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest

import scala.concurrent.ExecutionContext.Implicits.global

class RequestCreationServiceSpec extends SpecBase with MockitoSugar with ScalaFutures with BeforeAndAfterEach {
  private val aftConnector: AFTConnector = mock[AFTConnector]
  private val userAnswersCacheConnector: UserAnswersCacheConnector = mock[UserAnswersCacheConnector]
  private val schemeService: SchemeService = mock[SchemeService]
  private val minimalPsaConnector: MinimalPsaConnector = mock[MinimalPsaConnector]
  private implicit val req: FakeRequest[AnyContentAsEmpty.type] = FakeRequest("", "")
  private val psaIdInstance = PsaId(psaId)

  private val requestCreationService = new RequestCreationService(aftConnector, userAnswersCacheConnector, schemeService, minimalPsaConnector)

  override def beforeEach(): Unit = {
    reset(aftConnector, userAnswersCacheConnector, schemeService, minimalPsaConnector)

  }

  "Request creation service" must {
    "create a request" in {
      whenReady(requestCreationService.createRequest[AnyContent](psaIdInstance, srn, startDate)) { result =>
      val expectedName = None
        val expectedSAD = SessionAccessData(version = 1, accessMode = AccessMode.PageAccessModeViewOnly)
        val expectedOptionUA = Some(UserAnswers())
        val expectedOptionSD = Some(SessionData("", expectedName, expectedSAD))
        val expectedResult = OptionalDataRequest(req, "", psaIdInstance, expectedOptionUA, expectedOptionSD)
        result mustBe expectedResult
      }
    }
  }
}
