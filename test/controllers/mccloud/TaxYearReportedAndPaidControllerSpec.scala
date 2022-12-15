/*
 * Copyright 2022 HM Revenue & Customs
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

package controllers.mccloud

import controllers.actions.MutableFakeDataRetrievalAction
import controllers.base.ControllerSpecBase
import data.SampleData._
import forms.YearRangeFormProvider
import matchers.JsonMatchers
import models.ChargeType.ChargeTypeAnnualAllowance
import models.LocalDateBinder._
import models.requests.IdentifierRequest
import models.{GenericViewModel, NormalMode, YearRange}
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{times, verify, when}
import org.scalatest.{OptionValues, TryValues}
import org.scalatestplus.mockito.MockitoSugar
import pages.SchemeNameQuery
import pages.chargeE.MemberDetailsPage
import play.api.Application
import play.api.data.Form
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.Call
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.twirl.api.Html
import uk.gov.hmrc.viewmodels.NunjucksSupport

import scala.concurrent.Future

class TaxYearReportedAndPaidControllerSpec extends ControllerSpecBase
  with MockitoSugar with NunjucksSupport with JsonMatchers with OptionValues with TryValues {
  private val mutableFakeDataRetrievalAction: MutableFakeDataRetrievalAction = new MutableFakeDataRetrievalAction()
  private val application: Application =
    applicationBuilderMutableRetrievalAction(mutableFakeDataRetrievalAction,
      Seq()).build()

  private def onwardRoute = Call("GET", "/foo")

  private val formProvider = new YearRangeFormProvider
  private val form: Form[YearRange] = formProvider("taxYearReportedAndPaid.error.required")

  private def httpPathGET: String = routes.TaxYearReportedAndPaidController
    .onPageLoadWithIndex(ChargeTypeAnnualAllowance, NormalMode, srn, startDate, accessType, versionInt, 0, schemeIndex).url

  private def httpPathPOST: String = routes.TaxYearReportedAndPaidController
    .onSubmitWithIndex(ChargeTypeAnnualAllowance, NormalMode, srn, startDate, accessType, versionInt, 0, schemeIndex).url

  private val viewModel = GenericViewModel(
    submitUrl = httpPathPOST,
    returnUrl = controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, versionInt).url,
    schemeName = schemeName)

  private def userAnswers = userAnswersWithSchemeNamePstrQuarter
    .set(MemberDetailsPage(0), memberDetails).success.value
    .set(MemberDetailsPage(1), memberDetails).success.value
    .setOrException(SchemeNameQuery, schemeName)

  "TaxYearReportedAndPaidController Controller" must {

    "return OK and the correct view for a GET" in {
      when(mockAppConfig.schemeDashboardUrl(any(): IdentifierRequest[_])).thenReturn(onwardRoute.url)
      when(mockRenderer.render(any(), any())(any())).thenReturn(Future.successful(Html("")))

      mutableFakeDataRetrievalAction.setDataToReturn(Some(userAnswers))
      val request = FakeRequest(GET, httpPathGET)
      val templateCaptor = ArgumentCaptor.forClass(classOf[String])
      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

      val result = route(application, request).value

      status(result) mustEqual OK

      verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

      val expectedJson = Json.obj(
        "form" -> form,
        "viewModel" -> viewModel
      )

      templateCaptor.getValue mustEqual "mccloud/taxYearReportedAndPaid.njk"
      jsonCaptor.getValue must containJson(expectedJson)
    }

    "redirect to the next page when valid data is submitted" in {
      when(mockAppConfig.schemeDashboardUrl(any(): IdentifierRequest[_])).thenReturn(onwardRoute.url)
      when(mockUserAnswersCacheConnector.savePartial(any(), any(), any(), any())(any(), any())) thenReturn Future.successful(Json.obj())
      when(mockCompoundNavigator.nextPage(any(), any(), any(), any(), any(), any(), any())(any())).thenReturn(onwardRoute)

      mutableFakeDataRetrievalAction.setDataToReturn(Some(userAnswers))

      val request =
        FakeRequest(POST, httpPathPOST)
          .withFormUrlEncodedBody(("value", "2021"))

      val result = route(application, request).value

      status(result) mustEqual SEE_OTHER

      redirectLocation(result).value mustEqual onwardRoute.url
    }

    "return a Bad Request and errors when invalid data is submitted" in {

      when(mockAppConfig.schemeDashboardUrl(any(): IdentifierRequest[_])).thenReturn(onwardRoute.url)
      when(mockRenderer.render(any(), any())(any())).thenReturn(Future.successful(Html("")))

      mutableFakeDataRetrievalAction.setDataToReturn(Some(userAnswers))

      val request = FakeRequest(POST, httpPathPOST).withFormUrlEncodedBody(("value", ""))
      val boundForm = form.bind(Map("value" -> ""))
      val templateCaptor = ArgumentCaptor.forClass(classOf[String])
      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

      val result = route(application, request).value

      status(result) mustEqual BAD_REQUEST

      verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

      val expectedJson = Json.obj(
        "form" -> boundForm,
        "viewModel" -> viewModel
      )
      templateCaptor.getValue mustEqual "mccloud/taxYearReportedAndPaid.njk"
      jsonCaptor.getValue must containJson(expectedJson)
    }

    "redirect to Session Expired for a GET if no existing data is found" in {

      mutableFakeDataRetrievalAction.setDataToReturn(None)

      val request = FakeRequest(GET, httpPathGET)

      val result = route(application, request).value

      status(result) mustEqual SEE_OTHER

      redirectLocation(result).value mustEqual controllers.routes.SessionExpiredController.onPageLoad.url
    }

    "redirect to Session Expired for a POST if no existing data is found" in {

      mutableFakeDataRetrievalAction.setDataToReturn(None)

      val request =
        FakeRequest(POST, httpPathGET)
          .withFormUrlEncodedBody(("value", "true"))

      val result = route(application, request).value

      status(result) mustEqual SEE_OTHER

      redirectLocation(result).value mustEqual controllers.routes.SessionExpiredController.onPageLoad.url
    }
  }
}
