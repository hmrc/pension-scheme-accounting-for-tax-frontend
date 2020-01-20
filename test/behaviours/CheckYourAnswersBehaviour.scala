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

import connectors.AFTConnector
import controllers.base.ControllerSpecBase
import data.SampleData
import matchers.JsonMatchers
import org.mockito.Matchers
import org.mockito.Matchers.any
import org.mockito.Mockito.{times, verify, when}
import pages.Page
import play.api.inject.bind
import play.api.inject.guice.{GuiceApplicationBuilder, GuiceableModule}
import play.api.libs.json.{Json, Writes}
import play.api.test.Helpers.{redirectLocation, route, status, _}
import play.twirl.api.Html
import uk.gov.hmrc.viewmodels.NunjucksSupport

import scala.concurrent.Future

trait CheckYourAnswersBehaviour extends ControllerSpecBase with NunjucksSupport with JsonMatchers {
  override def beforeEach: Unit = {
    super.beforeEach
    when(mockUserAnswersCacheConnector.save(any(), any())(any(), any())).thenReturn(Future.successful(Json.obj()))
    when(mockRenderer.render(any(), any())(any())).thenReturn(Future.successful(Html("")))
  }
  val mockAftConnector: AFTConnector = mock[AFTConnector]
  def controllerWithOnClick[A](httpPath: => String,
                            page: Page)(implicit writes: Writes[A]): Unit = {

    "Save data to user answers and redirect to next page when valid data is submitted" in {
      when(mockCompoundNavigator.nextPage(Matchers.eq(page), any(), any(), any())).thenReturn(SampleData.dummyCall)
      when(mockAftConnector.fileAFTReturn(any(), any())(any(), any())).thenReturn(Future.successful(()))
      val application = new GuiceApplicationBuilder()
        .overrides(
          modules(Some(SampleData.userAnswersWithSchemeName)) ++ Seq[GuiceableModule](
            bind[AFTConnector].toInstance(mockAftConnector)
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
