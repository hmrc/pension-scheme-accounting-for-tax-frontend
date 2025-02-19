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

package controllers

import controllers.actions.MutableFakeDataRetrievalAction
import controllers.base.ControllerSpecBase
import data.SampleData
import data.SampleData._
import forms.YesNoFormProvider
import matchers.JsonMatchers
import models.ChargeType.{ChargeTypeAnnualAllowance, ChargeTypeLifetimeAllowance}
import models.LocalDateBinder._
import models.NormalMode
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{times, verify, when}
import org.scalatest.{OptionValues, TryValues}
import org.scalatestplus.mockito.MockitoSugar
import pages.QuarterPage
import pages.chargeE.MemberDetailsPage
import play.api.Application
import play.api.data.Form
import play.api.i18n.Messages
import play.api.libs.json.Json
import play.api.mvc.Call
import play.api.test.FakeRequest
import play.api.test.Helpers._
import viewmodels.TwirlRadios
import views.html.IsPublicServicePensionsRemedyView

import java.time.LocalDate
import scala.concurrent.Future

class IsPublicServicePensionsRemedyControllerSpec
  extends ControllerSpecBase
    with MockitoSugar
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
  private val mccloudPsrAlwaysTrueStartDate = LocalDate.of(2024, 4, 1)

  private def httpPathGETAnnualAllowance(index: Option[Int]): String =
    routes.IsPublicServicePensionsRemedyController
      .onPageLoad(ChargeTypeAnnualAllowance, NormalMode, srn, startDate, accessType, versionInt, index)
      .url

  private def httpPathPOSTAnnualAllowance(index: Option[Int]): String =
    routes.IsPublicServicePensionsRemedyController
      .onSubmit(ChargeTypeAnnualAllowance, NormalMode, srn, startDate, accessType, versionInt, index)
      .url

  private def httpPathGETLifetimeAllowance(index: Option[Int]): String =
    routes.IsPublicServicePensionsRemedyController
      .onPageLoad(ChargeTypeLifetimeAllowance, NormalMode, srn, startDate, accessType, versionInt, index)
      .url

  private def httpPathPOSTLifetimeAllowance(index: Option[Int]): String =
    routes.IsPublicServicePensionsRemedyController
      .onSubmit(ChargeTypeLifetimeAllowance, NormalMode, srn, startDate, accessType, versionInt, index)
      .url

  private def userAnswers =
    userAnswersWithSchemeNamePstrQuarter
      .set(MemberDetailsPage(0), memberDetails)
      .success
      .value
      .set(MemberDetailsPage(1), memberDetails)
      .success
      .value

  "IsPublicServicePensionsRemedy Controller" must {

    "return OK and the correct view for a GET for Annual allowance (fileUpload)" in {

      mutableFakeDataRetrievalAction.setDataToReturn(Some(userAnswers))
      val request = FakeRequest(GET, httpPathGETAnnualAllowance(None))

      val result = route(application, request).value

      status(result) mustEqual OK

      val view = application.injector.instanceOf[IsPublicServicePensionsRemedyView].apply(
        "isPublicServicePensionsRemedyBulk.heading",
        "isPublicServicePensionsRemedyBulk.title",
        Messages(s"chargeType.description.${ChargeTypeAnnualAllowance.toString}"),
        formBulk,
        TwirlRadios.yesNo(formBulk("value")),
        routes.IsPublicServicePensionsRemedyController
          .onSubmit(ChargeTypeAnnualAllowance, NormalMode, srn, startDate, accessType, versionInt, None),
        controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, versionInt).url,
        schemeName
      )(request, messages)

      compareResultAndView(result, view)
    }
    "return OK and the correct view for a GET (for ManualInput PSR question)" in {

      mutableFakeDataRetrievalAction.setDataToReturn(Some(userAnswers))
      val request = FakeRequest(GET, httpPathGETAnnualAllowance(Some(0)))

      val result = route(application, request).value

      status(result) mustEqual OK

      val view = application.injector.instanceOf[IsPublicServicePensionsRemedyView].apply(
        "isPublicServicePensionsRemedy.heading",
        "isPublicServicePensionsRemedy.title",
        Messages(s"chargeType.description.${ChargeTypeAnnualAllowance.toString}"),
        formManual,
        TwirlRadios.yesNo(formManual("value")),
        routes.IsPublicServicePensionsRemedyController
          .onSubmit(ChargeTypeAnnualAllowance, NormalMode, srn, startDate, accessType, versionInt, Some(0)),
        controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, versionInt).url,
        schemeName
      )(request, messages)

      compareResultAndView(result, view)
    }

    "return OK and the correct view for a GET for Lifetime allowance (Manual Input) for PSR dynamic" in {
      when(mockAppConfig.mccloudPsrAlwaysTrueStartDate).thenReturn(mccloudPsrAlwaysTrueStartDate)

      val ua = userAnswers.setOrException(QuarterPage, SampleData.taxQtrAprToJun2023)

      mutableFakeDataRetrievalAction.setDataToReturn(Some(ua))
      val request = FakeRequest(GET, httpPathGETLifetimeAllowance(Some(0)))

      val result = route(application, request).value

      status(result) mustEqual OK

      val view = application.injector.instanceOf[IsPublicServicePensionsRemedyView].apply(
        "isPublicServicePensionsRemedy.heading",
        "isPublicServicePensionsRemedy.title",
        Messages(s"chargeType.description.${ChargeTypeLifetimeAllowance.toString}"),
        formManual,
        TwirlRadios.yesNo(formManual("value")),
        routes.IsPublicServicePensionsRemedyController
          .onSubmit(ChargeTypeLifetimeAllowance, NormalMode, srn, startDate, accessType, versionInt, Some(0)),
        controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, versionInt).url,
        schemeName
      )(request, messages)

      compareResultAndView(result, view)
    }

    "return OK and the correct view for a GET for Lifetime allowance (Manual Input) for PSR default true" in {
      when(mockAppConfig.mccloudPsrAlwaysTrueStartDate).thenReturn(mccloudPsrAlwaysTrueStartDate)
      when(mockUserAnswersCacheConnector.savePartial(any(), any(), any(), any())(any(), any())) thenReturn Future.successful(Json.obj())
      when(mockCompoundNavigator.nextPage(any(), any(), any(), any(), any(), any(), any())(any())).thenReturn(onwardRoute)

      val ua = userAnswers.setOrException(QuarterPage, SampleData.taxQtrAprToJun2024)

      mutableFakeDataRetrievalAction.setDataToReturn(Some(ua))
      val request = FakeRequest(GET, httpPathGETLifetimeAllowance(Some(0)))

      val result = route(application, request).value

      status(result) mustEqual SEE_OTHER

      redirectLocation(result).value mustEqual onwardRoute.url
      verify(mockUserAnswersCacheConnector, times(1)).savePartial(any(), any(), any(), any())(any(), any())
      verify(mockCompoundNavigator, times(1)).nextPage(any(), any(), any(), any(), any(), any(), any())(any())
    }

    "redirect to the next page when valid data is submitted for AnnualAllowance (Manual Journey)" in {
      when(mockUserAnswersCacheConnector.savePartial(any(), any(), any(), any())(any(), any())) thenReturn Future.successful(Json.obj())
      when(mockCompoundNavigator.nextPage(any(), any(), any(), any(), any(), any(), any())(any())).thenReturn(onwardRoute)

      mutableFakeDataRetrievalAction.setDataToReturn(Some(userAnswers))

      val request =
        FakeRequest(POST, httpPathPOSTAnnualAllowance(Some(0)))
          .withFormUrlEncodedBody(("value", "true"))
      val result = route(application, request).value

      status(result) mustEqual SEE_OTHER

      redirectLocation(result).value mustEqual onwardRoute.url
      verify(mockUserAnswersCacheConnector, times(1)).savePartial(any(), any(), any(), any())(any(), any())
      verify(mockCompoundNavigator, times(1)).nextPage(any(), any(), any(), any(), any(), any(), any())(any())
    }

    "redirect to the next page when valid data and user select no to isPublicPensionsRemedy scheme for AnnualAllowance" in {
      when(mockUserAnswersCacheConnector.savePartial(any(), any(), any(), any())(any(), any())) thenReturn Future.successful(Json.obj())
      when(mockCompoundNavigator.nextPage(any(), any(), any(), any(), any(), any(), any())(any())).thenReturn(onwardRoute)
      mutableFakeDataRetrievalAction.setDataToReturn(Some(userAnswers))

      val request =
        FakeRequest(POST, httpPathPOSTAnnualAllowance(Some(0)))
          .withFormUrlEncodedBody(("value", "false"))
      val result = route(application, request).value

      status(result) mustEqual SEE_OTHER

      redirectLocation(result).value mustEqual onwardRoute.url
      verify(mockUserAnswersCacheConnector, times(1)).savePartial(any(), any(), any(), any())(any(), any())
      verify(mockCompoundNavigator, times(1)).nextPage(any(), any(), any(), any(), any(), any(), any())(any())
    }

    "redirect to the next page when valid data with two scheme is submitted for LifetimeAllowance" in {
      when(mockUserAnswersCacheConnector.savePartial(any(), any(), any(), any())(any(), any())) thenReturn Future.successful(Json.obj())
      when(mockCompoundNavigator.nextPage(any(), any(), any(), any(), any(), any(), any())(any())).thenReturn(onwardRoute)

      mutableFakeDataRetrievalAction.setDataToReturn(Some(userAnswers))

      val request =
        FakeRequest(POST, httpPathPOSTLifetimeAllowance(Some(0)))
          .withFormUrlEncodedBody(("value", "true"))
      val result = route(application, request).value

      status(result) mustEqual SEE_OTHER

      redirectLocation(result).value mustEqual onwardRoute.url
      verify(mockUserAnswersCacheConnector, times(1)).savePartial(any(), any(), any(), any())(any(), any())
      verify(mockCompoundNavigator, times(1)).nextPage(any(), any(), any(), any(), any(), any(), any())(any())
    }

    "redirect to the next page when valid data and user select no to isPublicPensionsRemedy scheme for LifetimeAllowance" in {
      when(mockUserAnswersCacheConnector.savePartial(any(), any(), any(), any())(any(), any())) thenReturn Future.successful(Json.obj())
      when(mockCompoundNavigator.nextPage(any(), any(), any(), any(), any(), any(), any())(any())).thenReturn(onwardRoute)
      mutableFakeDataRetrievalAction.setDataToReturn(Some(userAnswers))

      val request =
        FakeRequest(POST, httpPathPOSTLifetimeAllowance(Some(0)))
          .withFormUrlEncodedBody(("value", "false"))
      val result = route(application, request).value

      status(result) mustEqual SEE_OTHER

      redirectLocation(result).value mustEqual onwardRoute.url
      verify(mockUserAnswersCacheConnector, times(1)).savePartial(any(), any(), any(), any())(any(), any())
      verify(mockCompoundNavigator, times(1)).nextPage(any(), any(), any(), any(), any(), any(), any())(any())
    }

    "return a Bad Request and errors when invalid data is submitted (manual journey)" in {

      mutableFakeDataRetrievalAction.setDataToReturn(Some(userAnswers))

      val request = FakeRequest(POST, httpPathGETAnnualAllowance(Some(0))).withFormUrlEncodedBody(("value", ""))
      val boundForm = formManual.bind(Map("value" -> ""))

      val result = route(application, request).value

      status(result) mustEqual BAD_REQUEST

      val view = application.injector.instanceOf[IsPublicServicePensionsRemedyView].apply(
        "isPublicServicePensionsRemedy.heading",
        "isPublicServicePensionsRemedy.title",
        Messages(s"chargeType.description.${ChargeTypeAnnualAllowance.toString}"),
        boundForm,
        TwirlRadios.yesNo(boundForm("value")),
        routes.IsPublicServicePensionsRemedyController
          .onSubmit(ChargeTypeAnnualAllowance, NormalMode, srn, startDate, accessType, versionInt, Some(0)),
        controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, versionInt).url,
        schemeName
      )(request, messages)

      compareResultAndView(result, view)
    }
    "return a Bad Request and errors when invalid data is submitted (fileUpload journey)" in {

      mutableFakeDataRetrievalAction.setDataToReturn(Some(userAnswers))

      val request = FakeRequest(POST, httpPathGETAnnualAllowance(None)).withFormUrlEncodedBody(("value", ""))
      val boundForm = formBulk.bind(Map("value" -> ""))

      val result = route(application, request).value

      status(result) mustEqual BAD_REQUEST

      val view = application.injector.instanceOf[IsPublicServicePensionsRemedyView].apply(
        "isPublicServicePensionsRemedyBulk.heading",
        "isPublicServicePensionsRemedyBulk.title",
        Messages(s"chargeType.description.${ChargeTypeAnnualAllowance.toString}"),
        boundForm,
        TwirlRadios.yesNo(boundForm("value")),
        routes.IsPublicServicePensionsRemedyController
          .onSubmit(ChargeTypeAnnualAllowance, NormalMode, srn, startDate, accessType, versionInt, None),
        controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, versionInt).url,
        schemeName
      )(request, messages)

      compareResultAndView(result, view)
    }

    "redirect to Session Expired for a GET if no existing data is found (manual)" in {

      mutableFakeDataRetrievalAction.setDataToReturn(None)

      val request = FakeRequest(GET, httpPathGETAnnualAllowance(Some(0)))

      val result = route(application, request).value

      status(result) mustEqual SEE_OTHER

      redirectLocation(result).value mustEqual controllers.routes.SessionExpiredController.onPageLoad.url
    }

    "redirect to Session Expired for a POST if no existing data is found (manual)" in {

      mutableFakeDataRetrievalAction.setDataToReturn(None)

      val request =
        FakeRequest(POST, httpPathGETAnnualAllowance(Some(0)))
          .withFormUrlEncodedBody(("value", "true"))

      val result = route(application, request).value

      status(result) mustEqual SEE_OTHER

      redirectLocation(result).value mustEqual controllers.routes.SessionExpiredController.onPageLoad.url
    }
  }
}
