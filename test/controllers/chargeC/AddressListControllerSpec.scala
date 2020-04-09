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

///*
// * Copyright 2020 HM Revenue & Customs
// *
// * Licensed under the Apache License, Version 2.0 (the "License");
// * you may not use this file except in compliance with the License.
// * You may obtain a copy of the License at
// *
// *     http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS,
// * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// * See the License for the specific language governing permissions and
// * limitations under the License.
// */
//
//package controllers.chargeC
//
//import config.FrontendAppConfig
//import controllers.base.ControllerSpecBase
//import data.SampleData._
//import forms.chargeC.AddressListFormProvider
//import matchers.JsonMatchers
//import models.{AddressList, GenericViewModel, NormalMode, UserAnswers}
//import org.mockito.ArgumentCaptor
//import org.mockito.Matchers.any
//import org.mockito.Mockito.{times, verify, when}
//import org.scalatest.{OptionValues, TryValues}
//import org.scalatestplus.mockito.MockitoSugar
//import pages.chargeC.AddressListPage
//import play.api.inject.bind
//import play.api.libs.json.{JsObject, Json}
//import play.api.mvc.Call
//import play.api.test.FakeRequest
//import play.api.test.Helpers._
//import play.twirl.api.Html
//import uk.gov.hmrc.viewmodels.NunjucksSupport
//
//import scala.concurrent.Future
//
//class AddressListControllerSpec extends ControllerSpecBase with MockitoSugar with NunjucksSupport with JsonMatchers with OptionValues with TryValues {
//
//  val mockAppConfig: FrontendAppConfig = mock[FrontendAppConfig]
//  def onwardRoute = Call("GET", "/foo")
//
//  def addressListRoute = routes.AddressListController.onPageLoad(NormalMode, srn, ).url
//  def addressListSubmitRoute = routes.AddressListController.onSubmit(NormalMode, srn).url
//
//  val formProvider = new AddressListFormProvider()
//  val form = formProvider()
//
//  val viewModel = GenericViewModel(
//    submitUrl = addressListSubmitRoute,
//  returnUrl = onwardRoute.url,
//  schemeName = schemeName)
//
//  val answers: UserAnswers = userAnswersWithSchemeName.set(AddressListPage, AddressList.values.head).success.value
//
//  "AddressList Controller" must {
//
////    "return OK and the correct view for a GET" in {
////      when(mockAppConfig.managePensionsSchemeSummaryUrl).thenReturn(onwardRoute.url)
////      when(mockRenderer.render(any(), any())(any())).thenReturn(Future.successful(Html("")))
////
////      val application = applicationBuilder(userAnswers = Some(userAnswersWithSchemeName))
////        .overrides(
////          bind[FrontendAppConfig].toInstance(mockAppConfig)
////        )
////        .build()
////      val request = FakeRequest(GET, addressListRoute)
////      val templateCaptor = ArgumentCaptor.forClass(classOf[String])
////      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])
////
////      val result = route(application, request).value
////
////      status(result) mustEqual OK
////
////      verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())
////
////      val expectedJson = Json.obj(
////        "form"   -> form,
////        "viewModel" -> viewModel,
////        "radios" -> AddressList.radios(form)
////      )
////
////      templateCaptor.getValue mustEqual "addressList.njk"
////      jsonCaptor.getValue must containJson(expectedJson)
////
////      application.stop()
////    }
//
////    "populate the view correctly on a GET when the question has previously been answered" in {
////      when(mockAppConfig.managePensionsSchemeSummaryUrl).thenReturn(onwardRoute.url)
////      when(mockRenderer.render(any(), any())(any())).thenReturn(Future.successful(Html("")))
////
////      val application = applicationBuilder(userAnswers = Some(answers))
////        .overrides(
////          bind[FrontendAppConfig].toInstance(mockAppConfig)
////        )
////        .build()
////      val request = FakeRequest(GET, addressListRoute)
////      val templateCaptor = ArgumentCaptor.forClass(classOf[String])
////      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])
////
////      val result = route(application, request).value
////
////      status(result) mustEqual OK
////
////      verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())
////
////      val filledForm = form.bind(Map("value" -> AddressList.values.head.toString))
////
////      val expectedJson = Json.obj(
////        "form"   -> filledForm,
////        "viewModel" -> viewModel,
////        "radios" -> AddressList.radios(filledForm)
////      )
////
////      templateCaptor.getValue mustEqual "addressList.njk"
////      jsonCaptor.getValue must containJson(expectedJson)
////
////      application.stop()
////    }
////
////    "redirect to the next page when valid data is submitted" in {
////
////      when(mockAppConfig.managePensionsSchemeSummaryUrl).thenReturn(onwardRoute.url)
////      when(mockUserAnswersCacheConnector.save(any(), any())(any(), any())) thenReturn Future.successful(Json.obj())
////      when(mockCompoundNavigator.nextPage(any(), any(), any(), any())).thenReturn(onwardRoute)
////
////      val application = applicationBuilder(userAnswers = Some(userAnswersWithSchemeName))
////        .overrides(
////          bind[FrontendAppConfig].toInstance(mockAppConfig)
////        )
////        .build()
////
////      val request =
////        FakeRequest(POST, addressListRoute)
////      .withFormUrlEncodedBody(("value", AddressList.values.head.toString))
////
////      val result = route(application, request).value
////
////      status(result) mustEqual SEE_OTHER
////
////      redirectLocation(result).value mustEqual onwardRoute.url
////
////      application.stop()
////    }
////
////    "return a Bad Request and errors when invalid data is submitted" in {
////
////      when(mockAppConfig.managePensionsSchemeSummaryUrl).thenReturn(onwardRoute.url)
////      when(mockRenderer.render(any(), any())(any())).thenReturn(Future.successful(Html("")))
////
////      val application = applicationBuilder(userAnswers = Some(userAnswersWithSchemeName))
////        .overrides(
////          bind[FrontendAppConfig].toInstance(mockAppConfig)
////        )
////        .build()
////      val request = FakeRequest(POST, addressListRoute).withFormUrlEncodedBody(("value", "invalid value"))
////      val boundForm = form.bind(Map("value" -> "invalid value"))
////      val templateCaptor = ArgumentCaptor.forClass(classOf[String])
////      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])
////
////      val result = route(application, request).value
////
////      status(result) mustEqual BAD_REQUEST
////
////      verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())
////
////      val expectedJson = Json.obj(
////        "form"   -> boundForm,
////        "viewModel" -> viewModel,
////        "radios" -> AddressList.radios(boundForm)
////      )
////
////      templateCaptor.getValue mustEqual "addressList.njk"
////      jsonCaptor.getValue must containJson(expectedJson)
////
////      application.stop()
////    }
////
////    "redirect to Session Expired for a GET if no existing data is found" in {
////
////      val application = applicationBuilder(userAnswers = None).build()
////
////      val request = FakeRequest(GET, addressListRoute)
////
////      val result = route(application, request).value
////
////      status(result) mustEqual SEE_OTHER
////      redirectLocation(result).value mustEqual routes.SessionExpiredController.onPageLoad().url
////
////      application.stop()
////    }
////
////    "redirect to Session Expired for a POST if no existing data is found" in {
////
////      val application = applicationBuilder(userAnswers = None).build()
////
////      val request =
////        FakeRequest(POST, addressListRoute)
////      .withFormUrlEncodedBody(("value", AddressList.values.head.toString))
////
////      val result = route(application, request).value
////
////      status(result) mustEqual SEE_OTHER
////
////      redirectLocation(result).value mustEqual routes.SessionExpiredController.onPageLoad().url
////
////      application.stop()
////    }
//  }
//}
