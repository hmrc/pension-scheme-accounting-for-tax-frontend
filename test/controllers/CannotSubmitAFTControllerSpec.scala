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
import data.SampleData._
import matchers.JsonMatchers
import models.LocalDateBinder._
import models.{SchemeDetails, UserAnswers}
import org.mockito.ArgumentMatchers.any
import org.mockito.{ArgumentCaptor, MockitoSugar}
import org.scalatest.{OptionValues, TryValues}
import pages.SchemeNameQuery
import play.api.inject.bind
import play.api.inject.guice.GuiceableModule
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.Results.Ok
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.twirl.api.Html
import services.SchemeService
import uk.gov.hmrc.viewmodels.NunjucksSupport
import utils.AFTConstants.QUARTER_START_DATE

import scala.concurrent.Future

class CannotSubmitAFTControllerSpec extends ControllerSpecBase with MockitoSugar with NunjucksSupport
  with JsonMatchers with OptionValues with TryValues {
 private val srn = "test-srn"
  val startDate = QUARTER_START_DATE

  private val mockSchemeService = mock[SchemeService]

  private val extraModules: Seq[GuiceableModule] =
    Seq[GuiceableModule](
      bind[SchemeService].toInstance(mockSchemeService)
    )


  private val data = UserAnswers().set(SchemeNameQuery, schemeName).toOption
  private def getRoute: String = routes.CannotSubmitAFTController.onPageLoad(srn, startDate).url
  private def onClickRoute: String = routes.CannotSubmitAFTController.onClick(srn, startDate).url

  "Cannot submit AFT controller" must {

    "return OK and the correct view for a GET" in {
      when(mockRenderer.render(any(), any())(any())).thenReturn(Future.successful(Html("")))
      when(mockSchemeService.retrieveSchemeDetails(any(), any(), any())(any(), any())).thenReturn(Future.successful(SchemeDetails(schemeName, "", "", None)))

      val application = applicationBuilder(userAnswers = data, extraModules).overrides().build()
      val request = FakeRequest(GET, getRoute)
      val templateCaptor = ArgumentCaptor.forClass(classOf[String])
      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

      val result = route(application, request).value

      status(result) mustEqual OK

      verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

      val expectedJson = Json.obj(
        "schemeName" -> schemeName,
        "returnUrl" -> controllers.routes.CannotSubmitAFTController.onClick(srn, startDate).url
      )

      templateCaptor.getValue mustEqual "cannotSubmitAFT.njk"
      jsonCaptor.getValue must containJson(expectedJson)

      application.stop()
    }

    "return redirect for the onClick GET" in {
      when(mockAppConfig.schemeDashboardUrl(any(), any())).thenReturn("dummy-return-url/%s")
      when(mockUserAnswersCacheConnector.removeAll(any())(any(), any())).thenReturn(Future.successful(Ok))

      val application = applicationBuilder(userAnswers = data).overrides().build()
      val request = FakeRequest(GET, onClickRoute)

      val result = route(application, request).value

      status(result) mustEqual SEE_OTHER
      redirectLocation(result) mustBe Some(s"dummy-return-url/$srn")
      verify(mockUserAnswersCacheConnector, times(1)).removeAll(any())(any(), any())

      application.stop()
    }
  }
}
