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

package controllers.actions

import controllers.base.ControllerSpecBase
import data.SampleData
import models.requests.DataRequest
import org.mockito.ArgumentCaptor
import org.mockito.Matchers.any
import org.mockito.Mockito.reset
import org.mockito.Mockito.when
import org.scalatest.concurrent.ScalaFutures
import pages.IsPsaSuspendedQuery
import play.api.mvc.Result
import services.AllowAccessService
import uk.gov.hmrc.domain.PsaId
import utils.AFTConstants.QUARTER_START_DATE

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class AllowAccessActionSpec extends ControllerSpecBase with ScalaFutures {

  private val allowAccessService = mock[AllowAccessService]

  class TestHarness(
                     srn: String
                   )(implicit ec: ExecutionContext) extends AllowAccessAction(srn, QUARTER_START_DATE, allowAccessService, None) {
    def test(dataRequest: DataRequest[_]): Future[Option[Result]] = this.filter(dataRequest)
  }

  "Allow Access Action" must {
    "delegate to the allow access service with the correct srn, startDate" in {
      reset(allowAccessService)
      val srnCaptor = ArgumentCaptor.forClass(classOf[String])
      when(allowAccessService.filterForIllegalPageAccess(srnCaptor.capture(),any(), any(), any(), any())(any()))
        .thenReturn(Future.successful(None))

      val ua = SampleData.userAnswersWithSchemeNamePstrQuarter
          .set(IsPsaSuspendedQuery, value = false).toOption.get

      val dataRequest = DataRequest(fakeRequest, "", PsaId(SampleData.psaId), ua, SampleData.sessionData())

      val testHarness = new TestHarness(srn = SampleData.srn)
      whenReady(testHarness.test(dataRequest)) { result =>
        result mustBe None
        srnCaptor.getValue mustBe SampleData.srn
      }
    }
  }
}
