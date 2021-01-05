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

import java.time.LocalDate

import base.SpecBase
import config.FrontendAppConfig
import connectors.{AFTConnector, MinimalConnector}
import connectors.MinimalConnector.MinimalDetails
import connectors.cache.UserAnswersCacheConnector
import data.SampleData._
import models.{AFTOverview, SessionAccessData, AFTVersion, SessionData, SchemeDetails, AccessMode}
import models.requests.IdentifierRequest
import org.mockito.Matchers
import org.mockito.Matchers.any
import org.mockito.Mockito.{reset, when, _}
import org.scalatest.{MustMatchers, BeforeAndAfterEach}
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.mockito.MockitoSugar
import pages.{AFTSummaryPage, ChargeTypePage}
import play.api.mvc.AnyContentAsEmpty
import uk.gov.hmrc.domain.PsaId
import utils.AFTConstants._
import utils.DateHelper

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Await, Future}
import scala.concurrent.duration.Duration

class RequestCreationServiceSpec extends SpecBase with MustMatchers with MockitoSugar with ScalaFutures with BeforeAndAfterEach {
  private val mockAftConnector: AFTConnector = mock[AFTConnector]
  private val mockUserAnswersCacheConnector: UserAnswersCacheConnector = mock[UserAnswersCacheConnector]
  private val mockSchemeService: SchemeService = mock[SchemeService]
  private val mockMinimalPsaConnector: MinimalConnector = mock[MinimalConnector]
  private val mockAppConfig: FrontendAppConfig = mock[FrontendAppConfig]

  private val psaIdInstance = PsaId(psaId)

  private val sessionId = "session id"
  private val internalId = s"$srn$startDate"

  private val nameLockedBy = None
  private val sessionAccessDataCompile = SessionAccessData(version = 1, accessMode = AccessMode.PageAccessModeCompile, areSubmittedVersionsAvailable = false)
  private val sd = SessionData(sessionId, nameLockedBy, sessionAccessDataCompile)

  private val request: IdentifierRequest[AnyContentAsEmpty.type] =
    IdentifierRequest(fakeRequest, Some(psaIdInstance))

  private val schemeStatus = "Open"

  private val schemeDetails = SchemeDetails(schemeName, pstr, schemeStatus, None)

  private def requestCreationService =
    new RequestCreationService(mockAftConnector, mockUserAnswersCacheConnector, mockSchemeService, mockMinimalPsaConnector)

  private val email = "test@test.com"

  override def beforeEach(): Unit = {
    reset(mockAftConnector, mockUserAnswersCacheConnector, mockSchemeService, mockMinimalPsaConnector, mockAppConfig)

    when(mockUserAnswersCacheConnector.fetch(any())(any(), any())).thenReturn(Future.successful(Some(userAnswersWithSchemeName.data)))
    when(mockSchemeService.retrieveSchemeDetails(any(),any(), any())(any(), any())).thenReturn(Future.successful(schemeDetails))
    when(mockMinimalPsaConnector.getMinimalDetails(any(), any(), any()))
      .thenReturn(Future.successful(MinimalDetails(email, isPsaSuspended = false, None, None)))
    when(mockUserAnswersCacheConnector.lockDetail(any(), any())(any(), any())).thenReturn(Future.successful(None))
    when(mockUserAnswersCacheConnector.saveAndLock(any(), any(), any(), any())(any(), any())).thenReturn(Future.successful(userAnswersWithSchemeName.data))
    when(mockAftConnector.getListOfVersions(any(), any())(any(), any())).thenReturn(Future.successful(Seq[AFTVersion]()))
    when(mockUserAnswersCacheConnector.getSessionData(any())(any(), any())).thenReturn(Future.successful(Some(sd)))
  }

  "retrieveAndCreateRequest" when {


    "requested version is less than latest and date is on or after 21st July" must {
      "NOT save with a lock and create session access data with viewonly page access mode" in {
        val multipleVersions = Seq[AFTOverview](
          AFTOverview(
            periodStartDate = LocalDate.of(2020, 4, 1),
            periodEndDate = LocalDate.of(2020, 6, 28),
            numberOfVersions = 2,
            submittedVersionAvailable = true,
            compiledVersionAvailable = true
          )
        )

        when(mockAftConnector.getAftOverview(any(), any(), any())(any(), any()))
          .thenReturn(Future.successful(multipleVersions))

        when(mockAftConnector.getAFTDetails(any(), any(), any())(any(), any()))
          .thenReturn(Future.successful(userAnswersWithSchemeName.data))

        DateHelper.setDate(Some(LocalDate.of(2020, 7, 21)))

        Await.result(
          requestCreationService
            .retrieveAndCreateRequest(srn, QUARTER_START_DATE, 1, accessType, None)(request, implicitly, implicitly),
          Duration.Inf
        )

        verify(mockUserAnswersCacheConnector, times(1))
          .saveAndLock(any(),
            any(),
            Matchers.eq(SessionAccessData(version = 1, accessMode = AccessMode.PageAccessModeViewOnly, areSubmittedVersionsAvailable = true)),
            Matchers.eq(false))(any(), any())
      }
    }

    "when no user answers, no version, AFTSummaryPage and previous URL is within AFT" must {
      "create empty data request" in {

        val referer = Seq("Referer" -> "manage-pension-scheme-accounting-for-tax")

        val request: IdentifierRequest[AnyContentAsEmpty.type] =
          IdentifierRequest(fakeRequest.withHeaders(referer :_*), Some(psaIdInstance))

        when(mockUserAnswersCacheConnector.fetch(any())(any(), any()))
          .thenReturn(Future.successful(None))

        DateHelper.setDate(Some(LocalDate.of(2020, 7, 1)))

        val result = Await.result(
          requestCreationService
            .retrieveAndCreateRequest(srn, QUARTER_START_DATE, 1, accessType, Some(AFTSummaryPage))(request, implicitly, implicitly),
          Duration.Inf
        )

        result.userAnswers mustBe None
      }
    }

    "when no user answers, no version, AFTSummaryPage and previous URL is NOT within AFT" must {
      "create data request with details" in {

        val request: IdentifierRequest[AnyContentAsEmpty.type] =
          IdentifierRequest(fakeRequest, Some(psaIdInstance))

        val multipleVersions = Seq[AFTOverview](
          AFTOverview(
            periodStartDate = LocalDate.of(2020, 4, 1),
            periodEndDate = LocalDate.of(2020, 6, 28),
            numberOfVersions = 2,
            submittedVersionAvailable = true,
            compiledVersionAvailable = true
          )
        )
        when(mockAftConnector.getAftOverview(any(), any(), any())(any(), any()))
          .thenReturn(Future.successful(multipleVersions))

        when(mockAftConnector.getAFTDetails(any(), any(), any())(any(), any()))
          .thenReturn(Future.successful(userAnswersWithSchemeName.data))

        when(mockUserAnswersCacheConnector.fetch(any())(any(), any()))
          .thenReturn(Future.successful(None))

        DateHelper.setDate(Some(LocalDate.of(2020, 7, 1)))

        val result = Await.result(
          requestCreationService
            .retrieveAndCreateRequest(srn, QUARTER_START_DATE, 1, accessType, Some(AFTSummaryPage))(request, implicitly, implicitly),
          Duration.Inf
        )

        result.userAnswers.isDefined mustBe true
      }
    }

    "when no user answers, no version and ChargeTypePage" must {
      "create non-empty data request" in {

        val multipleVersions = Seq[AFTOverview](
          AFTOverview(
            periodStartDate = LocalDate.of(2020, 4, 1),
            periodEndDate = LocalDate.of(2020, 6, 28),
            numberOfVersions = 2,
            submittedVersionAvailable = true,
            compiledVersionAvailable = true
          )
        )
        when(mockAftConnector.getAFTDetails(any(), any(), any())(any(), any()))
          .thenReturn(Future.successful(userAnswersWithSchemeName.data))

        when(mockAftConnector.getAftOverview(any(), any(), any())(any(), any()))
          .thenReturn(Future.successful(multipleVersions))

        when(mockUserAnswersCacheConnector.fetch(any())(any(), any()))
          .thenReturn(Future.successful(None))

        DateHelper.setDate(Some(LocalDate.of(2020, 7, 1)))

        val result = Await.result(
          requestCreationService
            .retrieveAndCreateRequest(srn, QUARTER_START_DATE, 1, accessType, Some(ChargeTypePage))(request, implicitly, implicitly),
          Duration.Inf
        )

        result.userAnswers.isDefined mustBe true
      }
    }
  }


}
