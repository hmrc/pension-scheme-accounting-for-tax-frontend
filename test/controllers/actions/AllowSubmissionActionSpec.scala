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

import controllers.base.ControllerSpecBase
import data.SampleData
import models.UserAnswers
import models.requests.DataRequest
import org.mockito.Matchers.any
import org.mockito.Mockito.{reset, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import play.api.mvc.Result
import play.api.mvc.Results.NotFound
import services.AllowAccessService
import uk.gov.hmrc.domain.PsaId

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class AllowSubmissionActionSpec extends ControllerSpecBase with ScalaFutures with BeforeAndAfterEach {
  private val allowService = mock[AllowAccessService]

  override def beforeEach: Unit = {
    reset(allowService)
  }

  class Harness(allowService: AllowAccessService) extends AllowSubmissionActionImpl(allowService) {
    def callTransform[A](request: DataRequest[A]): Future[Option[Result]] = filter(request)
  }

  "Allow Submission Action" when {
    "submission is allowed" must {
      "return None" in {
        when(allowService.allowSubmission(any())(any())) thenReturn Future(None)
        val action = new Harness(allowService)

        val futureResult = action.callTransform(DataRequest(fakeRequest, "", PsaId(SampleData.psaId), UserAnswers(), SampleData.sessionData()))

        whenReady(futureResult) { result =>
          result mustBe None
        }
      }
    }

    "submission is not allowed" must {
      "return the Result " in {
        when(allowService.allowSubmission(any())(any())) thenReturn Future(Some(NotFound("Not Found")))
        val action = new Harness(allowService)

        val futureResult = action.callTransform(DataRequest(fakeRequest, "", PsaId(SampleData.psaId), UserAnswers(), SampleData.sessionData()))

        whenReady(futureResult) { result =>
          result.value mustBe NotFound("Not Found")
        }
      }
    }
  }
}
