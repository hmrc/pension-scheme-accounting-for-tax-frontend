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
import data.SampleData._
import matchers.JsonMatchers
import models.LocalDateBinder._
import models.SchemeDetails
import org.mockito.ArgumentMatchers.any
import org.mockito.{ArgumentCaptor, MockitoSugar}
import org.scalatest.{OptionValues, TryValues}
import play.api.inject.bind
import play.api.inject.guice.GuiceableModule
import play.api.libs.json.{JsObject, Json}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.twirl.api.Html
import services.SchemeService
import uk.gov.hmrc.viewmodels.NunjucksSupport
import utils.AFTConstants.QUARTER_START_DATE

import java.time.LocalDate
import scala.concurrent.Future

class NotSubmissionTimeControllerSpec extends ControllerSpecBase with MockitoSugar with NunjucksSupport
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
      when(mockRenderer.render(any(), any())(any())).thenReturn(Future.successful(Html("")))
      when(mockSchemeService.retrieveSchemeDetails(any(), any(), any())(any(), any())).thenReturn(Future.successful(SchemeDetails(schemeName, "", "", None)))
      when(mockAppConfig.schemeDashboardUrl(any(), any())).thenReturn(dummyCall.url)

      val application = applicationBuilder(extraModules = extraModules).overrides().build()
      val request = FakeRequest(GET, getRoute)
      val templateCaptor = ArgumentCaptor.forClass(classOf[String])
      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

      val result = route(application, request).value

      status(result) mustEqual OK

      verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

      val expectedJson = Json.obj(
        "date" -> "1 July 2020",
        "continueLink" -> dummyCall.url
      )

      templateCaptor.getValue mustEqual "notSubmissionTime.njk"
      jsonCaptor.getValue must containJson(expectedJson)

      application.stop()
    }
  }
}
