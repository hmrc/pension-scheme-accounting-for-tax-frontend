/*
 * Copyright 2021 HM Revenue & Customs
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

package controllers

import controllers.base.ControllerSpecBase
import data.SampleData
import models.requests.IdentifierRequest
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{times, verify, when}
import play.api.mvc.Results.Ok
import play.api.test.FakeRequest
import play.api.test.Helpers._
import utils.AFTConstants._

import scala.concurrent.Future

class ReturnToSchemeDetailsControllerSpec extends ControllerSpecBase {

  private val srn = "srn"
  private val startDate = QUARTER_START_DATE.toString
  private val application = applicationBuilder().build()

  "ReturnToSchemeDetails Controller" must {

    "release lock and redirect to scheme summary page" in {
      when(mockUserAnswersCacheConnector.removeAll(any())(any(), any())).thenReturn(Future.successful(Ok))
      when(mockAppConfig.schemeDashboardUrl(any(): IdentifierRequest[_])).thenReturn(frontendAppConfig.managePensionsSchemeSummaryUrl)
      val argCaptor = ArgumentCaptor.forClass(classOf[String])
      val result =
        route(application, FakeRequest(GET, controllers.routes.ReturnToSchemeDetailsController.
          returnToSchemeDetails(srn, startDate, SampleData.accessType, SampleData.versionInt).url)).value

      status(result) mustBe SEE_OTHER
      verify(mockUserAnswersCacheConnector, times(1)).removeAll(argCaptor.capture())(any(), any())
      argCaptor.getValue mustEqual s"$srn$startDate"
      redirectLocation(result) mustBe Some(frontendAppConfig.managePensionsSchemeSummaryUrl.format(srn))
    }
  }
}
