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
import data.SampleData.sponsoringOrganisationDetails
import forms.chargeC.SponsoringEmployerAddressFormProvider
import matchers.JsonMatchers
import models.chargeC.SponsoringEmployerAddress
import models.{GenericViewModel, NormalMode, UserAnswers}
import org.mockito.{ArgumentCaptor, Matchers}
import org.mockito.Matchers.any
import org.mockito.Mockito.{times, verify, when}
import org.scalatest.{OptionValues, TryValues}
import org.scalatestplus.mockito.MockitoSugar
import pages.chargeC.{IsSponsoringEmployerIndividualPage, SponsoringEmployerAddressPage, SponsoringIndividualDetailsPage, SponsoringOrganisationDetailsPage}
import play.api.data.Form
import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.viewmodels.NunjucksSupport
import play.api.test.Helpers._

class SponsoringEmployerAddressControllerSpec extends ControllerSpecBase with MockitoSugar with NunjucksSupport with JsonMatchers with OptionValues with TryValues with ControllerBehaviours {
  private val templateToBeRendered = "chargeC/sponsoringEmployerAddress.njk"
  private val form = new SponsoringEmployerAddressFormProvider()()
  private def httpPathGET: String = controllers.chargeC.routes.SponsoringEmployerAddressController.onPageLoad(NormalMode, SampleData.srn).url
  private def httpPathPOST: String = controllers.chargeC.routes.SponsoringEmployerAddressController.onSubmit(NormalMode, SampleData.srn).url

  private val valuesValid: Map[String, Seq[String]] = Map(
    "line1" -> Seq("line1"),
    "line2" -> Seq("line2"),
    "line3" -> Seq("line3"),
    "line4" -> Seq("line4"),
    "country" -> Seq("UK"),
    "postcode" -> Seq("ZZ1 1ZZ")

  )

  private val valuesInvalid: Map[String, Seq[String]] = Map(
    "line1" -> Seq.empty,
    "line2" -> Seq("line2"),
    "line3" -> Seq("line3"),
    "line4" -> Seq("line4"),
    "country" -> Seq("UK"),
    "postcode" -> Seq("ZZ1 1ZZ")
  )

  private def jsonToPassToTemplate(sponsorName:String):Form[SponsoringEmployerAddress]=>JsObject = form => Json.obj(
    "form" -> form,
    "viewModel" -> GenericViewModel(
      submitUrl = controllers.chargeC.routes.SponsoringEmployerAddressController.onSubmit(NormalMode, SampleData.srn).url,
      returnUrl = frontendAppConfig.managePensionsSchemeSummaryUrl.format(SampleData.srn),
      schemeName = SampleData.schemeName),
    "sponsorName" -> sponsorName
  )

  private val userAnswersIndividual: Option[UserAnswers] = Some(SampleData.userAnswersWithSchemeNameAndIndividual)
  private val userAnswersOrganisation: Option[UserAnswers] = Some(SampleData.userAnswersWithSchemeNameAndOrganisation)

  "SponsoringEmployerAddress Controller with individual sponsor" must {
    "return OK and the correct view for a GET" in {
      val application = applicationBuilder(userAnswers = userAnswersIndividual).build()
      val templateCaptor = ArgumentCaptor.forClass(classOf[String])
      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

      val result = route(application, httpGETRequest(httpPathGET)).value

      status(result) mustEqual OK

      verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

      templateCaptor.getValue mustEqual templateToBeRendered

      jsonCaptor.getValue must containJson(jsonToPassToTemplate(sponsorName = "First Last").apply(form))

      application.stop()
    }

    "return OK and the correct view for a GET when the question has previously been answered" in {
      val ua = userAnswersIndividual.map(_.set(SponsoringEmployerAddressPage, SampleData.sponsoringEmployerAddress)).get.toOption.get

      val application = applicationBuilder(userAnswers = Some(ua)).build()
      val templateCaptor = ArgumentCaptor.forClass(classOf[String])
      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

      val result = route(application, httpGETRequest(httpPathGET)).value

      status(result) mustEqual OK

      verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

      templateCaptor.getValue mustEqual templateToBeRendered

      jsonCaptor.getValue must containJson(jsonToPassToTemplate(sponsorName = "First Last")(form.fill(SampleData.sponsoringEmployerAddress)))

      application.stop()
    }

    "redirect to Session Expired page for a GET when there is no data" in {
      val application = applicationBuilder(userAnswers = None).build()

      val result = route(application, httpGETRequest(httpPathGET)).value

      status(result) mustEqual SEE_OTHER
      redirectLocation(result).value mustBe controllers.routes.SessionExpiredController.onPageLoad().url

      application.stop()
    }
  }

  "SponsoringEmployerAddress Controller with organisation sponsor" must {
    "return OK and the correct view for a GET" in {
      val application = applicationBuilder(userAnswers = userAnswersOrganisation).build()
      val templateCaptor = ArgumentCaptor.forClass(classOf[String])
      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

      val result = route(application, httpGETRequest(httpPathGET)).value

      status(result) mustEqual OK

      verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

      templateCaptor.getValue mustEqual templateToBeRendered

      jsonCaptor.getValue must containJson(jsonToPassToTemplate(sponsorName = SampleData.companyName).apply(form))

      application.stop()
    }

    "return OK and the correct view for a GET when the question has previously been answered" in {
      val ua = userAnswersOrganisation.map(_.set(SponsoringEmployerAddressPage, SampleData.sponsoringEmployerAddress)).get.toOption.get

      val application = applicationBuilder(userAnswers = Some(ua)).build()
      val templateCaptor = ArgumentCaptor.forClass(classOf[String])
      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

      val result = route(application, httpGETRequest(httpPathGET)).value

      status(result) mustEqual OK

      verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

      templateCaptor.getValue mustEqual templateToBeRendered

      jsonCaptor.getValue must containJson(jsonToPassToTemplate(sponsorName = SampleData.companyName)(form.fill(SampleData.sponsoringEmployerAddress)))

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
          SponsoringOrganisationDetailsPage.toString -> sponsoringOrganisationDetails,
          IsSponsoringEmployerIndividualPage.toString -> false,
          SponsoringEmployerAddressPage.toString -> Json.toJson(SampleData.sponsoringEmployerAddress)
        )
      )

      when(mockCompoundNavigator.nextPage(Matchers.eq(SponsoringEmployerAddressPage), any(), any(), any())).thenReturn(SampleData.dummyCall)

      val application = applicationBuilder(userAnswers = userAnswersOrganisation).build()

      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

      val result = route(application, httpPOSTRequest(httpPathPOST, valuesValid)).value

      status(result) mustEqual SEE_OTHER

      verify(mockUserAnswersCacheConnector, times(1)).save(any(), jsonCaptor.capture)(any(), any())

      jsonCaptor.getValue must containJson(expectedJson)

      redirectLocation(result) mustBe Some(SampleData.dummyCall.url)

      application.stop()
    }

    "return a BAD REQUEST when invalid data is submitted" in {
      val application = applicationBuilder(userAnswers = userAnswersOrganisation).build()

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
