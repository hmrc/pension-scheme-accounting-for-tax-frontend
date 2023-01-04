/*
 * Copyright 2023 HM Revenue & Customs
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
import org.mockito.Mockito.{reset, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.mockito.MockitoSugar
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.{AnyContentAsEmpty, Results}
import uk.gov.hmrc.domain.PsaId

import scala.concurrent.{ExecutionContext, Future}

class FileUploadAftReturnServiceSpec extends SpecBase with ScalaFutures with BeforeAndAfterEach with MockitoSugar with Results {
  private val mockUserAnswersCacheConnector: UserAnswersCacheConnector = mock[UserAnswersCacheConnector]
  private val fileUploadAftReturnService: FileUploadAftReturnService =
    new FileUploadAftReturnService(mockUserAnswersCacheConnector, new ChargeServiceHelper())
  private val psaId = PsaId(SampleData.psaId)

  private val emptyUserAnswers = UserAnswers()
  private val sessionAccessData = SessionAccessData(2, AccessMode.PageAccessModeCompile, areSubmittedVersionsAvailable = false)
  private val sessionData = SessionData("1", Some(LockDetail("name", psaId.id)), sessionAccessData)

  private def chargeBeforeProcess(chargeType: String): JsObject = Json.obj(chargeType -> Json.obj(
    "totalChargeAmount" -> 0,
    "members" -> Json.arr(
      Json.obj(
        "memberDetails" -> Json.obj(
          "firstName" -> "Bill",
          "lastName" -> "Bloggs",
          "nino" -> "CS121212C"
        ),
        "memberAFTVersion" -> 1,
        "chargeDetails" -> Json.obj(
          "dateNoticeReceived" -> "2018-02-28",
          "isPaymentMandatory" -> true,
          "chargeAmount" -> 0
        ),
        "amendedVersion" -> 1
      ))
  ))

  private def chargeAfterProcess(chargeType: String): JsObject = Json.obj(chargeType -> Json.obj(
    "totalChargeAmount" -> 0,
    "members" -> Json.arr(
      Json.obj(
        "memberDetails" -> Json.obj(
          "firstName" -> "Bill",
          "lastName" -> "Bloggs",
          "nino" -> "CS121212C"
        ),
        "memberAFTVersion" -> 1,
        "chargeDetails" -> Json.obj(
          "dateNoticeReceived" -> "2018-02-28",
          "isPaymentMandatory" -> true,
          "chargeAmount" -> 0
        ),
        "amendedVersion" -> 1,
        "memberStatus" -> "New"
      ))
  ))

  private def dataRequest(ua: UserAnswers): DataRequest[AnyContentAsEmpty.type] =
    DataRequest(fakeRequest, "", Some(PsaId(SampleData.psaId)), None, ua, sessionData)

  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
  implicit val dataRequest: DataRequest[AnyContentAsEmpty.type] = dataRequest(UserAnswers())

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockUserAnswersCacheConnector)
  }

  private def chargeAmountJson(chargeType: String): UserAnswers = {
    UserAnswers(Json.obj(
      chargeType -> Json.obj(
        "totalChargeAmount" -> 0
      )))
  }

  "preProcessAftReturn" must {
    "updateTotalAmount for AnnualAllowance" in {
      val userAnswersWithSchemeName: UserAnswers = chargeAmountJson("chargeEDetails")
      when(mockUserAnswersCacheConnector.save(any(), any())(any(), any())).thenReturn(Future.successful(Json.obj()))
      val result = fileUploadAftReturnService.preProcessAftReturn(ChargeTypeAnnualAllowance, emptyUserAnswers)(ec, hc = hc, request = dataRequest)
      whenReady(result) { resultSchemeDetails =>
        resultSchemeDetails mustBe userAnswersWithSchemeName
      }
    }

    "setMemberStatus for AnnualAllowance" in {
      val userAnswers: UserAnswers = UserAnswers(chargeAfterProcess("chargeEDetails"))
      when(mockUserAnswersCacheConnector.save(any(), any())(any(), any())).thenReturn(Future.successful(Json.obj()))
      val result = fileUploadAftReturnService.preProcessAftReturn(ChargeTypeAnnualAllowance,
        UserAnswers(chargeBeforeProcess("chargeEDetails")))(ec, hc = hc, request = dataRequest)
      whenReady(result) { resultSchemeDetails =>
        resultSchemeDetails mustBe userAnswers
      }
    }

    "updateTotalAmount for LifetimeAllowance" in {
      val userAnswersWithSchemeName: UserAnswers = chargeAmountJson("chargeDDetails")
      when(mockUserAnswersCacheConnector.save(any(), any())(any(), any())).thenReturn(Future.successful(Json.obj()))
      val result = fileUploadAftReturnService.preProcessAftReturn(ChargeTypeLifetimeAllowance, emptyUserAnswers)(ec, hc = hc, request = dataRequest)
      whenReady(result) { resultSchemeDetails =>
        resultSchemeDetails mustBe userAnswersWithSchemeName
      }
    }

    "setMemberStatus for LifetimeAllowance" in {
      val userAnswers: UserAnswers = UserAnswers(chargeAfterProcess("chargeDDetails"))
      when(mockUserAnswersCacheConnector.save(any(), any())(any(), any())).thenReturn(Future.successful(Json.obj()))
      val result = fileUploadAftReturnService.preProcessAftReturn(ChargeTypeLifetimeAllowance,
        UserAnswers(chargeBeforeProcess("chargeDDetails")))(ec, hc = hc, request = dataRequest)
      whenReady(result) { resultSchemeDetails =>
        resultSchemeDetails mustBe userAnswers
      }
    }

    "updateTotalAmount for OverseasTransfer" in {
      val userAnswersWithSchemeName: UserAnswers = chargeAmountJson("chargeGDetails")
      when(mockUserAnswersCacheConnector.save(any(), any())(any(), any())).thenReturn(Future.successful(Json.obj()))
      val result = fileUploadAftReturnService.preProcessAftReturn(ChargeTypeOverseasTransfer, emptyUserAnswers)(ec, hc = hc, request = dataRequest)
      whenReady(result) { resultSchemeDetails =>
        resultSchemeDetails mustBe userAnswersWithSchemeName
      }
    }

    "setMemberStatus for OverseasTransfer" in {
      val userAnswers: UserAnswers = UserAnswers(chargeAfterProcess("chargeGDetails"))
      when(mockUserAnswersCacheConnector.save(any(), any())(any(), any())).thenReturn(Future.successful(Json.obj()))
      val result = fileUploadAftReturnService.preProcessAftReturn(ChargeTypeOverseasTransfer,
        UserAnswers(chargeBeforeProcess("chargeGDetails")))(ec, hc = hc, request = dataRequest)
      whenReady(result) { resultSchemeDetails =>
        resultSchemeDetails mustBe userAnswers
      }
    }

    "not updateTotalAmount if chargeType is not one of (AnnualAllowance,LifetimeAllowance,OverseasTransfer)" in {
      when(mockUserAnswersCacheConnector.save(any(), any())(any(), any())).thenReturn(Future.successful(Json.obj()))
      val result = fileUploadAftReturnService.preProcessAftReturn(ChargeTypeDeRegistration, emptyUserAnswers)(ec, hc = hc, request = dataRequest)
      whenReady(result) { resultSchemeDetails =>
        resultSchemeDetails mustBe emptyUserAnswers
      }
    }

    "not setMemberStatus if chargeType is not one of (AnnualAllowance,LifetimeAllowance,OverseasTransfer)" in {
      val userAnswers: UserAnswers = UserAnswers(chargeBeforeProcess("abc"))
      when(mockUserAnswersCacheConnector.save(any(), any())(any(), any())).thenReturn(Future.successful(Json.obj()))
      val result = fileUploadAftReturnService.preProcessAftReturn(ChargeTypeDeRegistration,
        UserAnswers(chargeBeforeProcess("abc")))(ec, hc = hc, request = dataRequest)
      whenReady(result) { resultSchemeDetails =>
        resultSchemeDetails mustBe userAnswers
      }
    }
  }
}
