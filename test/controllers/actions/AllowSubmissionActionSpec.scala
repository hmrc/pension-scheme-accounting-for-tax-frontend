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
import handlers.ErrorHandler
import models.{Quarter, UserAnswers}
import models.requests.DataRequest
import org.mockito.Matchers.any
import org.mockito.Mockito.{reset, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import pages.QuarterPage
import play.api.mvc.Result
import play.api.mvc.Results.{BadRequest, NotFound}
import services.AFTService
import uk.gov.hmrc.domain.PsaId
import utils.AFTConstants._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class AllowSubmissionActionSpec extends ControllerSpecBase with ScalaFutures with BeforeAndAfterEach {

  private val aftService: AFTService = mock[AFTService]
  private val errorHandler: ErrorHandler = mock[ErrorHandler]

  override def beforeEach: Unit = {
    reset(aftService)
    when(errorHandler.onClientError(any(), any(), any())).thenReturn(Future.successful(BadRequest))
  }

  class Harness(aftService: AFTService, errorHandler: ErrorHandler) extends AllowSubmissionActionImpl(aftService, errorHandler) {
    def callTransform[A](request: DataRequest[A]): Future[Option[Result]] = filter(request)
  }

  "Allow Submission Action" when {
    "submission is allowed and Quarter is known" must {
      "return None" in {
        when(aftService.isSubmissionDisabled(any())).thenReturn(false)
        val action = new Harness(aftService, errorHandler)

        val ua: UserAnswers = UserAnswers().setOrException(QuarterPage, Quarter(QUARTER_START_DATE, QUARTER_END_DATE))

        val futureResult = action.callTransform(DataRequest(fakeRequest, "", Some(PsaId(SampleData.psaId)), None, ua, SampleData.sessionData()))

        whenReady(futureResult) { result =>
          result mustBe None
        }
      }
    }

    "submission is allowed and Quarter is unknown" must {
      "return None" in {
        when(aftService.isSubmissionDisabled(any())).thenReturn(false)
        val action = new Harness(aftService, errorHandler)

        val futureResult = action.callTransform(DataRequest(fakeRequest, "", Some(PsaId(SampleData.psaId)), None, UserAnswers(), SampleData.sessionData()))

        whenReady(futureResult) { result =>
          result.value mustBe BadRequest
        }
      }
    }

    "submission is not allowed" must {
      "return the Result " in {
        when(aftService.isSubmissionDisabled(any())).thenReturn(true)
        when(errorHandler.onClientError(any(), any(), any())).thenReturn(Future(NotFound("Not Found")))

        val action = new Harness(aftService, errorHandler)

        val futureResult = action.callTransform(DataRequest(fakeRequest, "", Some(PsaId(SampleData.psaId)), None, UserAnswers(), SampleData.sessionData()))

        whenReady(futureResult) { result =>
          result.value mustBe NotFound("Not Found")
        }
      }
    }
  }
}
