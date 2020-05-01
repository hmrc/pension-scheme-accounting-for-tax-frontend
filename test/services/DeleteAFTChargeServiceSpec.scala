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
import connectors.cache.UserAnswersCacheConnector
import data.SampleData
import data.SampleData._
import models.UserAnswers
import models.requests.{DataRequest, OptionalDataRequest}
import org.mockito.Matchers
import org.mockito.Matchers.any
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.mockito.MockitoSugar
import pages.chargeA.ShortServiceRefundQuery
import play.api.libs.json.Json
import play.api.mvc.{AnyContentAsEmpty, Results}
import uk.gov.hmrc.domain.PsaId
import utils.DeleteChargeHelper

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class DeleteAFTChargeServiceSpec extends SpecBase with ScalaFutures with BeforeAndAfterEach with MockitoSugar with Results {
  private val mockAFTConnector: AFTConnector = mock[AFTConnector]
  private val mockUserAnswersCacheConnector: UserAnswersCacheConnector = mock[UserAnswersCacheConnector]
  private val mockDeleteChargeHelper: DeleteChargeHelper = mock[DeleteChargeHelper]
  private val emptyUserAnswers = UserAnswers()
  implicit val request: OptionalDataRequest[AnyContentAsEmpty.type] =
    OptionalDataRequest(fakeRequest, "internal id", PsaId(SampleData.psaId), Some(emptyUserAnswers))

  private def dataRequest(ua: UserAnswers = UserAnswers()): DataRequest[AnyContentAsEmpty.type] =
    DataRequest(fakeRequest, "", PsaId(SampleData.psaId), ua)

  private val deleteChargeService = new DeleteAFTChargeService(mockAFTConnector, mockUserAnswersCacheConnector, mockDeleteChargeHelper)

  override def beforeEach(): Unit = {
    reset(mockAFTConnector, mockUserAnswersCacheConnector, mockDeleteChargeHelper)
    when(mockAFTConnector.fileAFTReturn(any(), any())(any(), any())).thenReturn(Future.successful(()))
  }

  "deleteAndFileAFTReturn" must {
    "zero out the last charge, file aft return and remove everything from cache if its the only charge available " in {
      when(mockDeleteChargeHelper.zeroOutLastCharge(any())).thenReturn(emptyUserAnswers)
      when(mockDeleteChargeHelper.hasLastChargeOnly(any())).thenReturn(true)
      when(mockUserAnswersCacheConnector.removeAll(any())(any(), any())).thenReturn(Future.successful(Ok))

      whenReady(deleteChargeService.deleteAndFileAFTReturn(pstr, emptyUserAnswers)(implicitly, implicitly, dataRequest(emptyUserAnswers))) { _ =>
        verify(mockDeleteChargeHelper, times(1)).zeroOutLastCharge(any())
        verify(mockAFTConnector, times(1)).fileAFTReturn(Matchers.eq(pstr), any())(any(), any())
        verify(mockUserAnswersCacheConnector, times(1)).removeAll(any())(any(), any())
        verify(mockUserAnswersCacheConnector, never()).save(any(), any())(any(), any())
      }
    }

    "remove the charge from user answers and save it if it's not the last charge for scheme level charge" in {
      val ua = UserAnswers(
        Json.obj(fields = "chargeADetails" -> Json.obj(fields = "totalAmount" -> 100.00),
                 "chargeBDetails" -> Json.obj(fields = "totalAmount" -> 400.00)))
      val uaWithoutChargeA = UserAnswers(
        Json.obj(
          fields = "chargeBDetails" -> Json.obj(fields = "totalAmount" -> 400.00)
        ))
      when(mockDeleteChargeHelper.hasLastChargeOnly(any())).thenReturn(false)
      when(mockUserAnswersCacheConnector.save(any(), any())(any(), any())).thenReturn(Future.successful(Json.obj()))

      whenReady(deleteChargeService.deleteAndFileAFTReturn(pstr, ua, Some(ShortServiceRefundQuery.path))(implicitly, implicitly, dataRequest(ua))) {
        _ =>
          verify(mockAFTConnector, times(1)).fileAFTReturn(Matchers.eq(pstr), Matchers.eq(uaWithoutChargeA))(any(), any())
          verify(mockUserAnswersCacheConnector, times(1)).save(any(), any())(any(), any())
          verify(mockUserAnswersCacheConnector, never()).removeAll(any())(any(), any())
      }
    }
  }
}
