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
import controllers.actions.MutableFakeDataRetrievalAction
import controllers.base.ControllerSpecBase
import data.SampleData._
import matchers.JsonMatchers
import models.UserAnswers
import org.mockito.Matchers.any
import org.mockito.Mockito.{times, verify, when}
import org.mockito.{ArgumentCaptor, Matchers, Mockito}
import pages.Page
import play.api.Application
import play.api.inject.bind
import play.api.libs.json.{JsObject, Json, Writes}
import play.api.test.Helpers.{redirectLocation, route, status, _}
import play.twirl.api.Html
import uk.gov.hmrc.viewmodels.NunjucksSupport

import scala.concurrent.Future
import models.LocalDateBinder._

trait CheckYourAnswersBehaviour extends ControllerSpecBase with NunjucksSupport with JsonMatchers {
  private val mockAftConnector: AFTConnector = mock[AFTConnector]

  private val mutableFakeDataRetrievalAction: MutableFakeDataRetrievalAction = new MutableFakeDataRetrievalAction()
  private val application: Application =
    applicationBuilderMutableRetrievalAction(mutableFakeDataRetrievalAction, Seq(bind[AFTConnector].toInstance(mockAftConnector))).build()

  override def beforeEach: Unit = {
    super.beforeEach
    Mockito.reset(mockAftConnector)
    when(mockUserAnswersCacheConnector.save(any(), any(), any(), any())(any(), any())).thenReturn(Future.successful(Json.obj()))
    when(mockRenderer.render(any(), any())(any())).thenReturn(Future.successful(Html("")))
    when(mockAppConfig.managePensionsSchemeSummaryUrl).thenReturn(frontendAppConfig.managePensionsSchemeSummaryUrl)

  }

  def cyaController(httpPath: => String,
                    templateToBeRendered: String,
                    jsonToPassToTemplate: JsObject,
                    userAnswers: UserAnswers = userAnswersWithSchemeNamePstrQuarter): Unit = {

    "return OK and the correct view for a GET" in {
      mutableFakeDataRetrievalAction.setDataToReturn(Option(userAnswers))

      val templateCaptor = ArgumentCaptor.forClass(classOf[String])

      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

      val result = route(application, httpGETRequest(httpPath)).value

      status(result) mustEqual OK

      verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

      templateCaptor.getValue mustEqual templateToBeRendered

      jsonCaptor.getValue must containJson(jsonToPassToTemplate)
    }

    "redirect to AFT summary page for a GET when necessary answers are missing" in {
      mutableFakeDataRetrievalAction.setDataToReturn(Option(userAnswersWithSchemeNamePstrQuarter))

      val result = route(application, httpGETRequest(httpPath)).value

      status(result) mustEqual SEE_OTHER

      redirectLocation(result).value mustBe controllers.routes.AFTSummaryController.onPageLoad(srn, startDate, accessType, versionInt).url
    }

    "redirect to Session Expired page for a GET when there is no data" in {
      mutableFakeDataRetrievalAction.setDataToReturn(None)

      val result = route(application, httpGETRequest(httpPath)).value

      status(result) mustEqual SEE_OTHER

      redirectLocation(result).value mustBe controllers.routes.SessionExpiredController.onPageLoad().url
    }
  }

  def controllerWithOnClick[A](httpPath: => String,
                               page: Page,
                               userAnswers: UserAnswers = userAnswersWithSchemeNamePstrQuarter)
                              (implicit writes: Writes[A]): Unit = {

    "Save data to user answers and redirect to next page when valid data is submitted" in {
      mutableFakeDataRetrievalAction.setDataToReturn(Option(userAnswers))

      when(mockUserAnswersCacheConnector.save(any(), any(), any(), any())(any(), any())).thenReturn(Future.successful(Json.obj()))

      when(mockCompoundNavigator.nextPage(Matchers.eq(page), any(), any(), any(), any(), any(), any())(any())).thenReturn(dummyCall)

      when(mockAftConnector.fileAFTReturn(any(), any(), any())(any(), any())).thenReturn(Future.successful(()))

      val result = route(application, httpGETRequest(httpPath)).value

      status(result) mustEqual SEE_OTHER

      verify(mockAftConnector, times(1)).fileAFTReturn(any(), any(), any())(any(), any())

      redirectLocation(result) mustBe Some(dummyCall.url)
    }
  }
}
