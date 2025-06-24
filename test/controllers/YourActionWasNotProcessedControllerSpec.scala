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

import controllers.base.ControllerSpecBase
import data.SampleData
import data.SampleData._
import matchers.JsonMatchers
import models.LocalDateBinder._
import models.UserAnswers
import models.requests.DataRequest
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatest.{OptionValues, TryValues}
import org.scalatestplus.mockito.MockitoSugar
import pages.SchemeNameQuery
import play.api.test.Helpers._
import utils.AFTConstants.QUARTER_START_DATE
import views.html.YourActionWasNotProcessedView

class YourActionWasNotProcessedControllerSpec extends ControllerSpecBase with MockitoSugar
  with JsonMatchers with OptionValues with TryValues {
  private val srn = "test-srn"

  private val data = UserAnswers().set(SchemeNameQuery, schemeName).toOption
  private val returnUrl = controllers.routes.ReturnToSchemeDetailsController.
    returnToSchemeDetails(srn, startDate, SampleData.accessType, SampleData.versionInt).url
  private def getRoute: String = routes.YourActionWasNotProcessedController.onPageLoad(srn, QUARTER_START_DATE).url

  "YourActionWasNotProcessedController" must {

    "return OK and the correct view for a GET" in {
      when(mockAppConfig.schemeDashboardUrl(any():DataRequest[?])).thenReturn(returnUrl)
      val application = applicationBuilder(userAnswers = data).overrides().build()

      val request = httpGETRequest(getRoute)

      val view = application.injector.instanceOf[YourActionWasNotProcessedView].apply(returnUrl, schemeName)(request, messages)

      val result = route(application, request).value

      status(result) mustEqual OK

      compareResultAndView(result, view)

      application.stop()
    }
  }
}
