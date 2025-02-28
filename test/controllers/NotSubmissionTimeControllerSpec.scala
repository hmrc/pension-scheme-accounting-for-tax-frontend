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
import data.SampleData._
import matchers.JsonMatchers
import models.LocalDateBinder._
import models.SchemeDetails
import models.requests.IdentifierRequest
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatest.{OptionValues, TryValues}
import org.scalatestplus.mockito.MockitoSugar
import play.api.inject.bind
import play.api.inject.guice.GuiceableModule
import play.api.test.FakeRequest
import play.api.test.Helpers._
import services.SchemeService
import utils.AFTConstants.QUARTER_START_DATE
import views.html.NotSubmissionTimeView

import java.time.LocalDate
import scala.concurrent.Future

class NotSubmissionTimeControllerSpec extends ControllerSpecBase with MockitoSugar
  with JsonMatchers with OptionValues with TryValues {
  private val srn = "test-srn"
  val startDate: LocalDate = QUARTER_START_DATE

  private val mockSchemeService = mock[SchemeService]

  private val extraModules: Seq[GuiceableModule] =
    Seq[GuiceableModule](
      bind[SchemeService].toInstance(mockSchemeService)
    )

  private def getRoute: String = routes.NotSubmissionTimeController.onPageLoad(srn, startDate).url

  "Not Submission Time controller" must {

    "Return OK and the correct view for a GET" in {
      when(mockSchemeService.retrieveSchemeDetails(any(), any())(any(), any())).thenReturn(Future.successful(SchemeDetails(schemeName, "", "", None)))
      when(mockAppConfig.schemeDashboardUrl(any(): IdentifierRequest[_])).thenReturn(dummyCall.url)

      val application = applicationBuilder(extraModules = extraModules).overrides().build()
      val request = FakeRequest(GET, getRoute)

      val result = route(application, request).value

      status(result) mustEqual OK

      val view = application.injector.instanceOf[NotSubmissionTimeView].apply(
        dummyCall.url,
        "1 July 2020"
      )(request, messages)

      compareResultAndView(result, view)
      application.stop()
    }
  }
}

