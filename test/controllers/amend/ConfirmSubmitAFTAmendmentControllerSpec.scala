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

package controllers.amend

import connectors.AFTConnector
import controllers.actions.{AllowSubmissionAction, FakeAllowSubmissionAction, MutableFakeDataRetrievalAction}
import controllers.base.ControllerSpecBase
import data.SampleData
import data.SampleData._
import forms.ConfirmSubmitAFTReturnFormProvider
import helpers.AmendmentHelper
import matchers.JsonMatchers
import models.LocalDateBinder._
import models.requests.DataRequest
import models.{AFTOverview, AFTOverviewVersion, AccessMode, UserAnswers}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito
import org.mockito.Mockito.{never, times, verify, when}
import pages.ConfirmSubmitAFTAmendmentPage
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceableModule
import play.api.libs.json.Json
import play.api.mvc.Call
import play.api.mvc.Results.Ok
import play.api.test.FakeRequest
import play.api.test.Helpers._
import utils.AFTConstants.{QUARTER_END_DATE, QUARTER_START_DATE}
import viewmodels.TwirlRadios
import views.html.ConfirmSubmitAFTAmendmentView

import scala.concurrent.Future

class ConfirmSubmitAFTAmendmentControllerSpec extends ControllerSpecBase with JsonMatchers {
  private def onwardRoute = Call("GET", "/onward")

  private val formProvider = new ConfirmSubmitAFTReturnFormProvider()
  private val form = formProvider()

  private val mockAmendmentHelper = mock[AmendmentHelper]
  private val mockAFTConnector = mock[AFTConnector]

  private def confirmSubmitAFTAmendmentRoute: String = routes.ConfirmSubmitAFTAmendmentController.onPageLoad(srn, QUARTER_START_DATE, accessType, 3).url

  private val mutableFakeDataRetrievalAction: MutableFakeDataRetrievalAction = new MutableFakeDataRetrievalAction()
  private val extraModules: Seq[GuiceableModule] = Seq(
    bind[AllowSubmissionAction].toInstance(new FakeAllowSubmissionAction),
    bind[AmendmentHelper].toInstance(mockAmendmentHelper),
    bind[AFTConnector].toInstance(mockAFTConnector)
  )
  private val application: Application = applicationBuilderMutableRetrievalAction(mutableFakeDataRetrievalAction, extraModules).build()

  private val versionNumber = 3

  override def beforeEach(): Unit = {
    super.beforeEach()
    Mockito.reset(mockUserAnswersCacheConnector)
    Mockito.reset(mockAmendmentHelper)
    Mockito.reset(mockAFTConnector)
    when(mockAppConfig.schemeDashboardUrl(any(): DataRequest[?])).thenReturn(dummyCall.url)
    when(mockAmendmentHelper.amendmentSummaryRows(any(), any(), any())(any())).thenReturn(Nil)
    when(mockAmendmentHelper.getTotalAmount(any())).thenReturn((BigDecimal(2000.00), BigDecimal(40000.00)))
    when(mockAFTConnector.getAFTDetails(any(), any(), any(), any(), any())(any(), any())).thenReturn(Future.successful(Json.obj()))
    when(mockAFTConnector.getAftOverview(any(), any(), any(),  any(), any())(any(), any())).thenReturn(Future.successful(
      Seq(AFTOverview(QUARTER_START_DATE, QUARTER_END_DATE,
        tpssReportPresent = false,
        Some(AFTOverviewVersion(2, submittedVersionAvailable = true, compiledVersionAvailable = true))))))
  }

  mutableFakeDataRetrievalAction.setSessionData(SampleData.sessionData
  (sessionAccessData = sessionAccessData(versionNumber, AccessMode.PageAccessModeCompile)))

  private val userAnswers: Option[UserAnswers] = Some(userAnswersWithSchemeName)

  "ConfirmSubmitAFTAmendment Controller" must {

    "return OK and the correct view for a GET and store flag indicating that value has not changed" in {
      mutableFakeDataRetrievalAction.setDataToReturn(userAnswers)
      when(mockUserAnswersCacheConnector.savePartial(any(), any(), any(), any())(any(), any())).thenReturn(Future.successful(Json.obj()))
      val request = FakeRequest(GET, confirmSubmitAFTAmendmentRoute)

      val result = route(application, request).value
      status(result) mustEqual OK

      val view = application.injector.instanceOf[ConfirmSubmitAFTAmendmentView].apply(
        Nil,
        Nil,
        form,
        TwirlRadios.yesNo(form("value")),
        routes.ConfirmSubmitAFTAmendmentController.onSubmit(srn, QUARTER_START_DATE, accessType, 3),
        dummyCall.url,
        schemeName
      )(request, messages)

      compareResultAndView(result, view)
    }

    "populate the view correctly on a GET when the question has previously been answered" in {
      val filledAnswers = userAnswers.map(_.set(ConfirmSubmitAFTAmendmentPage, value = true).getOrElse(UserAnswers()))
      mutableFakeDataRetrievalAction.setDataToReturn(filledAnswers)
      when(mockUserAnswersCacheConnector.savePartial(any(), any(), any(), any())(any(), any())).thenReturn(Future.successful(Json.obj()))
      val request = FakeRequest(GET, confirmSubmitAFTAmendmentRoute)
      val filledForm = form.bind(Map("value" -> "true"))

      val result = route(application, request).value
      status(result) mustEqual OK

      val view = application.injector.instanceOf[ConfirmSubmitAFTAmendmentView].apply(
        Nil,
        Nil,
        filledForm,
        TwirlRadios.yesNo(filledForm("value")),
        routes.ConfirmSubmitAFTAmendmentController.onSubmit(srn, QUARTER_START_DATE, accessType, 3),
        dummyCall.url,
        schemeName
      )(request, messages)

      compareResultAndView(result, view)
    }

    "redirect to the next page when submits with value true" in {
      mutableFakeDataRetrievalAction.setDataToReturn(userAnswers)
      when(mockUserAnswersCacheConnector.savePartial(any(), any(), any(), any())(any(), any())).thenReturn(Future.successful(Json.obj()))
      when(mockCompoundNavigator.nextPage(any(), any(), any(), any(), any(), any(), any())(any())).thenReturn(onwardRoute)
      val request =
        FakeRequest(POST, confirmSubmitAFTAmendmentRoute)
          .withFormUrlEncodedBody(("value", "true"))

      val result = route(application, request).value

      status(result) mustEqual SEE_OTHER
      redirectLocation(result).value mustEqual onwardRoute.url
      verify(mockUserAnswersCacheConnector, times(1)).savePartial(any(), any(), any(), any())(any(), any())
    }

    "remove the data and redirect to the pension scheme url when user submits with value false" in {
      mutableFakeDataRetrievalAction.setDataToReturn(userAnswers)
      when(mockUserAnswersCacheConnector.removeAll(any())(any(), any())).thenReturn(Future.successful(Ok))
      val request =
        FakeRequest(POST, confirmSubmitAFTAmendmentRoute)
          .withFormUrlEncodedBody(("value", "false"))

      val result = route(application, request).value

      status(result) mustEqual SEE_OTHER
      redirectLocation(result).value mustEqual dummyCall.url
      verify(mockUserAnswersCacheConnector, times(1)).removeAll(any())(any(), any())
      verify(mockUserAnswersCacheConnector, never).savePartial(any(), any(), any(), any())(any(), any())
    }

    "return a Bad Request and errors when invalid data is submitted" in {
      mutableFakeDataRetrievalAction.setDataToReturn(userAnswers)
      when(mockUserAnswersCacheConnector.savePartial(any(), any(), any(), any())(any(), any())).thenReturn(Future.successful(Json.obj()))
      val request = FakeRequest(POST, confirmSubmitAFTAmendmentRoute).withFormUrlEncodedBody(("value", ""))

      val result = route(application, request).value
      status(result) mustEqual BAD_REQUEST

      verify(mockUserAnswersCacheConnector, times(1)).savePartial(any(), any(), any(), any())(any(), any())
    }

    "redirect to Session Expired for a GET if no existing data is found" in {
      mutableFakeDataRetrievalAction.setDataToReturn(None)
      val request = FakeRequest(GET, confirmSubmitAFTAmendmentRoute)

      val result = route(application, request).value
      status(result) mustEqual SEE_OTHER

      redirectLocation(result).value mustEqual controllers.routes.SessionExpiredController.onPageLoad.url
    }

    "redirect to Session Expired for a POST if no existing data is found" in {
      mutableFakeDataRetrievalAction.setDataToReturn(None)
      val request =
        FakeRequest(POST, confirmSubmitAFTAmendmentRoute)
          .withFormUrlEncodedBody(("value", "true"))

      val result = route(application, request).value
      status(result) mustEqual SEE_OTHER

      redirectLocation(result).value mustEqual controllers.routes.SessionExpiredController.onPageLoad.url
    }
  }
}
