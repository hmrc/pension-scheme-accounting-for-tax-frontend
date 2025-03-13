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
import connectors.AFTConnector
import data.SampleData
import data.SampleData._
import models.requests.{DataRequest, OptionalDataRequest}
import models.{AccessMode, JourneyType, LockDetail, SessionAccessData, SessionData, UserAnswers}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, times, verify, when}
import org.mockito.{ArgumentCaptor, ArgumentMatchers}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.mockito.MockitoSugar
import pages.AFTStatusQuery
import play.api.mvc.{AnyContentAsEmpty, Results}
import uk.gov.hmrc.domain.PsaId
import utils.DateHelper

import java.time.format.DateTimeFormatter
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class AFTServiceSpec extends SpecBase with ScalaFutures with BeforeAndAfterEach with MockitoSugar with Results {
  private val mockAFTConnector: AFTConnector = mock[AFTConnector]

  private val psaId = PsaId(SampleData.psaId)
  private val internalId = "internal id"

  private val aftService = new AFTService(mockAFTConnector)

  private val emptyUserAnswers = UserAnswers()
  private val sessionAccessData = SessionAccessData(1, AccessMode.PageAccessModeCompile, areSubmittedVersionsAvailable = false)
  private val sessionData = SessionData("1", Some(LockDetail("name", psaId.id)), sessionAccessData)

  implicit val request: OptionalDataRequest[AnyContentAsEmpty.type] =
    OptionalDataRequest(fakeRequest, internalId, Some(psaId), None, Some(emptyUserAnswers), Some(sessionData))

  private def dataRequest(ua: UserAnswers): DataRequest[AnyContentAsEmpty.type] =
    DataRequest(fakeRequest, "", Some(PsaId(SampleData.psaId)), None, ua, sessionData)

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockAFTConnector)
  }

  "fileSubmitReturn" must {
    "remove lock and all user answers if no valid charges to be saved (i.e. user has deleted last member/ employer)" in {
      val jsonCaptor = ArgumentCaptor.forClass(classOf[UserAnswers])
      val uaBeforeCalling = userAnswersWithSchemeNamePstrQuarter
      when(mockAFTConnector.fileAFTReturn(any(), any(), any(), any(), any())(any(), any())).thenReturn(Future.successful(()))
      whenReady(aftService.fileSubmitReturn(pstr, uaBeforeCalling, srn)(implicitly, implicitly, dataRequest(uaBeforeCalling))) { _ =>
        verify(mockAFTConnector, times(1))
          .fileAFTReturn(any(), jsonCaptor.capture(), ArgumentMatchers.eq(JourneyType.AFT_SUBMIT_RETURN),
            ArgumentMatchers.eq(srn), any())(any(), any())
      }
      jsonCaptor.getValue.getOrException(AFTStatusQuery) mustBe "Submitted"
    }
  }

  "fileCompileReturn" must {
    "remove lock and all user answers if no valid charges to be saved (i.e. user has deleted last member/ employer)" in {
      val jsonCaptor = ArgumentCaptor.forClass(classOf[UserAnswers])
      val uaBeforeCalling = userAnswersWithSchemeNamePstrQuarter
      when(mockAFTConnector.fileAFTReturn(any(), any(), any(), any(), any())(any(), any())).thenReturn(Future.successful(()))
      whenReady(aftService.fileCompileReturn(pstr, uaBeforeCalling, srn)(implicitly, implicitly, dataRequest(uaBeforeCalling))) { _ =>
        verify(mockAFTConnector, times(1))
          .fileAFTReturn(any(), jsonCaptor.capture(), ArgumentMatchers.eq(JourneyType.AFT_COMPILE_RETURN),
            ArgumentMatchers.eq(srn), any())(any(), any())
      }
      jsonCaptor.getValue.getOrException(AFTStatusQuery) mustBe "Compiled"
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
