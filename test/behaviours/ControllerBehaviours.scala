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

package behaviours

import controllers.base.ControllerSpecBase
import data.SampleData
import matchers.JsonMatchers
import models.UserAnswers
import org.mockito.Matchers.any
import org.mockito.Mockito.{times, verify, when}
import org.mockito.{ArgumentCaptor, Matchers}
import pages.{Page, QuestionPage}
import play.api.data.Form
import play.api.http.HeaderNames
import play.api.libs.json.{JsObject, Json, Writes}
import play.api.mvc.{AnyContentAsEmpty, AnyContentAsFormUrlEncoded}
import play.api.test.Helpers._
import play.api.test.{FakeHeaders, FakeRequest}
import play.twirl.api.Html
import uk.gov.hmrc.viewmodels.NunjucksSupport

import scala.concurrent.Future

trait ControllerBehaviours extends ControllerSpecBase with NunjucksSupport with JsonMatchers {
  override def beforeEach: Unit = {
    super.beforeEach
    when(mockUserAnswersCacheConnector.save(any(), any())(any(), any())).thenReturn(Future.successful(Json.obj()))
    when(mockRenderer.render(any(), any())(any())).thenReturn(Future.successful(Html("")))
  }

  protected def httpGETRequest(path: String): FakeRequest[AnyContentAsEmpty.type] = FakeRequest(GET, path)

  def httpPOSTRequest(path: String, values: Map[String, Seq[String]]): FakeRequest[AnyContentAsFormUrlEncoded] =
    FakeRequest
      .apply(
        method = POST,
        uri = path,
        headers = FakeHeaders(Seq(HeaderNames.HOST -> "localhost")),
        body = AnyContentAsFormUrlEncoded(values))


  //scalastyle:off method.length
  def controllerWithGET(httpPath: => String,
                        page: Page,
                        templateToBeRendered: String,
                        jsonToPassToTemplate: JsObject,
                        userAnswers: Option[UserAnswers] = Some(SampleData.userAnswersWithSchemeName)): Unit = {
    "return OK and the correct view for a GET" in {
      val application = applicationBuilder(userAnswers = userAnswers).build()
      val templateCaptor = ArgumentCaptor.forClass(classOf[String])
      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

      when(mockCompoundNavigator.nextPage(Matchers.eq(page), any(), any(), any())).thenReturn(SampleData.dummyCall)

      val result = route(application, httpGETRequest(httpPath)).value

      status(result) mustEqual OK

      verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

      templateCaptor.getValue mustEqual templateToBeRendered

      jsonCaptor.getValue must containJson(jsonToPassToTemplate)

      application.stop()
    }
  }

  //scalastyle:off method.length
  def controllerWithGETNeverFilledForm[A](httpPath: => String,
                                          page: QuestionPage[A],
                                          data: A,
                                          form: Form[A],
                                          templateToBeRendered: String,
                                          jsonToPassToTemplate: Form[A] => JsObject)(implicit writes: Writes[A]): Unit = {
    "return OK and the correct view for a GET" in {
      val application = applicationBuilder(userAnswers = Some(SampleData.userAnswersWithSchemeName)).build()
      val templateCaptor = ArgumentCaptor.forClass(classOf[String])
      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

      val result = route(application, httpGETRequest(httpPath)).value

      status(result) mustEqual OK

      verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

      templateCaptor.getValue mustEqual templateToBeRendered

      jsonCaptor.getValue must containJson(jsonToPassToTemplate.apply(form))

      application.stop()
    }

    "redirect to Session Expired page for a GET when there is no data" in {
      val application = applicationBuilder(userAnswers = None).build()

      val result = route(application, httpGETRequest(httpPath)).value

      status(result) mustEqual SEE_OTHER
      redirectLocation(result).value mustBe controllers.routes.SessionExpiredController.onPageLoad().url

      application.stop()
    }
  }

  //scalastyle:off method.length
  def controllerWithGET[A](httpPath: => String,
                           page: QuestionPage[A],
                           data: A,
                           form: Form[A],
                           templateToBeRendered: String,
                           jsonToPassToTemplate: Form[A] => JsObject)(implicit writes: Writes[A]): Unit = {
    "return OK and the correct view for a GET" in {
      val application = applicationBuilder(userAnswers = Some(SampleData.userAnswersWithSchemeName)).build()
      val templateCaptor = ArgumentCaptor.forClass(classOf[String])
      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

      val result = route(application, httpGETRequest(httpPath)).value

      status(result) mustEqual OK

      verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

      templateCaptor.getValue mustEqual templateToBeRendered

      jsonCaptor.getValue must containJson(jsonToPassToTemplate.apply(form))

      application.stop()
    }

    "return OK and the correct view for a GET when the question has previously been answered" in {
      val ua = SampleData.userAnswersWithSchemeName.set(page, data).get

      val application = applicationBuilder(userAnswers = Some(ua)).build()
      val templateCaptor = ArgumentCaptor.forClass(classOf[String])
      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

      val result = route(application, httpGETRequest(httpPath)).value

      status(result) mustEqual OK

      verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

      templateCaptor.getValue mustEqual templateToBeRendered

      jsonCaptor.getValue must containJson(jsonToPassToTemplate(form.fill(data)))

      application.stop()
    }

    "redirect to Session Expired page for a GET when there is no data" in {
      val application = applicationBuilder(userAnswers = None).build()

      val result = route(application, httpGETRequest(httpPath)).value

      status(result) mustEqual SEE_OTHER
      redirectLocation(result).value mustBe controllers.routes.SessionExpiredController.onPageLoad().url

      application.stop()
    }
  }

  def controllerWithPOSTWithJson[A](httpPath: => String,
                                    page: QuestionPage[A],
                                    expectedJson: JsObject,
                                    form: Form[A],
                                    templateToBeRendered: String,
                                    requestValuesValid: Map[String, Seq[String]],
                                    requestValuesInvalid: Map[String, Seq[String]])(implicit writes: Writes[A]): Unit = {
    "Save data to user answers and redirect to next page when valid data is submitted" in {

      when(mockCompoundNavigator.nextPage(Matchers.eq(page), any(), any(), any())).thenReturn(SampleData.dummyCall)

      val application = applicationBuilder(userAnswers = Some(SampleData.userAnswersWithSchemeName)).build()

      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

      val result = route(application, httpPOSTRequest(httpPath, requestValuesValid)).value

      status(result) mustEqual SEE_OTHER

      verify(mockUserAnswersCacheConnector, times(1)).save(any(), jsonCaptor.capture)(any(), any())

      jsonCaptor.getValue must containJson(expectedJson)

      redirectLocation(result) mustBe Some(SampleData.dummyCall.url)

      application.stop()
    }

    "return a BAD REQUEST when invalid data is submitted" in {
      val application = applicationBuilder(userAnswers = Some(SampleData.userAnswersWithSchemeName)).build()

      val result = route(application, httpPOSTRequest(httpPath, requestValuesInvalid)).value

      status(result) mustEqual BAD_REQUEST

      verify(mockUserAnswersCacheConnector, times(0)).save(any(), any())(any(), any())

      application.stop()
    }

    "redirect to Session Expired page for a POST when there is no data" in {
      val application = applicationBuilder(userAnswers = None).build()

      val result = route(application, httpPOSTRequest(httpPath, requestValuesValid)).value

      status(result) mustEqual SEE_OTHER
      redirectLocation(result).value mustBe controllers.routes.SessionExpiredController.onPageLoad().url
      application.stop()
    }
  }

  def controllerWithPOST[A](httpPath: => String,
                            page: QuestionPage[A],
                            data: A,
                            form: Form[A],
                            templateToBeRendered: String,
                            requestValuesValid: Map[String, Seq[String]],
                            requestValuesInvalid: Map[String, Seq[String]])(implicit writes: Writes[A]): Unit = {

    controllerWithPOSTWithJson(
      httpPath,
      page,
      Json.obj(page.toString -> Json.toJson(data)),
      form,
      templateToBeRendered,
      requestValuesValid,
      requestValuesInvalid)
  }

  //scalastyle:on method.length
}
