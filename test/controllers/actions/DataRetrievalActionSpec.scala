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

package controllers.actions

import base.SpecBase
import connectors.cache.UserAnswersCacheConnector
import data.SampleData._
import models.SessionData
import models.requests.{IdentifierRequest, OptionalDataRequest}
import org.mockito.ArgumentMatchers.{eq => eqTo, _}
import org.mockito.Mockito.when
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.mockito.MockitoSugar
import play.api.mvc.AnyContent
import uk.gov.hmrc.domain.PsaId
import utils.AFTConstants.QUARTER_START_DATE

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class DataRetrievalActionSpec extends SpecBase with MockitoSugar with ScalaFutures {

  val dataCacheConnector: UserAnswersCacheConnector = mock[UserAnswersCacheConnector]

  private val nameLockedBy = None
  private val sd = SessionData(sessionId, nameLockedBy, sessionAccessDataCompile)
  private val request: IdentifierRequest[AnyContent] = IdentifierRequest("id", fakeRequest, Some(PsaId(psaId)), None)
  val id = s"$srn$startDate"

  class Harness extends DataRetrievalImpl(srn, QUARTER_START_DATE, dataCacheConnector) {
    def callTransform[A](request: IdentifierRequest[A]): Future[OptionalDataRequest[A]] = transform(request)
  }


  "Data Retrieval Action" when {
    "there is no data in the cache" must {
      "set userAnswers to 'None' in the request" in {

        when(dataCacheConnector.fetch(eqTo(id))(any(), any())) `thenReturn` Future(None)
        when(dataCacheConnector.getSessionData(eqTo(id))(any(), any())) `thenReturn` Future(None)
        val action = new Harness

        val expectedResult = OptionalDataRequest(request, id, Some(PsaId(psaId)), None, None, None)
        val futureResult = action.callTransform(request)

        whenReady(futureResult) { result =>
          result.userAnswers.isEmpty mustBe true
          result mustBe expectedResult
        }
      }
    }

    "there is data in the cache" must {
      "build a userAnswers object and add it to the request" in {

        when(dataCacheConnector.fetch(eqTo(id))(any(), any())) `thenReturn` Future.successful(Some(userAnswersWithSchemeName.data))
        when(dataCacheConnector.getSessionData(eqTo(id))(any(), any())) `thenReturn` Future(Some(sd))
        val action = new Harness

        val futureResult = action.callTransform(request)
        val expectedResult = OptionalDataRequest(request, id, Some(PsaId(psaId)), None, Some(userAnswersWithSchemeName), Some(sd))

        whenReady(futureResult) { result =>
          result.userAnswers.isDefined mustBe true
          result mustBe expectedResult
        }
      }
    }
  }
}
