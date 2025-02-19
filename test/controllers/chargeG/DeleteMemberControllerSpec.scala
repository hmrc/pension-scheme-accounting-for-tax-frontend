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
import forms.YesNoFormProvider
import matchers.JsonMatchers
import models.LocalDateBinder._
import models.requests.IdentifierRequest
import models.UserAnswers
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{times, verify, when}
import org.mockito.ArgumentMatchers
import org.scalatest.{OptionValues, TryValues}
import org.scalatestplus.mockito.MockitoSugar
import pages.PSTRQuery
import pages.chargeG.{MemberDetailsPage, TotalChargeAmountPage}
import play.api.Application
import play.api.data.Form
import play.api.inject.bind
import play.api.libs.json.Json
import play.api.mvc.Call
import play.api.test.FakeRequest
import play.api.test.Helpers._
import services.DeleteAFTChargeService
import uk.gov.hmrc.http.UpstreamErrorResponse
import viewmodels.TwirlRadios
import views.html.chargeG.DeleteMemberView

import scala.concurrent.Future

class DeleteMemberControllerSpec extends ControllerSpecBase with MockitoSugar with JsonMatchers with OptionValues with TryValues {
  private val mockDeleteAFTChargeService: DeleteAFTChargeService = mock[DeleteAFTChargeService]
  private val mutableFakeDataRetrievalAction: MutableFakeDataRetrievalAction = new MutableFakeDataRetrievalAction()
  private val application: Application =
    applicationBuilderMutableRetrievalAction(mutableFakeDataRetrievalAction,
      Seq(bind[DeleteAFTChargeService].toInstance(mockDeleteAFTChargeService))).build()

  private def onwardRoute = Call("GET", "/foo")

  private val memberName = "first last"
  private val formProvider = new YesNoFormProvider()
  private val form: Form[Boolean] = formProvider(messages("deleteMember.chargeG.error.required", memberName))

  private def httpPathGET: String = routes.DeleteMemberController.onPageLoad(srn, startDate, accessType, versionInt, 0).url

  private val pstr = "test pstr"

  private def userAnswers = userAnswersWithSchemeNamePstrQuarter
    .set(MemberDetailsPage(0), memberGDetails).success.value
    .set(MemberDetailsPage(1), memberGDetails).success.value

  private val answers: UserAnswers = userAnswers
    .set(PSTRQuery, pstr).success.value

  "DeleteMember Controller" must {

    "return OK and the correct view for a GET" in {
      when(mockAppConfig.schemeDashboardUrl(any(): IdentifierRequest[_])).thenReturn(onwardRoute.url)

      mutableFakeDataRetrievalAction.setDataToReturn(Some(userAnswers))
      val request = FakeRequest(GET, httpPathGET)

      val view = application.injector.instanceOf[DeleteMemberView].apply(
        form,
        TwirlRadios.yesNo(form("value")),
        memberName,
        routes.DeleteMemberController.onSubmit(srn, startDate, accessType, versionInt, 0),
        controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, versionInt).url,
        schemeName
      )(request, messages)

      val result = route(application, request).value

      status(result) mustEqual OK

      compareResultAndView(result, view)
    }

    "redirect to the next page when valid data is submitted and re-submit the data to DES with the deleted member marked as deleted" in {
      when(mockAppConfig.schemeDashboardUrl(any(): IdentifierRequest[_])).thenReturn(onwardRoute.url)
      when(mockUserAnswersCacheConnector.save(any(), any())(any(), any())) thenReturn Future.successful(Json.obj())
      when(mockCompoundNavigator.nextPage(any(), any(), any(), any(), any(), any(), any())(any())).thenReturn(onwardRoute)
      when(mockDeleteAFTChargeService.deleteAndFileAFTReturn(any(), any())(any(), any(), any())).thenReturn(Future.successful(()))
      mutableFakeDataRetrievalAction.setDataToReturn(Some(answers))

      val request =
        FakeRequest(POST, httpPathGET)
          .withFormUrlEncodedBody(("value", "true"))

      val result = route(application, request).value

      status(result) mustEqual SEE_OTHER

      redirectLocation(result).value mustEqual onwardRoute.url

      val expectedUA = userAnswersWithSchemeNamePstrQuarter
        .set(PSTRQuery, pstr).success.value
        .set(MemberDetailsPage(0), memberGDetails).success.value
        .set(TotalChargeAmountPage, BigDecimal(0.00)).toOption.get

      verify(mockDeleteAFTChargeService, times(1)).deleteAndFileAFTReturn(ArgumentMatchers.eq(pstr),
        ArgumentMatchers.eq(expectedUA))(any(), any(), any())
    }

    "return a Bad Request and errors when invalid data is submitted" in {
      when(mockAppConfig.schemeDashboardUrl(any(): IdentifierRequest[_])).thenReturn(onwardRoute.url)
      when(mockDeleteAFTChargeService.deleteAndFileAFTReturn(any(), any())(any(), any(), any())).thenReturn(Future.successful(()))

      mutableFakeDataRetrievalAction.setDataToReturn(Some(userAnswers))

      val request = FakeRequest(POST, httpPathGET).withFormUrlEncodedBody(("value", ""))
      val boundForm = form.bind(Map("value" -> ""))

      val view = application.injector.instanceOf[DeleteMemberView].apply(
        boundForm,
        TwirlRadios.yesNo(boundForm("value")),
        memberName,
        routes.DeleteMemberController.onSubmit(srn, startDate, accessType, versionInt, 0),
        controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, versionInt).url,
        schemeName
      )(request, messages)

      val result = route(application, request).value

      status(result) mustEqual BAD_REQUEST

      compareResultAndView(result, view)
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

    "redirect to your action was not processed page for a POST if 5XX error is thrown" in {
      mutableFakeDataRetrievalAction.setDataToReturn(Some(userAnswers))
      when(mockDeleteAFTChargeService.deleteAndFileAFTReturn(any(), any())(any(), any(), any()))
        .thenReturn(Future.failed(UpstreamErrorResponse("serviceUnavailable", SERVICE_UNAVAILABLE, SERVICE_UNAVAILABLE)))
      val request = FakeRequest(POST, httpPathGET).withFormUrlEncodedBody(("value", "true"))

      val result = route(application, request).value

      status(result) mustEqual SEE_OTHER
      redirectLocation(result).value mustEqual controllers.routes.YourActionWasNotProcessedController.onPageLoad(srn, startDate).url
    }
  }
}
