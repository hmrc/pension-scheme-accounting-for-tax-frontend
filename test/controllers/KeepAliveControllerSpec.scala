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

package controllers

import controllers.base.ControllerSpecBase
import models.UserAnswers
import org.mockito.ArgumentMatchers.any
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers._
import utils.AFTConstants.QUARTER_START_DATE

import scala.concurrent.Future

class KeepAliveControllerSpec extends ControllerSpecBase {
  private val srn = Some("srn")
  private val startDate = Some(QUARTER_START_DATE.toString)
  private def keepAliveRoute(srn: Option[String], startDate: Option[String]): String =
    controllers.routes.KeepAliveController.keepAlive(srn, startDate).url
  private val userAnswers = UserAnswers(Json.obj(
    "test-key" -> "test-value"
  ))
  private val application = applicationBuilder(userAnswers = Some(userAnswers)).build()

  "Keep alive controller" must {
    "touch mongo cache when srn and startDate are available" in {
      when(mockAppConfig.timeoutSeconds).thenReturn(frontendAppConfig.timeoutSeconds)
      when(mockAppConfig.countdownSeconds).thenReturn(frontendAppConfig.countdownSeconds)
      when(mockUserAnswersCacheConnector.fetch(any())(any(), any())).thenReturn(Future.successful(Some(userAnswers.data)))
      when(mockUserAnswersCacheConnector.save(any(), any())(any(), any())).thenReturn(Future.successful(userAnswers.data))

      val result = route(application, FakeRequest(GET, keepAliveRoute(srn, startDate))).value

      status(result) mustBe NO_CONTENT
      verify(mockUserAnswersCacheConnector, times(1)).fetch(any())(any(), any())
      verify(mockUserAnswersCacheConnector, times(1)).save(any(), any())(any(), any())
    }

    "not touch mongo cache when srn and startDate are not available" in {
      when(mockAppConfig.timeoutSeconds).thenReturn(frontendAppConfig.timeoutSeconds)
      when(mockAppConfig.countdownSeconds).thenReturn(frontendAppConfig.countdownSeconds)

      val result = route(application, FakeRequest(GET, keepAliveRoute(None, None))).value

      status(result) mustBe NO_CONTENT
      verify(mockUserAnswersCacheConnector, times(0)).fetch(any())(any(), any())
      verify(mockUserAnswersCacheConnector, times(0)).save(any(), any())(any(), any())
    }
  }
}
