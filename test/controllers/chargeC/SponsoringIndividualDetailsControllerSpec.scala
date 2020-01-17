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

package controllers.chargeC

import behaviours.ControllerBehaviours
import controllers.base.ControllerSpecBase
import data.SampleData
import forms.chargeC.SponsoringIndividualDetailsFormProvider
import matchers.JsonMatchers
import models.chargeC.SponsoringIndividualDetails
import models.{GenericViewModel, NormalMode, UserAnswers}
import org.mockito.{ArgumentCaptor, Matchers}
import org.mockito.Matchers.any
import org.mockito.Mockito.{times, verify, when}
import org.scalatest.{OptionValues, TryValues}
import org.scalatestplus.mockito.MockitoSugar
import pages.chargeC.SponsoringIndividualDetailsPage
import play.api.data.Form
import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.viewmodels.NunjucksSupport
import play.api.test.Helpers._

class SponsoringIndividualDetailsControllerSpec extends ControllerSpecBase with MockitoSugar with NunjucksSupport with JsonMatchers with OptionValues with TryValues with ControllerBehaviours {
  private val templateToBeRendered = "chargeC/sponsoringIndividualDetails.njk"
  private val form = new SponsoringIndividualDetailsFormProvider()()
  private def httpPathGET: String = controllers.chargeC.routes.SponsoringIndividualDetailsController.onPageLoad(NormalMode, SampleData.srn).url
  private def httpPathPOST: String = controllers.chargeC.routes.SponsoringIndividualDetailsController.onSubmit(NormalMode, SampleData.srn).url

  private val valuesValid: Map[String, Seq[String]] = Map(
    "firstName" -> Seq("First"),
    "lastName" -> Seq("Last"),
    "nino" -> Seq("CS121212C")
  )

  private val valuesInvalid: Map[String, Seq[String]] = Map(
    "firstName" -> Seq.empty,
    "lastName" -> Seq("Last"),
    "nino" -> Seq("CS121212C")
  )

  private val jsonToPassToTemplate:Form[SponsoringIndividualDetails]=>JsObject = form => Json.obj(
    "form" -> form,
    "viewModel" -> GenericViewModel(
      submitUrl = controllers.chargeC.routes.SponsoringIndividualDetailsController.onSubmit(NormalMode, SampleData.srn).url,
      returnUrl = frontendAppConfig.managePensionsSchemeSummaryUrl.format(SampleData.srn),
      schemeName = SampleData.schemeName)
  )

  private val userAnswers: Option[UserAnswers] = Some(SampleData.userAnswersWithSchemeName)

  "SponsoringIndividualDetails Controller" must {
    "return OK and the correct view for a GET" in {
      val application = applicationBuilder(userAnswers = userAnswers).build()
      val templateCaptor = ArgumentCaptor.forClass(classOf[String])
      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

      val result = route(application, httpGETRequest(httpPathGET)).value

      status(result) mustEqual OK

      verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

      templateCaptor.getValue mustEqual templateToBeRendered

      jsonCaptor.getValue must containJson(jsonToPassToTemplate.apply(form))

      application.stop()
    }

    "return OK and the correct view for a GET when the question has previously been answered" in {
      val ua = userAnswers.map(_.set(SponsoringIndividualDetailsPage, SampleData.sponsoringIndividualDetails)).get.toOption.get

      val application = applicationBuilder(userAnswers = Some(ua)).build()
      val templateCaptor = ArgumentCaptor.forClass(classOf[String])
      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

      val result = route(application, httpGETRequest(httpPathGET)).value

      status(result) mustEqual OK

      verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

      templateCaptor.getValue mustEqual templateToBeRendered

      jsonCaptor.getValue must containJson(jsonToPassToTemplate(form.fill(SampleData.sponsoringIndividualDetails)))

      application.stop()
    }

    "redirect to Session Expired page for a GET when there is no data" in {
      val application = applicationBuilder(userAnswers = None).build()

      val result = route(application, httpGETRequest(httpPathGET)).value

      status(result) mustEqual SEE_OTHER
      redirectLocation(result).value mustBe controllers.routes.SessionExpiredController.onPageLoad().url

      application.stop()
    }

    "Save data to user answers and redirect to next page when valid data is submitted" in {

      val expectedJson = Json.obj(
        "chargeCDetails" -> Json.obj(
          SponsoringIndividualDetailsPage.toString -> Json.toJson(SampleData.sponsoringIndividualDetails)
        )
      )

      when(mockCompoundNavigator.nextPage(Matchers.eq(SponsoringIndividualDetailsPage), any(), any(), any())).thenReturn(SampleData.dummyCall)

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