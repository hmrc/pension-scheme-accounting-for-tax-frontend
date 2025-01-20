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

import controllers.actions.{AllowSubmissionAction, FakeAllowSubmissionAction, MutableFakeDataRetrievalAction}
import controllers.base.ControllerSpecBase
import data.SampleData._
import forms.ConfirmSubmitAFTReturnFormProvider
import matchers.JsonMatchers
import models.LocalDateBinder._
import models.requests.IdentifierRequest
import models.UserAnswers
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{never, times, verify, when}
import org.mockito.Mockito
import pages.ConfirmSubmitAFTReturnPage
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceableModule
import play.api.libs.json.Json
import play.api.mvc.Call
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.twirl.api.Html
import utils.AFTConstants.QUARTER_START_DATE
import viewmodels.TwirlRadios
import views.html.ConfirmSubmitAFTReturnView

import scala.concurrent.Future

class ConfirmSubmitAFTReturnControllerSpec extends ControllerSpecBase with JsonMatchers {
  private def onwardRoute = Call("GET", "/onward")

  private val formProvider = new ConfirmSubmitAFTReturnFormProvider()
  private val form = formProvider()

  private def confirmSubmitAFTReturnRoute: String = routes.ConfirmSubmitAFTReturnController.onPageLoad(srn, QUARTER_START_DATE).url

  private val mutableFakeDataRetrievalAction: MutableFakeDataRetrievalAction = new MutableFakeDataRetrievalAction()
  private val extraModules: Seq[GuiceableModule] = Seq(bind[AllowSubmissionAction].toInstance(new FakeAllowSubmissionAction))
  private val application: Application = applicationBuilderMutableRetrievalAction(mutableFakeDataRetrievalAction, extraModules).build()

  override def beforeEach(): Unit = {
    super.beforeEach()
    Mockito.reset(mockUserAnswersCacheConnector)
    Mockito.reset(mockRenderer)
    when(mockRenderer.render(any(), any())(any())).thenReturn(Future.successful(Html("")))
    when(mockAppConfig.schemeDashboardUrl(any(): IdentifierRequest[_])).thenReturn(dummyCall.url)
  }

  private val userAnswers: Option[UserAnswers] = Some(userAnswersWithSchemeName)

  "ConfirmSubmitAFTReturn Controller" must {

    "return OK and the correct view for a GET" in {
      mutableFakeDataRetrievalAction.setDataToReturn(userAnswers)
      val request = FakeRequest(GET, confirmSubmitAFTReturnRoute)

      val result = route(application, request).value
      status(result) mustEqual OK

      val view = application.injector.instanceOf[ConfirmSubmitAFTReturnView].apply(
        form,
        TwirlRadios.yesNo(form("value")),
        routes.ConfirmSubmitAFTReturnController.onSubmit(srn, QUARTER_START_DATE),
        routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, versionInt).url,
        schemeName
      )(request, messages)

      compareResultAndView(result, view)
    }

    "populate the view correctly on a GET when the question has previously been answered" in {
      val filledAnswers = userAnswers.map(_.set(ConfirmSubmitAFTReturnPage, value = true).getOrElse(UserAnswers()))
      mutableFakeDataRetrievalAction.setDataToReturn(filledAnswers)
      val request = FakeRequest(GET, confirmSubmitAFTReturnRoute)
      val filledForm = form.bind(Map("value" -> "true"))

      val result = route(application, request).value
      status(result) mustEqual OK

      val view = application.injector.instanceOf[ConfirmSubmitAFTReturnView].apply(
        filledForm,
        TwirlRadios.yesNo(filledForm("value")),
        routes.ConfirmSubmitAFTReturnController.onSubmit(srn, QUARTER_START_DATE),
        routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, versionInt).url,
        schemeName
      )(request, messages)

      compareResultAndView(result, view)

    }

    "redirect to the next page when submits with value true" in {
      mutableFakeDataRetrievalAction.setDataToReturn(userAnswers)
      when(mockUserAnswersCacheConnector.savePartial(any(), any(), any(), any())(any(), any())).thenReturn(Future.successful(Json.obj()))
      when(mockCompoundNavigator.nextPage(any(), any(), any(), any(), any(), any(), any())(any())).thenReturn(onwardRoute)
      val request =
        FakeRequest(POST, confirmSubmitAFTReturnRoute)
          .withFormUrlEncodedBody(("value", "true"))

      val result = route(application, request).value

      status(result) mustEqual SEE_OTHER
      redirectLocation(result).value mustEqual onwardRoute.url
      verify(mockUserAnswersCacheConnector, times(1)).savePartial(any(), any(), any(), any())(any(), any())
    }

    "redirect to the next page when submits with value false" in {
      mutableFakeDataRetrievalAction.setDataToReturn(userAnswers)
      when(mockUserAnswersCacheConnector.savePartial(any(), any(), any(), any())(any(), any())).thenReturn(Future.successful(Json.obj()))
      when(mockCompoundNavigator.nextPage(any(), any(), any(), any(), any(), any(), any())(any())).thenReturn(onwardRoute)
      val request =
        FakeRequest(POST, confirmSubmitAFTReturnRoute)
          .withFormUrlEncodedBody(("value", "false"))

      val result = route(application, request).value

      status(result) mustEqual SEE_OTHER
      redirectLocation(result).value mustEqual onwardRoute.url
      verify(mockUserAnswersCacheConnector, times(1)).savePartial(any(), any(), any(), any())(any(), any())
    }

    "return a Bad Request and errors when invalid data is submitted" in {
      mutableFakeDataRetrievalAction.setDataToReturn(userAnswers)
      val request = FakeRequest(POST, confirmSubmitAFTReturnRoute).withFormUrlEncodedBody(("value", ""))

      val result = route(application, request).value
      status(result) mustEqual BAD_REQUEST

      verify(mockUserAnswersCacheConnector, never).savePartial(any(), any(), any(), any())(any(), any())
    }

    "redirect to Session Expired for a GET if no existing data is found" in {
      mutableFakeDataRetrievalAction.setDataToReturn(None)
      val request = FakeRequest(GET, confirmSubmitAFTReturnRoute)

      val result = route(application, request).value
      status(result) mustEqual SEE_OTHER

      redirectLocation(result).value mustEqual routes.SessionExpiredController.onPageLoad.url
    }

    "redirect to Session Expired for a POST if no existing data is found" in {
      mutableFakeDataRetrievalAction.setDataToReturn(None)
      val request =
        FakeRequest(POST, confirmSubmitAFTReturnRoute)
          .withFormUrlEncodedBody(("value", "true"))

      val result = route(application, request).value
      status(result) mustEqual SEE_OTHER

      redirectLocation(result).value mustEqual routes.SessionExpiredController.onPageLoad.url
    }
  }
}
