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

package controllers.amend

import connectors.AFTConnector
import controllers.actions.{AllowSubmissionAction, FakeAllowSubmissionAction, MutableFakeDataRetrievalAction}
import controllers.base.ControllerSpecBase
import data.SampleData
import data.SampleData._
import forms.ConfirmSubmitAFTReturnFormProvider
import helpers.AmendmentHelper
import matchers.JsonMatchers
import models.{AccessMode, GenericViewModel, NormalMode, SessionData, UserAnswers}
import org.mockito.Matchers.any
import org.mockito.Mockito.{never, times, verify, when}
import org.mockito.{ArgumentCaptor, Mockito}
import pages.{ConfirmSubmitAFTAmendmentPage, ConfirmSubmitAFTReturnPage}
import play.api.Application
import play.api.data.Form
import play.api.inject.bind
import play.api.inject.guice.GuiceableModule
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.Call
import play.api.mvc.Results.Ok
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.twirl.api.Html
import uk.gov.hmrc.viewmodels.{NunjucksSupport, Radios}
import utils.AFTConstants.QUARTER_START_DATE

import scala.concurrent.Future
import models.LocalDateBinder._

class ConfirmSubmitAFTAmendmentControllerSpec extends ControllerSpecBase with NunjucksSupport with JsonMatchers {
  private def onwardRoute = Call("GET", "/onward")

  private val formProvider = new ConfirmSubmitAFTReturnFormProvider()
  private val form = formProvider()

  private val mockAmendmentHelper = mock[AmendmentHelper]
  private val mockAFTConnector = mock[AFTConnector]

  private def confirmSubmitAFTAmendmentRoute: String = routes.ConfirmSubmitAFTAmendmentController.onPageLoad(srn, QUARTER_START_DATE).url

  private def confirmSubmitAFTAmendmentSubmitRoute: String = routes.ConfirmSubmitAFTAmendmentController.onSubmit(srn, QUARTER_START_DATE).url

  private val mutableFakeDataRetrievalAction: MutableFakeDataRetrievalAction = new MutableFakeDataRetrievalAction()
  private val extraModules: Seq[GuiceableModule] = Seq(
    bind[AllowSubmissionAction].toInstance(new FakeAllowSubmissionAction),
    bind[AmendmentHelper].toInstance(mockAmendmentHelper),
    bind[AFTConnector].toInstance(mockAFTConnector)
  )
  private val application: Application = applicationBuilderMutableRetrievalAction(mutableFakeDataRetrievalAction, extraModules).build()
  private val templateToBeRendered = "confirmSubmitAFTAmendment.njk"

  private val templateCaptor = ArgumentCaptor.forClass(classOf[String])
  private val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])
  private val versionNumber = 3

  private def jsonToBePassed(form: Form[Boolean]): JsObject = Json.obj(
    fields = "srn" -> srn,
    "startDate" -> Some(startDate),
    "form" -> form,
    "versionNumber" -> 3,
    "viewModel" -> GenericViewModel(
      submitUrl = confirmSubmitAFTAmendmentSubmitRoute,
      returnUrl = dummyCall.url,
      schemeName = schemeName),
    "tableRowsUK" -> Nil,
    "tableRowsNonUK" -> Nil,
    "radios" -> Radios.yesNo(form("value"))
  )

  override def beforeEach: Unit = {
    super.beforeEach
    Mockito.reset(mockUserAnswersCacheConnector, mockRenderer, mockAmendmentHelper, mockAFTConnector)
    when(mockRenderer.render(any(), any())(any())).thenReturn(Future.successful(Html("")))
    when(mockAppConfig.managePensionsSchemeSummaryUrl).thenReturn(dummyCall.url)
    when(mockAmendmentHelper.amendmentSummaryRows(any(), any(), any(), any())(any())).thenReturn(Nil)
    when(mockAmendmentHelper.getTotalAmount(any())).thenReturn((BigDecimal(2000.00), BigDecimal(40000.00)))
    when(mockAFTConnector.getAFTDetails(any(), any(), any())(any(), any())).thenReturn(Future.successful(Json.obj()))
  }
  mutableFakeDataRetrievalAction.setSessionData(SampleData.sessionData
  (sessionAccessData = sessionAccessData(versionNumber, AccessMode.PageAccessModeCompile)))

  private val userAnswers: Option[UserAnswers] = Some(userAnswersWithSchemeName)

  "ConfirmSubmitAFTAmendment Controller" must {

    "return OK and the correct view for a GET" in {
      mutableFakeDataRetrievalAction.setDataToReturn(userAnswers)
      val request = FakeRequest(GET, confirmSubmitAFTAmendmentRoute)
      val expectedJson = jsonToBePassed(form)

      val result = route(application, request).value
      status(result) mustEqual OK

      verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

      templateCaptor.getValue mustEqual templateToBeRendered
      jsonCaptor.getValue must containJson(expectedJson)
    }

    "populate the view correctly on a GET when the question has previously been answered" in {
      val filledAnswers = userAnswers.map(_.set(ConfirmSubmitAFTAmendmentPage, value = true).getOrElse(UserAnswers()))
      mutableFakeDataRetrievalAction.setDataToReturn(filledAnswers)
      val request = FakeRequest(GET, confirmSubmitAFTAmendmentRoute)
      val filledForm = form.bind(Map("value" -> "true"))
      val expectedJson = jsonToBePassed(filledForm)

      val result = route(application, request).value
      status(result) mustEqual OK

      verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

      templateCaptor.getValue mustEqual templateToBeRendered

      jsonCaptor.getValue must containJson(expectedJson)
    }

    "redirect to the next page when submits with value true" in {
      mutableFakeDataRetrievalAction.setDataToReturn(userAnswers)
      when(mockUserAnswersCacheConnector.save(any(), any(), any(), any())(any(), any())).thenReturn(Future.successful(Json.obj()))
      when(mockCompoundNavigator.nextPage(any(), any(), any(), any(), any())).thenReturn(onwardRoute)
      val request =
        FakeRequest(POST, confirmSubmitAFTAmendmentRoute)
          .withFormUrlEncodedBody(("value", "true"))

      val result = route(application, request).value

      status(result) mustEqual SEE_OTHER
      redirectLocation(result).value mustEqual onwardRoute.url
      verify(mockUserAnswersCacheConnector, times(1)).save(any(), any(), any(), any())(any(), any())
    }

    "remove the data and redirect to the pension scheme url when user submits with value false" in {
      mutableFakeDataRetrievalAction.setDataToReturn(userAnswers)
      when(mockUserAnswersCacheConnector.removeAll(any())(any(), any())).thenReturn(Future.successful(Ok))
      val request =
        FakeRequest(POST, confirmSubmitAFTAmendmentRoute)
          .withFormUrlEncodedBody(("value", "false"))

      val result = route(application, request).value

      status(result) mustEqual SEE_OTHER
      redirectLocation(result).value mustEqual dummyCall.url
      verify(mockUserAnswersCacheConnector, times(1)).removeAll(any())(any(), any())
      verify(mockUserAnswersCacheConnector, never).save(any(), any(), any(), any())(any(), any())
    }

    "return a Bad Request and errors when invalid data is submitted" in {
      mutableFakeDataRetrievalAction.setDataToReturn(userAnswers)
      val request = FakeRequest(POST, confirmSubmitAFTAmendmentRoute).withFormUrlEncodedBody(("value", ""))

      val result = route(application, request).value
      status(result) mustEqual BAD_REQUEST

      verify(mockRenderer, times(1)).render(any(), any())(any())
      verify(mockUserAnswersCacheConnector, never).save(any(), any(), any(), any())(any(), any())
    }

    "redirect to Session Expired for a GET if no existing data is found" in {
      mutableFakeDataRetrievalAction.setDataToReturn(None)
      val request = FakeRequest(GET, confirmSubmitAFTAmendmentRoute)

      val result = route(application, request).value
      status(result) mustEqual SEE_OTHER

      redirectLocation(result).value mustEqual controllers.routes.SessionExpiredController.onPageLoad().url
    }

    "redirect to Session Expired for a POST if no existing data is found" in {
      mutableFakeDataRetrievalAction.setDataToReturn(None)
      val request =
        FakeRequest(POST, confirmSubmitAFTAmendmentRoute)
          .withFormUrlEncodedBody(("value", "true"))

      val result = route(application, request).value
      status(result) mustEqual SEE_OTHER

      redirectLocation(result).value mustEqual controllers.routes.SessionExpiredController.onPageLoad().url
    }
  }
}
