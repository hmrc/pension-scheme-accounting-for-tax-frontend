/*
 * Copyright 2023 HM Revenue & Customs
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

import connectors.MinimalConnector.MinimalDetails
import connectors.{AFTConnector, MinimalConnector, SchemeDetailsConnector}
import controllers.base.ControllerSpecBase
import data.SampleData._
import handlers.ErrorHandler
import models.LocalDateBinder._
import models.SchemeStatus.{Open, Rejected, WoundUp}
import models.requests.{DataRequest, IdentifierRequest}
import models.{AccessMode, LockDetail, MinimalFlags, SessionAccessData, SessionData, UserAnswers}
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, when}
import org.scalatest.concurrent.ScalaFutures
import pages._
import play.api.mvc.Results._
import play.api.mvc.{AnyContent, Call, Result}
import play.api.test.Helpers.NOT_FOUND
import uk.gov.hmrc.domain.{PsaId, PspId}
import utils.AFTConstants.QUARTER_START_DATE

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class AllowAccessActionSpec extends ControllerSpecBase with ScalaFutures {

  private val aftConnector: AFTConnector = mock[AFTConnector]
  private val errorHandler: ErrorHandler = mock[ErrorHandler]
  private val mockMinimalConnector = mock[MinimalConnector]

  private val version = 1
  private val pensionsSchemeConnector: SchemeDetailsConnector = mock[SchemeDetailsConnector]
  private val sessionId = "1"
  private val optionLockedByName = Some(LockDetail("bob", psaId))

  private def sessionData(sad: SessionAccessData) = SessionData(sessionId, optionLockedByName, sad)

  private val sessionAccessDataViewOnly: SessionAccessData =
    SessionAccessData(version = version, accessMode = AccessMode.PageAccessModeViewOnly, areSubmittedVersionsAvailable = false)
  private val email = "a@a.c"

  private def dataRequest(ua: UserAnswers, viewOnly: Boolean = false, headers: Seq[(String, String)] = Seq.empty): DataRequest[AnyContent] = {
    val request = if (headers.isEmpty) fakeRequest else fakeRequest.withHeaders(headers: _*)
    DataRequest(request, "", Some(PsaId(psaId)), None, ua, sessionData(sessionAccessDataViewOnly))
  }

  private def identifierRequest(headers: Seq[(String, String)] = Seq.empty): IdentifierRequest[AnyContent] = {
    val request = if (headers.isEmpty) fakeRequest else fakeRequest.withHeaders(headers: _*)
    IdentifierRequest("id", request, Some(PsaId(psaId)), None)
  }

  private def identifierRequestPsp(headers: Seq[(String, String)] = Seq.empty): IdentifierRequest[AnyContent] = {
    val request = if (headers.isEmpty) fakeRequest else fakeRequest.withHeaders(headers: _*)
    IdentifierRequest("id", request, None, Some(PspId(pspId)))
  }

  private def dataRequestPsp(ua: UserAnswers, headers: Seq[(String, String)] = Seq.empty): DataRequest[AnyContent] = {
    val request = if (headers.isEmpty) fakeRequest else fakeRequest.withHeaders(headers: _*)
    DataRequest(request, "", None, Some(PspId(pspId)), ua, sessionData(sessionAccessDataViewOnly))
  }

  class TestHarness(srn: String = srn, page: Option[Page] = None)(implicit ec: ExecutionContext)
    extends AllowAccessAction(srn, QUARTER_START_DATE, page, versionInt, accessType, aftConnector, errorHandler,
      frontendAppConfig, pensionsSchemeConnector)(ec) {
    def test(dataRequest: DataRequest[_]): Future[Option[Result]] = this.filter(dataRequest)
  }

  class TestHarnessForIdentifierRequest()(implicit ec: ExecutionContext)
    extends AllowAccessActionForIdentifierRequest(frontendAppConfig, mockMinimalConnector)(ec) {
    def test(identifierRequest: IdentifierRequest[_]): Future[Option[Result]] = this.filter(identifierRequest)
  }

  override def beforeEach(): Unit = {
    reset(pensionsSchemeConnector)
    reset(errorHandler)
    reset(mockMinimalConnector)
    when(aftConnector.aftOverviewStartDate).thenReturn(QUARTER_START_DATE)
  }

  "Allow Access Action" must {
    "respond with None (i.e. allow access) when there is an association and " +
      "the scheme status is Open/Wound-up/Deregistered for a view-only Accessible page" in {
      val ua = userAnswersWithSchemeNamePstrQuarter
        .setOrException(SchemeStatusQuery, Open)
        .setOrException(MinimalFlagsQuery, MinimalFlags(deceasedFlag = false, rlsFlag = false))

      when(pensionsSchemeConnector.checkForAssociation(any(), any(), any())(any(), any()))
        .thenReturn(Future.successful(true))

      val testHarness = new TestHarness(page = Some(ViewOnlyAccessiblePage))

      whenReady(testHarness.test(dataRequest(ua))) {
        _ mustBe None
      }
    }

    "respond with call to error page for redirecting form pages in view-only returns " +
      "the scheme status is Open/Wound-up/Deregistered for a option page is None" in {
      val ua = userAnswersWithSchemeNamePstrQuarter
        .setOrException(SchemeStatusQuery, Open)
        .setOrException(MinimalFlagsQuery, MinimalFlags(deceasedFlag = false, rlsFlag = false))

      val expectedResult: Result =
        Redirect(controllers.routes.AFTSummaryController.onPageLoad(srn, QUARTER_START_DATE, accessType, versionInt))

      when(pensionsSchemeConnector.checkForAssociation(any(), any(), any())(any(), any()))
        .thenReturn(Future.successful(true))

      val testHarness = new TestHarness()

      whenReady(testHarness.test(dataRequest(ua))) { result =>
        result mustBe Some(expectedResult)
        whenReady(testHarness.test(dataRequest(ua))) { result =>
          result mustBe Some(expectedResult)
        }
      }
    }

    "respond with a call to the error handler for 404 (i.e. don't allow access) when the PSA is not suspended " +
      "but the scheme status is Rejected" in {
      val ua = userAnswersWithSchemeNamePstrQuarter
        .setOrException(SchemeStatusQuery, Rejected)
        .setOrException(MinimalFlagsQuery, MinimalFlags(deceasedFlag = false, rlsFlag = false))

      val errorResult = Ok("error")
      when(errorHandler.onClientError(any(), ArgumentMatchers.eq(NOT_FOUND), any())).thenReturn(Future.successful(errorResult))

      val testHarness = new TestHarness(srn)

      whenReady(testHarness.test(dataRequest(ua))) { result =>
        result mustBe Some(errorResult)
      }
    }

    "respond with a call to the error handler for 404 (i.e. don't allow access) when " +
      "the scheme status is Wound-up but there is no association" in {
      val ua = userAnswersWithSchemeName
        .setOrException(SchemeStatusQuery, WoundUp)
        .setOrException(MinimalFlagsQuery, MinimalFlags(deceasedFlag = false, rlsFlag = false))

      when(pensionsSchemeConnector.checkForAssociation(any(), any(), any())(any(), any()))
        .thenReturn(Future.successful(false))

      val errorResult = Ok("error")
      when(errorHandler.onClientError(any(), ArgumentMatchers.eq(NOT_FOUND), any())).thenReturn(Future.successful(errorResult))

      val testHarness = new TestHarness(srn)

      whenReady(testHarness.test(dataRequest(ua))) { result =>
        result mustBe Some(errorResult)
      }
    }

    "respond with a redirect to the AFT summary page when the PSA is not suspended and current page is charge type page and view only" in {
      val ua = userAnswersWithSchemeNamePstrQuarter
        .setOrException(SchemeStatusQuery, WoundUp)
        .setOrException(MinimalFlagsQuery, MinimalFlags(deceasedFlag = false, rlsFlag = false))

      when(pensionsSchemeConnector.checkForAssociation(any(), any(), any())(any(), any()))
        .thenReturn(Future.successful(true))

      val expectedResult = Redirect(controllers.routes.AFTSummaryController.onPageLoad(srn, QUARTER_START_DATE, accessType, versionInt))

      val testHarness = new TestHarness(page = Some(ChargeTypePage))

      whenReady(testHarness.test(dataRequest(ua, viewOnly = true))) { result =>
        result mustBe Some(expectedResult)
      }
    }

    "respond with a redirect to the session expired page (i.e. don't allow access) when " +
      "no PSA suspended flag is found in user answers" in {
      val ua = userAnswersWithSchemeName
        .setOrException(MinimalFlagsQuery, MinimalFlags(deceasedFlag = false, rlsFlag = false))

      val expectedResult = Redirect(controllers.routes.SessionExpiredController.onPageLoad)

      val testHarness = new TestHarness(srn)

      whenReady(testHarness.test(dataRequest(ua))) { result =>
        result mustBe Some(expectedResult)
      }
    }

    "respond with a redirect to the session expired page (i.e. don't allow access) when" +
      "there is PSA suspended flag but no scheme status is found in user answers" in {

      val ua = userAnswersWithSchemeName
        .setOrException(MinimalFlagsQuery, MinimalFlags(deceasedFlag = false, rlsFlag = false))

      val expectedResult = Redirect(controllers.routes.SessionExpiredController.onPageLoad)

      val testHarness = new TestHarness(srn)

      whenReady(testHarness.test(dataRequest(ua))) { result =>
        result mustBe Some(expectedResult)
      }
    }

    "respond with a redirect to deceased page when the PSA is deceased " in {
      val ua = userAnswersWithSchemeNamePstrQuarter
        .setOrException(SchemeStatusQuery, Open)
        .setOrException(MinimalFlagsQuery, MinimalFlags(deceasedFlag = true, rlsFlag = false))

      val testHarness = new TestHarness(srn)

      whenReady(testHarness.test(dataRequest(ua))) { result =>
        result mustBe Some(Redirect(Call("GET", frontendAppConfig.youMustContactHMRCUrl)))
      }
    }

    "respond with a redirect to update contact address page when the PSA has RLS flag set" in {
      val ua = userAnswersWithSchemeNamePstrQuarter
        .setOrException(SchemeStatusQuery, Open)
        .setOrException(MinimalFlagsQuery, MinimalFlags(deceasedFlag = false, rlsFlag = true))

      val testHarness = new TestHarness(srn)

      whenReady(testHarness.test(dataRequest(ua))) { result =>
        result mustBe Some(Redirect(Call("GET", frontendAppConfig.psaUpdateContactDetailsUrl)))
      }
    }

    "respond with a redirect to update contact address page when the PSP has RLS flag set" in {
      val ua = userAnswersWithSchemeNamePstrQuarter
        .setOrException(SchemeStatusQuery, Open)
        .setOrException(MinimalFlagsQuery, MinimalFlags(deceasedFlag = false, rlsFlag = true))

      val testHarness = new TestHarness(srn)

      whenReady(testHarness.test(dataRequestPsp(ua))) { result =>
        result mustBe Some(Redirect(Call("GET", frontendAppConfig.pspUpdateContactDetailsUrl)))
      }
    }

    "respond with a redirect to return to scheme details when no minimal details are present in user answers" in {
      val ua = userAnswersWithSchemeNamePstrQuarter
        .setOrException(SchemeStatusQuery, Open)

      val testHarness = new TestHarness(srn)

      whenReady(testHarness.test(dataRequestPsp(ua))) { result =>
        result mustBe Some(Redirect(controllers.routes.ReturnToSchemeDetailsController
          .returnToSchemeDetails(srn, startDate, accessType, version)))
      }
    }
  }

  "Allow Access Action For Identifier Request" must {
    "respond with None (i.e. allow access) when neither of minimal flags are true" in {

      val testHarness = new TestHarnessForIdentifierRequest()

      val minimalDetails = MinimalDetails(email, isPsaSuspended = false, Some(companyName), None, rlsFlag = false, deceasedFlag = false)

      when(mockMinimalConnector.getMinimalDetails(any(), any(), any()))
        .thenReturn(Future.successful(minimalDetails))

      whenReady(testHarness.test(identifierRequest())) {
        _ mustBe None
      }
    }
  }

  "respond with a redirect to deceased page when the PSA is deceased" in {
    val testHarness = new TestHarnessForIdentifierRequest()

    val minimalDetails = MinimalDetails(email, isPsaSuspended = false, Some(companyName), None, rlsFlag = false, deceasedFlag = true)

    when(mockMinimalConnector.getMinimalDetails(any(), any(), any()))
      .thenReturn(Future.successful(minimalDetails))

    whenReady(testHarness.test(identifierRequest())) { result =>
      result mustBe Some(Redirect(Call("GET", frontendAppConfig.youMustContactHMRCUrl)))
    }
  }

  "respond with a redirect to update contact address page when the PSA has RLS flag set" in {
    val minimalDetails = MinimalDetails(email, isPsaSuspended = false, Some(companyName), None, rlsFlag = true, deceasedFlag = false)

    when(mockMinimalConnector.getMinimalDetails(any(), any(), any()))
      .thenReturn(Future.successful(minimalDetails))

    val testHarness = new TestHarnessForIdentifierRequest()

    whenReady(testHarness.test(identifierRequest())) { result =>
      result mustBe Some(Redirect(Call("GET", frontendAppConfig.psaUpdateContactDetailsUrl)))
    }
  }

  "respond with a redirect to update contact address page when the PSP has RLS flag set" in {
    val minimalDetails = MinimalDetails(email, isPsaSuspended = false, Some(companyName), None, rlsFlag = true, deceasedFlag = false)

    when(mockMinimalConnector.getMinimalDetails(any(), any(), any()))
      .thenReturn(Future.successful(minimalDetails))

    val testHarness = new TestHarnessForIdentifierRequest()

    whenReady(testHarness.test(identifierRequestPsp())) { result =>
      result mustBe Some(Redirect(Call("GET", frontendAppConfig.pspUpdateContactDetailsUrl)))
    }
  }

}
