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

package controllers.fileUpload

import controllers.actions.MutableFakeDataRetrievalAction
import controllers.base.ControllerSpecBase
import data.SampleData._
import forms.fileUpload.InputSelectionFormProvider
import matchers.JsonMatchers
import models.ChargeType.ChargeTypeAnnualAllowance
import models.LocalDateBinder._
import models.fileUpload.InputSelection
import models.fileUpload.InputSelection.FileUploadInput
import models.{AccessType, ChargeType, GenericViewModel, UserAnswers}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{times, verify, when}
import org.mockito.{ArgumentCaptor, ArgumentMatchers}
import pages.fileUpload.InputSelectionPage
import play.api.Application
import play.api.data.Form
import play.api.libs.json.{JsObject, Json}
import play.api.test.FakeRequest
import play.api.test.Helpers.{route, status, _}
import utils.TwirlMigration
import views.html.fileUpload.InputSelectionView

import scala.concurrent.Future

class InputSelectionControllerSpec extends ControllerSpecBase with JsonMatchers {
  private val mutableFakeDataRetrievalAction: MutableFakeDataRetrievalAction = new MutableFakeDataRetrievalAction()
  private val chargeType = ChargeType.ChargeTypeAnnualAllowance

  private def ua: UserAnswers = userAnswersWithSchemeName

  private def httpPathPOST: String = controllers.fileUpload.routes.InputSelectionController
    .onSubmit(srn, startDate, accessType, versionInt, ChargeTypeAnnualAllowance).url

  private val valuesValid: Map[String, Seq[String]] = Map(
    "schemeName" -> Seq(schemeName),
    "value" -> Seq("fileUploadInput")
  )

  private val valuesInvalid: Map[String, Seq[String]] = Map(
    "schemeName" -> Seq(schemeName),
    "value" -> Seq("invalid value")
  )

  private val application: Application = applicationBuilderMutableRetrievalAction(mutableFakeDataRetrievalAction).build()
  private val formProvider = new InputSelectionFormProvider
  private val form: Form[InputSelection] = formProvider()
  val submitUrl = controllers.fileUpload.routes.InputSelectionController.onSubmit(srn, startDate, accessType, versionInt, chargeType)
  val returnUrl = controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, versionInt).url

  "onPageLoad" must {
    "return OK and the correct view for a GET" in {
      val request = FakeRequest(GET,controllers.fileUpload
                  .routes.InputSelectionController.onPageLoad(srn, startDate, accessType, versionInt, chargeType).url)
      mutableFakeDataRetrievalAction.setDataToReturn(Some(ua))

      val view = application.injector.instanceOf[InputSelectionView].apply(
        form, schemeName, submitUrl, returnUrl, ChargeType.fileUploadText(chargeType),
        TwirlMigration.toTwirlRadiosWithHintText(InputSelection.radios(form)))(request, messages)

      val result = route(application, request).value

      status(result) mustEqual OK

      compareResultAndView(result, view)
    }
  }

  "onSubmit" must {
    "Show error when invalid data is submitted" in {
      val request = httpPOSTRequest(httpPathPOST, valuesInvalid)
      val boundForm = form.bind(Map("value" -> "invalid value"))

      when(mockCompoundNavigator.nextPage(
        ArgumentMatchers.eq(InputSelectionPage(chargeType)), any(), any(), any(), any(), any(), any())(any())).thenReturn(dummyCall)

      val view = application.injector.instanceOf[InputSelectionView].apply(
        boundForm, schemeName, submitUrl, returnUrl, ChargeType.fileUploadText(chargeType),
        TwirlMigration.toTwirlRadiosWithHintText(InputSelection.radios(boundForm)))(request, messages)

      val result = route(application, request).value

      status(result) mustEqual BAD_REQUEST

      compareResultAndView(result, view)

    }

    "Save data to user answers and redirect to next page when valid data is submitted" in {

      when(mockUserAnswersCacheConnector.savePartial(any(), any(), any(), any())(any(), any())).thenReturn(Future.successful(Json.obj()))

      val expectedJson = Json.obj(
        InputSelectionPage(chargeType).toString -> FileUploadInput.toString
      )

      when(mockCompoundNavigator.nextPage(ArgumentMatchers.eq(InputSelectionPage(chargeType)),
        any(), any(), any(), any(), any(), any())(any())).thenReturn(dummyCall)

      mutableFakeDataRetrievalAction.setDataToReturn(Some(ua))

      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

      val result = route(application, httpPOSTRequest(httpPathPOST, valuesValid)).value

      status(result) mustEqual SEE_OTHER

      verify(mockUserAnswersCacheConnector, times(1)).savePartial(any(), jsonCaptor.capture, any(), any())(any(), any())

      jsonCaptor.getValue must containJson(expectedJson)

      redirectLocation(result) mustBe Some(dummyCall.url)
    }
  }

  private def viewModel(srn: String, startDate: String, accessType: AccessType, version: Int, chargeType: ChargeType) = GenericViewModel(
    submitUrl = controllers.fileUpload.routes.InputSelectionController.onSubmit(srn, startDate, accessType, version, chargeType).url,
    returnUrl = controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, version).url,
    schemeName = schemeName
  )

}
