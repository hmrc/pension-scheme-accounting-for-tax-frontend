/*
 * Copyright 2024 HM Revenue & Customs
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

package controllers.chargeG

import controllers.actions.MutableFakeDataRetrievalAction
import controllers.base.ControllerSpecBase
import data.SampleData._
import forms.chargeG.ChargeDetailsFormProvider
import matchers.JsonMatchers
import models.LocalDateBinder._
import models.chargeG.ChargeDetails
import models.requests.IdentifierRequest
import models.{GenericViewModel, NormalMode, UserAnswers}
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{times, verify, when}
import org.scalatestplus.mockito.MockitoSugar
import play.api.Application
import play.api.data.Form
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.{AnyContentAsEmpty, AnyContentAsFormUrlEncoded, Call}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.twirl.api.Html
import uk.gov.hmrc.viewmodels.{DateInput, NunjucksSupport}
import utils.AFTConstants.{QUARTER_END_DATE, QUARTER_START_DATE}
import utils.DateHelper

import java.time.LocalDate
import scala.concurrent.Future

class ChargeDetailsControllerSpec extends ControllerSpecBase with MockitoSugar with NunjucksSupport with JsonMatchers {
  private val mutableFakeDataRetrievalAction: MutableFakeDataRetrievalAction = new MutableFakeDataRetrievalAction()
  private val application: Application = applicationBuilderMutableRetrievalAction(mutableFakeDataRetrievalAction).build()
  private val formProvider = new ChargeDetailsFormProvider()
  private val startDate = LocalDate.parse(QUARTER_START_DATE)
  private val endDate = LocalDate.parse(QUARTER_END_DATE)

  private def form: Form[ChargeDetails] = formProvider(startDate, endDate)

  private def onwardRoute: Call = Call("GET", "/foo")

  private def httpPathGET: String = routes.ChargeDetailsController.onPageLoad(NormalMode, srn, startDate, accessType, versionInt, 0).url

  private def httpPathPOST: String = routes.ChargeDetailsController.onSubmit(NormalMode, srn, startDate, accessType, versionInt, 0).url

  private def getRequest: FakeRequest[AnyContentAsEmpty.type] =
    FakeRequest(GET, httpPathGET)

  private def postRequest: FakeRequest[AnyContentAsFormUrlEncoded] =
    FakeRequest(POST, httpPathPOST)
      .withFormUrlEncodedBody(
        "qropsReferenceNumber" -> chargeGDetails.qropsReferenceNumber,
        "qropsTransferDate.day" -> chargeGDetails.qropsTransferDate.getDayOfMonth.toString,
        "qropsTransferDate.month" -> chargeGDetails.qropsTransferDate.getMonthValue.toString,
        "qropsTransferDate.year" -> chargeGDetails.qropsTransferDate.getYear.toString
      )

  private def viewModel: GenericViewModel = GenericViewModel(
    submitUrl = httpPathPOST,
    returnUrl = controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, versionInt).url,
    schemeName = schemeName
  )

  private val userAnswersWithSchemeNameAndMemberGName: UserAnswers =
    userAnswersWithSchemeNamePstrQuarter.set(pages.chargeG.MemberDetailsPage(0), memberGDetails).toOption.get

  "ChargeGDetails Controller" must {

    "return OK and the correct view for a GET" in {

      when(mockAppConfig.schemeDashboardUrl(any(): IdentifierRequest[_])).thenReturn(onwardRoute.url)
      when(mockRenderer.render(any(), any())(any())).thenReturn(Future.successful(Html("")))

      mutableFakeDataRetrievalAction.setDataToReturn(Some(userAnswersWithSchemeNameAndMemberGName))

      val templateCaptor = ArgumentCaptor.forClass(classOf[String])
      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

      val result = route(application, getRequest).value

      status(result) mustEqual OK

      verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

      val expectedJson = Json.obj(
        "form" -> form,
        "viewModel" -> viewModel,
        "date" -> DateInput.localDate(form("qropsTransferDate")),
        "memberName" -> memberGDetails.fullName
      )

      templateCaptor.getValue mustEqual "chargeG/chargeDetails.njk"
      jsonCaptor.getValue must containJson(expectedJson)
    }

    "populate the view correctly on a GET when the question has previously been answered" in {

      when(mockAppConfig.schemeDashboardUrl(any(): IdentifierRequest[_])).thenReturn(onwardRoute.url)
      when(mockRenderer.render(any(), any())(any())).thenReturn(Future.successful(Html("")))
      mutableFakeDataRetrievalAction.setDataToReturn(Some(chargeGMember))

      val templateCaptor = ArgumentCaptor.forClass(classOf[String])
      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

      val result = route(application, getRequest).value

      status(result) mustEqual OK

      verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

      val expectedJson = Json.obj(
        "form" -> form.fill(chargeGDetails),
        "viewModel" -> viewModel,
        "date" -> DateInput.localDate(form.fill(chargeGDetails)("qropsTransferDate")),
        "memberName" -> memberGDetails.fullName
      )

      templateCaptor.getValue mustEqual "chargeG/chargeDetails.njk"
      jsonCaptor.getValue must containJson(expectedJson)
    }

    "redirect to the next page when valid data is submitted" in {
      DateHelper.setDate(Some(startDate))
      when(mockAppConfig.schemeDashboardUrl(any(): IdentifierRequest[_])).thenReturn(onwardRoute.url)
      when(mockUserAnswersCacheConnector.savePartial(any(), any(), any(), any())(any(), any())) thenReturn Future.successful(Json.obj())
      when(mockCompoundNavigator.nextPage(any(), any(), any(), any(), any(), any(), any())(any())).thenReturn(onwardRoute)
      when(mockRenderer.render(any(), any())(any())).thenReturn(Future.successful(Html("")))

      mutableFakeDataRetrievalAction.setDataToReturn(Some(userAnswersWithSchemeNameAndMemberGName))

      val result = route(application, postRequest).value

      status(result) mustEqual SEE_OTHER

      redirectLocation(result).value mustEqual onwardRoute.url
    }

    "return a Bad Request and errors when invalid data is submitted" in {

      when(mockRenderer.render(any(), any())(any())).thenReturn(Future.successful(Html("")))
      when(mockAppConfig.schemeDashboardUrl(any(): IdentifierRequest[_])).thenReturn(onwardRoute.url)

      mutableFakeDataRetrievalAction.setDataToReturn(Some(userAnswersWithSchemeNameAndMemberGName))

      val request = FakeRequest(POST, httpPathPOST).withFormUrlEncodedBody(("value", "invalid value"))
      val boundForm = form.bind(Map("value" -> "invalid value"))
      val templateCaptor = ArgumentCaptor.forClass(classOf[String])
      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

      val result = route(application, request).value

      status(result) mustEqual BAD_REQUEST

      verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

      val expectedJson = Json.obj(
        "form" -> boundForm,
        "viewModel" -> viewModel,
        "date" -> DateInput.localDate(boundForm("qropsTransferDate")),
        "memberName" -> memberGDetails.fullName
      )

      templateCaptor.getValue mustEqual "chargeG/chargeDetails.njk"
      jsonCaptor.getValue must containJson(expectedJson)
    }

    "redirect to Session Expired for a GET if no existing data is found" in {

      mutableFakeDataRetrievalAction.setDataToReturn(None)

      val result = route(application, getRequest).value

      status(result) mustEqual SEE_OTHER
      redirectLocation(result).value mustEqual controllers.routes.SessionExpiredController.onPageLoad.url
    }

    "redirect to Session Expired for a POST if no existing data is found" in {

      mutableFakeDataRetrievalAction.setDataToReturn(None)

      val result = route(application, postRequest).value

      status(result) mustEqual SEE_OTHER

      redirectLocation(result).value mustEqual controllers.routes.SessionExpiredController.onPageLoad.url
    }
  }
}
