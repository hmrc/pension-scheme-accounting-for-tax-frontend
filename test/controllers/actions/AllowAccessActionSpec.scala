/*
 * Copyright 2021 HM Revenue & Customs
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

import connectors.{AFTConnector, SchemeDetailsConnector}
import controllers.base.ControllerSpecBase
import data.SampleData.{accessType, versionInt, _}
import handlers.ErrorHandler
import models.LocalDateBinder._
import models.SchemeStatus.{Open, Rejected, WoundUp}
import models.requests.DataRequest
import models.{AccessMode, LockDetail, MinimalFlags, SessionAccessData, SessionData, UserAnswers}
import org.mockito.Matchers
import org.mockito.Matchers.any
import org.mockito.Mockito.{reset, when}
import org.scalatest.concurrent.ScalaFutures
import pages._
import play.api.mvc.Results._
import play.api.mvc.{AnyContent, Call, Result}
import play.api.test.Helpers.NOT_FOUND
import uk.gov.hmrc.domain.PsaId
import utils.AFTConstants.QUARTER_START_DATE

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class AllowAccessActionSpec extends ControllerSpecBase with ScalaFutures {

  private val aftConnector: AFTConnector = mock[AFTConnector]
  private val errorHandler: ErrorHandler = mock[ErrorHandler]
  private val version = 1
  private val pensionsSchemeConnector: SchemeDetailsConnector = mock[SchemeDetailsConnector]
  private val sessionId = "1"
  private val optionLockedByName = Some(LockDetail("bob", psaId))
  private def sessionData(sad:SessionAccessData) = SessionData(sessionId, optionLockedByName, sad)
  private val sessionAccessDataViewOnly: SessionAccessData =
    SessionAccessData(version = version, accessMode = AccessMode.PageAccessModeViewOnly, areSubmittedVersionsAvailable = false)
  private def dataRequest(ua:UserAnswers, viewOnly:Boolean = false, headers: Seq[(String,String)] = Seq.empty): DataRequest[AnyContent] = {
    val request = if (headers.isEmpty) fakeRequest else fakeRequest.withHeaders(headers :_*)
    DataRequest(request, "", Some(PsaId(psaId)), None, ua, sessionData(sessionAccessDataViewOnly))
  }

  class TestHarness(srn: String = srn, page: Option[Page] = None)(implicit ec: ExecutionContext)
    extends AllowAccessAction(srn, QUARTER_START_DATE, page, versionInt, accessType, aftConnector, errorHandler, frontendAppConfig, pensionsSchemeConnector)(ec) {
    def test(dataRequest: DataRequest[_]): Future[Option[Result]] = this.filter(dataRequest)
  }

  override def beforeEach: Unit = {
    reset(pensionsSchemeConnector, errorHandler)
    when(aftConnector.aftOverviewStartDate).thenReturn(QUARTER_START_DATE)
  }

  "Allow Access Action" must {
    "respond with None (i.e. allow access) when there is an association and " +
      "the scheme status is Open/Wound-up/Deregistered for a view-only Accessible page" in {
      val ua = userAnswersWithSchemeNamePstrQuarter
        .setOrException(SchemeStatusQuery, Open)
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

      val errorResult = Ok("error")
      when(errorHandler.onClientError(any(), Matchers.eq(NOT_FOUND), any())).thenReturn(Future.successful(errorResult))

      val testHarness = new TestHarness("")

      whenReady(testHarness.test(dataRequest(ua))) { result =>
        result mustBe Some(errorResult)
      }
    }

    "respond with a call to the error handler for 404 (i.e. don't allow access) when " +
      "the scheme status is Wound-up but there is no association" in {
      val ua = userAnswersWithSchemeName
        .setOrException(SchemeStatusQuery, WoundUp)
      when(pensionsSchemeConnector.checkForAssociation(any(), any(), any())(any(), any()))
        .thenReturn(Future.successful(false))

      val errorResult = Ok("error")
      when(errorHandler.onClientError(any(), Matchers.eq(NOT_FOUND), any())).thenReturn(Future.successful(errorResult))

      val testHarness = new TestHarness("")

      whenReady(testHarness.test(dataRequest(ua))) { result =>
        result mustBe Some(errorResult)
      }
    }

    "respond with a redirect to the AFT summary page when the PSA is not suspended and current page is charge type page and view only" in {
      val ua = userAnswersWithSchemeNamePstrQuarter
        .setOrException(SchemeStatusQuery, WoundUp)
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

      val expectedResult = Redirect(controllers.routes.SessionExpiredController.onPageLoad())

      val testHarness = new TestHarness("")

      whenReady(testHarness.test(dataRequest(ua))) { result =>
        result mustBe Some(expectedResult)
      }
    }

    "respond with a redirect to the session expired page (i.e. don't allow access) when" +
      "there is PSA suspeneded flag but no scheme status is found in user answers" in {

      val expectedResult = Redirect(controllers.routes.SessionExpiredController.onPageLoad())

      val testHarness = new TestHarness("")

      whenReady(testHarness.test(dataRequest(userAnswersWithSchemeName))) { result =>
        result mustBe Some(expectedResult)
      }
    }

    "respond with a redirect to deceased page when the PSA is deceased " in {
      val ua = userAnswersWithSchemeNamePstrQuarter
        .setOrException(SchemeStatusQuery, Open)
        .setOrException(MinimalFlagsQuery, MinimalFlags(deceasedFlag = true, rlsFlag = false))

      val errorResult = Ok("error")
      when(errorHandler.onClientError(any(), Matchers.eq(NOT_FOUND), any())).thenReturn(Future.successful(errorResult))

      val testHarness = new TestHarness("")

      whenReady(testHarness.test(dataRequest(ua))) { result =>
        result mustBe Some(Redirect(Call("GET", frontendAppConfig.youMustContactHMRCUrl)))
      }
    }
  }

}
