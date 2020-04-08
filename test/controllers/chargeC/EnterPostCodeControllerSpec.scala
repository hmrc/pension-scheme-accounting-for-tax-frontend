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

import config.FrontendAppConfig
import controllers.actions.MutableFakeDataRetrievalAction
import controllers.base.ControllerSpecBase
import data.SampleData.companyName
import data.SampleData.dummyCall
import data.SampleData.srn
import data.SampleData.startDate
import data.SampleData.userAnswersWithSchemeNameAndIndividual
import data.SampleData.userAnswersWithSchemeNameAndOrganisation
import forms.chargeC.EnterPostCodeFormProvider
import forms.chargeC.SponsoringEmployerAddressFormProvider
import matchers.JsonMatchers
import models.GenericViewModel
import models.NormalMode
import models.UserAnswers
import models.chargeC.SponsoringEmployerAddress
import org.mockito.ArgumentCaptor
import org.mockito.Matchers
import org.mockito.Matchers.any
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.when
import org.scalatest.OptionValues
import org.scalatest.TryValues
import org.scalatestplus.mockito.MockitoSugar
import pages.chargeC.EnterPostCodePage
import pages.chargeC.EnterPostCodePage
import pages.chargeC.SponsoringOrganisationDetailsPage
import pages.chargeC.WhichTypeOfSponsoringEmployerPage
import play.api.Application
import play.api.data.Form
import play.api.inject.bind
import play.api.libs.json.JsObject
import play.api.libs.json.Json
import play.api.mvc.Call
import play.api.test.Helpers._
import play.twirl.api.Html
import uk.gov.hmrc.viewmodels.NunjucksSupport
import models.LocalDateBinder._
import data.SampleData._

import scala.concurrent.Future

class EnterPostCodeControllerSpec extends ControllerSpecBase with MockitoSugar
  with NunjucksSupport with JsonMatchers with OptionValues with TryValues {
  private val userAnswersIndividual: Option[UserAnswers] = Some(userAnswersWithSchemeNameAndIndividual)
  private val userAnswersOrganisation: Option[UserAnswers] = Some(userAnswersWithSchemeNameAndOrganisation)
  private val mutableFakeDataRetrievalAction: MutableFakeDataRetrievalAction = new MutableFakeDataRetrievalAction()
  private val application: Application = applicationBuilderMutableRetrievalAction(mutableFakeDataRetrievalAction).build()
  private val templateToBeRendered = "chargeC/enterPostCode.njk"
  private val form = new EnterPostCodeFormProvider()()
  private val index = 0
  private val postcode = "ZZ11ZZ"

  private def httpPathGET: String = controllers.chargeC.routes.EnterPostCodeController.onPageLoad(NormalMode, srn, startDate, index).url

  private def httpPathPOST: String = controllers.chargeC.routes.EnterPostCodeController.onSubmit(NormalMode, srn, startDate, index).url

  private val valuesValid: Map[String, Seq[String]] = Map(
    "value" -> Seq("ZZ1 1ZZ")
  )

  private val valuesInvalid: Map[String, Seq[String]] = Map(
    "line1" -> Seq.empty,
    "line2" -> Seq("line2"),
    "line3" -> Seq("line3"),
    "line4" -> Seq("line4"),
    "country" -> Seq("UK"),
    "postcode" -> Seq("ZZ1 1ZZ")
  )

  private def jsonToPassToTemplate(sponsorName: String, isSelected: Boolean = false): Form[String] => JsObject = form => Json.obj(
    "form" -> form,
    "viewModel" -> GenericViewModel(
      submitUrl = controllers.chargeC.routes.EnterPostCodeController.onSubmit(NormalMode, srn, startDate, index).url,
      returnUrl = dummyCall.url,
      schemeName = schemeName),
    "sponsorName" -> sponsorName
  )

  override def beforeEach: Unit = {
    super.beforeEach
    when(mockUserAnswersCacheConnector.save(any(), any())(any(), any())).thenReturn(Future.successful(Json.obj()))
    when(mockRenderer.render(any(), any())(any())).thenReturn(Future.successful(Html("")))
    when(mockAppConfig.managePensionsSchemeSummaryUrl).thenReturn(dummyCall.url)
    when(mockAppConfig.validCountryCodes).thenReturn(Seq("UK"))
  }


  "EnterPostCode Controller with individual sponsor" must {
    "return OK and the correct view for a GET" in {
      mutableFakeDataRetrievalAction.setDataToReturn(userAnswersIndividual)
      val templateCaptor = ArgumentCaptor.forClass(classOf[String])
      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

      val result = route(application, httpGETRequest(httpPathGET)).value

      status(result) mustEqual OK

      verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

      templateCaptor.getValue mustEqual templateToBeRendered
      jsonCaptor.getValue must containJson(jsonToPassToTemplate(sponsorName = "First Last").apply(form))
    }

    "redirect to Session Expired page for a GET when there is no data" in {
      mutableFakeDataRetrievalAction.setDataToReturn(None)

      val result = route(application, httpGETRequest(httpPathGET)).value

      status(result) mustEqual SEE_OTHER

      redirectLocation(result).value mustBe controllers.routes.SessionExpiredController.onPageLoad().url
    }

  "EnterPostCode Controller with organisation sponsor" must {
    "return OK and the correct view for a GET" in {
      mutableFakeDataRetrievalAction.setDataToReturn(userAnswersOrganisation)
      val templateCaptor = ArgumentCaptor.forClass(classOf[String])
      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

      val result = route(application, httpGETRequest(httpPathGET)).value

      status(result) mustEqual OK

      verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

      templateCaptor.getValue mustEqual templateToBeRendered

      jsonCaptor.getValue must containJson(jsonToPassToTemplate(sponsorName = companyName).apply(form))
    }
  }

    "Save data to user answers and redirect to next page when valid data is submitted" in {

      val expectedJson = Json.obj(
        "chargeCDetails" -> Json.obj(
          "employers" -> Json.arr(Json.obj(
            SponsoringOrganisationDetailsPage.toString -> sponsoringOrganisationDetails,
            WhichTypeOfSponsoringEmployerPage.toString -> "organisation"
          ))
        )
      )

      when(mockCompoundNavigator.nextPage(Matchers.eq(EnterPostCodePage(index)), any(), any(), any(), any())).thenReturn(dummyCall)

      mutableFakeDataRetrievalAction.setDataToReturn(userAnswersOrganisation)

      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

      val result = route(application, httpPOSTRequest(httpPathPOST, valuesValid)).value

      status(result) mustEqual SEE_OTHER

      verify(mockUserAnswersCacheConnector, times(1)).save(any(), jsonCaptor.capture)(any(), any())

      jsonCaptor.getValue must containJson(expectedJson)

      redirectLocation(result) mustBe Some(dummyCall.url)
    }

//    "return a BAD REQUEST when invalid data is submitted" in {
//      mutableFakeDataRetrievalAction.setDataToReturn(userAnswersOrganisation)
//
//      val result = route(application, httpPOSTRequest(httpPathPOST, valuesInvalid)).value
//
//      status(result) mustEqual BAD_REQUEST
//
//      verify(mockUserAnswersCacheConnector, times(0)).save(any(), any())(any(), any())
//    }
//
//    "redirect to Session Expired page for a POST when there is no data" in {
//      mutableFakeDataRetrievalAction.setDataToReturn(None)
//
//      val result = route(application, httpPOSTRequest(httpPathPOST, valuesValid)).value
//
//      status(result) mustEqual SEE_OTHER
//
//      redirectLocation(result).value mustBe controllers.routes.SessionExpiredController.onPageLoad().url
//    }
  }
}
