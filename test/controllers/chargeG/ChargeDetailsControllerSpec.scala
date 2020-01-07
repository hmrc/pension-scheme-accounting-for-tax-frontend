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
import controllers.base.ControllerSpecBase
import data.SampleData._
import forms.chargeG.ChargeDetailsFormProvider
import matchers.JsonMatchers
import models.chargeG.ChargeDetails
import models.{GenericViewModel, NormalMode, UserAnswers}
import org.mockito.ArgumentCaptor
import org.mockito.Matchers.any
import org.mockito.Mockito.{times, verify, when}
import org.scalatestplus.mockito.MockitoSugar
import play.api.data.Form
import play.api.inject.bind
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.{AnyContentAsEmpty, AnyContentAsFormUrlEncoded, Call}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.twirl.api.Html
import uk.gov.hmrc.viewmodels.{DateInput, NunjucksSupport}

import scala.concurrent.Future

class ChargeDetailsControllerSpec extends ControllerSpecBase with MockitoSugar with NunjucksSupport with JsonMatchers {

  private val mockAppConfig: FrontendAppConfig = mock[FrontendAppConfig]
  private val formProvider = new ChargeDetailsFormProvider()

  private def form: Form[ChargeDetails] = formProvider("The date of the transfer into the QROPS must be between 1 April 2020 and 30 June 2020")

  private def onwardRoute: Call = Call("GET", "/foo")

  private def chargeDetailsRoute: String = routes.ChargeDetailsController.onPageLoad(NormalMode, srn, 0).url

  private def chargeDetailsSubmitRoute: String = routes.ChargeDetailsController.onSubmit(NormalMode, srn, 0).url

  private def getRequest: FakeRequest[AnyContentAsEmpty.type] =
    FakeRequest(GET, chargeDetailsRoute)

  private def postRequest: FakeRequest[AnyContentAsFormUrlEncoded] =
    FakeRequest(POST, chargeDetailsSubmitRoute)
      .withFormUrlEncodedBody(
        "qropsReferenceNumber" -> chargeGDetails.qropsReferenceNumber,
        "qropsTransferDate.day" -> chargeGDetails.qropsTransferDate.getDayOfMonth.toString,
        "qropsTransferDate.month" -> chargeGDetails.qropsTransferDate.getMonthValue.toString,
        "qropsTransferDate.year" -> chargeGDetails.qropsTransferDate.getYear.toString
      )

  private val viewModel: GenericViewModel = GenericViewModel(
    submitUrl = chargeDetailsSubmitRoute,
    returnUrl = onwardRoute.url,
    schemeName = schemeName
  )

  private val userAnswersWithSchemeNameAndMemberGName: UserAnswers =
    userAnswersWithSchemeName.set(pages.chargeG.MemberDetailsPage(0), memberDetailsG).toOption.get

  "ChargeGDetails Controller" must {

    "return OK and the correct view for a GET" in {

      when(mockAppConfig.managePensionsSchemeSummaryUrl).thenReturn(onwardRoute.url)
      when(mockRenderer.render(any(), any())(any())).thenReturn(Future.successful(Html("")))

      val application = applicationBuilder(userAnswers = Some(userAnswersWithSchemeNameAndMemberGName))
        .overrides(
          bind[FrontendAppConfig].toInstance(mockAppConfig)
        )
        .build()
      val templateCaptor = ArgumentCaptor.forClass(classOf[String])
      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

      val result = route(application, getRequest).value

      status(result) mustEqual OK

      verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

      val expectedJson = Json.obj(
        "form" -> form,
        "viewModel" -> viewModel,
        "date" -> DateInput.localDate(form("qropsTransferDate")),
        "memberName" -> memberDetailsG.fullName
      )

      templateCaptor.getValue mustEqual "chargeG/chargeDetails.njk"
      jsonCaptor.getValue must containJson(expectedJson)

      application.stop()
    }

    "populate the view correctly on a GET when the question has previously been answered" in {

      when(mockAppConfig.managePensionsSchemeSummaryUrl).thenReturn(onwardRoute.url)
      when(mockRenderer.render(any(), any())(any())).thenReturn(Future.successful(Html("")))

      val application = applicationBuilder(userAnswers = Some(chargeGMember))
        .overrides(
          bind[FrontendAppConfig].toInstance(mockAppConfig)
        )
        .build()

      val templateCaptor = ArgumentCaptor.forClass(classOf[String])
      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

      val result = route(application, getRequest).value

      status(result) mustEqual OK

      verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

      val filledForm = form.bind(
        Map(
          "qropsReferenceNumber" -> chargeGDetails.qropsReferenceNumber,
          "qropsTransferDate.day" -> chargeGDetails.qropsTransferDate.getDayOfMonth.toString,
          "qropsTransferDate.month" -> chargeGDetails.qropsTransferDate.getMonthValue.toString,
          "qropsTransferDate.year" -> chargeGDetails.qropsTransferDate.getYear.toString
        )
      )

      val expectedJson = Json.obj(
        "form" -> filledForm,
        "viewModel" -> viewModel,
        "date" -> DateInput.localDate(form("qropsTransferDate")),
        "memberName" -> memberDetailsG.fullName
      )

      println(s"\n\n$expectedJson\n")
      println(s"\n${jsonCaptor.getValue}\n\n")

      templateCaptor.getValue mustEqual "chargeG/chargeDetails.njk"
      jsonCaptor.getValue must containJson(expectedJson)

      application.stop()
    }

    "redirect to the next page when valid data is submitted" in {

      when(mockAppConfig.managePensionsSchemeSummaryUrl).thenReturn(onwardRoute.url)
      when(mockUserAnswersCacheConnector.save(any(), any())(any(), any())) thenReturn Future.successful(Json.obj())
      when(mockCompoundNavigator.nextPage(any(), any(), any(), any())).thenReturn(onwardRoute)

      val application = applicationBuilder(userAnswers = Some(userAnswersWithSchemeNameAndMemberGName))
        .overrides(
          bind[FrontendAppConfig].toInstance(mockAppConfig)
        )
        .build()

      val result = route(application, postRequest).value

      status(result) mustEqual SEE_OTHER

      redirectLocation(result).value mustEqual onwardRoute.url

      application.stop()
    }

    "return a Bad Request and errors when invalid data is submitted" in {

      when(mockRenderer.render(any(), any())(any()))
        .thenReturn(Future.successful(Html("")))
      when(mockAppConfig.managePensionsSchemeSummaryUrl).thenReturn(onwardRoute.url)


      val application = applicationBuilder(userAnswers = Some(userAnswersWithSchemeNameAndMemberGName))
        .overrides(
          bind[FrontendAppConfig].toInstance(mockAppConfig)
        )
        .build()

      val request = FakeRequest(POST, chargeDetailsSubmitRoute).withFormUrlEncodedBody(("value", "invalid value"))
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
        "memberName" -> memberDetailsG.fullName
      )

      templateCaptor.getValue mustEqual "chargeG/chargeDetails.njk"
      jsonCaptor.getValue must containJson(expectedJson)

      application.stop()
    }

    "redirect to Session Expired for a GET if no existing data is found" in {

      val application = applicationBuilder(userAnswers = None).build()

      val result = route(application, getRequest).value

      status(result) mustEqual SEE_OTHER
      redirectLocation(result).value mustEqual controllers.routes.SessionExpiredController.onPageLoad().url

      application.stop()
    }

    "redirect to Session Expired for a POST if no existing data is found" in {

      val application = applicationBuilder(userAnswers = None).build()

      val result = route(application, postRequest).value

      status(result) mustEqual SEE_OTHER

      redirectLocation(result).value mustEqual controllers.routes.SessionExpiredController.onPageLoad().url

      application.stop()
    }
  }
}
