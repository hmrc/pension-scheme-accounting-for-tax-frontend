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
import play.api.test.Helpers.{route, status, _}
import play.twirl.api.Html
import uk.gov.hmrc.viewmodels.NunjucksSupport

import scala.concurrent.Future

class InputSelectionControllerSpec extends ControllerSpecBase with NunjucksSupport with JsonMatchers {
  private val mutableFakeDataRetrievalAction: MutableFakeDataRetrievalAction = new MutableFakeDataRetrievalAction()
  private val templateToBeRendered = "fileUpload/inputSelection.njk"
  private val chargeType = ChargeType.ChargeTypeAnnualAllowance

  private def ua: UserAnswers = userAnswersWithSchemeName

  val expectedJson: JsObject = Json.obj()

  override def beforeEach(): Unit = {
    super.beforeEach()
    when(mockRenderer.render(any(), any())(any())).thenReturn(Future.successful(Html("")))
  }

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
  "onPageLoad" must {
    "return OK and the correct view for a GET" in {

      mutableFakeDataRetrievalAction.setDataToReturn(Some(ua))

      val templateCaptor = ArgumentCaptor.forClass(classOf[String])
      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

      val result = route(application,
        httpGETRequest(controllers.fileUpload
          .routes.InputSelectionController.onPageLoad(srn, startDate, accessType, versionInt, chargeType).url)).value
      status(result) mustEqual OK
      verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

      templateCaptor.getValue mustEqual templateToBeRendered

      val jsonToPassToTemplate = Json.obj(
        "chargeType" -> ChargeType.fileUploadText(chargeType),
        "srn" -> srn,
        "startDate" -> Some("2020-04-01"),
        "form" -> form,
        "radios" -> InputSelection.radios(form),
        "viewModel" -> viewModel(srn, startDate, accessType, versionInt, chargeType)
      )

      jsonCaptor.getValue must containJson(jsonToPassToTemplate)
    }
  }

  "onSubmit" must {
    "Show error when invalid data is submitted" in {

      val boundForm = form.bind(Map("value" -> "invalid value"))

      when(mockCompoundNavigator.nextPage(
        ArgumentMatchers.eq(InputSelectionPage(chargeType)), any(), any(), any(), any(), any(), any())(any())).thenReturn(dummyCall)

      val templateCaptor = ArgumentCaptor.forClass(classOf[String])
      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

      val result = route(application, httpPOSTRequest(httpPathPOST, valuesInvalid)).value

      status(result) mustEqual BAD_REQUEST

      val expectedJson = Json.obj(
        "chargeType" -> chargeType.toString,
        "srn" -> srn,
        "startDate" -> Some("2020-04-01"),
        "form" -> boundForm,
        "radios" -> InputSelection.radios(form),
        "viewModel" -> viewModel(srn, startDate, accessType, versionInt, chargeType)
      )
      verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())
      mutableFakeDataRetrievalAction.setDataToReturn(Some(ua))

      templateCaptor.getValue mustEqual "fileUpload/inputSelection.njk"
      jsonCaptor.getValue must containJson(expectedJson)

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
