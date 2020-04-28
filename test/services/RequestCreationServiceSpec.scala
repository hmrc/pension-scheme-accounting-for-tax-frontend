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
import connectors.AFTConnector
import connectors.MinimalPsaConnector
import connectors.cache.UserAnswersCacheConnector
import data.SampleData
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.mockito.MockitoSugar
import uk.gov.hmrc.domain.PsaId
import SampleData._
import config.FrontendAppConfig
import models.AccessMode
import models.SchemeDetails
import models.SessionAccessData
import models.SessionData
import models.UserAnswers
import models.requests.OptionalDataRequest
import org.scalatest.BeforeAndAfterEach
import play.api.mvc.AnyContent
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import org.mockito.Matchers._
import org.mockito.Mockito.{reset, when}
import org.scalatest.AsyncWordSpec
import org.scalatest.MustMatchers
import play.api.libs.json.JsObject
import play.api.libs.json.JsString
import play.api.libs.json.Json

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class RequestCreationServiceSpec extends SpecBase  with MustMatchers with MockitoSugar with ScalaFutures with BeforeAndAfterEach {
  private val mockAftConnector: AFTConnector = mock[AFTConnector]
  private val mockUserAnswersCacheConnector: UserAnswersCacheConnector = mock[UserAnswersCacheConnector]
  private val mockSchemeService: SchemeService = mock[SchemeService]
  private val mockMinimalPsaConnector: MinimalPsaConnector = mock[MinimalPsaConnector]
  private val mockAppConfig: FrontendAppConfig = mock[FrontendAppConfig]
  private implicit val req: FakeRequest[AnyContentAsEmpty.type] = FakeRequest("", "")
  private val psaIdInstance = PsaId(psaId)

  private val sessionId = "???"
  private val internalId = s"$srn$startDate"

  private val jsObject = Json.obj( "one" -> "two")
  private val optionUA = Some(UserAnswers(jsObject))
  private val name = None
  private val sessionAccessData = SessionAccessData(version = 1, accessMode = AccessMode.PageAccessModeViewOnly)
  private val optionSD = Some(SessionData(sessionId, name, sessionAccessData))

  private val optionVersion = Some("1")

  private val schemeStatus = "test status"

  private val schemeDetails = SchemeDetails(schemeName, pstr, schemeStatus)

  private val requestCreationService = new RequestCreationService(mockAftConnector, mockUserAnswersCacheConnector, mockSchemeService, mockMinimalPsaConnector, mockAppConfig)

  override def beforeEach(): Unit = {
    reset(mockAftConnector, mockUserAnswersCacheConnector, mockSchemeService, mockMinimalPsaConnector, mockAppConfig)
    when(mockUserAnswersCacheConnector.fetch(any())(any(),any())).thenReturn(Future.successful(Some(jsObject)))
    when(mockUserAnswersCacheConnector.getSessionData(any())(any(),any())).thenReturn(Future.successful(optionSD))
    when(mockSchemeService.retrieveSchemeDetails(any(), any())(any(), any())).thenReturn(Future.successful(schemeDetails))
    when(mockAppConfig.overviewApiEnablementDate).thenReturn("2020-07-01")
  }

  "createRequest" must {
    "create a request with  both user answers and session data" in {
      whenReady(requestCreationService.createRequest[AnyContent](psaIdInstance, srn, startDate)) { result =>
        val expectedResult = OptionalDataRequest(req, internalId, psaIdInstance, optionUA, optionSD)
        result mustBe expectedResult
      }
    }

    "create a request with no user answers but session data" in {
      when(mockUserAnswersCacheConnector.fetch(any())(any(),any())).thenReturn(Future.successful(None))
      whenReady(requestCreationService.createRequest[AnyContent](psaIdInstance, srn, startDate)) { result =>
        val expectedResult = OptionalDataRequest(req, internalId, psaIdInstance, None, optionSD)
        result mustBe expectedResult
      }
    }
  }

  //"retrieveAndCreateRequest" must {
  //  "create a request" when {
  //    "??? " in {
  //      whenReady(requestCreationService.retrieveAndCreateRequest[AnyContent](psaIdInstance, srn, startDate, optionVersion)) { result =>
  //        val expectedResult = OptionalDataRequest(req, internalId, psaIdInstance, optionUA, optionSD)
  //        result mustBe expectedResult
  //      }
  //    }
  //
  //  }
  //}

  /*

  "retrieveAFTRequiredDetails" when {
    "no version is given and there are no versions in AFT" must {
      "NOT call get AFT details and " +
        "retrieve the suspended flag from DES and " +
        "set the IsNewReturn flag, and " +
        "retrieve and the quarter, status, scheme name and pstr and " +
        "save all of these with a lock" in {
        when(mockAFTConnector.getListOfVersions(any(), any())(any(), any())).thenReturn(Future.successful(Seq[AFTVersion]()))

        whenReady(aftService.retrieveAFTRequiredDetails(srn, QUARTER_START_DATE, None)(implicitly, implicitly,
          optionalDataRequest(viewOnly = false))) { case (resultScheme, _) =>
          verify(mockAFTConnector, times(1)).getListOfVersions(any(), any())(any(), any())

          verify(mockAFTConnector, never()).getAFTDetails(any(), any(), any())(any(), any())

          verify(mockSchemeService, times(1)).retrieveSchemeDetails(Matchers.eq(psaId.id), Matchers.eq(srn))(any(), any())
          resultScheme mustBe schemeDetails

          verify(mockMinimalPsaConnector, times(1)).getMinimalPsaDetails(Matchers.eq(psaId.id))(any(), any())

          verify(mockUserAnswersCacheConnector, never()).save(any(), any())(any(), any())
          val expectedUAAfterSave = userAnswersWithSchemeName
            .setOrException(IsPsaSuspendedQuery, value = false)
            .setOrException(PSAEmailQuery, value = email)
            .setOrException(IsNewReturn, value = true)
            .setOrException(AFTStatusQuery, value = aftStatus)
            .setOrException(SchemeStatusQuery, Open)
              .setOrException(QuarterPage, Quarter(QUARTER_START_DATE, QUARTER_END_DATE))
          verify(mockUserAnswersCacheConnector, times(1)).saveAndLock(any(), Matchers.eq(expectedUAAfterSave.data))(any(), any())
        }
      }
    }

    "no version is given and there ARE versions in AFT and suspended flag is not in user answers" must {
      "NOT call get AFT details and " +
        "retrieve the suspended flag from DES and " +
        "NOT set the IsNewReturn flag and " +
        "NOT retrieve the quarter, status, scheme name or pstr and" +
        "save with a lock" in {
        when(mockAFTConnector.getListOfVersions(any(), any())(any(), any())).thenReturn(Future.successful(Seq[AFTVersion](AFTVersion(1, LocalDate.now()))))

        whenReady(aftService.retrieveAFTRequiredDetails(srn, QUARTER_START_DATE, None)) { case (resultScheme, _) =>
          verify(mockAFTConnector, times(1)).getListOfVersions(any(), any())(any(), any())

          verify(mockAFTConnector, never()).getAFTDetails(any(), any(), any())(any(), any())

          resultScheme mustBe schemeDetails
          verify(mockSchemeService, times(1)).retrieveSchemeDetails(Matchers.eq(psaId.id), Matchers.eq(srn))(any(), any())

          verify(mockMinimalPsaConnector, times(1)).getMinimalPsaDetails(Matchers.eq(psaId.id))(any(), any())

          verify(mockUserAnswersCacheConnector, never()).save(any(), any())(any(), any())
          val expectedUAAfterSave = emptyUserAnswers.setOrException(IsPsaSuspendedQuery, value = false).
            setOrException(PSAEmailQuery, email).setOrException(SchemeStatusQuery, Open)
          verify(mockUserAnswersCacheConnector, times(1)).saveAndLock(any(), Matchers.eq(expectedUAAfterSave.data))(any(), any())
        }
      }
    }

    "a version is given" must {
      "call get AFT details and " +
        "retrieve and the suspended flag from DES and " +
        "NOT set the IsNewReturn flag and " +
        "NOT retrieve the quarter, status, scheme name or pstr (since these are retrieved by get aft details) and " +
        "save with a lock" in {

        when(mockAFTConnector.getAFTDetails(any(), any(), any())(any(), any())).thenReturn(Future.successful(emptyUserAnswers.data))

        whenReady(aftService.retrieveAFTRequiredDetails(srn, QUARTER_START_DATE, Some(version))(implicitly, implicitly, optionalDataRequest(viewOnly = false))) { case (resultScheme, _) =>
          verify(mockAFTConnector, times(1)).getAFTDetails(any(), any(), any())(any(), any())

          resultScheme mustBe schemeDetails
          verify(mockSchemeService, times(1)).retrieveSchemeDetails(Matchers.eq(psaId.id), Matchers.eq(srn))(any(), any())

          verify(mockMinimalPsaConnector, times(1)).getMinimalPsaDetails(Matchers.eq(psaId.id))(any(), any())

          verify(mockUserAnswersCacheConnector, never()).save(any(), any())(any(), any())
          val expectedUAAfterSave = emptyUserAnswers.setOrException(IsPsaSuspendedQuery, value = false).
            setOrException(PSAEmailQuery, email).setOrException(SchemeStatusQuery, Open)
          verify(mockUserAnswersCacheConnector, times(1)).saveAndLock(any(), Matchers.eq(expectedUAAfterSave.data))(any(), any())
        }
      }
    }

    "user is suspended" must {
      "NOT save with a lock" in {
        when(mockMinimalPsaConnector.getMinimalPsaDetails(any())(any(), any())).thenReturn(Future.successful(MinimalPSA(email, isPsaSuspended = true)))
        when(mockAFTConnector.getListOfVersions(any(), any())(any(), any())).thenReturn(Future.successful(Seq[AFTVersion](AFTVersion(1, LocalDate.now()))))

        whenReady(aftService.retrieveAFTRequiredDetails(srn, QUARTER_START_DATE, None)) { case (_, _) =>
          verify(mockUserAnswersCacheConnector, times(1)).save(any(), any())(any(), any())
        }
      }
    }

    "viewOnly flag in the request is set to true" must {
      "NOT call saveAndLock but should call save" in {
        val uaToSave = userAnswersWithSchemeName
          .setOrException(IsPsaSuspendedQuery, value = false).setOrException(PSAEmailQuery, email).setOrException(SchemeStatusQuery, Open)
        when(mockAFTConnector.getAFTDetails(any(), any(), any())(any(), any())).thenReturn(Future.successful(userAnswersWithSchemeName.data))

        whenReady(aftService.retrieveAFTRequiredDetails(srn, QUARTER_START_DATE, Some(version))
        (implicitly, implicitly, optionalDataRequest(viewOnly = true))) { case (resultScheme, _) =>
          resultScheme mustBe schemeDetails
          verify(mockUserAnswersCacheConnector, never()).saveAndLock(any(), any())(any(), any())
          verify(mockUserAnswersCacheConnector, times(1)).save(any(), Matchers.eq(uaToSave.data))(any(), any())

        }
      }
    }

    "viewOnly flag in the request is set to false" must {
      "call saveAndLock but should NOT call save instead of save with lock" in {
        when(mockAFTConnector.getAFTDetails(any(), any(), any())(any(), any()))
          .thenReturn(Future.successful(userAnswersWithSchemeNamePstrQuarter.data))

        whenReady(aftService.retrieveAFTRequiredDetails(srn, QUARTER_START_DATE, Some(version))
        (implicitly, implicitly, optionalDataRequest(viewOnly = false))) { case (resultScheme, _) =>
          resultScheme mustBe schemeDetails
          verify(mockUserAnswersCacheConnector, times(1)).saveAndLock(any(), any())(any(), any())
          verify(mockUserAnswersCacheConnector, never()).save(any(), any())(any(), any())
        }
      }
    }
  }
   */


}
