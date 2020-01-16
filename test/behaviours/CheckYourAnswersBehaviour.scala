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

package behaviours

import config.FrontendAppConfig
import connectors.AFTConnector
import data.SampleData
import models.UserAnswers
import org.mockito.{ArgumentCaptor, Matchers, Mockito}
import org.mockito.Matchers.any
import org.mockito.Mockito.{times, verify, when}
import org.scalatest.BeforeAndAfterEach
import pages.Page
import play.api.inject.bind
import play.api.inject.guice.{GuiceApplicationBuilder, GuiceableModule}
import play.api.libs.json.{JsObject, Json, Writes}
import play.api.test.Helpers.{redirectLocation, route, status, _}

import scala.concurrent.Future

trait CheckYourAnswersBehaviour extends ControllerBehaviours with BeforeAndAfterEach {
  val mockAftConnector: AFTConnector = mock[AFTConnector]

  override def beforeEach: Unit = Mockito.reset(mockAftConnector)

  def cyaController(httpPath: => String,
                    page: Page,
                    templateToBeRendered: String,
                    jsonToPassToTemplate: JsObject,
                    userAnswers: Option[UserAnswers] = Some(SampleData.userAnswersWithSchemeName)): Unit = {
    "return OK and the correct view for a GET" in {

      when(mockAppConfig.managePensionsSchemeSummaryUrl).thenReturn(SampleData.dummyCall.url)

      val application = applicationBuilder(userAnswers = userAnswers)
        .overrides(
          bind[FrontendAppConfig].toInstance(mockAppConfig)
        )
        .build()

      val templateCaptor = ArgumentCaptor.forClass(classOf[String])

      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

      val result = route(application, httpGETRequest(httpPath)).value

      status(result) mustEqual OK

      verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

      templateCaptor.getValue mustEqual templateToBeRendered

      jsonCaptor.getValue must containJson(jsonToPassToTemplate)

      application.stop()
    }
  }

  def controllerWithOnClick[A](httpPath: => String, page: Page, userAnswers: UserAnswers = SampleData.userAnswersWithSchemeName)
                              (implicit writes: Writes[A]): Unit = {

    "Save data to user answers and redirect to next page when valid data is submitted" in {
      when(mockUserAnswersCacheConnector.save(any(), any())(any(), any())).thenReturn(Future.successful(Json.obj()))

      when(mockAppConfig.managePensionsSchemeSummaryUrl).thenReturn(SampleData.dummyCall.url)

      when(mockCompoundNavigator.nextPage(Matchers.eq(page), any(), any(), any())).thenReturn(SampleData.dummyCall)

      when(mockAftConnector.fileAFTReturn(any(), any())(any(), any())).thenReturn(Future.successful(()))

      val application = new GuiceApplicationBuilder()
        .overrides(
          modules(Some(userAnswers)) ++ Seq[GuiceableModule](
            bind[AFTConnector].toInstance(mockAftConnector),
            bind[FrontendAppConfig].toInstance(mockAppConfig)
          ): _*
        ).build()

      val result = route(application, httpGETRequest(httpPath)).value

      status(result) mustEqual SEE_OTHER

      verify(mockAftConnector, times(1)).fileAFTReturn(any(), any())(any(), any())

      redirectLocation(result) mustBe Some(SampleData.dummyCall.url)

      application.stop()
    }

    "redirect to Session Expired page for a GET when there is no data" in {
      val application = applicationBuilder(userAnswers = None).build()

      val result = route(application, httpGETRequest(httpPath)).value

      status(result) mustEqual SEE_OTHER

      redirectLocation(result).value mustBe controllers.routes.SessionExpiredController.onPageLoad().url

      application.stop()
    }
  }
}
