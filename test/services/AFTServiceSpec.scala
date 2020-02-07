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
import connectors.cache.UserAnswersCacheConnector
import connectors.{AFTConnector, MinimalPsaConnector}
import data.SampleData
import data.SampleData._
import models.requests.{DataRequest, OptionalDataRequest}
import models.{SchemeDetails, UserAnswers}
import org.mockito.Matchers.any
import org.mockito.Mockito.{reset, times, verify, when}
import org.mockito.{ArgumentCaptor, Matchers}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.mockito.MockitoSugar
import pages.{AFTStatusQuery, IsNewReturn, IsPsaSuspendedQuery}
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.{AnyContentAsEmpty, Result, Results}
import uk.gov.hmrc.domain.PsaId

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class AFTServiceSpec extends SpecBase with ScalaFutures with BeforeAndAfterEach with MockitoSugar with Results {
  private val mockAFTConnector: AFTConnector = mock[AFTConnector]
  private val mockUserAnswersCacheConnector: UserAnswersCacheConnector = mock[UserAnswersCacheConnector]

  private val mockSchemeService: SchemeService = mock[SchemeService]
  private val mockMinimalPsaConnector: MinimalPsaConnector = mock[MinimalPsaConnector]
  private val mockAllowService: AllowAccessService = mock[AllowAccessService]

  private val aftStatus = "Compiled"
  private val psaId = PsaId(SampleData.psaId)
  private val internalId = "internal id"

  private val aftService = new AFTService(mockAFTConnector, mockUserAnswersCacheConnector, mockSchemeService, mockMinimalPsaConnector, mockAllowService)

  implicit val request: OptionalDataRequest[AnyContentAsEmpty.type] = OptionalDataRequest(fakeRequest, internalId, psaId, Some(userAnswersWithSchemeName))

  private def dataRequest(ua: UserAnswers): DataRequest[AnyContentAsEmpty.type] = DataRequest(fakeRequest, "", PsaId(SampleData.psaId), ua)

  override def beforeEach(): Unit = {
    reset(mockAFTConnector, mockUserAnswersCacheConnector, mockSchemeService, mockMinimalPsaConnector, mockAllowService)
    when(mockSchemeService.retrieveSchemeDetails(any(), any())(any(), any())).thenReturn(Future.successful(SampleData.schemeDetails))
    when(mockMinimalPsaConnector.isPsaSuspended(any())(any(), any())).thenReturn(Future.successful(false))
    when(mockAllowService.filterForIllegalPageAccess(any(), any())(any())).thenReturn(Future.successful(None))
    when(mockUserAnswersCacheConnector.save(any(), any())(any(), any())).thenReturn(Future.successful(Json.obj()))
  }

  "fileAFTReturn" must {
    "connect to the aft backend service and then remove the IsNewReturn flag from user answers and save it in the Mongo cache if it is present" in {
      val uaBeforeCalling = userAnswersWithSchemeName.setOrException(IsNewReturn, true)
      when(mockAFTConnector.fileAFTReturn(any(), any())(any(), any()))
        .thenReturn(Future.successful(()))
      when(mockUserAnswersCacheConnector.save(any(), any())(any(), any()))
        .thenReturn(Future.successful(Json.obj()))
      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

      whenReady(aftService.fileAFTReturn(pstr, uaBeforeCalling)(implicitly, implicitly, dataRequest(uaBeforeCalling))) { _ =>
        verify(mockAFTConnector, times(1)).fileAFTReturn(Matchers.eq(pstr), Matchers.eq(uaBeforeCalling))(any(), any())
        verify(mockUserAnswersCacheConnector, times(1)).save(any(), jsonCaptor.capture())(any(), any())
        val uaAfterSave = UserAnswers(jsonCaptor.getValue)
        uaAfterSave.get(IsNewReturn) mustBe None
      }
    }

    "not throw exception if IsNewReturn flag is not present" in {
      val uaBeforeCalling = userAnswersWithSchemeName
      when(mockAFTConnector.fileAFTReturn(any(), any())(any(), any()))
        .thenReturn(Future.successful(()))
      when(mockUserAnswersCacheConnector.save(any(), any())(any(), any()))
        .thenReturn(Future.successful(Json.obj()))
      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

      whenReady(aftService.fileAFTReturn(pstr, uaBeforeCalling)(implicitly, implicitly, dataRequest(uaBeforeCalling))) { _ =>
        verify(mockAFTConnector, times(1)).fileAFTReturn(Matchers.eq(pstr), Matchers.eq(uaBeforeCalling))(any(), any())
        verify(mockUserAnswersCacheConnector, times(1)).save(any(), jsonCaptor.capture())(any(), any())
        val uaAfterSave = UserAnswers(jsonCaptor.getValue)
        uaAfterSave.get(IsNewReturn) mustBe None
      }
    }
  }

  "getAFTDetails" must {
    "connect to the aft backend service with the specified arguments and return what the connector returns" in {
      val startDate = "start date"
      val aftVersion = "aft version"
      val jsonReturnedFromConnector = userAnswersWithSchemeName.data
      when(mockAFTConnector.getAFTDetails(any(), any(), any())(any(), any()))
        .thenReturn(Future.successful(jsonReturnedFromConnector))

      whenReady(aftService.getAFTDetails(pstr, startDate, aftVersion)) { result =>
        result mustBe jsonReturnedFromConnector
        verify(mockAFTConnector, times(1)).getAFTDetails(Matchers.eq(pstr), Matchers.eq(startDate), Matchers.eq(aftVersion))(any(), any())
      }
    }
  }

  "retrieveAFTRequiredDetails" when {
    "no version is given and there are no versions in AFT and suspended flag is not in user answers" must {
      "NOT call get AFT details but SHOULD retrieve the suspended flag from DES and save it and the IsNewReturn flag in Mongo" in {
        val uaToSave = userAnswersWithSchemeName
          .setOrException(IsPsaSuspendedQuery, value = false)
          .setOrException(IsNewReturn, value = true)
          .setOrException(AFTStatusQuery, value = aftStatus)

        when(mockAFTConnector.getListOfVersions(any())(any(), any()))
          .thenReturn(Future.successful(Seq[Int]()))

        whenReady(aftService.retrieveAFTRequiredDetails(srn, None)) { case (resultScheme, _) =>
          resultScheme mustBe schemeDetails
          verify(mockSchemeService, times(1)).retrieveSchemeDetails(Matchers.eq(psaId.id), Matchers.eq(srn))(any(), any())
          verify(mockAFTConnector, times(0)).getAFTDetails(any(), any(), any())(any(), any())
          verify(mockAFTConnector, times(1)).getListOfVersions(any())(any(), any())
          verify(mockMinimalPsaConnector, times(1)).isPsaSuspended(Matchers.eq(psaId.id))(any(), any())
          verify(mockUserAnswersCacheConnector, times(1)).save(any(), Matchers.eq(uaToSave.data))(any(), any())
        }
      }
    }

    "no version is given and there ARE versions in AFT and suspended flag is not in user answers" must {
      "NOT call get AFT details but SHOULD retrieve the suspended flag from DES and save it but NOT the IsNewReturn flag in Mongo" in {
        val uaToSave = userAnswersWithSchemeName
          .setOrException(IsPsaSuspendedQuery, value = false)
          .setOrException(AFTStatusQuery, value = aftStatus)

        when(mockAFTConnector.getListOfVersions(any())(any(), any()))
          .thenReturn(Future.successful(Seq[Int](1)))

        whenReady(aftService.retrieveAFTRequiredDetails(srn, None)) { case (resultScheme, _) =>
          resultScheme mustBe schemeDetails
          verify(mockSchemeService, times(1)).retrieveSchemeDetails(Matchers.eq(psaId.id), Matchers.eq(srn))(any(), any())
          verify(mockAFTConnector, times(0)).getAFTDetails(any(), any(), any())(any(), any())
          verify(mockAFTConnector, times(1)).getListOfVersions(any())(any(), any())
          verify(mockMinimalPsaConnector, times(1)).isPsaSuspended(Matchers.eq(psaId.id))(any(), any())
          verify(mockUserAnswersCacheConnector, times(1)).save(any(), Matchers.eq(uaToSave.data))(any(), any())
        }
      }
    }

    "a version is given" must {
      "call get AFT details and retrieve the suspended flag from DES and save it in Mongo" in {
        val uaToSave = userAnswersWithSchemeName
          .setOrException(IsPsaSuspendedQuery, value = false)
          .setOrException(AFTStatusQuery, value = aftStatus)

        when(mockAFTConnector.getAFTDetails(any(), any(), any())(any(), any()))
          .thenReturn(Future.successful(userAnswersWithSchemeName.data))

        whenReady(aftService.retrieveAFTRequiredDetails(srn, Some(version))) { case (resultScheme, _) =>
          resultScheme mustBe schemeDetails
          verify(mockSchemeService, times(1)).retrieveSchemeDetails(Matchers.eq(psaId.id), Matchers.eq(srn))(any(), any())
          verify(mockAFTConnector, times(1)).getAFTDetails(any(), any(), any())(any(), any())
          verify(mockMinimalPsaConnector, times(1)).isPsaSuspended(Matchers.eq(psaId.id))(any(), any())
          verify(mockUserAnswersCacheConnector, times(1)).save(any(), Matchers.eq(uaToSave.data))(any(), any())
        }
      }
    }
  }
}
