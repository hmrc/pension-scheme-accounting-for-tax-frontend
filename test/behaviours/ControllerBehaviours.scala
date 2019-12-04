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

import base.SpecBase
import matchers.JsonMatchers
import models.{GenericViewModel, NormalMode}
import org.mockito.{ArgumentCaptor, Matchers}
import org.mockito.Matchers.any
import org.mockito.Mockito.{reset, times, verify, when}
import pages.QuestionPage
import play.api.data.Form
import play.api.http.HeaderNames
import play.api.libs.json.{JsObject, JsValue, Json, Writes}
import play.api.mvc.{AnyContentAsEmpty, AnyContentAsFormUrlEncoded}
import play.api.test.Helpers._
import play.api.test.{FakeHeaders, FakeRequest}
import play.twirl.api.Html
import uk.gov.hmrc.viewmodels.{DateInput, NunjucksSupport}

import scala.concurrent.Future

trait ControllerBehaviours extends SpecBase with NunjucksSupport with JsonMatchers {

  override def beforeEach: Unit = {
    reset(mockRenderer)
    when(mockRenderer.render(any(), any())(any())).thenReturn(Future.successful(Html("")))
    reset(mockUserAnswersCacheConnector)
    reset(mockCompoundNavigator)
  }

  private def httpGETRequest(path:String): FakeRequest[AnyContentAsEmpty.type] = FakeRequest(GET, path)

  private def httpPOSTRequest(path: String, values:Map[String, Seq[String]]): FakeRequest[AnyContentAsFormUrlEncoded] =
    FakeRequest
      .apply(
        method = POST,
        uri = path,
        headers = FakeHeaders(Seq(HeaderNames.HOST -> "localhost")),
        body = AnyContentAsFormUrlEncoded(values))

  def controllerWithGET[A](path: => String,
                           form:Form[A],
                           pageToBeRendered:String,
                           data:A,
                           page:QuestionPage[A],
                           jsonForPage: JsObject)(implicit writes:Writes[A]): Unit = {
    "return OK and the correct view for a GET" in {
      val application = applicationBuilder(userAnswers = Some(userAnswersWithSchemeName)).build()
      val templateCaptor = ArgumentCaptor.forClass(classOf[String])
      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

      val result = route(application, httpGETRequest(path)).value

      status(result) mustEqual OK

      verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

      templateCaptor.getValue mustEqual pageToBeRendered

      jsonCaptor.getValue must containJson(jsonForPage)

      application.stop()
    }

    "return OK and the correct view for a GET when the question has previously been answered" in {
      val ua = userAnswersWithSchemeName.set(page, data).get

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

  def controllerWithPOST[A](path: => String,
                            form:Form[A],
                            pageToBeRendered:String,
                            data:A,
                            page:QuestionPage[A],
                            requestValuesValid:Map[String, Seq[String]],
                            requestValuesInvalid:Map[String, Seq[String]])(implicit writes:Writes[A]):Unit = {
    "Save data to user answers and redirect to next page when valid data is submitted" in {

      when(mockCompoundNavigator.nextPage(Matchers.eq(page),any(),any(),any())(any(),any())).thenReturn(dummyCall)

      val application = applicationBuilder(userAnswers = Some(userAnswersWithSchemeName)).build()
      val expectedJson = Json.obj(page.toString -> Json.toJson(data) )
      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])
      val json = Json.obj()

      when(mockUserAnswersCacheConnector.save(any(),any())(any(),any())).thenReturn(Future.successful(json))

      val result = route(application, httpPOSTRequest(path, requestValuesValid)).value

      status(result) mustEqual SEE_OTHER

      verify(mockUserAnswersCacheConnector, times(1)).save(any(), jsonCaptor.capture)(any(),any())

      jsonCaptor.getValue must containJson(expectedJson)

      redirectLocation(result) mustBe Some(dummyCall.url)

      application.stop()
    }

    "return a BAD REQUEST when invalid data is submitted" in {
      val application = applicationBuilder(userAnswers = Some(userAnswersWithSchemeName)).build()


      val result = route(application, httpPOSTRequest(path, requestValuesInvalid)).value

      status(result) mustEqual BAD_REQUEST

      verify(mockUserAnswersCacheConnector, times(0)).save(any(), any())(any(),any())

      application.stop()
    }

  }
}
