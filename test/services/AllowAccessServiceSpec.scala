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
import handlers.ErrorHandler
import models.requests.OptionalDataRequest
import models.{Quarter, UserAnswers}
import org.mockito.Matchers
import org.mockito.Matchers.any
import org.mockito.Mockito.{reset, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.mockito.MockitoSugar
import pages.{IsPsaSuspendedQuery, QuarterPage}
import play.api.mvc.Results
import play.api.test.Helpers.NOT_FOUND
import uk.gov.hmrc.domain.PsaId
import utils.AFTConstants

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class AllowAccessServiceSpec extends SpecBase with ScalaFutures  with BeforeAndAfterEach with MockitoSugar with Results {

  private val pensionsSchemeConnector: SchemeDetailsConnector = mock[SchemeDetailsConnector]
  private val aftService: AFTService = mock[AFTService]
  private val errorHandler: ErrorHandler = mock[ErrorHandler]
  private def optionalDataRequest(ua:UserAnswers) = OptionalDataRequest(fakeRequest, "", PsaId(SampleData.psaId), Option(ua))

  override def beforeEach(): Unit = {
    reset(pensionsSchemeConnector, errorHandler)
  }

  "filterForIllegalPageAccess" must {
    "respond with None (i.e. allow access) when the PSA is not suspended and there is an association" in {
      val ua = SampleData.userAnswersWithSchemeName
        .set(IsPsaSuspendedQuery, value = false).toOption.get
      when(pensionsSchemeConnector.checkForAssociation(any(), any())(any(), any(), any()))
        .thenReturn(Future.successful(true))

      val allowAccessService = new AllowAccessService(pensionsSchemeConnector, aftService, errorHandler)

      whenReady(allowAccessService.filterForIllegalPageAccess("", ua)(optionalDataRequest(ua))) { result =>
        result mustBe None
      }
    }

    "respond with a call to the error handler for 404 (i.e. don't allow access) when the PSA is not suspended and there is no association" in {
      val ua = SampleData.userAnswersWithSchemeName
        .set(IsPsaSuspendedQuery, value = false).toOption.get
      when(pensionsSchemeConnector.checkForAssociation(any(), any())(any(), any(), any()))
        .thenReturn(Future.successful(false))

      val errorResult = Ok("error")
      when(errorHandler.onClientError(any(), Matchers.eq(NOT_FOUND), any())).thenReturn(Future.successful(errorResult))

      val allowAccessService = new AllowAccessService(pensionsSchemeConnector, aftService, errorHandler)

      whenReady(allowAccessService.filterForIllegalPageAccess("", ua)(optionalDataRequest(ua))) { result =>
        result mustBe Some(errorResult)
      }
    }

    "respond with a redirect to the cannot make changes page (i.e. don't allow access) when the PSA is suspended" in {
      val ua = SampleData.userAnswersWithSchemeName
        .set(IsPsaSuspendedQuery, value = true).toOption.get

      val expectedResult = Redirect(controllers.routes.CannotMakeChangesController.onPageLoad(SampleData.srn))

      val allowAccessService = new AllowAccessService(pensionsSchemeConnector, aftService, errorHandler)

      whenReady(allowAccessService.filterForIllegalPageAccess(SampleData.srn, ua)(optionalDataRequest(ua))) { result =>
        result mustBe Some(expectedResult)
      }
    }

    "respond with a redirect to the session expired page (i.e. don't allow access) when no PSA suspended flag is found in user answers" in {
      val ua = SampleData.userAnswersWithSchemeName

      val expectedResult = Redirect(controllers.routes.SessionExpiredController.onPageLoad())

      val allowAccessService = new AllowAccessService(pensionsSchemeConnector, aftService, errorHandler)

      whenReady(allowAccessService.filterForIllegalPageAccess("", ua)(optionalDataRequest(ua))) { result =>
        result mustBe Some(expectedResult)
      }
    }
  }

  "allowSubmission" must {
    "return None if the submission is allowed" in {
      val ua = SampleData.userAnswersWithSchemeName.set(QuarterPage, Quarter(AFTConstants.QUARTER_START_DATE, AFTConstants.QUARTER_END_DATE))
        .toOption.getOrElse(SampleData.userAnswersWithSchemeName)

      when(aftService.isSubmissionDisabled(any())).thenReturn(false)

      val allowAccessService = new AllowAccessService(pensionsSchemeConnector, aftService, errorHandler)

      whenReady(allowAccessService.allowSubmission(ua)(optionalDataRequest(ua))) { result =>
        result mustBe None
      }
    }

    "return Not Found if the submission is not allowed" in {
      val ua = SampleData.userAnswersWithSchemeName.set(QuarterPage, Quarter(AFTConstants.QUARTER_START_DATE, AFTConstants.QUARTER_END_DATE))
        .toOption.getOrElse(SampleData.userAnswersWithSchemeName)

      when(aftService.isSubmissionDisabled(any())).thenReturn(true)
      when(errorHandler.onClientError(any(), any(), any())).thenReturn(Future(NotFound("Not Found")))

      val allowAccessService = new AllowAccessService(pensionsSchemeConnector, aftService, errorHandler)

      whenReady(allowAccessService.allowSubmission(ua)(optionalDataRequest(ua))) { result =>
        result.value mustBe NotFound("Not Found")
      }
    }
  }
}
