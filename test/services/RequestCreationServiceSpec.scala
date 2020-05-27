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

import java.time.LocalDate

import base.SpecBase
import config.FrontendAppConfig
import connectors.AFTConnector
import connectors.MinimalPsaConnector
import connectors.MinimalPsaConnector.MinimalPSA
import connectors.cache.UserAnswersCacheConnector
import models.AccessMode
import models.SchemeDetails
import models.SessionAccessData
import models.SessionData
import org.mockito.Matchers
import models.requests.OptionalDataRequest
import models.AFTVersion
import models.UserAnswers
import org.mockito.Matchers.any
import org.mockito.Mockito.reset
import org.mockito.Mockito.when
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.MustMatchers
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.mockito.MockitoSugar
import play.api.mvc.AnyContentAsEmpty
import uk.gov.hmrc.domain.PsaId
import utils.AFTConstants._
import data.SampleData._
import models.AFTOverview
import pages.AFTSummaryPage
import pages.ChargeTypePage
import utils.DateHelper

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration.Duration

class RequestCreationServiceSpec extends SpecBase with MustMatchers with MockitoSugar with ScalaFutures with BeforeAndAfterEach {
  private val mockAftConnector: AFTConnector = mock[AFTConnector]
  private val mockUserAnswersCacheConnector: UserAnswersCacheConnector = mock[UserAnswersCacheConnector]
  private val mockSchemeService: SchemeService = mock[SchemeService]
  private val mockMinimalPsaConnector: MinimalPsaConnector = mock[MinimalPsaConnector]
  private val mockAppConfig: FrontendAppConfig = mock[FrontendAppConfig]

  private val psaIdInstance = PsaId(psaId)

  private val sessionId = "session id"
  private val internalId = s"$srn$startDate"

  private val nameLockedBy = None
  private val sessionAccessDataCompile = SessionAccessData(version = 1, accessMode = AccessMode.PageAccessModeCompile)
  private val sd = SessionData(sessionId, nameLockedBy, sessionAccessDataCompile)

  private val emptyUserAnswers = UserAnswers()

  private val aftStatus = "Compiled"

  private val request: OptionalDataRequest[AnyContentAsEmpty.type] =
    OptionalDataRequest(fakeRequest, internalId, psaIdInstance, Some(emptyUserAnswers), Some(sd))

  private val schemeStatus = "Open"

  private val schemeDetails = SchemeDetails(schemeName, pstr, schemeStatus)

  private def requestCreationService =
    new RequestCreationService(mockAftConnector, mockUserAnswersCacheConnector, mockSchemeService, mockMinimalPsaConnector, mockAppConfig)

  private val email = "test@test.com"
  private val psaName = "Pension Scheme Administrator"

  private val seqAFTVersion = Seq(AFTVersion(1, LocalDate.of(2020, 4, 1)))

  override def beforeEach(): Unit = {
    reset(mockAftConnector, mockUserAnswersCacheConnector, mockSchemeService, mockMinimalPsaConnector, mockAppConfig)

    when(mockUserAnswersCacheConnector.fetch(any())(any(), any())).thenReturn(Future.successful(Some(userAnswersWithSchemeName.data)))
    when(mockSchemeService.retrieveSchemeDetails(any(), any())(any(), any())).thenReturn(Future.successful(schemeDetails))
    when(mockMinimalPsaConnector.getMinimalPsaDetails(any())(any(), any()))
      .thenReturn(Future.successful(MinimalPSA(email, isPsaSuspended = false, None, None)))
    when(mockUserAnswersCacheConnector.lockedBy(any(), any())(any(), any())).thenReturn(Future.successful(None))
    when(mockAppConfig.overviewApiEnablementDate).thenReturn("2020-07-01")
    when(mockUserAnswersCacheConnector.save(any(), any(), any(), any())(any(), any())).thenReturn(Future.successful(userAnswersWithSchemeName.data))
    when(mockAftConnector.getListOfVersions(any(), any())(any(), any())).thenReturn(Future.successful(Seq[AFTVersion]()))
    when(mockUserAnswersCacheConnector.getSessionData(any())(any(), any())).thenReturn(Future.successful(Some(sd)))
  }

  "createRequest" must {
    "create a request with user answers and session data" in {
      val result = Await.result(
        requestCreationService.createRequest(psaIdInstance, srn, startDate)(request, implicitly, implicitly),
        Duration.Inf
      )

      val expectedResult = OptionalDataRequest(request, internalId, psaIdInstance, Some(userAnswersWithSchemeName), Some(sd))
      result mustBe expectedResult
    }
  }

  "retrieveAndCreateRequest" when {


    "requested version is less than latest and date is after 1st July" must {
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

        when(mockAftConnector.getAftOverview(any())(any(), any()))
          .thenReturn(Future.successful(multipleVersions))

        when(mockAftConnector.getAFTDetails(any(), any(), any())(any(), any()))
          .thenReturn(Future.successful(userAnswersWithSchemeName.data))

        DateHelper.setDate(Some(LocalDate.of(2020, 7, 1)))

        Await.result(
          requestCreationService
            .retrieveAndCreateRequest(psaIdInstance, srn, QUARTER_START_DATE, Some("1"), None)(request, implicitly, implicitly),
          Duration.Inf
        )

        verify(mockUserAnswersCacheConnector, times(1))
          .save(any(),
            any(),
            Matchers.eq(Option(SessionAccessData(version = 1, accessMode = AccessMode.PageAccessModeViewOnly))),
            Matchers.eq(false))(any(), any())
      }
    }

    "when no user answers, no version, AFTSummaryPage and previous URL is within AFT" must {
      "create empty data request" in {

        val referer = Seq("Referer" -> "manage-pension-scheme-accounting-for-tax")

        val request: OptionalDataRequest[AnyContentAsEmpty.type] =
        OptionalDataRequest(fakeRequest.withHeaders(referer :_*), internalId, psaIdInstance, Some(emptyUserAnswers), Some(sd))

        when(mockUserAnswersCacheConnector.fetch(any())(any(), any()))
          .thenReturn(Future.successful(None))

        DateHelper.setDate(Some(LocalDate.of(2020, 7, 1)))

        val result = Await.result(
          requestCreationService
            .retrieveAndCreateRequest(psaIdInstance, srn, QUARTER_START_DATE, None, Some(AFTSummaryPage))(request, implicitly, implicitly),
          Duration.Inf
        )

        result.userAnswers mustBe None
      }
    }

    "when no user answers, no version, AFTSummaryPage and previous URL is NOT within AFT" must {
      "create data request with details" in {

        val request: OptionalDataRequest[AnyContentAsEmpty.type] =
          OptionalDataRequest(fakeRequest, internalId, psaIdInstance, Some(emptyUserAnswers), Some(sd))

        val multipleVersions = Seq[AFTOverview](
          AFTOverview(
            periodStartDate = LocalDate.of(2020, 4, 1),
            periodEndDate = LocalDate.of(2020, 6, 28),
            numberOfVersions = 2,
            submittedVersionAvailable = true,
            compiledVersionAvailable = true
          )
        )
        when(mockAftConnector.getAftOverview(any())(any(), any()))
          .thenReturn(Future.successful(multipleVersions))

        when(mockAftConnector.getAFTDetails(any(), any(), any())(any(), any()))
          .thenReturn(Future.successful(userAnswersWithSchemeName.data))

        when(mockUserAnswersCacheConnector.fetch(any())(any(), any()))
          .thenReturn(Future.successful(None))

        DateHelper.setDate(Some(LocalDate.of(2020, 7, 1)))

        val result = Await.result(
          requestCreationService
            .retrieveAndCreateRequest(psaIdInstance, srn, QUARTER_START_DATE, None, Some(AFTSummaryPage))(request, implicitly, implicitly),
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
        when(mockAftConnector.getAftOverview(any())(any(), any()))
          .thenReturn(Future.successful(multipleVersions))

        when(mockUserAnswersCacheConnector.fetch(any())(any(), any()))
          .thenReturn(Future.successful(None))

        DateHelper.setDate(Some(LocalDate.of(2020, 7, 1)))

        val result = Await.result(
          requestCreationService
            .retrieveAndCreateRequest(psaIdInstance, srn, QUARTER_START_DATE, None, Some(ChargeTypePage))(request, implicitly, implicitly),
          Duration.Inf
        )

        result.userAnswers.isDefined mustBe true
      }
    }
  }


}
