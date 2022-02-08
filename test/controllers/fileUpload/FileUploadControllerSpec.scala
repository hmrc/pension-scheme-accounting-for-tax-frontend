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

import connectors.UpscanInitiateConnector
import controllers.actions.MutableFakeDataRetrievalAction
import controllers.base.ControllerSpecBase
import data.SampleData._
import matchers.JsonMatchers
import models.LocalDateBinder._
import models.{ChargeType, GenericViewModel, InProgress, Failed, UploadId, UpscanFileReference, UpscanInitiateResponse, UserAnswers}
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceableModule
import play.api.libs.json.{JsObject, Json}
import play.api.test.Helpers.{route, status, _}
import play.twirl.api.Html
import services.fileUpload.UploadProgressTracker
import uk.gov.hmrc.viewmodels.NunjucksSupport

import scala.concurrent.Future

class FileUploadControllerSpec extends ControllerSpecBase with NunjucksSupport with JsonMatchers {
  private val mutableFakeDataRetrievalAction: MutableFakeDataRetrievalAction = new MutableFakeDataRetrievalAction()
  private val templateToBeRendered = "fileUpload/fileupload.njk"
  private val chargeType = ChargeType.ChargeTypeAnnualAllowance

  private def ua: UserAnswers = userAnswersWithSchemeName

  val expectedJson: JsObject = Json.obj()

  private val mockUpscanInitiateConnector: UpscanInitiateConnector = mock[UpscanInitiateConnector]
  private val fakeUploadProgressTracker: MutableFakeUploadProgressTracker = new MutableFakeUploadProgressTracker()

  val extraModules: Seq[GuiceableModule] = Seq[GuiceableModule](
    bind[UpscanInitiateConnector].toInstance(mockUpscanInitiateConnector),
    bind[UploadProgressTracker].toInstance(fakeUploadProgressTracker)
  )

  private val formFieldsMap = Map(
    "testField1" -> "value1",
    "testField2" -> "value2"
  )

  private val upscanInitiateResponse = UpscanInitiateResponse(
    fileReference = UpscanFileReference(""),
    postTarget = "/postTarget",
    formFields = formFieldsMap
  )

  private val maxUploadFileSize = 99

  override def beforeEach: Unit = {
    super.beforeEach
    reset(mockUpscanInitiateConnector, mockAppConfig, mockRenderer)
    when(mockRenderer.render(any(), any())(any())).thenReturn(Future.successful(Html("")))
    when(mockAppConfig.maxUploadFileSize).thenReturn(maxUploadFileSize)
  }

  private val application: Application = applicationBuilderMutableRetrievalAction(mutableFakeDataRetrievalAction, extraModules).build()

  "onPageLoad" must {
    "return OK and the correct view for a GET" in {
      when(mockUpscanInitiateConnector.initiateV2(any(), any())(any())).thenReturn(Future.successful(upscanInitiateResponse))

      mutableFakeDataRetrievalAction.setDataToReturn(Some(ua))

      val templateCaptor = ArgumentCaptor.forClass(classOf[String])
      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

      val result = route(
        application,
        httpGETRequest(controllers.fileUpload.routes.FileUploadController.onPageLoad(srn, startDate, accessType, versionInt, chargeType).url)).value

      status(result) mustEqual OK

      verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

      templateCaptor.getValue mustEqual templateToBeRendered

      val jsonToPassToTemplate = Json.obj(
        "chargeType" -> chargeType.toString,
        "chargeTypeText" -> ChargeType.fileUploadText(chargeType),
        "srn" -> srn,
        "startDate" -> Some("2020-04-01"),
        "formFields" -> formFieldsMap.toList,
        "error" -> None,
        "maxFileUploadSize" -> maxUploadFileSize,
        "viewModel" -> GenericViewModel(
          submitUrl = upscanInitiateResponse.postTarget,
          returnUrl = controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, versionInt).url,
          schemeName = schemeName
        )
      )

      jsonCaptor.getValue must containJson(jsonToPassToTemplate)
      verify(mockAppConfig, times(1)).maxUploadFileSize
    }
  }

  "showResult" must {
    "redirect to Upload Check for success result" in {

      when(mockUserAnswersCacheConnector.savePartial(any(), any(), any(), any())(any(), any())).thenReturn(Future.successful(Json.obj()))
      mutableFakeDataRetrievalAction.setDataToReturn(Some(ua))

      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

      val uploadId = UploadId("")

      val result = route(
        application,
        httpGETRequest(
          controllers.fileUpload.routes.FileUploadController.showResult(srn, startDate, accessType, versionInt, chargeType, uploadId).url)
      ).value

      status(result) mustEqual SEE_OTHER

      verify(mockUserAnswersCacheConnector, times(1)).savePartial(any(), jsonCaptor.capture, any(), any())(any(), any())
      val jsonToPassToTemplate = Json.obj(
        "schemeName" ->schemeName,
        "pstr" -> pstr,
        "annualAllowance" -> Json.obj("uploadedFileName"->"name")
      )

      redirectLocation(result) mustBe Some(routes.FileUploadCheckController.onPageLoad(srn, startDate, accessType, version.toInt, chargeType, uploadId).url)

      jsonCaptor.getValue mustBe jsonToPassToTemplate
    }

    "redirect to showResult for result InProgress" in {
      fakeUploadProgressTracker.setDataToReturn(InProgress)
      mutableFakeDataRetrievalAction.setDataToReturn(Some(ua))
      val uploadId = UploadId("")
      val result = route(
        application,
        httpGETRequest(
          controllers.fileUpload.routes.FileUploadController.showResult(srn, startDate, accessType, versionInt, chargeType, uploadId).url)
      ).value

      status(result) mustEqual SEE_OTHER
      redirectLocation(result) mustBe Some(routes.FileUploadController.showResult(srn, startDate, accessType, version.toInt, chargeType, uploadId).url)

    }
  }

  "redirect to quarantineError for result Failed(QUARANTINE)" in {
    fakeUploadProgressTracker.setDataToReturn(Failed("QUARANTINE", "file may contain virus"))
    mutableFakeDataRetrievalAction.setDataToReturn(Some(ua))
    val uploadId = UploadId("")
    val result = route(
      application,
      httpGETRequest(
        routes.FileUploadController.showResult(srn, startDate, accessType, versionInt, chargeType, uploadId).url)
    ).value

    status(result) mustEqual SEE_OTHER
    redirectLocation(result) mustBe Some(routes.UpscanErrorController.quarantineError(srn, startDate, accessType, versionInt).url)
  }

  "redirect to rejectedError for result Failed(REJECTED)" in {
    fakeUploadProgressTracker.setDataToReturn(Failed("REJECTED", "file type may be incorrect"))
    mutableFakeDataRetrievalAction.setDataToReturn(Some(ua))
    val uploadId = UploadId("")
    val result = route(
      application,
      httpGETRequest(
        routes.FileUploadController.showResult(srn, startDate, accessType, versionInt, chargeType, uploadId).url)
    ).value

    status(result) mustEqual SEE_OTHER
    redirectLocation(result) mustBe Some(routes.UpscanErrorController.rejectedError(srn, startDate, accessType, versionInt).url)
  }

  "redirect to unknownError for result Failed(UNKNOWN)" in {
    fakeUploadProgressTracker.setDataToReturn(Failed("UNKNOWN", "Please try again later"))
    mutableFakeDataRetrievalAction.setDataToReturn(Some(ua))
    val uploadId = UploadId("")
    val result = route(
      application,
      httpGETRequest(
        routes.FileUploadController.showResult(srn, startDate, accessType, versionInt, chargeType, uploadId).url)
    ).value

    status(result) mustEqual SEE_OTHER
    redirectLocation(result) mustBe Some(routes.UpscanErrorController.unknownError(srn, startDate, accessType, versionInt).url)
  }

}
