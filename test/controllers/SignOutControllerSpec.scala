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

package controllers

import controllers.base.ControllerSpecBase
import models.UserAnswers
import org.mockito.Matchers.any
import org.mockito.Mockito.{times, verify, when}
import play.api.libs.json.Json
import play.api.mvc.Results.Ok
import play.api.test.FakeRequest
import play.api.test.Helpers._

import scala.concurrent.Future

class SignOutControllerSpec extends ControllerSpecBase {

  private def signOutRoute: String = controllers.routes.SignOutController.signOut().url
  private val userAnswers = UserAnswers(Json.obj(
    "test-key" -> "test-value"
  ))


  "SignOut Controller" must {

    "clear data and redirect to feedback survey page" in {
      when(mockUserAnswersCacheConnector.removeAll(any())(any(), any())).thenReturn(Future.successful(Ok))
      when(mockAppConfig.signOutUrl).thenReturn(frontendAppConfig.signOutUrl)
      val application = applicationBuilder(userAnswers = Some(userAnswers)).build()
      val result = route(application, FakeRequest(GET, signOutRoute)).value

      status(result) mustBe SEE_OTHER
      verify(mockUserAnswersCacheConnector, times(1)).removeAll(any())(any(), any())
      redirectLocation(result) mustBe Some(frontendAppConfig.signOutUrl)
    }
  }
}
