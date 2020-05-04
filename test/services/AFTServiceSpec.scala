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

import java.time.format.DateTimeFormatter

import base.SpecBase
import connectors.cache.UserAnswersCacheConnector
import connectors.AFTConnector
import data.SampleData
import data.SampleData._
import models.requests.DataRequest
import models.requests.OptionalDataRequest
import models.AccessMode
import models.SessionAccessData
import models.SessionData
import models.UserAnswers
import org.mockito.Matchers.any
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.mockito.MockitoSugar
import play.api.libs.json.Json
import play.api.mvc.AnyContentAsEmpty
import play.api.mvc.Results
import uk.gov.hmrc.domain.PsaId
import utils.DateHelper

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class AFTServiceSpec extends SpecBase with ScalaFutures with BeforeAndAfterEach with MockitoSugar with Results {
  private val mockAFTConnector: AFTConnector = mock[AFTConnector]
  private val mockUserAnswersCacheConnector: UserAnswersCacheConnector = mock[UserAnswersCacheConnector]

  private val mockUserAnswersValidationService = mock[AFTReturnTidyService]

  private val psaId = PsaId(SampleData.psaId)
  private val internalId = "internal id"

  private val aftService = new AFTService(mockAFTConnector, mockUserAnswersCacheConnector,
    mockUserAnswersValidationService)

  private val emptyUserAnswers = UserAnswers()
  private val sessionAccessData = SessionAccessData(1, AccessMode.PageAccessModeCompile)
  private val sessionAccessDataViewOnly = SessionAccessData(1, AccessMode.PageAccessModeViewOnly)
  private val sessionData = SessionData("1", Some("name"), sessionAccessData)
  private val sessionDataViewOnlyData = SessionData("1", Some("name"), sessionAccessDataViewOnly)

  implicit val request: OptionalDataRequest[AnyContentAsEmpty.type] = OptionalDataRequest(fakeRequest, internalId, psaId, Some(emptyUserAnswers), sessionData)

  private def dataRequest(ua: UserAnswers = UserAnswers()): DataRequest[AnyContentAsEmpty.type] =
    DataRequest(fakeRequest, "", PsaId(SampleData.psaId), ua, sessionData)

  private def optionalDataRequest(viewOnly: Boolean): OptionalDataRequest[_] = OptionalDataRequest(
    fakeRequest, "", PsaId(SampleData.psaId), Some(UserAnswers()), sessionDataViewOnlyData)
  private val email = "test@test.com"
  private val name = "Pension Scheme Administrator"

  override def beforeEach(): Unit = {
    reset(mockAFTConnector, mockUserAnswersCacheConnector, mockUserAnswersValidationService)
    when(mockUserAnswersCacheConnector.save(any(), any(), any(), any())(any(), any())).thenReturn(Future.successful(Json.obj()))
    when(mockUserAnswersCacheConnector.save(any(), any(), any(), any())(any(), any())).thenReturn(Future.successful(Json.obj()))
    when(mockUserAnswersValidationService.isAtLeastOneValidCharge(any())).thenReturn(true)
  }

  "fileAFTReturn" must {

    "remove lock and all user answers if no valid charges to be saved (i.e. user has deleted last member/ employer)" in {
      val uaBeforeCalling = userAnswersWithSchemeNamePstrQuarter
      when(mockAFTConnector.fileAFTReturn(any(), any())(any(), any())).thenReturn(Future.successful(()))
      when(mockUserAnswersCacheConnector.removeAll(any())(any(), any())).thenReturn(Future.successful(Ok("success")))
      when(mockUserAnswersValidationService.isAtLeastOneValidCharge(any())).thenReturn(false)
      whenReady(aftService.fileAFTReturn(pstr, uaBeforeCalling)(implicitly, implicitly, dataRequest(uaBeforeCalling))) { _ =>
        verify(mockUserAnswersCacheConnector, times(1)).removeAll(any())(any(), any())
        verify(mockUserAnswersValidationService, times(1)).isAtLeastOneValidCharge(any())
        verify(mockUserAnswersValidationService, times(1)).reinstateDeletedMemberOrEmployer(any())
      }
    }
  }

  "isSubmissionDisabled" when {
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    "quarter end date is todays date " must {
      "return disabled as true" in {
        val quarterEndDate = formatter.format(DateHelper.today)
        val result = aftService.isSubmissionDisabled(quarterEndDate)
        result mustBe true
      }
    }

    "quarter end date is in the past " must {
      "return enabled as false" in {
        val quarterEndDate = formatter.format(DateHelper.today.minusDays(1))
        val result = aftService.isSubmissionDisabled(quarterEndDate)
        result mustBe false
      }
    }

    "quarter end date is in the future " must {
      "return disabled as true" in {
        val quarterEndDate = formatter.format(DateHelper.today.plusDays(1))
        val result = aftService.isSubmissionDisabled(quarterEndDate)
        result mustBe true
      }
    }
  }
}
