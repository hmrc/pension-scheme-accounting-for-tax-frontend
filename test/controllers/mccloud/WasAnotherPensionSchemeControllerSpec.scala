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
import models.NormalMode
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatest.{OptionValues, TryValues}
import org.scalatestplus.mockito.MockitoSugar
import pages.chargeE.MemberDetailsPage
import play.api.Application
import play.api.data.Form
import play.api.i18n.Messages
import play.api.libs.json.Json
import play.api.mvc.Call
import play.api.test.FakeRequest
import play.api.test.Helpers._
import viewmodels.TwirlRadios
import views.html.mccloud.WasAnotherPensionScheme

import scala.concurrent.Future

class WasAnotherPensionSchemeControllerSpec
  extends ControllerSpecBase
    with MockitoSugar
    with JsonMatchers
    with OptionValues
    with TryValues {
  private val mutableFakeDataRetrievalAction: MutableFakeDataRetrievalAction = new MutableFakeDataRetrievalAction()
  private val application: Application =
    applicationBuilderMutableRetrievalAction(mutableFakeDataRetrievalAction, Seq()).build()

  private def onwardRoute = Call("GET", "/foo")

  private val formProvider = new YesNoFormProvider()
  private val chargeTypeDescription = Messages(s"chargeType.description.${ChargeTypeAnnualAllowance.toString}")
  private val form: Form[Boolean] = formProvider(messages("wasAnotherPensionScheme.error.required", chargeTypeDescription))

  private def httpPathGETAnnualAllowance: String =
    routes.WasAnotherPensionSchemeController
      .onPageLoad(ChargeTypeAnnualAllowance, NormalMode, srn, startDate, accessType, versionInt, 0)
      .url

  private val annualAllowanceSubmitCall = routes.WasAnotherPensionSchemeController
    .onSubmit(ChargeTypeAnnualAllowance, NormalMode, srn, startDate, accessType, versionInt, 0)

  private def httpPathGETLifetimeAllowance: String =
    routes.WasAnotherPensionSchemeController
      .onPageLoad(ChargeTypeLifetimeAllowance, NormalMode, srn, startDate, accessType, versionInt, 0)
      .url

  private val lifetimeAllowanceSubmitCall = routes.WasAnotherPensionSchemeController
    .onSubmit(ChargeTypeLifetimeAllowance, NormalMode, srn, startDate, accessType, versionInt, 0)


  private def userAnswers =
    userAnswersWithSchemeNamePstrQuarter
      .set(MemberDetailsPage(0), memberDetails)
      .success
      .value
      .set(MemberDetailsPage(1), memberDetails)
      .success
      .value

  "WasAnotherPensionScheme Controller" must {

    "return OK and the correct view for a GET for AnnualAllowance" in {

      mutableFakeDataRetrievalAction.setDataToReturn(Some(userAnswers))
      val request = FakeRequest(GET, httpPathGETAnnualAllowance)

      val result = route(application, request).value

      status(result) mustEqual OK

      val view = application.injector.instanceOf[WasAnotherPensionScheme].apply(
        form,
        TwirlRadios.yesNo(form("value")),
        "chargeType.description.annualAllowance",
        annualAllowanceSubmitCall,
        controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, versionInt).url,
        schemeName
      )(request, messages)

      compareResultAndView(result, view)
    }


    "return OK and the correct view for a GET for LifetimeAllowance" in {

      mutableFakeDataRetrievalAction.setDataToReturn(Some(userAnswers))
      val request = FakeRequest(GET, httpPathGETLifetimeAllowance)

      val result = route(application, request).value

      status(result) mustEqual OK

      val view = application.injector.instanceOf[WasAnotherPensionScheme].apply(
        form,
        TwirlRadios.yesNo(form("value")),
        "chargeType.description.lifeTimeAllowance",
        lifetimeAllowanceSubmitCall,
        controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, versionInt).url,
        schemeName
      )(request, messages)

      compareResultAndView(result, view)
    }

    "redirect to the next page when valid data is submitted for AnnualAllowance" in {
      when(mockUserAnswersCacheConnector.savePartial(any(), any(), any(), any())(any(), any())) `thenReturn` Future.successful(Json.obj())
      when(mockCompoundNavigator.nextPage(any(), any(), any(), any(), any(), any(), any())(any())).thenReturn(onwardRoute)

      mutableFakeDataRetrievalAction.setDataToReturn(Some(userAnswers))

      val request =
        FakeRequest(POST, httpPathGETAnnualAllowance)
          .withFormUrlEncodedBody(("value", "true"))

      val result = route(application, request).value

      status(result) mustEqual SEE_OTHER

      redirectLocation(result).value mustEqual onwardRoute.url
    }

    "redirect to the next page when valid data is submitted and user select not to wasAnotherPensionScheme for AnnualAllowance" in {
      when(mockUserAnswersCacheConnector.savePartial(any(), any(), any(), any())(any(), any())) `thenReturn` Future.successful(Json.obj())
      when(mockCompoundNavigator.nextPage(any(), any(), any(), any(), any(), any(), any())(any())).thenReturn(onwardRoute)

      mutableFakeDataRetrievalAction.setDataToReturn(Some(userAnswers))

      val request =
        FakeRequest(POST, httpPathGETAnnualAllowance)
          .withFormUrlEncodedBody(("value", "false"))

      val result = route(application, request).value

      status(result) mustEqual SEE_OTHER

      redirectLocation(result).value mustEqual onwardRoute.url
    }

    "redirect to the next page when valid data is submitted for LifetimeAllowance" in {
      when(mockUserAnswersCacheConnector.savePartial(any(), any(), any(), any())(any(), any())) `thenReturn` Future.successful(Json.obj())
      when(mockCompoundNavigator.nextPage(any(), any(), any(), any(), any(), any(), any())(any())).thenReturn(onwardRoute)

      mutableFakeDataRetrievalAction.setDataToReturn(Some(userAnswers))

      val request =
        FakeRequest(POST, httpPathGETLifetimeAllowance)
          .withFormUrlEncodedBody(("value", "true"))

      val result = route(application, request).value

      status(result) mustEqual SEE_OTHER

      redirectLocation(result).value mustEqual onwardRoute.url
    }

    "redirect to the next page when valid data is submitted and user select not to wasAnotherPensionScheme for LifetimeAllowance" in {
      when(mockUserAnswersCacheConnector.savePartial(any(), any(), any(), any())(any(), any())) `thenReturn` Future.successful(Json.obj())
      when(mockCompoundNavigator.nextPage(any(), any(), any(), any(), any(), any(), any())(any())).thenReturn(onwardRoute)

      mutableFakeDataRetrievalAction.setDataToReturn(Some(userAnswers))

      val request =
        FakeRequest(POST, httpPathGETLifetimeAllowance)
          .withFormUrlEncodedBody(("value", "false"))

      val result = route(application, request).value

      status(result) mustEqual SEE_OTHER

      redirectLocation(result).value mustEqual onwardRoute.url
    }

    "redirect to Session Expired for a GET if no existing data is found" in {

      mutableFakeDataRetrievalAction.setDataToReturn(None)

      val request = FakeRequest(GET, httpPathGETAnnualAllowance)

      val result = route(application, request).value

      status(result) mustEqual SEE_OTHER

      redirectLocation(result).value mustEqual controllers.routes.SessionExpiredController.onPageLoad.url
    }

    "redirect to Session Expired for a POST if no existing data is found" in {

      mutableFakeDataRetrievalAction.setDataToReturn(None)

      val request =
        FakeRequest(POST, httpPathGETAnnualAllowance)
          .withFormUrlEncodedBody(("value", "true"))

      val result = route(application, request).value

      status(result) mustEqual SEE_OTHER

      redirectLocation(result).value mustEqual controllers.routes.SessionExpiredController.onPageLoad.url
    }
  }
}
