/*
 * Copyright 2019 HM Revenue & Customs
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

import java.time.{LocalDate, ZoneOffset}

import base.SpecBase
import forms.ChargeDetailsFormProvider
import matchers.JsonMatchers
import models.{NormalMode, UserAnswers}
import org.mockito.ArgumentCaptor
import org.mockito.Matchers.any
import org.mockito.Mockito.{times, verify, when}
import org.scalatestplus.mockito.MockitoSugar
import pages.ChargeDetailsPage
import play.api.inject.bind
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.{AnyContentAsEmpty, AnyContentAsFormUrlEncoded, Call}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.twirl.api.Html
import uk.gov.hmrc.viewmodels.{DateInput, NunjucksSupport}

import scala.concurrent.Future

class ChargeDetailsControllerSpec extends SpecBase with MockitoSugar with NunjucksSupport with JsonMatchers {

//  val formProvider = new ChargeDetailsFormProvider()
//  private def form = formProvider()
//
//  private def onwardRoute = Call("GET", "/foo")
//
//  private val validAnswer = LocalDate.now(ZoneOffset.UTC)

  //private lazy val chargeDetailsRoute = controllers.chargeF.routes.ChargeDetailsController.onPageLoad(NormalMode).url

//  private override val emptyUserAnswers = UserAnswers()
//
//  private def getRequest(): FakeRequest[AnyContentAsEmpty.type] =
//    FakeRequest(GET, chargeDetailsRoute)
//
//  private def postRequest(): FakeRequest[AnyContentAsFormUrlEncoded] =
//    FakeRequest(POST, chargeDetailsRoute)
//      .withFormUrlEncodedBody(
//        "value.day"   -> validAnswer.getDayOfMonth.toString,
//        "value.month" -> validAnswer.getMonthValue.toString,
//        "value.year"  -> validAnswer.getYear.toString
//      )

  "ChargeDetails Controller" must {

//    "must return OK and the correct view for a GET" in {
//
//      when(mockRenderer.render(any(), any())(any()))
//        .thenReturn(Future.successful(Html("")))
//
//      val application = applicationBuilder(userAnswers = Some(emptyUserAnswers)).build()
//      val templateCaptor = ArgumentCaptor.forClass(classOf[String])
//      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])
//
//      val result = route(application, getRequest).value
//
//      status(result) mustEqual OK
//
//      verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())
//
//      val viewModel = DateInput.localDate(form("value"))
//
//      val expectedJson = Json.obj(
//        "form" -> form,
//        "mode" -> NormalMode.toString,
//        "date" -> viewModel
//      )
//
//      templateCaptor.getValue mustEqual "chargeDetails.njk"
//      jsonCaptor.getValue must containJson(expectedJson)
//
//      application.stop()
//    }
//
//    "must populate the view correctly on a GET when the question has previously been answered" in {
//
//      when(mockRenderer.render(any(), any())(any()))
//        .thenReturn(Future.successful(Html("")))
//
//      val userAnswers = UserAnswers().set(ChargeDetailsPage, validAnswer).success.value
//      val application = applicationBuilder(userAnswers = Some(userAnswers)).build()
//      val templateCaptor = ArgumentCaptor.forClass(classOf[String])
//      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])
//
//      val result = route(application, getRequest).value
//
//      status(result) mustEqual OK
//
//      verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())
//
//      val filledForm = form.bind(
//        Map(
//          "value.day"   -> validAnswer.getDayOfMonth.toString,
//          "value.month" -> validAnswer.getMonthValue.toString,
//          "value.year"  -> validAnswer.getYear.toString
//        )
//      )
//
//      val viewModel = DateInput.localDate(filledForm("value"))
//
//      val expectedJson = Json.obj(
//        "form" -> filledForm,
//        "mode" -> NormalMode,
//        "date" -> viewModel
//      )
//
//      templateCaptor.getValue mustEqual "chargeDetails.njk"
//      jsonCaptor.getValue must containJson(expectedJson)
//
//      application.stop()
//    }

//    "must redirect to the next page when valid data is submitted" in {
//
//      val application =
//        applicationBuilder(userAnswers = Some(emptyUserAnswers))
//          .build()
//
//      val result = route(application, postRequest).value
//
//      status(result) mustEqual SEE_OTHER
//
//      redirectLocation(result).value mustEqual onwardRoute.url
//
//      application.stop()
//    }

//    "must return a Bad Request and errors when invalid data is submitted" in {
//
//      when(mockRenderer.render(any(), any())(any()))
//        .thenReturn(Future.successful(Html("")))
//
//      val application = applicationBuilder(userAnswers = Some(emptyUserAnswers)).build()
//      val request = FakeRequest(POST, chargeDetailsRoute).withFormUrlEncodedBody(("value", "invalid value"))
//      val boundForm = form.bind(Map("value" -> "invalid value"))
//      val templateCaptor = ArgumentCaptor.forClass(classOf[String])
//      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])
//
//      val result = route(application, request).value
//
//      status(result) mustEqual BAD_REQUEST
//
//      verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())
//
//      val viewModel = DateInput.localDate(boundForm("value"))
//
//      val expectedJson = Json.obj(
//        "form" -> boundForm,
//        "mode" -> NormalMode,
//        "date" -> viewModel
//      )
//
//      templateCaptor.getValue mustEqual "chargeDetails.njk"
//      jsonCaptor.getValue must containJson(expectedJson)
//
//      application.stop()
//    }

//    "must redirect to Session Expired for a GET if no existing data is found" in {
//
//      val application = applicationBuilder(userAnswers = None).build()
//
//      val result = route(application, getRequest).value
//
//      status(result) mustEqual SEE_OTHER
//      redirectLocation(result).value mustEqual routes.SessionExpiredController.onPageLoad().url
//
//      application.stop()
//    }
//
//    "must redirect to Session Expired for a POST if no existing data is found" in {
//
//      val application = applicationBuilder(userAnswers = None).build()
//
//      val result = route(application, postRequest).value
//
//      status(result) mustEqual SEE_OTHER
//
//      redirectLocation(result).value mustEqual routes.SessionExpiredController.onPageLoad().url
//
//      application.stop()
//    }
  }
}
