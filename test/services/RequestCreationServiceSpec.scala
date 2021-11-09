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

package services

import base.SpecBase
import config.FrontendAppConfig
import connectors.MinimalConnector.MinimalDetails
import connectors.cache.UserAnswersCacheConnector
import connectors.{AFTConnector, MinimalConnector}
import data.SampleData
import data.SampleData._
import models.requests.IdentifierRequest
import models.{AFTOverview, AFTOverviewVersion, AccessMode, MinimalFlags, SchemeDetails, SchemeStatus, SessionAccessData, SessionData, UserAnswers}
import org.mockito.ArgumentMatchers.any
import org.mockito.{ArgumentCaptor, ArgumentMatchers, MockitoSugar}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.must.Matchers
import pages._
import play.api.libs.json.JsObject
import play.api.mvc.AnyContentAsEmpty
import uk.gov.hmrc.domain.PsaId
import utils.AFTConstants._
import utils.DateHelper

import java.time.LocalDate
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

class RequestCreationServiceSpec extends SpecBase with Matchers with MockitoSugar with ScalaFutures with BeforeAndAfterEach {

  private val mockAftConnector: AFTConnector = mock[AFTConnector]
  private val mockUserAnswersCacheConnector: UserAnswersCacheConnector = mock[UserAnswersCacheConnector]
  private val mockSchemeService: SchemeService = mock[SchemeService]
  private val mockMinimalPsaConnector: MinimalConnector = mock[MinimalConnector]
  private val mockAppConfig: FrontendAppConfig = mock[FrontendAppConfig]

  private val psaIdInstance = PsaId(psaId)

  private val sessionId = "session id"

  private val nameLockedBy = None
  private val sessionAccessDataCompile = SessionAccessData(version = 1, accessMode = AccessMode.PageAccessModeCompile, areSubmittedVersionsAvailable = false)
  private val sd = SessionData(sessionId, nameLockedBy, sessionAccessDataCompile)

  private val request: IdentifierRequest[AnyContentAsEmpty.type] =
    IdentifierRequest("id", fakeRequest, Some(psaIdInstance))

  private val schemeStatus = "Open"

  private val schemeDetails = SchemeDetails(schemeName, pstr, schemeStatus, None)

  private def requestCreationService =
    new RequestCreationService(mockAftConnector, mockUserAnswersCacheConnector, mockSchemeService, mockMinimalPsaConnector)

  private val email = "test@test.com"

  private val expectedUAToBePassedToSaveAndLock = UserAnswers()
    .setOrException(SchemeNameQuery, SampleData.schemeName)
    .setOrException(PSTRQuery, SampleData.pstr)
    .setOrException(MinimalFlagsQuery, MinimalFlags(deceasedFlag = true, rlsFlag = false))
    .setOrException(SchemeStatusQuery, SchemeStatus.Open)
    .setOrException(NameQuery, companyName)
    .setOrException(EmailQuery, email)

  override def beforeEach(): Unit = {
    reset(mockAftConnector, mockUserAnswersCacheConnector, mockSchemeService, mockMinimalPsaConnector, mockAppConfig)

    when(mockUserAnswersCacheConnector.fetch(any())(any(), any())).thenReturn(Future.successful(Some(userAnswersWithSchemeName.data)))
    when(mockSchemeService.retrieveSchemeDetails(any(), any(), any())(any(), any())).thenReturn(Future.successful(schemeDetails))
    when(mockMinimalPsaConnector.getMinimalDetails(any(), any(), any()))
      .thenReturn(Future.successful(MinimalDetails(email, isPsaSuspended = false, Some(companyName), None, rlsFlag = false, deceasedFlag = true)))
    when(mockUserAnswersCacheConnector.lockDetail(any(), any())(any(), any())).thenReturn(Future.successful(None))
    when(mockUserAnswersCacheConnector.saveAndLock(any(), any(), any(), any())(any(), any())).thenReturn(Future.successful(userAnswersWithSchemeName.data))
    when(mockUserAnswersCacheConnector.getSessionData(any())(any(), any())).thenReturn(Future.successful(Some(sd)))
  }

  "retrieveAndCreateRequest" when {

    "requested version is less than latest and date is on or after 21st July" must {
      "NOT save with a lock and create session access data with viewonly page access mode" in {
        val multipleVersions = Seq[AFTOverview](
          AFTOverview(
            periodStartDate = LocalDate.of(2020, 4, 1),
            periodEndDate = LocalDate.of(2020, 6, 28),
            tpssReportPresent = false,
            Some(AFTOverviewVersion(
              numberOfVersions = 2,
              submittedVersionAvailable = true,
              compiledVersionAvailable = true
            )))
        )

        when(mockAftConnector.getAftOverview(any(), any(), any())(any(), any()))
          .thenReturn(Future.successful(multipleVersions))

        when(mockAftConnector.getAFTDetails(any(), any(), any())(any(), any()))
          .thenReturn(Future.successful(userAnswersWithSchemeName.data))

        val jsonCaptorForSaveAndLock = ArgumentCaptor.forClass(classOf[JsObject])

        when(mockUserAnswersCacheConnector.saveAndLock(any(), jsonCaptorForSaveAndLock.capture(), any(), any())(any(), any()))
          .thenReturn(Future.successful(userAnswersWithSchemeName.data))

        DateHelper.setDate(Some(LocalDate.of(2020, 7, 21)))

        val result = Await.result(
          requestCreationService
            .retrieveAndCreateRequest(srn, QUARTER_START_DATE, 1, accessType, None)(request, implicitly, implicitly),
          Duration.Inf
        )

        verify(mockUserAnswersCacheConnector, times(1))
          .saveAndLock(any(),
            any(),
            ArgumentMatchers.eq(SessionAccessData(version = 1, accessMode = AccessMode.PageAccessModeViewOnly, areSubmittedVersionsAvailable = true)),
            ArgumentMatchers.eq(false))(any(), any())

        result.userAnswers.isDefined mustBe true

        jsonCaptorForSaveAndLock.getValue mustBe expectedUAToBePassedToSaveAndLock.data
      }
    }

    "when no user answers, no version, AFTSummaryPage and previous URL is NOT within AFT" must {
      "create data request with details" in {

        val request: IdentifierRequest[AnyContentAsEmpty.type] =
          IdentifierRequest("id", fakeRequest, Some(psaIdInstance))

        val multipleVersions = Seq[AFTOverview](
          AFTOverview(
            periodStartDate = LocalDate.of(2020, 4, 1),
            periodEndDate = LocalDate.of(2020, 6, 28),
            tpssReportPresent = false,
            Some(AFTOverviewVersion(
              numberOfVersions = 2,
              submittedVersionAvailable = true,
              compiledVersionAvailable = true
            )))
        )
        when(mockAftConnector.getAftOverview(any(), any(), any())(any(), any()))
          .thenReturn(Future.successful(multipleVersions))

        when(mockAftConnector.getAFTDetails(any(), any(), any())(any(), any()))
          .thenReturn(Future.successful(userAnswersWithSchemeName.data))

        when(mockUserAnswersCacheConnector.fetch(any())(any(), any()))
          .thenReturn(Future.successful(None))

        val jsonCaptorForSaveAndLock = ArgumentCaptor.forClass(classOf[JsObject])

        when(mockUserAnswersCacheConnector.saveAndLock(any(), jsonCaptorForSaveAndLock.capture(), any(), any())(any(), any()))
          .thenReturn(Future.successful(userAnswersWithSchemeName.data))

        DateHelper.setDate(Some(LocalDate.of(2020, 7, 1)))

        val result = Await.result(
          requestCreationService
            .retrieveAndCreateRequest(srn, QUARTER_START_DATE, 1, accessType, Some(AFTSummaryPage))(request, implicitly, implicitly),
          Duration.Inf
        )

        result.userAnswers.isDefined mustBe true

        jsonCaptorForSaveAndLock.getValue mustBe expectedUAToBePassedToSaveAndLock.data
      }
    }

    "when no user answers, no version and ChargeTypePage" must {
      "create non-empty data request" in {

        val multipleVersions = Seq[AFTOverview](
          AFTOverview(
            periodStartDate = LocalDate.of(2020, 4, 1),
            periodEndDate = LocalDate.of(2020, 6, 28),
            tpssReportPresent = false,
            Some(AFTOverviewVersion(
              numberOfVersions = 2,
              submittedVersionAvailable = true,
              compiledVersionAvailable = true
            )))
        )
        when(mockAftConnector.getAFTDetails(any(), any(), any())(any(), any()))
          .thenReturn(Future.successful(userAnswersWithSchemeName.data))

        when(mockAftConnector.getAftOverview(any(), any(), any())(any(), any()))
          .thenReturn(Future.successful(multipleVersions))

        when(mockUserAnswersCacheConnector.fetch(any())(any(), any()))
          .thenReturn(Future.successful(None))

        val jsonCaptorForSaveAndLock = ArgumentCaptor.forClass(classOf[JsObject])

        when(mockUserAnswersCacheConnector.saveAndLock(any(), jsonCaptorForSaveAndLock.capture(), any(), any())(any(), any()))
          .thenReturn(Future.successful(userAnswersWithSchemeName.data))

        DateHelper.setDate(Some(LocalDate.of(2020, 7, 1)))

        val result = Await.result(
          requestCreationService
            .retrieveAndCreateRequest(srn, QUARTER_START_DATE, 1, accessType, Some(ChargeTypePage))(request, implicitly, implicitly),
          Duration.Inf
        )

        result.userAnswers.isDefined mustBe true

        jsonCaptorForSaveAndLock.getValue mustBe expectedUAToBePassedToSaveAndLock.data
      }
    }
  }


}
