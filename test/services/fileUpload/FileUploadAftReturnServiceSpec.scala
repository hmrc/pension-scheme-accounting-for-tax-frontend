/*
 * Copyright 2022 HM Revenue & Customs
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

package services.fileUpload

import base.SpecBase
import connectors.cache.UserAnswersCacheConnector
import data.SampleData
import helpers.ChargeServiceHelper
import models.ChargeType.{ChargeTypeAnnualAllowance, ChargeTypeDeRegistration, ChargeTypeLifetimeAllowance, ChargeTypeOverseasTransfer}
import models.requests.DataRequest
import models.{AccessMode, LockDetail, SessionAccessData, SessionData, UserAnswers}
import org.mockito.ArgumentMatchers.any
import org.mockito.MockitoSugar
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import play.api.libs.json.Json
import play.api.mvc.{AnyContentAsEmpty, Results}
import uk.gov.hmrc.domain.PsaId

import scala.concurrent.{ExecutionContext, Future}

class FileUploadAftReturnServiceSpec extends SpecBase with ScalaFutures with BeforeAndAfterEach with MockitoSugar with Results {
  private val mockUserAnswersCacheConnector: UserAnswersCacheConnector = mock[UserAnswersCacheConnector]
  private val fileUploadAftReturnService:FileUploadAftReturnService =
    new FileUploadAftReturnService(mockUserAnswersCacheConnector,new ChargeServiceHelper())
  private val psaId = PsaId(SampleData.psaId)

  private val emptyUserAnswers = UserAnswers()
  private val sessionAccessData = SessionAccessData(1, AccessMode.PageAccessModeCompile, areSubmittedVersionsAvailable = false)
  private val sessionData = SessionData("1", Some(LockDetail("name", psaId.id)), sessionAccessData)

  private def dataRequest(ua: UserAnswers): DataRequest[AnyContentAsEmpty.type] =
    DataRequest(fakeRequest, "", Some(PsaId(SampleData.psaId)), None, ua, sessionData)
  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
  implicit val dataRequest:DataRequest[AnyContentAsEmpty.type]=dataRequest(UserAnswers())

  override def beforeEach: Unit = {
    super.beforeEach
    reset(mockUserAnswersCacheConnector)
  }
  private def chargeAmountJson(chargeType:String):UserAnswers ={
      UserAnswers(Json.obj(
        chargeType -> Json.obj(
          "totalChargeAmount" -> 0
        )))
  }

  "preProcessAftReturn" must {
    "updateTotalAmount for AnnualAllowance" in {
      val userAnswersWithSchemeName: UserAnswers =chargeAmountJson("chargeEDetails")
      when(mockUserAnswersCacheConnector.save(any(), any())(any(), any())).thenReturn(Future.successful(Json.obj()))
      val result = fileUploadAftReturnService.preProcessAftReturn(ChargeTypeAnnualAllowance,emptyUserAnswers)(ec,hc=hc,request =dataRequest)
      whenReady(result) { resultSchemeDetails =>
        resultSchemeDetails mustBe userAnswersWithSchemeName
      }
    }

    "updateTotalAmount for LifetimeAllowance" in {
      val userAnswersWithSchemeName: UserAnswers =chargeAmountJson("chargeDDetails")
      when(mockUserAnswersCacheConnector.save(any(), any())(any(), any())).thenReturn(Future.successful(Json.obj()))
      val result = fileUploadAftReturnService.preProcessAftReturn(ChargeTypeLifetimeAllowance,emptyUserAnswers)(ec,hc=hc,request =dataRequest)
      whenReady(result) { resultSchemeDetails =>
        resultSchemeDetails mustBe userAnswersWithSchemeName
      }
    }


    "updateTotalAmount for OverseasTransfer" in {
      val userAnswersWithSchemeName: UserAnswers =chargeAmountJson("chargeGDetails")
      when(mockUserAnswersCacheConnector.save(any(), any())(any(), any())).thenReturn(Future.successful(Json.obj()))
      val result = fileUploadAftReturnService.preProcessAftReturn(ChargeTypeOverseasTransfer,emptyUserAnswers)(ec,hc=hc,request =dataRequest)
      whenReady(result) { resultSchemeDetails =>
        resultSchemeDetails mustBe userAnswersWithSchemeName
      }
    }

    "not updateTotalAmount if chargeType is not one of (AnnualAllowance,LifetimeAllowance,OverseasTransfer)" in {
      when(mockUserAnswersCacheConnector.save(any(), any())(any(), any())).thenReturn(Future.successful(Json.obj()))
      val result = fileUploadAftReturnService.preProcessAftReturn(ChargeTypeDeRegistration,emptyUserAnswers)(ec,hc=hc,request =dataRequest)
      whenReady(result) { resultSchemeDetails =>
        resultSchemeDetails mustBe emptyUserAnswers
      }
    }
  }
}
