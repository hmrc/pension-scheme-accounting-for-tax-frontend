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

package controllers

import connectors.cache.SessionDataCacheConnector
import controllers.base.ControllerSpecBase
import models.UserAnswers
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import play.api.inject.bind
import play.api.inject.guice.GuiceableModule
import play.api.libs.json.Json
import play.api.mvc.Results.Ok
import play.api.test.FakeRequest
import play.api.test.Helpers._
import utils.AFTConstants._

import scala.concurrent.Future

class SignOutControllerSpec extends ControllerSpecBase {

  private val srn = "srn"
  private val startDate = Some(QUARTER_START_DATE.toString)

  private def signOutRoute(startDate: Option[String] = startDate): String = controllers.routes.SignOutController.signOut(srn, startDate).url

  private val userAnswers = UserAnswers(Json.obj(
    "test-key" -> "test-value"
  ))

  private val mockSessionDataCacheConnector = mock[SessionDataCacheConnector]

  private val extraModules: Seq[GuiceableModule] =
    Seq[GuiceableModule](
      bind[SessionDataCacheConnector].toInstance(mockSessionDataCacheConnector)
    )

  private val application = applicationBuilder(userAnswers = Some(userAnswers), extraModules = extraModules).build()

  "SignOut Controller" must {

    "clear data and redirect to feedback survey page when there is a startDate" in {
      reset(mockSessionDataCacheConnector)
      when(mockSessionDataCacheConnector.removeAll(any())(any(), any()))
        .thenReturn(Future.successful(Ok))
      when(mockUserAnswersCacheConnector.removeAll(any())(any(), any())).thenReturn(Future.successful(Ok))
      when(mockAppConfig.signOutUrl).thenReturn(frontendAppConfig.signOutUrl)
      val result = route(application, FakeRequest(GET, signOutRoute())).value

      status(result) mustBe SEE_OTHER
      verify(mockUserAnswersCacheConnector, times(1)).removeAll(any())(any(), any())
      redirectLocation(result) mustBe Some(frontendAppConfig.signOutUrl)
    }

    "not clear data but redirect to feedback survey page when there is no startDate" in {
      reset(mockSessionDataCacheConnector)
      when(mockSessionDataCacheConnector.removeAll(any())(any(), any()))
        .thenReturn(Future.successful(Ok))
      when(mockAppConfig.signOutUrl).thenReturn(frontendAppConfig.signOutUrl)

      val result = route(application, FakeRequest(GET, signOutRoute(None))).value

      status(result) mustBe SEE_OTHER
      verify(mockUserAnswersCacheConnector, never).removeAll(any())(any(), any())
      redirectLocation(result) mustBe Some(frontendAppConfig.signOutUrl)
    }
  }
}
