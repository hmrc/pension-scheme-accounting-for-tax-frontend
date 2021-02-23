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

import controllers.actions.{AllowSubmissionAction, FakeAllowSubmissionAction, MutableFakeDataRetrievalAction}
import controllers.base.ControllerSpecBase
import data.SampleData._
import forms.ConfirmSubmitAFTReturnFormProvider
import matchers.JsonMatchers
import models.LocalDateBinder._
import play.api.mvc.Results.Ok
import models.requests.IdentifierRequest
import models.{GenericViewModel, UserAnswers}
import org.mockito.Matchers.any
import org.mockito.Mockito.{never, times, verify, when}
import org.mockito.{ArgumentCaptor, Mockito}
import pages.ConfirmSubmitAFTReturnPage
import play.api.Application
import play.api.data.Form
import play.api.inject.bind
import play.api.inject.guice.GuiceableModule
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.Call
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.twirl.api.Html
import uk.gov.hmrc.viewmodels.{NunjucksSupport, Radios}
import utils.AFTConstants.QUARTER_START_DATE

import scala.concurrent.Future

class ConfirmSubmitAFTReturnControllerSpec extends ControllerSpecBase with NunjucksSupport with JsonMatchers {
  private def onwardRoute = Call("GET", "/onward")

  private val formProvider = new ConfirmSubmitAFTReturnFormProvider()
  private val form = formProvider()

  private def confirmSubmitAFTReturnRoute: String = routes.ConfirmSubmitAFTReturnController.onPageLoad(srn, QUARTER_START_DATE).url

  private def confirmSubmitAFTReturnSubmitRoute: String = routes.ConfirmSubmitAFTReturnController.onSubmit(srn, QUARTER_START_DATE).url

  private val mutableFakeDataRetrievalAction: MutableFakeDataRetrievalAction = new MutableFakeDataRetrievalAction()
  private val extraModules: Seq[GuiceableModule] = Seq(bind[AllowSubmissionAction].toInstance(new FakeAllowSubmissionAction))
  private val application: Application = applicationBuilderMutableRetrievalAction(mutableFakeDataRetrievalAction, extraModules).build()
  private val templateToBeRendered = "confirmSubmitAFTReturn.njk"

  private val templateCaptor = ArgumentCaptor.forClass(classOf[String])
  private val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

  private def jsonToBePassed(form: Form[Boolean]): JsObject = Json.obj(
    fields = "srn" -> srn,
    "form" -> form,
    "viewModel" -> GenericViewModel(
      submitUrl = confirmSubmitAFTReturnSubmitRoute,
      returnUrl = controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, QUARTER_START_DATE, accessType, versionInt).url,
      schemeName = schemeName),
    "radios" -> Radios.yesNo(form("value"))
  )

  override def beforeEach: Unit = {
    super.beforeEach
    Mockito.reset(mockUserAnswersCacheConnector, mockRenderer)
    when(mockRenderer.render(any(), any())(any())).thenReturn(Future.successful(Html("")))
    when(mockAppConfig.schemeDashboardUrl(any(): IdentifierRequest[_])).thenReturn(dummyCall.url)
  }

  private val userAnswers: Option[UserAnswers] = Some(userAnswersWithSchemeName)

  "ConfirmSubmitAFTReturn Controller" must {

    "return OK and the correct view for a GET" in {
      mutableFakeDataRetrievalAction.setDataToReturn(userAnswers)
      val request = FakeRequest(GET, confirmSubmitAFTReturnRoute)
      val expectedJson = jsonToBePassed(form)

      val result = route(application, request).value
      status(result) mustEqual OK

      verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

      templateCaptor.getValue mustEqual templateToBeRendered
      jsonCaptor.getValue must containJson(expectedJson)
    }

    "populate the view correctly on a GET when the question has previously been answered" in {
      val filledAnswers = userAnswers.map(_.set(ConfirmSubmitAFTReturnPage, value = true).getOrElse(UserAnswers()))
      mutableFakeDataRetrievalAction.setDataToReturn(filledAnswers)
      val request = FakeRequest(GET, confirmSubmitAFTReturnRoute)
      val filledForm = form.bind(Map("value" -> "true"))
      val expectedJson = jsonToBePassed(filledForm)

      val result = route(application, request).value
      status(result) mustEqual OK

      verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

      templateCaptor.getValue mustEqual templateToBeRendered
      jsonCaptor.getValue must containJson(expectedJson)
    }

    "redirect to the next page and save all user answers data when submits with value true" in {
      mutableFakeDataRetrievalAction.setDataToReturn(userAnswers)
      when(mockUserAnswersCacheConnector.save(any(), any())(any(), any())).thenReturn(Future.successful(Json.obj()))
      when(mockCompoundNavigator.nextPage(any(), any(), any(), any(), any(), any(), any())(any())).thenReturn(onwardRoute)
      val request =
        FakeRequest(POST, confirmSubmitAFTReturnRoute)
          .withFormUrlEncodedBody(("value", "true"))

      val result = route(application, request).value

      status(result) mustEqual SEE_OTHER
      redirectLocation(result).value mustEqual onwardRoute.url
      verify(mockUserAnswersCacheConnector, times(1)).save(any(), any())(any(), any())
    }

    "redirect to the next page and remove all user answers data when submits with value false" in {
      mutableFakeDataRetrievalAction.setDataToReturn(userAnswers)
      when(mockUserAnswersCacheConnector.removeAll(any())(any(),any()))
        .thenReturn(Future.successful(Ok))
      when(mockCompoundNavigator.nextPage(any(), any(), any(), any(), any(), any(), any())(any())).thenReturn(onwardRoute)
      val request =
        FakeRequest(POST, confirmSubmitAFTReturnRoute)
          .withFormUrlEncodedBody(("value", "false"))

      val result = route(application, request).value

      status(result) mustEqual SEE_OTHER
      redirectLocation(result).value mustEqual onwardRoute.url
      verify(mockUserAnswersCacheConnector, times(1)).removeAll(any())(any(), any())
    }

    "return a Bad Request and errors when invalid data is submitted" in {
      mutableFakeDataRetrievalAction.setDataToReturn(userAnswers)
      val request = FakeRequest(POST, confirmSubmitAFTReturnRoute).withFormUrlEncodedBody(("value", ""))

      val result = route(application, request).value
      status(result) mustEqual BAD_REQUEST

      verify(mockRenderer, times(1)).render(any(), any())(any())
      verify(mockUserAnswersCacheConnector, never).save(any(), any())(any(), any())
    }

    "redirect to Session Expired for a GET if no existing data is found" in {
      mutableFakeDataRetrievalAction.setDataToReturn(None)
      val request = FakeRequest(GET, confirmSubmitAFTReturnRoute)

      val result = route(application, request).value
      status(result) mustEqual SEE_OTHER

      redirectLocation(result).value mustEqual routes.SessionExpiredController.onPageLoad().url
    }

    "redirect to Session Expired for a POST if no existing data is found" in {
      mutableFakeDataRetrievalAction.setDataToReturn(None)
      val request =
        FakeRequest(POST, confirmSubmitAFTReturnRoute)
          .withFormUrlEncodedBody(("value", "true"))

      val result = route(application, request).value
      status(result) mustEqual SEE_OTHER

      redirectLocation(result).value mustEqual routes.SessionExpiredController.onPageLoad().url
    }
  }
}
