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

package controllers.mccloud

import controllers.actions.MutableFakeDataRetrievalAction
import controllers.base.ControllerSpecBase
import data.SampleData._
import forms.mccloud.EnterPstrFormProvider
import matchers.JsonMatchers
import models.ChargeType.ChargeTypeAnnualAllowance
import models.LocalDateBinder._
import models.requests.IdentifierRequest
import models.NormalMode
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatest.{OptionValues, TryValues}
import org.scalatestplus.mockito.MockitoSugar
import pages.chargeE.MemberDetailsPage
import play.api.Application
import play.api.data.Form
import play.api.libs.json.Json
import play.api.mvc.Call
import play.api.test.FakeRequest
import play.api.test.Helpers._
import views.html.mccloud.EnterPstr

import scala.concurrent.Future

class EnterPstrControllerSpec extends ControllerSpecBase
  with MockitoSugar with JsonMatchers with OptionValues with TryValues {
  private val mutableFakeDataRetrievalAction: MutableFakeDataRetrievalAction = new MutableFakeDataRetrievalAction()
  private val application: Application =
    applicationBuilderMutableRetrievalAction(mutableFakeDataRetrievalAction,
      Seq()).build()

  private def onwardRoute = Call("GET", "/foo")

  private val formProvider = new EnterPstrFormProvider()
  private val form: Form[String] = formProvider()

  private def httpPathGET(schemeIndex: Int): String = routes.EnterPstrController
    .onPageLoad(ChargeTypeAnnualAllowance, NormalMode, srn, startDate, accessType, versionInt, 0, schemeIndex).url

  private def submitCall(schemeIndex: Int = 0) = routes.EnterPstrController
    .onSubmit(ChargeTypeAnnualAllowance, NormalMode, srn, startDate, accessType, versionInt, 0, schemeIndex)

  private val returnUrl = controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, versionInt).url

  private def userAnswers = userAnswersWithSchemeNamePstrQuarter
    .set(MemberDetailsPage(0), memberDetails).success.value
    .set(MemberDetailsPage(1), memberDetails).success.value

  "EnterPstrController Controller" must {

    "return OK and the correct view for a GET for scheme index zero" in {
      when(mockAppConfig.schemeDashboardUrl(any(): IdentifierRequest[?])).thenReturn(onwardRoute.url)

      mutableFakeDataRetrievalAction.setDataToReturn(Some(userAnswers))
      val request = FakeRequest(GET, httpPathGET(schemeIndex = 0))

      val result = route(application, request).value

      status(result) mustEqual OK

      val view = application.injector.instanceOf[EnterPstr].apply(
        form,
        "",
        "chargeType.description.annualAllowance",
        submitCall(),
        returnUrl,
        schemeName
      )(request, messages)

      compareResultAndView(result, view)
    }

    "return OK and the correct view for a GET and correct ordinal for scheme index one" in {
      when(mockAppConfig.schemeDashboardUrl(any(): IdentifierRequest[?])).thenReturn(onwardRoute.url)

      mutableFakeDataRetrievalAction.setDataToReturn(Some(userAnswers))
      val request = FakeRequest(GET, httpPathGET(schemeIndex = 1))

      val result = route(application, request).value

      status(result) mustEqual OK

      val view = application.injector.instanceOf[EnterPstr].apply(
        form,
        "second",
        "chargeType.description.annualAllowance",
        submitCall(1),
        returnUrl,
        schemeName
      )(request, messages)

      compareResultAndView(result, view)
    }

    "redirect to the next page when valid data is submitted" in {
      when(mockAppConfig.schemeDashboardUrl(any(): IdentifierRequest[?])).thenReturn(onwardRoute.url)
      when(mockUserAnswersCacheConnector.savePartial(any(), any(), any(), any())(any(), any())) `thenReturn` Future.successful(Json.obj())
      when(mockCompoundNavigator.nextPage(any(), any(), any(), any(), any(), any(), any())(any())).thenReturn(onwardRoute)

      mutableFakeDataRetrievalAction.setDataToReturn(Some(userAnswers))

      val request =
        FakeRequest(POST, httpPathGET(0))
          .withFormUrlEncodedBody(("value", "12345678RA"))

      val result = route(application, request).value

      status(result) mustEqual SEE_OTHER

      redirectLocation(result).value mustEqual onwardRoute.url
    }

    "return a Bad Request and errors when invalid data is submitted" in {

      when(mockAppConfig.schemeDashboardUrl(any(): IdentifierRequest[?])).thenReturn(onwardRoute.url)

      mutableFakeDataRetrievalAction.setDataToReturn(Some(userAnswers))

      val request = FakeRequest(POST, httpPathGET(0)).withFormUrlEncodedBody(("value", ""))
      val boundForm = form.bind(Map("value" -> ""))

      val result = route(application, request).value

      status(result) mustEqual BAD_REQUEST

      val view = application.injector.instanceOf[EnterPstr].apply(
        boundForm,
        "",
        "chargeType.description.annualAllowance",
        submitCall(),
        returnUrl,
        schemeName
      )(request, messages)

      compareResultAndView(result, view)
    }

    "redirect to Session Expired for a GET if no existing data is found" in {

      mutableFakeDataRetrievalAction.setDataToReturn(None)

      val request = FakeRequest(GET, httpPathGET(0))

      val result = route(application, request).value

      status(result) mustEqual SEE_OTHER

      redirectLocation(result).value mustEqual controllers.routes.SessionExpiredController.onPageLoad.url
    }

    "redirect to Session Expired for a POST if no existing data is found" in {

      mutableFakeDataRetrievalAction.setDataToReturn(None)

      val request =
        FakeRequest(POST, httpPathGET(0))
          .withFormUrlEncodedBody(("value", "true"))

      val result = route(application, request).value

      status(result) mustEqual SEE_OTHER

      redirectLocation(result).value mustEqual controllers.routes.SessionExpiredController.onPageLoad.url
    }
  }
}
