/*
 * Copyright 2023 HM Revenue & Customs
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

import controllers.actions.MutableFakeDataRetrievalAction
import controllers.base.ControllerSpecBase
import controllers.routes
import data.SampleData._
import forms.YesNoFormProvider
import matchers.JsonMatchers
import models.ChargeType.ChargeTypeAnnualAllowance
import models.LocalDateBinder._
import models.{GenericViewModel, NormalMode}
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{times, verify, when}
import org.scalatest.{OptionValues, TryValues}
import org.scalatestplus.mockito.MockitoSugar
import pages.chargeE.MemberDetailsPage
import play.api.Application
import play.api.data.Form
import play.api.i18n.Messages
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.Call
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.twirl.api.Html
import uk.gov.hmrc.viewmodels.{NunjucksSupport, Radios}

import scala.concurrent.Future

class IsPublicServicePensionsRemedyControllerSpec
    extends ControllerSpecBase
    with MockitoSugar
    with NunjucksSupport
    with JsonMatchers
    with OptionValues
    with TryValues {
  private val mutableFakeDataRetrievalAction: MutableFakeDataRetrievalAction = new MutableFakeDataRetrievalAction()
  private val application: Application =
    applicationBuilderMutableRetrievalAction(mutableFakeDataRetrievalAction, Seq()).build()

  private def onwardRoute = Call("GET", "/foo")

  private val formProvider = new YesNoFormProvider()
  private val chargeTypeDescription = Messages(s"chargeType.description.${ChargeTypeAnnualAllowance.toString}")
  private val formManual: Form[Boolean] = formProvider(messages("isPublicServicePensionsRemedy.error.required", chargeTypeDescription))
  private val formBulk: Form[Boolean] = formProvider(messages("isPublicServicePensionsRemedyBulk.error.required", chargeTypeDescription))

  private def httpPathGET(index: Option[Int]): String =
    routes.IsPublicServicePensionsRemedyController
      .onPageLoad(ChargeTypeAnnualAllowance, NormalMode, srn, startDate, accessType, versionInt, index)
      .url

  private def httpPathPOST(index: Option[Int]): String =
    routes.IsPublicServicePensionsRemedyController
      .onSubmit(ChargeTypeAnnualAllowance, NormalMode, srn, startDate, accessType, versionInt, index)
      .url

  private def viewModel(index: Option[Int]) = GenericViewModel(
    submitUrl = httpPathPOST(index),
    returnUrl = controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, versionInt).url,
    schemeName = schemeName
  )

  private def userAnswers =
    userAnswersWithSchemeNamePstrQuarter
      .set(MemberDetailsPage(0), memberDetails)
      .success
      .value
      .set(MemberDetailsPage(1), memberDetails)
      .success
      .value

  "IsPublicServicePensionsRemedy Controller" must {

    "return OK and the correct view for a GET (for FileUpload PSR question)" in {
      when(mockRenderer.render(any(), any())(any())).thenReturn(Future.successful(Html("")))

      mutableFakeDataRetrievalAction.setDataToReturn(Some(userAnswers))
      val request = FakeRequest(GET, httpPathGET(None))
      val templateCaptor = ArgumentCaptor.forClass(classOf[String])
      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

      val result = route(application, request).value

      status(result) mustEqual OK

      verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

      def expectedJson(heading: String, title: String) = Json.obj(
        "form" -> formBulk,
        "viewModel" -> viewModel(None),
        "radios" -> Radios.yesNo(formBulk("value")),
        "chargeTypeDescription" -> Messages(s"chargeType.description.${ChargeTypeAnnualAllowance.toString}"),
        "manOrBulkHeading" -> s"isPublicServicePensionsRemedy$heading",
        "manOrBulkTitle" -> s"isPublicServicePensionsRemedy$title"
      )

      templateCaptor.getValue mustEqual "isPublicServicePensionsRemedy.njk"
      jsonCaptor.getValue must containJson(expectedJson("Bulk.heading", "Bulk.title"))
    }
    "return OK and the correct view for a GET (for ManualInput PSR question)" in {
      when(mockRenderer.render(any(), any())(any())).thenReturn(Future.successful(Html("")))

      mutableFakeDataRetrievalAction.setDataToReturn(Some(userAnswers))
      val request = FakeRequest(GET, httpPathGET(Some(0)))
      val templateCaptor = ArgumentCaptor.forClass(classOf[String])
      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

      val result = route(application, request).value

      status(result) mustEqual OK

      verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

      def expectedJson(heading: String, title: String) = Json.obj(
        "form" -> formManual,
        "viewModel" -> viewModel(Some(0)),
        "radios" -> Radios.yesNo(formManual("value")),
        "chargeTypeDescription" -> Messages(s"chargeType.description.${ChargeTypeAnnualAllowance.toString}"),
        "manOrBulkHeading" -> s"isPublicServicePensionsRemedy$heading",
        "manOrBulkTitle" -> s"isPublicServicePensionsRemedy$title"
      )

      templateCaptor.getValue mustEqual "isPublicServicePensionsRemedy.njk"
      jsonCaptor.getValue must containJson(expectedJson(".heading", ".title"))
    }

    "redirect to the next page when valid data is submitted and re-submit the data to DES with the deleted member marked as deleted (manual Journey)" in {
      when(mockUserAnswersCacheConnector.savePartial(any(), any(), any(), any())(any(), any())) thenReturn Future.successful(Json.obj())
      when(mockCompoundNavigator.nextPage(any(), any(), any(), any(), any(), any(), any())(any())).thenReturn(onwardRoute)

      mutableFakeDataRetrievalAction.setDataToReturn(Some(userAnswers))

      val request =
        FakeRequest(POST, httpPathGET(Some(0)))
          .withFormUrlEncodedBody(("value", "true"))

      val result = route(application, request).value

      status(result) mustEqual SEE_OTHER

      redirectLocation(result).value mustEqual onwardRoute.url
    }

    "return a Bad Request and errors when invalid data is submitted (manual journey)" in {
      when(mockRenderer.render(any(), any())(any())).thenReturn(Future.successful(Html("")))

      mutableFakeDataRetrievalAction.setDataToReturn(Some(userAnswers))

      val request = FakeRequest(POST, httpPathGET(Some(0))).withFormUrlEncodedBody(("value", ""))
      val boundForm = formManual.bind(Map("value" -> ""))
      val templateCaptor = ArgumentCaptor.forClass(classOf[String])
      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

      val result = route(application, request).value

      status(result) mustEqual BAD_REQUEST

      verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

      val expectedJson = Json.obj(
        "form" -> boundForm,
        "viewModel" -> viewModel(Some(0)),
        "radios" -> Radios.yesNo(boundForm("value"))
      )

      templateCaptor.getValue mustEqual "isPublicServicePensionsRemedy.njk"

      jsonCaptor.getValue must containJson(expectedJson)
    }
    "return a Bad Request and errors when invalid data is submitted (fileUpload journey)" in {
      when(mockRenderer.render(any(), any())(any())).thenReturn(Future.successful(Html("")))

      mutableFakeDataRetrievalAction.setDataToReturn(Some(userAnswers))

      val request = FakeRequest(POST, httpPathGET(None)).withFormUrlEncodedBody(("value", ""))
      val boundForm = formBulk.bind(Map("value" -> ""))
      val templateCaptor = ArgumentCaptor.forClass(classOf[String])
      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

      val result = route(application, request).value

      status(result) mustEqual BAD_REQUEST

      verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

      val expectedJson = Json.obj(
        "form" -> boundForm,
        "viewModel" -> viewModel(None),
        "radios" -> Radios.yesNo(boundForm("value"))
      )

      templateCaptor.getValue mustEqual "isPublicServicePensionsRemedy.njk"

      jsonCaptor.getValue must containJson(expectedJson)
    }

    "redirect to Session Expired for a GET if no existing data is found (manual)" in {

      mutableFakeDataRetrievalAction.setDataToReturn(None)

      val request = FakeRequest(GET, httpPathGET(Some(0)))

      val result = route(application, request).value

      status(result) mustEqual SEE_OTHER

      redirectLocation(result).value mustEqual controllers.routes.SessionExpiredController.onPageLoad.url
    }

    "redirect to Session Expired for a POST if no existing data is found (manual)" in {

      mutableFakeDataRetrievalAction.setDataToReturn(None)

      val request =
        FakeRequest(POST, httpPathGET(Some(0)))
          .withFormUrlEncodedBody(("value", "true"))

      val result = route(application, request).value

      status(result) mustEqual SEE_OTHER

      redirectLocation(result).value mustEqual controllers.routes.SessionExpiredController.onPageLoad.url
    }
  }
}
