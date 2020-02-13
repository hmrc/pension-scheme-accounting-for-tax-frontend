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

package controllers.chargeG

import config.FrontendAppConfig
import connectors.AFTConnector
import controllers.actions.MutableFakeDataRetrievalAction
import controllers.base.ControllerSpecBase
import data.SampleData._
import forms.DeleteMemberFormProvider
import matchers.JsonMatchers
import models.{GenericViewModel, UserAnswers}
import org.mockito.Matchers.any
import org.mockito.Mockito.{times, verify, when}
import org.mockito.{ArgumentCaptor, Matchers}
import org.scalatest.{OptionValues, TryValues}
import org.scalatestplus.mockito.MockitoSugar
import pages.PSTRQuery
import pages.chargeG.{MemberDetailsPage, TotalChargeAmountPage}
import play.api.Application
import play.api.data.Form
import play.api.inject.bind
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.Call
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.twirl.api.Html
import uk.gov.hmrc.viewmodels.{NunjucksSupport, Radios}

import scala.concurrent.Future

class DeleteMemberControllerSpec extends ControllerSpecBase with MockitoSugar with NunjucksSupport with JsonMatchers with OptionValues with TryValues {
  private val mockAftConnector: AFTConnector = mock[AFTConnector]
  private val mutableFakeDataRetrievalAction: MutableFakeDataRetrievalAction = new MutableFakeDataRetrievalAction()
  private val application: Application =
    applicationBuilderMutableRetrievalAction(mutableFakeDataRetrievalAction, Seq(bind[AFTConnector].toInstance(mockAftConnector))).build()

  private def onwardRoute = Call("GET", "/foo")

  private val memberName = "first last"
  private val formProvider = new DeleteMemberFormProvider()
  private val form: Form[Boolean] = formProvider(messages("deleteMember.chargeG.error.required", memberName))

  private def httpPathGET: String = routes.DeleteMemberController.onPageLoad(srn, 0).url

  private def httpPathPOST: String = routes.DeleteMemberController.onSubmit(srn, 0).url

  private val viewModel = GenericViewModel(
    submitUrl = httpPathPOST,
    returnUrl = onwardRoute.url,
    schemeName = schemeName)

  private val pstr = "test pstr"

  private def userAnswers = userAnswersWithSchemeName
    .set(MemberDetailsPage(0), memberGDetails).success.value
    .set(MemberDetailsPage(1), memberGDetails).success.value

  private val answers: UserAnswers = userAnswers
    .set(PSTRQuery, pstr).success.value

  "DeleteMember Controller" must {

    "return OK and the correct view for a GET" in {
      when(mockAppConfig.managePensionsSchemeSummaryUrl).thenReturn(onwardRoute.url)
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
        "viewModel" -> viewModel,
        "radios" -> Radios.yesNo(form("value")),
        "memberName" -> memberName
      )

      templateCaptor.getValue mustEqual "chargeG/deleteMember.njk"
      jsonCaptor.getValue must containJson(expectedJson)
    }

    "redirect to the next page when valid data is submitted and re-submit the data to DES with the deleted member marked as deleted" in {
      when(mockAppConfig.managePensionsSchemeSummaryUrl).thenReturn(onwardRoute.url)
      when(mockUserAnswersCacheConnector.save(any(), any())(any(), any())) thenReturn Future.successful(Json.obj())
      when(mockCompoundNavigator.nextPage(any(), any(), any(), any())).thenReturn(onwardRoute)
      when(mockAftConnector.fileAFTReturn(any(), any())(any(), any())).thenReturn(Future.successful(()))
      mutableFakeDataRetrievalAction.setDataToReturn(Some(answers))

      val request =
        FakeRequest(POST, httpPathGET)
          .withFormUrlEncodedBody(("value", "true"))

      val result = route(application, request).value

      status(result) mustEqual SEE_OTHER

      redirectLocation(result).value mustEqual onwardRoute.url

      val expectedUA = answers.set(MemberDetailsPage(0), memberGDetails.copy(isDeleted = true)).toOption.get
        .set(TotalChargeAmountPage, BigDecimal(0.00)).toOption.get

      verify(mockAftConnector, times(1)).fileAFTReturn(Matchers.eq(pstr), Matchers.eq(expectedUA))(any(), any())
    }

    "return a Bad Request and errors when invalid data is submitted" in {

      when(mockAppConfig.managePensionsSchemeSummaryUrl).thenReturn(onwardRoute.url)
      when(mockRenderer.render(any(), any())(any())).thenReturn(Future.successful(Html("")))
      when(mockAftConnector.fileAFTReturn(any(), any())(any(), any())).thenReturn(Future.successful(()))

      mutableFakeDataRetrievalAction.setDataToReturn(Some(userAnswers))

      val request = FakeRequest(POST, httpPathGET).withFormUrlEncodedBody(("value", ""))
      val boundForm = form.bind(Map("value" -> ""))
      val templateCaptor = ArgumentCaptor.forClass(classOf[String])
      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

      val result = route(application, request).value

      status(result) mustEqual BAD_REQUEST

      verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

      val expectedJson = Json.obj(
        "form" -> boundForm,
        "viewModel" -> viewModel,
        "radios" -> Radios.yesNo(boundForm("value"))
      )

      templateCaptor.getValue mustEqual "chargeG/deleteMember.njk"
      jsonCaptor.getValue must containJson(expectedJson)
    }

    "redirect to Session Expired for a GET if no existing data is found" in {

      mutableFakeDataRetrievalAction.setDataToReturn(None)

      val request = FakeRequest(GET, httpPathGET)

      val result = route(application, request).value

      status(result) mustEqual SEE_OTHER

      redirectLocation(result).value mustEqual controllers.routes.SessionExpiredController.onPageLoad().url
    }

    "redirect to Session Expired for a POST if no existing data is found" in {

      mutableFakeDataRetrievalAction.setDataToReturn(None)

      val request =
        FakeRequest(POST, httpPathGET)
          .withFormUrlEncodedBody(("value", "true"))

      val result = route(application, request).value

      status(result) mustEqual SEE_OTHER

      redirectLocation(result).value mustEqual controllers.routes.SessionExpiredController.onPageLoad().url
    }
  }
}
