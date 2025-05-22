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

package controllers.chargeC

import controllers.actions.MutableFakeDataRetrievalAction
import controllers.base.ControllerSpecBase
import data.SampleData._
import forms.chargeC.IsSponsoringEmployerIndividualFormProvider
import matchers.JsonMatchers
import models.LocalDateBinder._
import models.SponsoringEmployerType.SponsoringEmployerTypeIndividual
import models.requests.IdentifierRequest
import models.{NormalMode, SponsoringEmployerType, UserAnswers}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatest.{OptionValues, TryValues}
import org.scalatestplus.mockito.MockitoSugar
import pages.chargeC.WhichTypeOfSponsoringEmployerPage
import play.api.Application
import play.api.libs.json.Json
import play.api.mvc.Call
import play.api.test.FakeRequest
import play.api.test.Helpers._
import views.html.chargeC.WhichTypeOfSponsoringEmployerView

import scala.concurrent.Future

class WhichTypeOfSponsoringEmployerControllerSpec
  extends ControllerSpecBase
    with MockitoSugar
    with JsonMatchers
    with OptionValues
    with TryValues {
  private val index = 0
  private val mutableFakeDataRetrievalAction: MutableFakeDataRetrievalAction = new MutableFakeDataRetrievalAction()
  private def application: Application = registerApp(applicationBuilderMutableRetrievalAction(mutableFakeDataRetrievalAction).build())

  private val answers: UserAnswers = userAnswersWithSchemeNamePstrQuarter.set(
    WhichTypeOfSponsoringEmployerPage(index), SponsoringEmployerTypeIndividual).success.value

  def onwardRoute: Call = Call("GET", "/foo")

  private val formProvider = new IsSponsoringEmployerIndividualFormProvider()
  private val form = formProvider()

  private def httpPathGET: String = routes.WhichTypeOfSponsoringEmployerController.onPageLoad(NormalMode, srn, startDate, accessType, versionInt, index).url

  "IsSponsoringEmployerIndividual Controller" must {

    "return OK and the correct view for a GET" in {
      when(mockAppConfig.schemeDashboardUrl(any(): IdentifierRequest[_])).thenReturn(onwardRoute.url)

      mutableFakeDataRetrievalAction.setDataToReturn(Some(userAnswersWithSchemeNamePstrQuarter))

      val request = httpGETRequest(httpPathGET)
      val submitCall = routes.WhichTypeOfSponsoringEmployerController.onSubmit(NormalMode, srn, startDate, accessType, versionInt, index)
      val returnUrl = controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, versionInt).url
      val view = application.injector.instanceOf[WhichTypeOfSponsoringEmployerView].apply(
        form,
        schemeName,
        submitCall,
        returnUrl,
        SponsoringEmployerType.radios(form)
      )(request, messages)

      val result = route(application, request).value

      status(result) mustEqual OK

      compareResultAndView(result, view)

    }

    "populate the view correctly on a GET when the question has previously been answered" in {
      when(mockAppConfig.schemeDashboardUrl(any(): IdentifierRequest[_])).thenReturn(onwardRoute.url)

      mutableFakeDataRetrievalAction.setDataToReturn(Some(answers))

      val request = httpGETRequest(httpPathGET)
      val submitCall = routes.WhichTypeOfSponsoringEmployerController.onSubmit(NormalMode, srn, startDate, accessType, versionInt, index)
      val returnUrl = controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, versionInt).url
      val view = application.injector.instanceOf[WhichTypeOfSponsoringEmployerView].apply(
        form.fill(SponsoringEmployerTypeIndividual),
        schemeName,
        submitCall,
        returnUrl,
        SponsoringEmployerType.radios(form)
      )(request, messages)

      val result = route(application, request).value

      status(result) mustEqual OK

      compareResultAndView(result, view)
    }

    "redirect to the next page when valid data is submitted" in {
      when(mockAppConfig.schemeDashboardUrl(any(): IdentifierRequest[_])).thenReturn(onwardRoute.url)
      when(mockUserAnswersCacheConnector.savePartial(any(), any(), any(), any())(any(), any())) thenReturn Future.successful(Json.obj())
      when(mockCompoundNavigator.nextPage(any(), any(), any(), any(), any(), any(), any())(any())).thenReturn(onwardRoute)

      mutableFakeDataRetrievalAction.setDataToReturn(Some(userAnswersWithSchemeNamePstrQuarter))

      val request =
        FakeRequest(POST, httpPathGET)
          .withFormUrlEncodedBody(("value", "individual"))

      val result = route(application, request).value

      status(result) mustEqual SEE_OTHER

      redirectLocation(result).value mustEqual onwardRoute.url
    }

    "return a Bad Request and errors when invalid data is submitted" in {

      when(mockAppConfig.schemeDashboardUrl(any(): IdentifierRequest[_])).thenReturn(onwardRoute.url)

      mutableFakeDataRetrievalAction.setDataToReturn(Some(userAnswersWithSchemeNamePstrQuarter))

      val request = FakeRequest(POST, httpPathGET).withFormUrlEncodedBody(("value", ""))
      val boundForm = form.bind(Map("value" -> ""))


      val submitCall = routes.WhichTypeOfSponsoringEmployerController.onSubmit(NormalMode, srn, startDate, accessType, versionInt, index)
      val returnUrl = controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, versionInt).url
      val view = application.injector.instanceOf[WhichTypeOfSponsoringEmployerView].apply(
        boundForm,
        schemeName,
        submitCall,
        returnUrl,
        SponsoringEmployerType.radios(form)
      )(request, messages)

      val result = route(application, request).value

      status(result) mustEqual BAD_REQUEST

      compareResultAndView(result, view)
    }

    "redirect to Session Expired for a GET if no existing data is found" in {

      mutableFakeDataRetrievalAction.setDataToReturn(None)

      val request = FakeRequest(GET, httpPathGET)

      val result = route(application, request).value

      status(result) mustEqual SEE_OTHER

      redirectLocation(result).value mustEqual controllers.routes.SessionExpiredController.onPageLoad.url
    }

    "redirect to Session Expired for a POST if no existing data is found" in {

      mutableFakeDataRetrievalAction.setDataToReturn(None)

      val request =
        FakeRequest(POST, httpPathGET)
          .withFormUrlEncodedBody(("value", "true"))

      val result = route(application, request).value

      status(result) mustEqual SEE_OTHER

      redirectLocation(result).value mustEqual controllers.routes.SessionExpiredController.onPageLoad.url
    }
  }
}
