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

package behaviours

import connectors.AFTConnector
import controllers.actions.MutableFakeDataRetrievalAction
import controllers.base.ControllerSpecBase
import data.SampleData._
import matchers.JsonMatchers
import models.LocalDateBinder._
import models.UserAnswers
import models.requests.IdentifierRequest
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{times, verify, when}
import org.mockito.{ArgumentMatchers, Mockito}
import pages.Page
import play.api.Application
import play.api.inject.bind
import play.api.libs.json.Json
import play.api.test.Helpers.{redirectLocation, route, status, _}
import uk.gov.hmrc.govukfrontend.views.viewmodels.summarylist.SummaryListRow
import uk.gov.hmrc.http.UpstreamErrorResponse
import views.html.CheckYourAnswersView

import scala.concurrent.Future

trait CheckYourAnswersBehaviour extends ControllerSpecBase with JsonMatchers {
  private val mockAftConnector: AFTConnector = mock[AFTConnector]

  private val mutableFakeDataRetrievalAction: MutableFakeDataRetrievalAction = new MutableFakeDataRetrievalAction()
  private val application: Application =
    applicationBuilderMutableRetrievalAction(mutableFakeDataRetrievalAction, Seq(bind[AFTConnector].toInstance(mockAftConnector))).build()

  override def beforeEach(): Unit = {
    super.beforeEach()
    Mockito.reset(mockAftConnector)
    when(mockUserAnswersCacheConnector.savePartial(any(), any(), any(), any())(any(), any())).thenReturn(Future.successful(Json.obj()))
    when(mockAppConfig.schemeDashboardUrl(any(): IdentifierRequest[?])).thenReturn(frontendAppConfig.managePensionsSchemeSummaryUrl)

  }

  def cyaController(httpPath: => String,
                    chargeName: String,
                    list: Seq[SummaryListRow],
                    canChange: Boolean = true,
                    removeChargeUrl: Option[String] = None,
                    showAnotherSchemeBtn: Boolean = false,
                    selectAnotherSchemeUrl: String = "",
                    returnToSummaryLink: String = "",
                    returnUrl: String,
                    submitUrl: String,
                    userAnswers: UserAnswers = userAnswersWithSchemeNamePstrQuarter): Unit = {

    "return OK and the correct view for a GET" in {
      mutableFakeDataRetrievalAction.setDataToReturn(Option(userAnswers))

      val result = route(application, httpGETRequest(httpPath)).value

      status(result) mustEqual OK

      val view = application.injector.instanceOf[CheckYourAnswersView].apply(
        chargeName,
        list,
        canChange,
        removeChargeUrl,
        showAnotherSchemeBtn,
        selectAnotherSchemeUrl,
        returnToSummaryLink,
        returnUrl,
        schemeName,
        submitUrl
      )(httpGETRequest(httpPath), messages)

      compareResultAndView(result, view)
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

      redirectLocation(result).value mustBe controllers.routes.SessionExpiredController.onPageLoad.url
    }
  }

  def controllerWithOnClick[A](httpPath: => String,
                               page: Page,
                               userAnswers: UserAnswers = userAnswersWithSchemeNamePstrQuarter): Unit = {

    "Save data to user answers and redirect to next page when valid data is submitted" in {
      mutableFakeDataRetrievalAction.setDataToReturn(Option(userAnswers))

      when(mockUserAnswersCacheConnector.savePartial(any(), any(), any(), any())(any(), any())).thenReturn(Future.successful(Json.obj()))

      when(mockCompoundNavigator.nextPage(ArgumentMatchers.eq(page), any(), any(), any(), any(), any(), any())(any())).thenReturn(dummyCall)

      when(mockAftConnector.fileAFTReturn(any(), any(), any(), any(), any())(any(), any())).thenReturn(
        Future.successful(()))

      val result = route(application, httpGETRequest(httpPath)).value

      status(result) mustEqual SEE_OTHER

      verify(mockAftConnector, times(1)).fileAFTReturn(any(), any(), any(), any(), any())(any(), any())

      redirectLocation(result) mustBe Some(dummyCall.url)
    }
  }

  def redirectToErrorOn5XX[A](httpPath: => String,
                              page: Page,
                              userAnswers: UserAnswers = userAnswersWithSchemeNamePstrQuarter): Unit = {

    "redirect to your action was not processed page on a POST when 5XX error is thrown" in {
      mutableFakeDataRetrievalAction.setDataToReturn(Option(userAnswers))

      when(mockUserAnswersCacheConnector.savePartial(any(), any(), any(), any())(any(), any())).thenReturn(Future.successful(Json.obj()))

      when(mockCompoundNavigator.nextPage(ArgumentMatchers.eq(page), any(), any(), any(), any(), any(), any())(any())).thenReturn(dummyCall)

      when(mockAftConnector.fileAFTReturn(any(), any(), any(), any(), any())(any(), any())).thenReturn(
        Future.failed(UpstreamErrorResponse("serviceUnavailable", SERVICE_UNAVAILABLE, SERVICE_UNAVAILABLE)))

      val result = route(application, httpGETRequest(httpPath)).value

      status(result) mustEqual SEE_OTHER

      redirectLocation(result) mustBe Some(controllers.routes.YourActionWasNotProcessedController.onPageLoad(srn, startDate).url)
    }
  }
}
