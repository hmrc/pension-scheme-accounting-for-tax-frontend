/*
 * Copyright 2022 HM Revenue & Customs
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
import models.ChargeType.{ChargeTypeAnnualAllowance, ChargeTypeLifetimeAllowance}
import models.LocalDateBinder._
import models.{ChargeType, GenericViewModel, NormalMode}
import org.mockito.{ArgumentCaptor, ArgumentMatchers}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{times, verify, when}
import org.scalatest.{OptionValues, TryValues}
import org.scalatestplus.mockito.MockitoSugar
import pages.chargeE.MemberDetailsPage
import pages.mccloud.EnterPstrPage
import play.api.Application
import play.api.data.Form
import play.api.i18n.Messages
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.Call
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.twirl.api.Html
import uk.gov.hmrc.viewmodels.NunjucksSupport

import scala.concurrent.Future

class EnterPstrControllerSpec extends ControllerSpecBase
  with MockitoSugar with NunjucksSupport with JsonMatchers with OptionValues with TryValues {
  private val mutableFakeDataRetrievalAction: MutableFakeDataRetrievalAction = new MutableFakeDataRetrievalAction()
  private val application: Application =
    applicationBuilderMutableRetrievalAction(mutableFakeDataRetrievalAction,
      Seq()).build()

  private def onwardRoute = Call("GET", "/foo")

  private val formProvider = new EnterPstrFormProvider()
  private val form: Form[String] = formProvider()

  private def httpPathGET(chargeType: ChargeType, schemeIndex: Int): String = routes.EnterPstrController
    .onPageLoad(chargeType, NormalMode, srn, startDate, accessType, versionInt, 0, schemeIndex).url

  private def httpPathPOST(chargeType: ChargeType, schemeIndex: Int): String = routes.EnterPstrController
    .onSubmit(chargeType, NormalMode, srn, startDate, accessType, versionInt, 0, schemeIndex).url

  private val viewModelAnnual = GenericViewModel(
    submitUrl = httpPathPOST(ChargeTypeAnnualAllowance, 0),
    returnUrl = controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, versionInt).url,
    schemeName = schemeName)

  private val viewModelLifetime = GenericViewModel(
    submitUrl = httpPathPOST(ChargeTypeLifetimeAllowance, 1),
    returnUrl = controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, versionInt).url,
    schemeName = schemeName)

  private def userAnswers = userAnswersWithSchemeNamePstrQuarter
    .set(MemberDetailsPage(0), memberDetails).success.value
    .set(MemberDetailsPage(1), memberDetails).success.value

  "EnterPstrController Controller" must {

    "return OK and the correct view for a GET (Annual first scheme)" in {
      when(mockRenderer.render(any(), any())(any())).thenReturn(Future.successful(Html("")))

      mutableFakeDataRetrievalAction.setDataToReturn(Some(userAnswers))
      val request = FakeRequest(GET, httpPathGET(ChargeTypeAnnualAllowance, 0))
      val templateCaptor = ArgumentCaptor.forClass(classOf[String])
      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

      val result = route(application, request).value

      status(result) mustEqual OK

      verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

      val expectedJson = Json.obj(
        "form" -> form,
        "viewModel" -> viewModelAnnual,
        "chargeTitle" -> Messages("enterPstr.title.annual", "")
      )

      templateCaptor.getValue mustEqual "mccloud/enterPstr.njk"
      jsonCaptor.getValue must containJson(expectedJson)
    }

    "return OK and the correct view for a GET (Lifetime second scheme)" in {
      when(mockRenderer.render(any(), any())(any())).thenReturn(Future.successful(Html("")))

      mutableFakeDataRetrievalAction.setDataToReturn(Some(userAnswers))
      val request = FakeRequest(GET, httpPathGET(ChargeTypeLifetimeAllowance, 1))
      val templateCaptor = ArgumentCaptor.forClass(classOf[String])
      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

      val result = route(application, request).value

      status(result) mustEqual OK

      verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

      val expectedJson = Json.obj(
        "form" -> form,
        "viewModel" -> viewModelLifetime,
        "chargeTitle" -> Messages("enterPstr.title.lifetime", "second")
      )

      templateCaptor.getValue mustEqual "mccloud/enterPstr.njk"
      jsonCaptor.getValue must containJson(expectedJson)
    }

    "return Bad Request and the correct view for a GET (Lifetime schemeIndex 5)" in {
      when(mockRenderer.render(ArgumentMatchers.eq("badRequest.njk"), any())(any())).thenReturn(Future.successful(Html("")))

      mutableFakeDataRetrievalAction.setDataToReturn(Some(userAnswers))
      val request = FakeRequest(GET, httpPathGET(ChargeTypeLifetimeAllowance, 5))

      val result = route(application, request).value

      status(result) mustEqual BAD_REQUEST

      verify(mockRenderer, times(1)).render(ArgumentMatchers.eq("badRequest.njk"), any())(any())
    }

    "return Bad Request and the correct view for a GET (Lifetime schemeIndex -1)" in {
      when(mockRenderer.render(ArgumentMatchers.eq("badRequest.njk"), any())(any())).thenReturn(Future.successful(Html("")))

      mutableFakeDataRetrievalAction.setDataToReturn(Some(userAnswers))
      val request = FakeRequest(GET, httpPathGET(ChargeTypeLifetimeAllowance, -1))

      val result = route(application, request).value

      status(result) mustEqual BAD_REQUEST

      verify(mockRenderer, times(1)).render(ArgumentMatchers.eq("badRequest.njk"), any())(any())

    }

    "return Bad Request and the correct view for a GET (Annual schemeIndex 5)" in {
      when(mockRenderer.render(ArgumentMatchers.eq("badRequest.njk"), any())(any())).thenReturn(Future.successful(Html("")))

      mutableFakeDataRetrievalAction.setDataToReturn(Some(userAnswers))
      val request = FakeRequest(GET, httpPathGET(ChargeTypeAnnualAllowance, 5))

      val result = route(application, request).value

      status(result) mustEqual BAD_REQUEST

      verify(mockRenderer, times(1)).render(ArgumentMatchers.eq("badRequest.njk"), any())(any())
    }

    "return Bad Request and the correct view for a GET (Annual schemeIndex -1)" in {
      when(mockRenderer.render(ArgumentMatchers.eq("badRequest.njk"), any())(any())).thenReturn(Future.successful(Html("")))

      mutableFakeDataRetrievalAction.setDataToReturn(Some(userAnswers))
      val request = FakeRequest(GET, httpPathGET(ChargeTypeAnnualAllowance, -1))

      val result = route(application, request).value

      status(result) mustEqual BAD_REQUEST

      verify(mockRenderer, times(1)).render(ArgumentMatchers.eq("badRequest.njk"), any())(any())

    }

    "redirect to Session Expired for a GET if no existing data is found (Annual first scheme)" in {

      mutableFakeDataRetrievalAction.setDataToReturn(None)

      val request = FakeRequest(GET, httpPathGET(ChargeTypeAnnualAllowance, 0))

      val result = route(application, request).value

      status(result) mustEqual SEE_OTHER

      redirectLocation(result).value mustEqual controllers.routes.SessionExpiredController.onPageLoad.url
    }

    "redirect to Session Expired for a GET if no existing data is found (Lifetime first scheme)" in {

      mutableFakeDataRetrievalAction.setDataToReturn(None)

      val request = FakeRequest(GET, httpPathGET(ChargeTypeLifetimeAllowance, 0))

      val result = route(application, request).value

      status(result) mustEqual SEE_OTHER

      redirectLocation(result).value mustEqual controllers.routes.SessionExpiredController.onPageLoad.url
    }

    "redirect to the next page when valid data is submitted (Annual first scheme)" in {
      when(mockUserAnswersCacheConnector.savePartial(any(), any(), any(), any())(any(), any())) thenReturn Future.successful(Json.obj())
      when(mockCompoundNavigator.nextPage(any(), any(), any(), any(), any(), any(), any())(any())).thenReturn(onwardRoute)

      mutableFakeDataRetrievalAction.setDataToReturn(Some(userAnswers))

      val request =
        FakeRequest(POST, httpPathPOST(ChargeTypeAnnualAllowance, 0))
          .withFormUrlEncodedBody(("value", "12345678RA"))

      val result = route(application, request).value

      status(result) mustEqual SEE_OTHER

      redirectLocation(result).value mustEqual onwardRoute.url
    }

    "redirect to the next page when valid data is submitted (Lifetime second scheme)" in {
      when(mockUserAnswersCacheConnector.savePartial(any(), any(), any(), any())(any(), any())) thenReturn Future.successful(Json.obj())
      when(mockCompoundNavigator.nextPage(any(), any(), any(), any(), any(), any(), any())(any())).thenReturn(onwardRoute)

      val updatedUa = userAnswers.set(EnterPstrPage(ChargeTypeLifetimeAllowance, 0, 0), "12345678RL").success.value
      mutableFakeDataRetrievalAction.setDataToReturn(Some(updatedUa))

      val request =
        FakeRequest(POST, httpPathPOST(ChargeTypeLifetimeAllowance, 1))
          .withFormUrlEncodedBody(("value", "12345678RA"))

      val result = route(application, request).value

      status(result) mustEqual SEE_OTHER

      redirectLocation(result).value mustEqual onwardRoute.url
    }

    "return a Bad Request and errors when invalid data is submitted (Annual first scheme)" in {
      when(mockRenderer.render(any(), any())(any())).thenReturn(Future.successful(Html("")))

      mutableFakeDataRetrievalAction.setDataToReturn(Some(userAnswers))

      val request = FakeRequest(POST, httpPathPOST(ChargeTypeAnnualAllowance, 0)).withFormUrlEncodedBody(("value", ""))
      val boundForm = form.bind(Map("value" -> ""))
      val templateCaptor = ArgumentCaptor.forClass(classOf[String])
      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

      val result = route(application, request).value

      status(result) mustEqual BAD_REQUEST

      verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

      val expectedJson = Json.obj(
        "form" -> boundForm,
        "viewModel" -> viewModelAnnual,
        "chargeTitle" -> Messages("enterPstr.title.annual", "")
      )

      templateCaptor.getValue mustEqual "mccloud/enterPstr.njk"

      jsonCaptor.getValue must containJson(expectedJson)
    }

    "return a Bad Request and errors when invalid data is submitted (Lifetime second schemeIndex)" in {
      when(mockRenderer.render(any(), any())(any())).thenReturn(Future.successful(Html("")))

      mutableFakeDataRetrievalAction.setDataToReturn(Some(userAnswers))

      val request = FakeRequest(POST, httpPathPOST(ChargeTypeLifetimeAllowance, 1)).withFormUrlEncodedBody(("value", ""))
      val boundForm = form.bind(Map("value" -> ""))
      val templateCaptor = ArgumentCaptor.forClass(classOf[String])
      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

      val result = route(application, request).value

      status(result) mustEqual BAD_REQUEST

      verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

      val expectedJson = Json.obj(
        "form" -> boundForm,
        "viewModel" -> viewModelLifetime,
        "chargeTitle" -> Messages("enterPstr.title.lifetime", "second")
      )

      templateCaptor.getValue mustEqual "mccloud/enterPstr.njk"

      jsonCaptor.getValue must containJson(expectedJson)
    }

    "redirect to Session Expired for a POST if no existing data is found (Annual first scheme)" in {

      mutableFakeDataRetrievalAction.setDataToReturn(None)

      val request =
        FakeRequest(POST, httpPathPOST(ChargeTypeAnnualAllowance, 0))
          .withFormUrlEncodedBody(("value", "true"))

      val result = route(application, request).value

      status(result) mustEqual SEE_OTHER

      redirectLocation(result).value mustEqual controllers.routes.SessionExpiredController.onPageLoad.url
    }

    "redirect to Session Expired for a POST if no existing data is found (Lifetime first scheme)" in {

      mutableFakeDataRetrievalAction.setDataToReturn(None)

      val request =
        FakeRequest(POST, httpPathPOST(ChargeTypeLifetimeAllowance, 0))
          .withFormUrlEncodedBody(("value", "true"))

      val result = route(application, request).value

      status(result) mustEqual SEE_OTHER

      redirectLocation(result).value mustEqual controllers.routes.SessionExpiredController.onPageLoad.url
    }

    "return a Bad Request and errors when invalid data is submitted (Annual with schemeIndex -1)" in {
      when(mockRenderer.render(ArgumentMatchers.eq("badRequest.njk"), any())(any())).thenReturn(Future.successful(Html("")))

      mutableFakeDataRetrievalAction.setDataToReturn(Some(userAnswers))

      val request = FakeRequest(POST, httpPathPOST(ChargeTypeAnnualAllowance, -1)).withFormUrlEncodedBody(("value", ""))
      val boundForm = form.bind(Map("value" -> ""))
      val result = route(application, request).value

      status(result) mustEqual BAD_REQUEST

      verify(mockRenderer, times(1)).render(ArgumentMatchers.eq("badRequest.njk"), any())(any())
    }

    "return a Bad Request and errors when invalid data is submitted (Annual with schemeIndex 5)" in {
      when(mockRenderer.render(ArgumentMatchers.eq("badRequest.njk"), any())(any())).thenReturn(Future.successful(Html("")))

      mutableFakeDataRetrievalAction.setDataToReturn(Some(userAnswers))

      val request = FakeRequest(POST, httpPathPOST(ChargeTypeAnnualAllowance, 5)).withFormUrlEncodedBody(("value", ""))
      val boundForm = form.bind(Map("value" -> ""))
      val result = route(application, request).value

      status(result) mustEqual BAD_REQUEST

      verify(mockRenderer, times(1)).render(ArgumentMatchers.eq("badRequest.njk"), any())(any())
    }

    "return a Bad Request and errors when invalid data is submitted (Lifetime with schemeIndex -1)" in {
      when(mockRenderer.render(ArgumentMatchers.eq("badRequest.njk"), any())(any())).thenReturn(Future.successful(Html("")))

      mutableFakeDataRetrievalAction.setDataToReturn(Some(userAnswers))

      val request = FakeRequest(POST, httpPathPOST(ChargeTypeLifetimeAllowance, -1)).withFormUrlEncodedBody(("value", ""))
      val boundForm = form.bind(Map("value" -> ""))
      val result = route(application, request).value

      status(result) mustEqual BAD_REQUEST

      verify(mockRenderer, times(1)).render(ArgumentMatchers.eq("badRequest.njk"), any())(any())
    }

    "return a Bad Request and errors when invalid data is submitted (Lifetime with schemeIndex 5)" in {
      when(mockRenderer.render(ArgumentMatchers.eq("badRequest.njk"), any())(any())).thenReturn(Future.successful(Html("")))

      mutableFakeDataRetrievalAction.setDataToReturn(Some(userAnswers))

      val request = FakeRequest(POST, httpPathPOST(ChargeTypeLifetimeAllowance, 5)).withFormUrlEncodedBody(("value", ""))
      val boundForm = form.bind(Map("value" -> ""))
      val result = route(application, request).value

      status(result) mustEqual BAD_REQUEST

      verify(mockRenderer, times(1)).render(ArgumentMatchers.eq("badRequest.njk"), any())(any())
    }
  }
}
