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
import connectors.cache.UserAnswersCacheConnector
import forms.ChargeDetailsFormProvider
import matchers.JsonMatchers
import models.chargeF.ChargeDetails
import models.{GenericViewModel, NormalMode}
import org.mockito.ArgumentCaptor
import org.mockito.Matchers.any
import org.mockito.Mockito.{times, verify, _}
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.mockito.MockitoSugar
import pages.ChargeDetailsPage
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.{AnyContentAsEmpty, AnyContentAsFormUrlEncoded, Call}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.twirl.api.Html
import uk.gov.hmrc.viewmodels.{DateInput, NunjucksSupport}

import scala.concurrent.Future

class ChargeDetailsControllerSpec extends SpecBase with MockitoSugar with NunjucksSupport with JsonMatchers with BeforeAndAfterEach {
  private val pageToBeRendered = "chargeF/chargeDetails.njk"
  private val formProvider = new ChargeDetailsFormProvider()

  private def form = formProvider()

  private def onwardRoute = Call("GET", "/foo")

  private val validAnswer = LocalDate.now(ZoneOffset.UTC)

  private val srn = "aa"

  private lazy val chargeDetailsRoute = controllers.chargeF.routes.ChargeDetailsController.onPageLoad(NormalMode, srn).url

  protected val mockUserAnswersCacheConnector: UserAnswersCacheConnector = mock[UserAnswersCacheConnector]

  private def request: FakeRequest[AnyContentAsEmpty.type] =
    FakeRequest(GET, chargeDetailsRoute)

  private def postRequest(): FakeRequest[AnyContentAsFormUrlEncoded] =
    FakeRequest(POST, chargeDetailsRoute)
      .withFormUrlEncodedBody(
        "value.day" -> validAnswer.getDayOfMonth.toString,
        "value.month" -> validAnswer.getMonthValue.toString,
        "value.year" -> validAnswer.getYear.toString
      )

  override def beforeEach: Unit = {
    reset(mockRenderer)
    when(mockRenderer.render(any(), any())(any())).thenReturn(Future.successful(Html("")))
  }


  "ChargeDetails Controller" must {
    "return OK and the correct view for a GET" in {
      val application = applicationBuilder(userAnswers = Some(userAnswersWithSchemeName)).build()
      val templateCaptor = ArgumentCaptor.forClass(classOf[String])
      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

      val result = route(application, request).value

      status(result) mustEqual OK

      verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

      val viewModel = GenericViewModel(
        submitUrl = controllers.chargeF.routes.ChargeDetailsController.onSubmit(NormalMode, srn).url,
        returnUrl = frontendAppConfig.managePensionsSchemeSummaryUrl.format(srn),
        schemeName = schemeName)

      val expectedJson = Json.obj(
        "form" -> form,
        "viewModel" -> viewModel,
        "date" -> DateInput.localDate(form("deregistrationDate"))
      )

      templateCaptor.getValue mustEqual pageToBeRendered
      jsonCaptor.getValue must containJson(expectedJson)

      application.stop()
    }

    "return OK and the correct view for a GET when the question has previously been answered" in {
      val chargeDetails = ChargeDetails(LocalDate.of(2010, 12, 2), BigDecimal(22.3))

      val ua = userAnswersWithSchemeName.set(ChargeDetailsPage, chargeDetails).get

      val application = applicationBuilder(userAnswers = Some(ua)).build()
      val templateCaptor = ArgumentCaptor.forClass(classOf[String])
      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

      val result = route(application, request).value

      status(result) mustEqual OK

      verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

      val viewModel = GenericViewModel(
        submitUrl = controllers.chargeF.routes.ChargeDetailsController.onSubmit(NormalMode, srn).url,
        returnUrl = frontendAppConfig.managePensionsSchemeSummaryUrl.format(srn),
        schemeName = schemeName)

      val filledForm = form.fill(chargeDetails)

      val expectedJson = Json.obj(
        "form" -> filledForm,
        "viewModel" -> viewModel,
        "date" -> DateInput.localDate(filledForm("deregistrationDate"))
      )

      templateCaptor.getValue mustEqual pageToBeRendered

      jsonCaptor.getValue must containJson(expectedJson)

      application.stop()
    }

    "return a Bad Request and errors when invalid data is submitted" in {
      val application = applicationBuilder(userAnswers = Some(userAnswersWithSchemeName)).build()
      val templateCaptor = ArgumentCaptor.forClass(classOf[String])
      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

      val result = route(application, request).value

      status(result) mustEqual OK

      verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

      val viewModel = GenericViewModel(
        submitUrl = controllers.chargeF.routes.ChargeDetailsController.onSubmit(NormalMode, srn).url,
        returnUrl = frontendAppConfig.managePensionsSchemeSummaryUrl.format(srn),
        schemeName = schemeName)

      val expectedJson = Json.obj(
        "form" -> form,
        "viewModel" -> viewModel,
        "date" -> DateInput.localDate(form("deregistrationDate"))
      )

      templateCaptor.getValue mustEqual pageToBeRendered
      jsonCaptor.getValue must containJson(expectedJson)

      application.stop()
    }

//    "must return a Bad Request and errors when invalid data is submitted" in {
//      val application = applicationBuilder(userAnswers = Some(userAnswersWithSchemeName)).build()
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
