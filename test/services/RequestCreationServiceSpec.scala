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

import config.FrontendAppConfig
import models.AccessMode
import models.SchemeDetails
import models.SessionAccessData
import models.SessionData
import org.mockito.Mockito.reset
import org.mockito.Mockito.when
import org.scalatest.MustMatchers

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import java.time.LocalDate

import base.SpecBase
import connectors.cache.UserAnswersCacheConnector
import connectors.AFTConnector
import connectors.MinimalPsaConnector
import connectors.MinimalPsaConnector.MinimalPSA
import models.requests.OptionalDataRequest
import models.AFTVersion
import models.UserAnswers
import org.mockito.Matchers.any
import org.mockito.Mockito._
import org.mockito.Matchers
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.mockito.MockitoSugar
import play.api.libs.json.Json
import play.api.mvc.AnyContentAsEmpty
import uk.gov.hmrc.domain.PsaId
import utils.AFTConstants._
import data.SampleData._
import models.AFTOverview
import models.Quarter
import models.SchemeStatus.Open
import pages.AFTStatusQuery
import pages.IsPsaSuspendedQuery
import pages.PSAEmailQuery
import pages.PSANameQuery
import pages.QuarterPage
import pages.SchemeStatusQuery
import utils.DateHelper

import scala.concurrent.Await
import scala.concurrent.duration.Duration

class RequestCreationServiceSpec extends SpecBase with MustMatchers with MockitoSugar with ScalaFutures with BeforeAndAfterEach {

  private val mockAftConnector: AFTConnector = mock[AFTConnector]
  private val mockUserAnswersCacheConnector: UserAnswersCacheConnector = mock[UserAnswersCacheConnector]
  private val mockSchemeService: SchemeService = mock[SchemeService]
  private val mockMinimalPsaConnector: MinimalPsaConnector = mock[MinimalPsaConnector]
  private val mockAppConfig: FrontendAppConfig = mock[FrontendAppConfig]
  private val psaIdInstance = PsaId(psaId)

  private val sessionId = "???"
  private val internalId = s"$srn$startDate"

  private val jsObject = Json.obj("one" -> "two")
  private val optionUA = Some(UserAnswers(jsObject))
  private val nameLockedBy = None
  private val sessionAccessDataCompile = SessionAccessData(version = 1, accessMode = AccessMode.PageAccessModeCompile)
  private val sessionAccessDataViewOnly = SessionAccessData(version = 1, accessMode = AccessMode.PageAccessModeViewOnly)
  private val sd = SessionData(sessionId, nameLockedBy, sessionAccessDataCompile)
  private val sdViewOnly = SessionData(sessionId, nameLockedBy, sessionAccessDataViewOnly)

  private val emptyUserAnswers = UserAnswers()

  private val aftStatus = "Compiled"

  private val request: OptionalDataRequest[AnyContentAsEmpty.type] =
    OptionalDataRequest(fakeRequest, internalId, psaIdInstance, Some(emptyUserAnswers), sd)

  private def optionalDataRequest(viewOnly: Boolean): OptionalDataRequest[_] = OptionalDataRequest(
    fakeRequest,
    "",
    psaIdInstance,
    Some(UserAnswers()),
    if (viewOnly) sd else sd
  )

  private val optionVersion = Some("1")

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
    when(mockUserAnswersCacheConnector.lockedBy(any())(any(), any())).thenReturn(Future.successful(None))
    when(mockAppConfig.overviewApiEnablementDate).thenReturn("2020-07-01")
    when(mockUserAnswersCacheConnector.save(any(), any(), any(), any())(any(), any())).thenReturn(Future.successful(jsObject))
    when(mockAftConnector.getListOfVersions(any(), any())(any(), any())).thenReturn(Future.successful(Seq[AFTVersion]()))
    when(mockUserAnswersCacheConnector.getSessionData(any())(any(), any())).thenReturn(Future.successful(Some(sd)))

  }

  "createRequest" must {
    //"create a request with user answers and session data" in {
    //  whenReady(requestCreationService.createRequest(psaIdInstance, srn, startDate)(request, implicitly, implicitly)) { result =>
    //    val expectedResult = OptionalDataRequest(request, internalId, psaIdInstance, optionUA, sd)
    //    result mustBe expectedResult
    //  }
    //}
  }

  "retrieveAndCreateRequest" when {
    "no version is given and there are no versions in AFT and suspended flag is in user answers" must {
      "NOT call get AFT details and " +
        "NOT retrieve the suspended flag from DES and " +
        "retrieve and the quarter, status, scheme name and pstr and " +
        "save all of these with a lock" in {
        DateHelper.setDate(Some(LocalDate.of(2020, 4, 1)))

        Await.result(
          requestCreationService
            .retrieveAndCreateRequest(psaIdInstance, srn, QUARTER_START_DATE, None)(request, implicitly, implicitly),
          Duration.Inf
        )

        verify(mockAftConnector, times(2)).getListOfVersions(any(), any())(any(), any())
        verify(mockAftConnector, never()).getAFTDetails(any(), any(), any())(any(), any())
        verify(mockSchemeService, times(1)).retrieveSchemeDetails(Matchers.eq(psaId), Matchers.eq(srn))(any(), any())

        val expectedUAAfterSave = userAnswersWithSchemeName
          .setOrException(IsPsaSuspendedQuery, value = false)
          .setOrException(PSAEmailQuery, value = email)
          .setOrException(AFTStatusQuery, value = aftStatus)
          .setOrException(PSANameQuery, value = psaName)
          .setOrException(SchemeStatusQuery, Open)
          .setOrException(QuarterPage, Quarter(QUARTER_START_DATE, QUARTER_END_DATE))
        verify(mockUserAnswersCacheConnector, times(1))
          .save(any(), Matchers.eq(expectedUAAfterSave.data), any(), Matchers.eq(true))(any(), any())
      }
    }

    "no version is given and there are ARE versions in AFT and suspended flag is not in user answers" must {
      "NOT call get AFT details and " +
        "retrieve the suspended flag from DES and " +
        "retrieve scheme name and pstr and " +
        "save all of these with a lock" in {

        when(mockAftConnector.getListOfVersions(any(), any())(any(), any())).thenReturn(Future.successful(seqAFTVersion))

        DateHelper.setDate(Some(LocalDate.of(2020, 4, 1)))

        Await.result(
          requestCreationService
            .retrieveAndCreateRequest(psaIdInstance, srn, QUARTER_START_DATE, None)(request, implicitly, implicitly),
          Duration.Inf
        )

        verify(mockAftConnector, times(2)).getListOfVersions(any(), any())(any(), any())
        verify(mockAftConnector, never()).getAFTDetails(any(), any(), any())(any(), any())
        verify(mockSchemeService, times(1)).retrieveSchemeDetails(Matchers.eq(psaId), Matchers.eq(srn))(any(), any())
        verify(mockMinimalPsaConnector, times(1)).getMinimalPsaDetails(Matchers.eq(psaId))(any(), any())

        val expectedUAAfterSave = userAnswersWithSchemeName
          .setOrException(IsPsaSuspendedQuery, value = false)
          .setOrException(PSAEmailQuery, value = email)
          .setOrException(PSANameQuery, value = psaName)
          .setOrException(SchemeStatusQuery, Open)
        verify(mockUserAnswersCacheConnector, times(1))
          .save(any(), Matchers.eq(expectedUAAfterSave.data), any(), Matchers.eq(true))(any(), any())
      }
    }

    "a version is given and there are no versions in AFT and suspended flag is in user answers" must {
      "call get AFT details and " +
        "retrieve the suspended flag from DES and " +
        "NOT retrieve the quarter, status, scheme name or pstr (since these are retrieved by get aft details) and " +
        "save with a lock" in {
        DateHelper.setDate(Some(LocalDate.of(2020, 4, 1)))
        when(mockAftConnector.getAFTDetails(any(), any(), any())(any(), any())).thenReturn(Future.successful(emptyUserAnswers.data))
        Await.result(
          requestCreationService
            .retrieveAndCreateRequest(psaIdInstance, srn, QUARTER_START_DATE, Some(version))(request, implicitly, implicitly),
          Duration.Inf
        )

        verify(mockAftConnector, times(1)).getAFTDetails(any(), any(), any())(any(), any())

        verify(mockSchemeService, times(1)).retrieveSchemeDetails(Matchers.eq(psaId), Matchers.eq(srn))(any(), any())

        val expectedUAAfterSave = emptyUserAnswers
          .setOrException(IsPsaSuspendedQuery, value = false)
          .setOrException(PSAEmailQuery, email)
          .setOrException(PSANameQuery, value = psaName)
          .setOrException(SchemeStatusQuery, Open)

        verify(mockUserAnswersCacheConnector, times(1))
          .save(any(), Matchers.eq(expectedUAAfterSave.data), any(), Matchers.eq(true))(any(), any())
      }
    }

    "user is suspended" must {
      "NOT save with a lock" in {
        when(mockMinimalPsaConnector.getMinimalPsaDetails(any())(any(), any()))
          .thenReturn(Future.successful(MinimalPSA(email, isPsaSuspended = true, None, None)))
        when(mockAftConnector.getListOfVersions(any(), any())(any(), any()))
          .thenReturn(Future.successful(Seq[AFTVersion](AFTVersion(1, LocalDate.now()))))

        Await.result(
          requestCreationService
            .retrieveAndCreateRequest(psaIdInstance, srn, QUARTER_START_DATE, None)(request, implicitly, implicitly),
          Duration.Inf
        )

        verify(mockUserAnswersCacheConnector, times(1)).save(any(), any(), any(), Matchers.eq(false))(any(), any())
      }
    }

    "aft return is locked to another user" must {
      "NOT save with a lock" in {
        when(mockMinimalPsaConnector.getMinimalPsaDetails(any())(any(), any()))
          .thenReturn(Future.successful(MinimalPSA(email, isPsaSuspended = false, None, None)))
        when(mockAftConnector.getListOfVersions(any(), any())(any(), any()))
          .thenReturn(Future.successful(Seq[AFTVersion](AFTVersion(1, LocalDate.now()))))
        when(mockUserAnswersCacheConnector.lockedBy(any())(any(), any())).thenReturn(Future.successful(lockedByName))

        Await.result(
          requestCreationService
            .retrieveAndCreateRequest(psaIdInstance, srn, QUARTER_START_DATE, None)(request, implicitly, implicitly),
          Duration.Inf
        )

        verify(mockUserAnswersCacheConnector, times(1)).save(any(), any(), any(), Matchers.eq(false))(any(), any())
      }
    }

    "requested version is less than latest and date is after 1st July" must {
      "NOT save with a lock but create session access data with viewonly" in {
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

        when(mockAftConnector.getAFTDetails(any(), any(), any())(any(), any())).thenReturn(Future.successful(userAnswersWithSchemeName.data))

        DateHelper.setDate(Some(LocalDate.of(2020, 7, 1)))

        Await.result(
          requestCreationService
            .retrieveAndCreateRequest(psaIdInstance, srn, QUARTER_START_DATE, Some("1"))(request, implicitly, implicitly),
          Duration.Inf
        )

        val sessionAccessData = SessionAccessData(version = 1, accessMode = AccessMode.PageAccessModeViewOnly)

        verify(mockUserAnswersCacheConnector, times(1))
          .save(any(), any(), Matchers.eq(Option(sessionAccessData)), Matchers.eq(false))(any(), any())
      }
    }

    "requested version is latest and date is after 1st July" must {
      "NOT save with a lock but create session access data with compile" in {
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

        when(mockAftConnector.getAFTDetails(any(), any(), any())(any(), any())).thenReturn(Future.successful(userAnswersWithSchemeName.data))

        DateHelper.setDate(Some(LocalDate.of(2020, 7, 1)))

        Await.result(
          requestCreationService
            .retrieveAndCreateRequest(psaIdInstance, srn, QUARTER_START_DATE, Some("2"))(request, implicitly, implicitly),
          Duration.Inf
        )

        val sessionAccessData = SessionAccessData(version = 2, accessMode = AccessMode.PageAccessModeCompile)

        verify(mockUserAnswersCacheConnector, times(1))
          .save(any(), any(), Matchers.eq(Option(sessionAccessData)), Matchers.eq(true))(any(), any())
      }
    }
  }
}
