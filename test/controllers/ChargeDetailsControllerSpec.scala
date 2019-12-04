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
import models.chargeF.ChargeDetails
import models.{GenericViewModel, NormalMode}
import org.mockito.ArgumentCaptor
import org.mockito.Matchers.any
import org.mockito.Mockito.{times, verify, _}
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.mockito.MockitoSugar
import pages.ChargeDetailsPage
import play.api.http.HeaderNames
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.{AnyContentAsEmpty, AnyContentAsFormUrlEncoded, Call}
import play.api.test.Helpers._
import play.api.test.{FakeHeaders, FakeRequest}
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

  private def httpGETRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest(GET, chargeDetailsRoute)

  private def httpPOSTRequest(values:Map[String, Seq[String]]): FakeRequest[AnyContentAsFormUrlEncoded] =
    FakeRequest
      .apply(
        method = POST,
        uri = chargeDetailsRoute,
        headers = FakeHeaders(Seq(HeaderNames.HOST -> "localhost")),
        body = AnyContentAsFormUrlEncoded(values))

  override def beforeEach: Unit = {
    reset(mockRenderer)
    when(mockRenderer.render(any(), any())(any())).thenReturn(Future.successful(Html("")))
    reset(mockUserAnswersCacheConnector)
  }


  "ChargeDetails Controller" must {
    "return OK and the correct view for a GET" in {
      val application = applicationBuilder(userAnswers = Some(userAnswersWithSchemeName)).build()
      val templateCaptor = ArgumentCaptor.forClass(classOf[String])
      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

      val result = route(application, httpGETRequest).value

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

      val result = route(application, httpGETRequest).value

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

    "Save data to user answers and redirect to next page when valid data is submitted" in {
      val application = applicationBuilder(userAnswers = Some(userAnswersWithSchemeName)).build()
      val chargeDetails = ChargeDetails(LocalDate.of(2003, 4, 3), BigDecimal(33.44))
      val expectedJson = Json.obj(ChargeDetailsPage.toString -> Json.toJson(chargeDetails) )
      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])
      val json = Json.obj()

      when(mockUserAnswersCacheConnector.save(any(),any())(any(),any())).thenReturn(Future.successful(json))

      val values: Map[String, Seq[String]] = Map(
        "deregistrationDate.day" -> Seq("3"),
        "deregistrationDate.month" -> Seq("4"),
        "deregistrationDate.year" -> Seq("2003"),
        "amountTaxDue" -> Seq("33.44")
      )

      val result = route(application, httpPOSTRequest(values)).value

      status(result) mustEqual SEE_OTHER

      verify(mockUserAnswersCacheConnector, times(1)).save(any(), jsonCaptor.capture)(any(),any())

      jsonCaptor.getValue must containJson(expectedJson)

      application.stop()
    }

    "return a BAD REQUEST when invalid data is submitted" in {
      val application = applicationBuilder(userAnswers = Some(userAnswersWithSchemeName)).build()
      val values: Map[String, Seq[String]] = Map(
        "deregistrationDate.day" -> Seq("32"),
        "deregistrationDate.month" -> Seq("13"),
        "deregistrationDate.year" -> Seq("2003"),
        "amountTaxDue" -> Seq("33.44")
      )

      val result = route(application, httpPOSTRequest(values)).value

      status(result) mustEqual BAD_REQUEST

      verify(mockUserAnswersCacheConnector, times(0)).save(any(), any())(any(),any())

      application.stop()
    }
  }
}
