/*
 * Copyright 2024 HM Revenue & Customs
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
import data.SampleData
import data.SampleData._
import helpers.DeleteChargeHelper
import models.requests.DataRequest
import models.{AccessMode, LockDetail, SessionAccessData, SessionData, UserAnswers}
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.mockito.MockitoSugar
import play.api.libs.json.Json
import play.api.mvc.{AnyContent, Results}
import uk.gov.hmrc.domain.PsaId

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class DeleteAFTChargeServiceSpec extends SpecBase with ScalaFutures with BeforeAndAfterEach with MockitoSugar with Results {
  private val mockAFTService: AFTService = mock[AFTService]
  private val mockUserAnswersCacheConnector: UserAnswersCacheConnector = mock[UserAnswersCacheConnector]
  private val mockDeleteChargeHelper: DeleteChargeHelper = mock[DeleteChargeHelper]

  private def sessionAccessData(version: Int) = SessionAccessData(version, AccessMode.PageAccessModeCompile, areSubmittedVersionsAvailable = false)

  private def sessionData(version: Int) = SessionData(s"id", Some(LockDetail("name", psaId)), sessionAccessData(version))

  private val emptyUserAnswers = UserAnswers()

  private def dataRequest(ua: UserAnswers, version: Int): DataRequest[AnyContent] =
    DataRequest(fakeRequest, "", Some(PsaId(SampleData.psaId)), None, ua, sessionData(version))

  private val deleteChargeService = new DeleteAFTChargeService(mockAFTService, mockUserAnswersCacheConnector, mockDeleteChargeHelper)

  override def beforeEach(): Unit = {
    reset(mockAFTService)
    reset(mockUserAnswersCacheConnector)
    reset(mockDeleteChargeHelper)
    when(mockAFTService.fileCompileReturn(any(), any(), any())(any(), any(), any())).thenReturn(Future.successful(()))
  }

  "deleteAndFileAFTReturn" must {

    "file aft return and removeWithCleanup everything from cache if all charges have been deleted or zeroed out" in {
      when(mockDeleteChargeHelper.allChargesDeletedOrZeroed(any())).thenReturn(true)
      when(mockUserAnswersCacheConnector.removeAll(any())(any(), any())).thenReturn(Future.successful(Ok))

      whenReady(deleteChargeService.deleteAndFileAFTReturn(pstr, emptyUserAnswers, srn)(implicitly, implicitly, dataRequest(emptyUserAnswers, 1))) { _ =>
        verify(mockDeleteChargeHelper, times(1)).allChargesDeletedOrZeroed(any())
        verify(mockAFTService, times(1)).fileCompileReturn(any(), any(), any())(any(), any(), any())
        verify(mockUserAnswersCacheConnector, times(1)).removeAll(any())(any(), any())
        verify(mockUserAnswersCacheConnector, never).save(any(), any())(any(), any())
      }
    }

    "file aft return and save if all charges have been deleted or zeroed out for an amendment" in {
      when(mockDeleteChargeHelper.allChargesDeletedOrZeroed(any())).thenReturn(true)
      when(mockUserAnswersCacheConnector.removeAll(any())(any(), any())).thenReturn(Future.successful(Ok))
      when(mockUserAnswersCacheConnector.save(any(), any())(any(), any())).thenReturn(Future.successful(Json.obj()))

      whenReady(deleteChargeService.deleteAndFileAFTReturn(pstr, emptyUserAnswers, srn)(implicitly, implicitly, dataRequest(emptyUserAnswers, 2))) { _ =>
        verify(mockDeleteChargeHelper, times(1)).allChargesDeletedOrZeroed(any())
        verify(mockAFTService, times(1)).fileCompileReturn(any(), any(), any())(any(), any(), any())
        verify(mockUserAnswersCacheConnector, never).removeAll(any())(any(), any())
        verify(mockUserAnswersCacheConnector, times(1)).save(any(), any())(any(), any())
      }
    }

    "file aft return and save it if it's not the last charge for scheme level charge" in {
      val ua = UserAnswers(
        Json.obj(fields = "chargeADetails" -> Json.obj(fields = "totalAmount" -> 100.00),
          "chargeBDetails" -> Json.obj(fields = "totalAmount" -> 400.00)))

      when(mockDeleteChargeHelper.allChargesDeletedOrZeroed(any())).thenReturn(false)
      when(mockUserAnswersCacheConnector.save(any(), any())(any(), any())).thenReturn(Future.successful(Json.obj()))

      whenReady(deleteChargeService.deleteAndFileAFTReturn(pstr, ua, srn)(implicitly, implicitly, dataRequest(ua, 1))) {
        _ =>
          verify(mockAFTService, times(1)).fileCompileReturn(ArgumentMatchers.eq(pstr), ArgumentMatchers.eq(ua), ArgumentMatchers.eq(srn))(any(), any(), any())
          verify(mockUserAnswersCacheConnector, times(1)).save(any(), any())(any(), any())
          verify(mockUserAnswersCacheConnector, never).removeAll(any())(any(), any())
      }
    }
  }
}
