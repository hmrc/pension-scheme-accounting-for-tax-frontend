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

package behaviours

import java.time.LocalDate

import base.SpecBase
import matchers.JsonMatchers
import models.chargeF.ChargeDetails
import models.{GenericViewModel, NormalMode}
import org.mockito.ArgumentCaptor
import org.mockito.Matchers.any
import org.mockito.Mockito.{times, verify}
import pages.ChargeDetailsPage
import play.api.data.Form
import play.api.http.HeaderNames
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.{AnyContentAsEmpty, AnyContentAsFormUrlEncoded}
import play.api.test.Helpers._
import play.api.test.{FakeHeaders, FakeRequest}
import uk.gov.hmrc.viewmodels.{DateInput, NunjucksSupport}

trait ControllerBehaviours extends SpecBase with NunjucksSupport with JsonMatchers {

  private def httpGETRequest(path:String): FakeRequest[AnyContentAsEmpty.type] = FakeRequest(GET, path)

  private def httpPOSTRequest(path: String, values:Map[String, Seq[String]]): FakeRequest[AnyContentAsFormUrlEncoded] =
    FakeRequest
      .apply(
        method = POST,
        uri = path,
        headers = FakeHeaders(Seq(HeaderNames.HOST -> "localhost")),
        body = AnyContentAsFormUrlEncoded(values))

  /*
  //    "return OK and the correct view for a GET" in {
//      val application = applicationBuilder(userAnswers = Some(userAnswersWithSchemeName)).build()
//      val templateCaptor = ArgumentCaptor.forClass(classOf[String])
//      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])
//
//      val result = route(application, httpGETRequest).value
//
//      status(result) mustEqual OK
//
//      verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())
//
//      val viewModel = GenericViewModel(
//        submitUrl = controllers.chargeF.routes.ChargeDetailsController.onSubmit(NormalMode, srn).url,
//        returnUrl = frontendAppConfig.managePensionsSchemeSummaryUrl.format(srn),
//        schemeName = schemeName)
//
//      val expectedJson = Json.obj(
//        "form" -> form,
//        "viewModel" -> viewModel,
//        "date" -> DateInput.localDate(form("deregistrationDate"))
//      )
//
//      templateCaptor.getValue mustEqual pageToBeRendered
//      jsonCaptor.getValue must containJson(expectedJson)
//
//      application.stop()
//    }
//
   */

  def controllerWithGET[A](path:String, form:Form[A], pageToBeRendered:String, data:A) = {
    "return OK and the correct view for a GET" in {
      val application = applicationBuilder(userAnswers = Some(userAnswersWithSchemeName)).build()
      val templateCaptor = ArgumentCaptor.forClass(classOf[String])
      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

      val result = route(application, httpGETRequest(path)).value

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

      val result = route(application, httpGETRequest(path)).value

      status(result) mustEqual OK

      verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

      val viewModel = GenericViewModel(
        submitUrl = controllers.chargeF.routes.ChargeDetailsController.onSubmit(NormalMode, srn).url,
        returnUrl = frontendAppConfig.managePensionsSchemeSummaryUrl.format(srn),
        schemeName = schemeName)

      val filledForm = form.fill(data)

      val expectedJson = Json.obj(
        "form" -> filledForm,
        "viewModel" -> viewModel,
        "date" -> DateInput.localDate(filledForm("deregistrationDate"))
      )

      templateCaptor.getValue mustEqual pageToBeRendered

      jsonCaptor.getValue must containJson(expectedJson)

      application.stop()
    }

  }
}
