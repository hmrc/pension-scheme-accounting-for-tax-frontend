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
import connectors.{AFTConnector, SchemeDetailsConnector}
import data.SampleData
import handlers.ErrorHandler
import models.SchemeStatus.{Open, Rejected, WoundUp}
import models.requests.OptionalDataRequest
import models.{Quarter, UserAnswers}
import org.mockito.Matchers
import org.mockito.Matchers.any
import org.mockito.Mockito.{reset, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.mockito.MockitoSugar
import pages._
import play.api.mvc.Results
import play.api.test.Helpers.NOT_FOUND
import uk.gov.hmrc.domain.PsaId
import utils.AFTConstants

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import AFTConstants._
import models.AccessMode
import models.LocalDateBinder._
import models.SessionAccessData
import models.SessionData
import models.requests.DataRequest

class AllowAccessServiceSpec extends SpecBase with ScalaFutures  with BeforeAndAfterEach with MockitoSugar with Results {

  private val version = 1
  private val pensionsSchemeConnector: SchemeDetailsConnector = mock[SchemeDetailsConnector]
  private val aftService: AFTService = mock[AFTService]
  private val aftConnector: AFTConnector = mock[AFTConnector]
  private val errorHandler: ErrorHandler = mock[ErrorHandler]
  private val sessionId = "1"
  private val optionLockedByName = Some("bob")
  private def sessionData(sad:SessionAccessData) = SessionData(sessionId, optionLockedByName, sad)
  private val sessionAccessDataViewOnly = SessionAccessData(version = version, accessMode = AccessMode.PageAccessModeViewOnly)
  private def dataRequest(ua:UserAnswers, viewOnly:Boolean = false, headers: Seq[(String,String)] = Seq.empty) = {
    val request = if (headers.isEmpty) fakeRequest else fakeRequest.withHeaders(headers :_*)
    DataRequest(request, "", PsaId(SampleData.psaId), ua, sessionData(sessionAccessDataViewOnly))
  }

  override def beforeEach(): Unit = {
    reset(pensionsSchemeConnector, errorHandler)
    when(aftConnector.aftOverviewStartDate).thenReturn(QUARTER_START_DATE)
  }

  "filterForIllegalPageAccess" must {
    "respond with None (i.e. allow access) when the PSA is not suspended, there is an association and " +
      "the scheme status is Open/Wound-up/Deregistered for a view-only Accessible page" in {
      val ua = SampleData.userAnswersWithSchemeNamePstrQuarter
        .setOrException(IsPsaSuspendedQuery, value = false)
        .setOrException(SchemeStatusQuery, Open)
      when(pensionsSchemeConnector.checkForAssociation(any(), any())(any(), any(), any()))
        .thenReturn(Future.successful(true))

      val allowAccessService = new AllowAccessService(pensionsSchemeConnector, aftService, aftConnector, errorHandler)

      whenReady(allowAccessService.filterForIllegalPageAccess(SampleData.srn, QUARTER_START_DATE, ua, Some(ViewOnlyAccessiblePage))(dataRequest(ua))) {
        _ mustBe None
      }
    }

    "respond with call to error page for redirecting form pages in view-only returns " +
      "the scheme status is Open/Wound-up/Deregistered for a option page is None" in {
      val ua = SampleData.userAnswersWithSchemeNamePstrQuarter
        .setOrException(IsPsaSuspendedQuery, value = false)
        .setOrException(SchemeStatusQuery, Open)

      val expectedResult = Redirect(controllers.routes.AFTSummaryController.onPageLoad(SampleData.srn, QUARTER_START_DATE, None))

      when(pensionsSchemeConnector.checkForAssociation(any(), any())(any(), any(), any()))
        .thenReturn(Future.successful(true))

      val allowAccessService = new AllowAccessService(pensionsSchemeConnector, aftService, aftConnector, errorHandler)

      whenReady(allowAccessService.filterForIllegalPageAccess(SampleData.srn, QUARTER_START_DATE, ua)(dataRequest(ua))) { result =>
        result mustBe Some(expectedResult)
        whenReady(allowAccessService.filterForIllegalPageAccess(SampleData.srn, QUARTER_START_DATE, ua)(dataRequest(ua))) { result =>
          result mustBe Some(expectedResult)
        }
      }
    }

    "respond with a call to the error handler for 404 (i.e. don't allow access) when the PSA is not suspended " +
      "but the scheme status is Rejected" in {
      val ua = SampleData.userAnswersWithSchemeNamePstrQuarter
        .setOrException(IsPsaSuspendedQuery, value = false)
        .setOrException(SchemeStatusQuery, Rejected)

      val errorResult = Ok("error")
      when(errorHandler.onClientError(any(), Matchers.eq(NOT_FOUND), any())).thenReturn(Future.successful(errorResult))

      val allowAccessService = new AllowAccessService(pensionsSchemeConnector, aftService, aftConnector, errorHandler)

      whenReady(allowAccessService.filterForIllegalPageAccess("", QUARTER_START_DATE, ua)(dataRequest(ua))) { result =>
        result mustBe Some(errorResult)
      }
    }

    "respond with a call to the error handler for 404 (i.e. don't allow access) when the PSA is not suspended, " +
      "the scheme status is Wound-up but there is no association" in {
      val ua = SampleData.userAnswersWithSchemeName
        .setOrException(IsPsaSuspendedQuery, value = false)
        .setOrException(SchemeStatusQuery, WoundUp)
      when(pensionsSchemeConnector.checkForAssociation(any(), any())(any(), any(), any()))
        .thenReturn(Future.successful(false))

      val errorResult = Ok("error")
      when(errorHandler.onClientError(any(), Matchers.eq(NOT_FOUND), any())).thenReturn(Future.successful(errorResult))

      val allowAccessService = new AllowAccessService(pensionsSchemeConnector, aftService, aftConnector, errorHandler)

      whenReady(allowAccessService.filterForIllegalPageAccess("", QUARTER_START_DATE, ua)(dataRequest(ua))) { result =>
        result mustBe Some(errorResult)
      }
    }

    "respond with a redirect to the AFT summary page when the PSA is not suspended and current page is charge type page and view only" in {
      val ua = SampleData.userAnswersWithSchemeNamePstrQuarter
        .setOrException(IsPsaSuspendedQuery, value = false)
        .setOrException(SchemeStatusQuery, WoundUp)
      when(pensionsSchemeConnector.checkForAssociation(any(), any())(any(), any(), any()))
        .thenReturn(Future.successful(true))

      val expectedResult = Redirect(controllers.routes.AFTSummaryController.onPageLoad(SampleData.srn, QUARTER_START_DATE, None))

      val allowAccessService = new AllowAccessService(pensionsSchemeConnector, aftService, aftConnector, errorHandler)

      whenReady(allowAccessService.filterForIllegalPageAccess(SampleData.srn, QUARTER_START_DATE, ua, Some(ChargeTypePage))(dataRequest(ua, viewOnly = true))) { result =>
        result mustBe Some(expectedResult)
      }
    }

    "respond with a redirect to the cannot start AFT return page when the PSA is suspended and current page is charge type page" in {
      val ua = SampleData.userAnswersWithSchemeName
        .setOrException(IsPsaSuspendedQuery, value = true)
        .setOrException(SchemeStatusQuery, WoundUp)
      when(pensionsSchemeConnector.checkForAssociation(any(), any())(any(), any(), any()))
        .thenReturn(Future.successful(true))

      val expectedResult = Redirect(controllers.routes.CannotStartAFTReturnController.onPageLoad(SampleData.srn, QUARTER_START_DATE))

      val allowAccessService = new AllowAccessService(pensionsSchemeConnector, aftService, aftConnector, errorHandler)

      whenReady(allowAccessService.filterForIllegalPageAccess(SampleData.srn, QUARTER_START_DATE, ua, Some(ChargeTypePage))(dataRequest(ua))) { result =>
        result mustBe Some(expectedResult)
      }
    }

    "respond with a redirect to the cannot change AFT return page when the PSA is suspended and current page is AFT summary page and referer is NOT present in request" in {
      val ua = SampleData.userAnswersWithSchemeName
        .setOrException(IsPsaSuspendedQuery, value = true)
        .setOrException(SchemeStatusQuery, WoundUp)
      when(pensionsSchemeConnector.checkForAssociation(any(), any())(any(), any(), any()))
        .thenReturn(Future.successful(true))

      val expectedResult = Redirect(controllers.routes.CannotChangeAFTReturnController.onPageLoad(SampleData.srn, QUARTER_START_DATE, Some(version.toString)))

      val allowAccessService = new AllowAccessService(pensionsSchemeConnector, aftService, aftConnector, errorHandler)

      whenReady(allowAccessService
        .filterForIllegalPageAccess(SampleData.srn, QUARTER_START_DATE, ua, Some(AFTSummaryPage), Some(version.toString))(dataRequest(ua))) { result =>
        result mustBe Some(expectedResult)
      }
    }

    "respond with a redirect to the cannot change AFT return page when the PSA is suspended and current page is AFT summary page and referer is NOT an AFT URL" in {
      val ua = SampleData.userAnswersWithSchemeName
        .setOrException(IsPsaSuspendedQuery, value = true)
        .setOrException(SchemeStatusQuery, WoundUp)
      when(pensionsSchemeConnector.checkForAssociation(any(), any())(any(), any(), any()))
        .thenReturn(Future.successful(true))

      val expectedResult = Redirect(controllers.routes.CannotChangeAFTReturnController.onPageLoad(SampleData.srn, QUARTER_START_DATE, Some(version.toString)))

      val allowAccessService = new AllowAccessService(pensionsSchemeConnector, aftService, aftConnector, errorHandler)

      whenReady(allowAccessService
        .filterForIllegalPageAccess(SampleData.srn, QUARTER_START_DATE, ua,
          Some(AFTSummaryPage),
          Some(version.toString))(dataRequest(ua, viewOnly = true, headers = Seq("Referer" -> "manage-pension-schemes")))) { result =>
        result mustBe Some(expectedResult)
      }
    }

    "respond with a None (i.e. allow access) when the PSA is suspended and current page is AFT summary page and referer is an AFT URL" in {
      val ua = SampleData.userAnswersWithSchemeNamePstrQuarter
        .setOrException(IsPsaSuspendedQuery, value = true)
        .setOrException(SchemeStatusQuery, WoundUp)
      when(pensionsSchemeConnector.checkForAssociation(any(), any())(any(), any(), any()))
        .thenReturn(Future.successful(true))

      val allowAccessService = new AllowAccessService(pensionsSchemeConnector, aftService, aftConnector, errorHandler)

      whenReady(allowAccessService
        .filterForIllegalPageAccess(SampleData.srn, QUARTER_START_DATE, ua,
          Some(AFTSummaryPage),
          Some(version.toString))(dataRequest(ua, viewOnly = true, headers = Seq("Referer" -> "manage-pension-scheme-accounting-for-tax")))) { result =>
        result mustBe None
      }
    }

    "respond with a redirect to the session expired page (i.e. don't allow access) when " +
      "no PSA suspended flag is found in user answers" in {
      val ua = SampleData.userAnswersWithSchemeName

      val expectedResult = Redirect(controllers.routes.SessionExpiredController.onPageLoad())

      val allowAccessService = new AllowAccessService(pensionsSchemeConnector, aftService, aftConnector, errorHandler)

      whenReady(allowAccessService.filterForIllegalPageAccess("", QUARTER_START_DATE, ua)(dataRequest(ua))) { result =>
        result mustBe Some(expectedResult)
      }
    }

    "respond with a redirect to the session expired page (i.e. don't allow access) when" +
      "there is PSA suspeneded flag but no scheme status is found in user answers" in {
      val ua = SampleData.userAnswersWithSchemeName.set(IsPsaSuspendedQuery, value = false).toOption.get

      val expectedResult = Redirect(controllers.routes.SessionExpiredController.onPageLoad())

      val allowAccessService = new AllowAccessService(pensionsSchemeConnector, aftService, aftConnector, errorHandler)

      whenReady(allowAccessService.filterForIllegalPageAccess("", QUARTER_START_DATE, ua)(dataRequest(ua))) { result =>
        result mustBe Some(expectedResult)
      }
    }

    "respond with a redirect to the session expired page (i.e. don't allow access) when" +
      "there is scheme status but no PSA suspeneded flag is found in user answers" in {
      val ua = SampleData.userAnswersWithSchemeName.set(SchemeStatusQuery, Open).toOption.get

      val expectedResult = Redirect(controllers.routes.SessionExpiredController.onPageLoad())

      val allowAccessService = new AllowAccessService(pensionsSchemeConnector, aftService, aftConnector, errorHandler)

      whenReady(allowAccessService.filterForIllegalPageAccess("", QUARTER_START_DATE, ua)(dataRequest(ua))) { result =>
        result mustBe Some(expectedResult)
      }
    }
  }

  "allowSubmission" must {
    "return None if the submission is allowed" in {
      val ua = SampleData.userAnswersWithSchemeName.set(QuarterPage, Quarter(AFTConstants.QUARTER_START_DATE, AFTConstants.QUARTER_END_DATE))
        .toOption.getOrElse(SampleData.userAnswersWithSchemeName)

      when(aftService.isSubmissionDisabled(any())).thenReturn(false)

      val allowAccessService = new AllowAccessService(pensionsSchemeConnector, aftService, aftConnector, errorHandler)

      whenReady(allowAccessService.allowSubmission(ua)(dataRequest(ua))) { result =>
        result mustBe None
      }
    }

    "return Not Found if the submission is not allowed" in {
      val ua = SampleData.userAnswersWithSchemeName.set(QuarterPage, Quarter(AFTConstants.QUARTER_START_DATE, AFTConstants.QUARTER_END_DATE))
        .toOption.getOrElse(SampleData.userAnswersWithSchemeName)

      when(aftService.isSubmissionDisabled(any())).thenReturn(true)
      when(errorHandler.onClientError(any(), any(), any())).thenReturn(Future(NotFound("Not Found")))

      val allowAccessService = new AllowAccessService(pensionsSchemeConnector, aftService, aftConnector, errorHandler)

      whenReady(allowAccessService.allowSubmission(ua)(dataRequest(ua))) { result =>
        result.value mustBe NotFound("Not Found")
      }
    }
  }
}
