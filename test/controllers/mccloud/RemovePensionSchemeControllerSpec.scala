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
import forms.YesNoFormProvider
import matchers.JsonMatchers
import models.ChargeType.{ChargeTypeAnnualAllowance, ChargeTypeLifetimeAllowance}
import models.LocalDateBinder._
import models.{CheckMode, GenericViewModel, NormalMode, UserAnswers}
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{times, verify, when}
import org.scalatest.{OptionValues, TryValues}
import org.scalatestplus.mockito.MockitoSugar
import play.api.Application
import play.api.data.Form
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.Call
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.twirl.api.Html
import uk.gov.hmrc.viewmodels.{NunjucksSupport, Radios}
import viewmodels.TwirlRadios
import views.html.mccloud.RemovePensionScheme

import scala.concurrent.Future

class RemovePensionSchemeControllerSpec
  extends ControllerSpecBase
    with MockitoSugar
    with NunjucksSupport
    with JsonMatchers
    with OptionValues
    with TryValues {

  private val mutableFakeDataRetrievalAction: MutableFakeDataRetrievalAction = new MutableFakeDataRetrievalAction()
  private val application: Application =
    applicationBuilderMutableRetrievalAction(mutableFakeDataRetrievalAction, Seq()).build()

  private def onwardRoute = Call("GET", "/foo")

  private val formProvider = new YesNoFormProvider()
  private val form: Form[Boolean] = formProvider(messages("removePensionScheme.error.required"))

  private def httpPathGETAnnualAllowance: String =
    routes.RemovePensionSchemeController
      .onPageLoad(ChargeTypeAnnualAllowance, CheckMode, srn, startDate, accessType, versionInt, 0, schemeIndex)
      .url

  private def httpPathGETLifetimeAllowance: String =
    routes.RemovePensionSchemeController
      .onPageLoad(ChargeTypeLifetimeAllowance, CheckMode, srn, startDate, accessType, versionInt, 0, schemeIndex)
      .url

  private def httpPathPOSTAnnualAllowance: String =
    routes.RemovePensionSchemeController
      .onSubmit(ChargeTypeAnnualAllowance, CheckMode, srn, startDate, accessType, versionInt, 0, schemeIndex)
      .url

  private def httpPathPOSTLifetimeAllowance: String =
    routes.RemovePensionSchemeController
      .onSubmit(ChargeTypeLifetimeAllowance, CheckMode, srn, startDate, accessType, versionInt, 0, schemeIndex)
      .url

  private def submitCallAnnualAllowance = routes.RemovePensionSchemeController
    .onSubmit(ChargeTypeAnnualAllowance, CheckMode, srn, startDate, accessType, versionInt, 0, schemeIndex)

  private def submitCallLifetimeAllowance = routes.RemovePensionSchemeController
    .onSubmit(ChargeTypeLifetimeAllowance, CheckMode, srn, startDate, accessType, versionInt, 0, schemeIndex)

  private def userAnswersOneSchemeAnnual: UserAnswers = uaWithPSPRAndOneSchemeAnnual

  private def userAnswersTwoSchemesAnnual: UserAnswers = uaWithPSPRAndTwoSchemesAnnual

  private def userAnswersOneSchemeLifetime: UserAnswers = uaWithPSPRAndOneSchemeLifetime

  private def userAnswersTwoSchemesLifetime: UserAnswers = uaWithPSPRAndTwoSchemesLifetime

  "RemovePensionSchemeController" must {

    "return OK and the correct view for a GET for ChargeTypeAnnualAllowance" in {
      when(mockRenderer.render(any(), any())(any())).thenReturn(Future.successful(Html("")))

      mutableFakeDataRetrievalAction.setDataToReturn(Some(userAnswersOneSchemeAnnual))
      val request = FakeRequest(GET, httpPathGETAnnualAllowance)

      val result = route(application, request).value

      status(result) mustEqual OK

//      val view = app.injector.instanceOf[RemovePensionScheme].apply(
//        form,
//        TwirlRadios.yesNo(form("value")),
//        submitCallAnnualAllowance,
//        controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, versionInt).url,
//        schemeName
//      )(request, messages)
//
//      compareResultAndView(result, view)
    }

    "return OK and the correct view for a GET for ChargeTypeLifetimeAllowance" in {
      when(mockRenderer.render(any(), any())(any())).thenReturn(Future.successful(Html("")))

      mutableFakeDataRetrievalAction.setDataToReturn(Some(userAnswersOneSchemeLifetime))
      val request = FakeRequest(GET, httpPathGETLifetimeAllowance)

      val result = route(application, request).value

      status(result) mustEqual OK

//      val view = app.injector.instanceOf[RemovePensionScheme].apply(
//        form,
//        TwirlRadios.yesNo(form("value")),
//        submitCallLifetimeAllowance,
//        controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, versionInt).url,
//        schemeName
//      )(request, messages)
//
//      compareResultAndView(result, view)
    }

    "redirect to Session Expired for a GET if no existing data is found for AnnualAllowance" in {

      mutableFakeDataRetrievalAction.setDataToReturn(None)

      val request = FakeRequest(GET, httpPathGETAnnualAllowance)

      val result = route(application, request).value

      status(result) mustEqual SEE_OTHER

      redirectLocation(result).value mustEqual controllers.routes.SessionExpiredController.onPageLoad.url
    }

    "redirect to Session Expired for a GET if no existing data is found for LifetimeAllowance" in {

      mutableFakeDataRetrievalAction.setDataToReturn(None)

      val request = FakeRequest(GET, httpPathGETLifetimeAllowance)

      val result = route(application, request).value

      status(result) mustEqual SEE_OTHER

      redirectLocation(result).value mustEqual controllers.routes.SessionExpiredController.onPageLoad.url
    }

    "redirect to the next page when valid data with one scheme is submitted for AnnualAllowance" in {
      when(mockUserAnswersCacheConnector.savePartial(any(), any(), any(), any())(any(), any())) thenReturn Future.successful(Json.obj())
      when(mockCompoundNavigator.nextPage(any(), any(), any(), any(), any(), any(), any())(any())).thenReturn(onwardRoute)

      mutableFakeDataRetrievalAction.setDataToReturn(Some(userAnswersOneSchemeAnnual))

      val request =
        FakeRequest(POST, httpPathPOSTAnnualAllowance)
          .withFormUrlEncodedBody(("value", "true"))
      val result = route(application, request).value

      status(result) mustEqual SEE_OTHER

      redirectLocation(result).value mustEqual onwardRoute.url
      verify(mockUserAnswersCacheConnector, times(1)).savePartial(any(), any(), any(), any())(any(), any())
      verify(mockCompoundNavigator, times(1)).nextPage(any(), any(), any(), any(), any(), any(), any())(any())
    }

    "redirect to the next page when valid data with two scheme is submitted for AnnualAllowance" in {
      when(mockUserAnswersCacheConnector.savePartial(any(), any(), any(), any())(any(), any())) thenReturn Future.successful(Json.obj())
      when(mockCompoundNavigator.nextPage(any(), any(), any(), any(), any(), any(), any())(any())).thenReturn(onwardRoute)

      mutableFakeDataRetrievalAction.setDataToReturn(Some(userAnswersTwoSchemesAnnual))

      val request =
        FakeRequest(POST, httpPathPOSTAnnualAllowance)
          .withFormUrlEncodedBody(("value", "true"))
      val result = route(application, request).value

      status(result) mustEqual SEE_OTHER

      redirectLocation(result).value mustEqual onwardRoute.url
      verify(mockUserAnswersCacheConnector, times(1)).savePartial(any(), any(), any(), any())(any(), any())
      verify(mockCompoundNavigator, times(1)).nextPage(any(), any(), any(), any(), any(), any(), any())(any())
    }

    "redirect to the next page when valid data and user select no to remove scheme for AnnualAllowance" in {
      when(mockCompoundNavigator.nextPage(any(), any(), any(), any(), any(), any(), any())(any())).thenReturn(onwardRoute)
      mutableFakeDataRetrievalAction.setDataToReturn(Some(userAnswersTwoSchemesAnnual))

      val request =
        FakeRequest(POST, httpPathPOSTAnnualAllowance)
          .withFormUrlEncodedBody(("value", "false"))
      val result = route(application, request).value

      status(result) mustEqual SEE_OTHER

      redirectLocation(result).value mustEqual onwardRoute.url
      verify(mockUserAnswersCacheConnector, times(0)).savePartial(any(), any(), any(), any())(any(), any())
      verify(mockCompoundNavigator, times(1)).nextPage(any(), any(), any(), any(), any(), any(), any())(any())
    }

    "redirect to the next page when valid data with one scheme is submitted for LifetimeAllowance" in {
      when(mockUserAnswersCacheConnector.savePartial(any(), any(), any(), any())(any(), any())) thenReturn Future.successful(Json.obj())
      when(mockCompoundNavigator.nextPage(any(), any(), any(), any(), any(), any(), any())(any())).thenReturn(onwardRoute)

      mutableFakeDataRetrievalAction.setDataToReturn(Some(userAnswersOneSchemeLifetime))

      val request =
        FakeRequest(POST, httpPathPOSTLifetimeAllowance)
          .withFormUrlEncodedBody(("value", "true"))
      val result = route(application, request).value

      status(result) mustEqual SEE_OTHER

      redirectLocation(result).value mustEqual onwardRoute.url
      verify(mockUserAnswersCacheConnector, times(1)).savePartial(any(), any(), any(), any())(any(), any())
      verify(mockCompoundNavigator, times(1)).nextPage(any(), any(), any(), any(), any(), any(), any())(any())
    }

    "redirect to the next page when valid data with two scheme is submitted for LifetimeAllowance" in {
      when(mockUserAnswersCacheConnector.savePartial(any(), any(), any(), any())(any(), any())) thenReturn Future.successful(Json.obj())
      when(mockCompoundNavigator.nextPage(any(), any(), any(), any(), any(), any(), any())(any())).thenReturn(onwardRoute)

      mutableFakeDataRetrievalAction.setDataToReturn(Some(userAnswersTwoSchemesLifetime))

      val request =
        FakeRequest(POST, httpPathPOSTLifetimeAllowance)
          .withFormUrlEncodedBody(("value", "true"))
      val result = route(application, request).value

      status(result) mustEqual SEE_OTHER

      redirectLocation(result).value mustEqual onwardRoute.url
      verify(mockUserAnswersCacheConnector, times(1)).savePartial(any(), any(), any(), any())(any(), any())
      verify(mockCompoundNavigator, times(1)).nextPage(any(), any(), any(), any(), any(), any(), any())(any())
    }

    "redirect to the next page when valid data and user select no to remove scheme for LifetimeAllowance" in {
      when(mockCompoundNavigator.nextPage(any(), any(), any(), any(), any(), any(), any())(any())).thenReturn(onwardRoute)

      mutableFakeDataRetrievalAction.setDataToReturn(Some(userAnswersTwoSchemesLifetime))

      val request =
        FakeRequest(POST, httpPathPOSTLifetimeAllowance)
          .withFormUrlEncodedBody(("value", "false"))
      val result = route(application, request).value

      status(result) mustEqual SEE_OTHER

      redirectLocation(result).value mustEqual onwardRoute.url
      verify(mockUserAnswersCacheConnector, times(0)).savePartial(any(), any(), any(), any())(any(), any())
      verify(mockCompoundNavigator, times(1)).nextPage(any(), any(), any(), any(), any(), any(), any())(any())
    }

    "redirect to Session Expired for a POST if no existing data is found" in {

      mutableFakeDataRetrievalAction.setDataToReturn(None)

      val request =
        FakeRequest(POST, httpPathPOSTAnnualAllowance)
          .withFormUrlEncodedBody(("value", "true"))

      val result = route(application, request).value

      status(result) mustEqual SEE_OTHER

      redirectLocation(result).value mustEqual controllers.routes.SessionExpiredController.onPageLoad.url
    }
  }
}
