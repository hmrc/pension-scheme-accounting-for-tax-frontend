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

package controllers

import behaviours.ControllerBehaviours
import data.SampleData
import forms.ChargeTypeFormProvider
import models.ChargeType.ChargeTypeAnnualAllowance
import models.{ChargeType, Enumerable, GenericViewModel, NormalMode, UserAnswers}
import org.mockito.{ArgumentCaptor, Matchers}
import org.mockito.Matchers.any
import org.mockito.Mockito.{times, verify, when}
import org.scalatest.BeforeAndAfterEach
import pages.{ChargeTypePage, QuestionPage}
import play.api.data.Form
import play.api.inject.bind
import play.api.inject.guice.{GuiceApplicationBuilder, GuiceableModule}
import play.api.libs.json.{JsObject, Json, Writes}
import play.api.test.FakeRequest
import play.api.test.Helpers.{GET, route, status, _}
import services.SchemeService
import play.api.test.Helpers._

import scala.concurrent.Future

class ChargeTypeControllerSpec extends ControllerBehaviours with BeforeAndAfterEach with Enumerable.Implicits {

  private val template = "chargeType.njk"

  private def form = new ChargeTypeFormProvider()()

  private def httpPathGET: String = controllers.routes.ChargeTypeController.onPageLoad(NormalMode, SampleData.srn).url

  private def httpPathPOST: String = controllers.routes.ChargeTypeController.onSubmit(NormalMode, SampleData.srn).url

  private val valuesValid: Map[String, Seq[String]] = Map(
    "value" -> Seq(ChargeTypeAnnualAllowance.toString)
  )

  private val valuesInvalid: Map[String, Seq[String]] = Map(
    "value" -> Seq("Unknown Charge")
  )

  private val jsonToTemplate: Form[ChargeType] => JsObject = form => Json.obj(
    fields = "form" -> form,
    "radios" -> ChargeType.radios(form),
    "viewModel" -> GenericViewModel(
      submitUrl = controllers.routes.ChargeTypeController.onSubmit(NormalMode, SampleData.srn).url,
      returnUrl = frontendAppConfig.managePensionsSchemeSummaryUrl.format(SampleData.srn),
      schemeName = SampleData.schemeName)
  )

  private val mockSchemeService = mock[SchemeService]

  private val userAnswers: Option[UserAnswers] = Some(SampleData.userAnswersWithSchemeName)

  "ChargeType Controller" must {
    "return OK and the correct view for a GET" in {
      val application = new GuiceApplicationBuilder()
        .overrides(
          modules(Some(SampleData.userAnswersWithSchemeName)) ++ Seq[GuiceableModule](
            bind[SchemeService].toInstance(mockSchemeService)
          ): _*
        ).build()

      when(mockSchemeService.retrieveSchemeDetails(any(), any())(any(), any())).thenReturn(Future.successful(SampleData.schemeDetails))


      val templateCaptor = ArgumentCaptor.forClass(classOf[String])
      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

      val result = route(application, FakeRequest(GET, httpPathGET)).value

      status(result) mustEqual OK

      verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

      templateCaptor.getValue mustEqual template

      jsonCaptor.getValue must containJson(jsonToTemplate.apply(form))

      application.stop()
    }

    "return OK and the correct view for a GET when the question has previously been answered" in {
      val ua = SampleData.userAnswersWithSchemeName.set(ChargeTypePage, ChargeTypeAnnualAllowance).get
      val application = new GuiceApplicationBuilder()
        .overrides(
          modules(Some(ua)) ++ Seq[GuiceableModule](
            bind[SchemeService].toInstance(mockSchemeService)
          ): _*
        ).build()

      when(mockSchemeService.retrieveSchemeDetails(any(), any())(any(), any())).thenReturn(Future.successful(SampleData.schemeDetails))

      val templateCaptor = ArgumentCaptor.forClass(classOf[String])
      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

      val result = route(application, FakeRequest(GET, httpPathGET)).value

      status(result) mustEqual OK

      verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

      templateCaptor.getValue mustEqual template

      jsonCaptor.getValue must containJson(jsonToTemplate(form.fill(ChargeTypeAnnualAllowance)))

      application.stop()
    }

    "Save data to user answers and redirect to next page when valid data is submitted" in {

      val expectedJson = Json.obj(ChargeTypePage.toString -> Json.toJson(ChargeTypeAnnualAllowance)(writes(ChargeType.enumerable)))

      when(mockCompoundNavigator.nextPage(Matchers.eq(ChargeTypePage), any(), any(), any())).thenReturn(SampleData.dummyCall)

      val application = applicationBuilder(userAnswers = userAnswers).build()

      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

      val result = route(application, httpPOSTRequest(httpPathPOST, valuesValid)).value

      status(result) mustEqual SEE_OTHER

      verify(mockUserAnswersCacheConnector, times(1)).save(any(), jsonCaptor.capture)(any(), any())

      jsonCaptor.getValue must containJson(expectedJson)

      redirectLocation(result) mustBe Some(SampleData.dummyCall.url)

      application.stop()
    }

    "return a BAD REQUEST when invalid data is submitted" in {
      val application = applicationBuilder(userAnswers = userAnswers).build()

      val result = route(application, httpPOSTRequest(httpPathPOST, valuesInvalid)).value

      status(result) mustEqual BAD_REQUEST

      verify(mockUserAnswersCacheConnector, times(0)).save(any(), any())(any(), any())

      application.stop()
    }

    "redirect to Session Expired page for a POST when there is no data" in {
      val application = applicationBuilder(userAnswers = None).build()

      val result = route(application, httpPOSTRequest(httpPathPOST, valuesValid)).value

      status(result) mustEqual SEE_OTHER
      redirectLocation(result).value mustBe controllers.routes.SessionExpiredController.onPageLoad().url
      application.stop()
    }
  }
}
