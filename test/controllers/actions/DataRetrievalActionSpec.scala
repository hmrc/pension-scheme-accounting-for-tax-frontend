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

package controllers.actions

import java.time.LocalDate

import connectors.cache.UserAnswersCacheConnector
import controllers.base.ControllerSpecBase
import data.SampleData
import models.requests.{IdentifierRequest, OptionalDataRequest}
import org.mockito.Matchers.any
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import play.api.libs.json.Json
import uk.gov.hmrc.domain.PsaId
import utils.AFTConstants._
import SampleData._

import scala.concurrent
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class DataRetrievalActionSpec extends ControllerSpecBase with ScalaFutures with BeforeAndAfterEach {
  val srn: String = "srn"
  val startDate: LocalDate = QUARTER_START_DATE
  val dataCacheConnector: UserAnswersCacheConnector = mock[UserAnswersCacheConnector]

  override def beforeEach: Unit = {
    reset(dataCacheConnector)
  }
  class Harness(dataCacheConnector: UserAnswersCacheConnector) extends DataRetrievalImpl(srn, startDate, dataCacheConnector) {
    def callTransform[A](request: IdentifierRequest[A]): Future[OptionalDataRequest[A]] = transform(request)
  }

  "Data Retrieval Action" when {
    "there is no data in the cache" must {
      "set addRequiredDetailsToUserAnswers to 'None' and viewOnly flag to false in the request" in {
        val dataCacheConnector = mock[UserAnswersCacheConnector]
        when(dataCacheConnector.fetch(any())(any(), any())) thenReturn Future(None)
        when(dataCacheConnector.getSessionData(any())(any(), any())).thenReturn (Future.successful(Some(SampleData.sessionData())))
        val action = new Harness(dataCacheConnector)

        val futureResult = action.callTransform(IdentifierRequest(fakeRequest, PsaId("A0000000")))

        whenReady(futureResult) { result =>
          result.userAnswers.isEmpty mustBe true
          result.sessionData.map(_.isEditable) mustBe Some(true)
        }
      }
    }

    "there is data in the cache" must {
      "build a addRequiredDetailsToUserAnswers object, set viewOnly flag to true and add it to the request" in {
        val dataCacheConnector = mock[UserAnswersCacheConnector]
        when(dataCacheConnector.fetch(any())(any(), any())) thenReturn Future.successful(Some(Json.obj()))
        //when(dataCacheConnector.getSessionData(any())(any(), any())) thenReturn Future(true)
        when(dataCacheConnector.getSessionData(any())(any(), any())).thenReturn (Future.successful(Some(SampleData.sessionData(
          sessionAccessData = sessionAccessData(
            accessMode = accessModeViewOnly)))))
        val action = new Harness(dataCacheConnector)

        val futureResult = action.callTransform(IdentifierRequest(fakeRequest, PsaId("A0000000")))

        whenReady(futureResult) { result =>
          result.userAnswers.isDefined mustBe true
          result.sessionData.map(_.isViewOnly) mustBe Some(true)
        }
      }

      "build a addRequiredDetailsToUserAnswers object, set viewOnly to true if psa is suspended and add it to the request" in {
        val dataCacheConnector = mock[UserAnswersCacheConnector]
        when(dataCacheConnector.fetch(any())(any(), any())) thenReturn Future.successful(Some(Json.obj("isPsaSuspended" -> true)))
        when(dataCacheConnector.getSessionData(any())(any(), any())) thenReturn (Future.successful(Some(SampleData.sessionData())))
        val action = new Harness(dataCacheConnector)

        val futureResult = action.callTransform(IdentifierRequest(fakeRequest, PsaId("A0000000")))

        whenReady(futureResult) { result =>
          result.userAnswers.isDefined mustBe true
          result.sessionData.map(_.isViewOnly) mustBe Some(true)
        }
      }

      "build a addRequiredDetailsToUserAnswers object, set viewOnly to false if psa is not suspended, isLocked is false and add it to the request" in {
        val dataCacheConnector = mock[UserAnswersCacheConnector]
        when(dataCacheConnector.fetch(any())(any(), any())) thenReturn Future.successful(Some(Json.obj("isPsaSuspended" -> false)))
        when(dataCacheConnector.getSessionData(any())(any(), any())) thenReturn (Future.successful(Some(SampleData.sessionData())))
        val action = new Harness(dataCacheConnector)

        val futureResult = action.callTransform(IdentifierRequest(fakeRequest, PsaId("A0000000")))

        whenReady(futureResult) { result =>
          result.userAnswers.isDefined mustBe true
          result.sessionData.map(_.isViewOnly) mustBe Some(false)
        }
      }
    }
  }
}
