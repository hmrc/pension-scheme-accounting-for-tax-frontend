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

import connectors.UpscanInitiateConnector
import controllers.actions.MutableFakeDataRetrievalAction
import controllers.base.ControllerSpecBase
import data.SampleData._
import matchers.JsonMatchers
import models.LocalDateBinder._
import models.{ChargeType, FileUploadDataCache, FileUploadStatus, UploadId, UpscanFileReference, UpscanInitiateResponse, UserAnswers}
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, times, verify, when}
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceableModule
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.Call
import play.api.test.FakeRequest
import play.api.test.Helpers.{route, status, _}
import services.fileUpload.UploadProgressTracker
import uk.gov.hmrc.govukfrontend.views.Aliases.{ErrorMessage, Text}
import views.html.fileUpload.FileUploadView

import java.time.LocalDateTime
import scala.concurrent.Future

class FileUploadControllerSpec extends ControllerSpecBase with JsonMatchers {
  private val mutableFakeDataRetrievalAction: MutableFakeDataRetrievalAction = new MutableFakeDataRetrievalAction()
  private val chargeType = ChargeType.ChargeTypeAnnualAllowance

  private def ua: UserAnswers = userAnswersWithSchemeName

  private val dateTimeNow = LocalDateTime.now

  private val fileUploadDataCache: FileUploadDataCache =
    FileUploadDataCache(
      uploadId = "uploadId",
      reference = "reference",
      status = FileUploadStatus("InProgress"),
      created = dateTimeNow,
      lastUpdated = dateTimeNow,
      expireAt = dateTimeNow
    )

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

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockUpscanInitiateConnector)
    when(mockAppConfig.maxUploadFileSize).thenReturn(maxUploadFileSize)
  }

  private val application: Application = applicationBuilderMutableRetrievalAction(mutableFakeDataRetrievalAction, extraModules).build()

  "onPageLoad" must {
    "return OK and the correct view for a GET" in {
      val request = FakeRequest(GET, controllers.fileUpload.routes.FileUploadController.onPageLoad(srn, startDate, accessType, versionInt, chargeType).url)

      fakeUploadProgressTracker.setDataToReturn(fileUploadDataCache)
      when(mockUpscanInitiateConnector.initiateV2(any(), any(), any())(any(), any())).thenReturn(Future.successful(upscanInitiateResponse))

      mutableFakeDataRetrievalAction.setDataToReturn(Some(ua))

      val submitUrl = Call("POST",upscanInitiateResponse.postTarget)
      val returnUrl = controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, versionInt).url

      val view = application.injector.instanceOf[FileUploadView].apply(
        schemeName, chargeType.toString,ChargeType.fileUploadText(chargeType), submitUrl,
        returnUrl, None,formFieldsMap)(request, messages)

      val result = route(application, request).value

      status(result) mustEqual OK

      compareResultAndView(result, view)
    }

    "return view with EntityTooLarge error" in {
      val url = controllers.fileUpload.routes.FileUploadController.onPageLoad(srn, startDate, accessType, versionInt, chargeType).url
      val queryString = "?errorCode=EntityTooLarge&errorMessage=FileExceededLimit"
      val request = FakeRequest(GET, s"$url$queryString")
      fakeUploadProgressTracker.setDataToReturn(fileUploadDataCache)
      when(mockUpscanInitiateConnector.initiateV2(any(), any(), any())(any(), any())).thenReturn(Future.successful(upscanInitiateResponse))

      mutableFakeDataRetrievalAction.setDataToReturn(Some(ua))

      val submitUrl = Call("POST",upscanInitiateResponse.postTarget)
      val returnUrl = controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, versionInt).url

      val view = application.injector.instanceOf[FileUploadView].apply(
        schemeName, chargeType.toString,ChargeType.fileUploadText(chargeType), submitUrl,
        returnUrl, Some(ErrorMessage(content = Text(messages("generic.upload.error.size" , 4 * 1024 * 1024)))),formFieldsMap)(request, messages)

      val result = route(application, request).value

      status(result) mustEqual OK

      compareResultAndView(result, view)
    }

    "return view with InvalidArgument error" in {
      val url = controllers.fileUpload.routes.FileUploadController.onPageLoad(srn, startDate, accessType, versionInt, chargeType).url
      val queryString = "?errorCode=InvalidArgument&errorMessage='file' field not found"
      val request = FakeRequest(GET, s"$url$queryString")
      fakeUploadProgressTracker.setDataToReturn(fileUploadDataCache)
      when(mockUpscanInitiateConnector.initiateV2(any(), any(), any())(any(), any())).thenReturn(Future.successful(upscanInitiateResponse))

      mutableFakeDataRetrievalAction.setDataToReturn(Some(ua))

      val submitUrl = Call("POST",upscanInitiateResponse.postTarget)
      val returnUrl = controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, versionInt).url

      val view = application.injector.instanceOf[FileUploadView].apply(
        schemeName, chargeType.toString,ChargeType.fileUploadText(chargeType), submitUrl,
        returnUrl, Some(ErrorMessage(content = Text(messages("generic.upload.error.required")))),formFieldsMap)(request, messages)

      val result = route(application, request).value

      status(result) mustEqual OK

      compareResultAndView(result, view)
    }

    "return view with InvalidArgument-format error" in {
      val url = controllers.fileUpload.routes.FileUploadController.onPageLoad(srn, startDate, accessType, versionInt, chargeType).url
      val queryString = "?errorCode=InvalidArgument&errorMessage='file' invalid file format"
      val request = FakeRequest(GET, s"$url$queryString")
      fakeUploadProgressTracker.setDataToReturn(fileUploadDataCache)
      when(mockUpscanInitiateConnector.initiateV2(any(), any(), any())(any(), any())).thenReturn(Future.successful(upscanInitiateResponse))

      mutableFakeDataRetrievalAction.setDataToReturn(Some(ua))

      val submitUrl = Call("POST",upscanInitiateResponse.postTarget)
      val returnUrl = controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, versionInt).url

      val view = application.injector.instanceOf[FileUploadView].apply(
        schemeName, chargeType.toString,ChargeType.fileUploadText(chargeType), submitUrl,
        returnUrl, Some(ErrorMessage(content = Text(messages("generic.upload.error.format")))),formFieldsMap)(request, messages)

      val result = route(application, request).value

      status(result) mustEqual OK

      compareResultAndView(result, view)
    }

    "return view with EntityTooSmall error" in {
      val url = controllers.fileUpload.routes.FileUploadController.onPageLoad(srn, startDate, accessType, versionInt, chargeType).url
      val queryString = "?errorCode=EntityTooSmall&errorMessage=FileTooSmall"
      val request = FakeRequest(GET, s"$url$queryString")
      fakeUploadProgressTracker.setDataToReturn(fileUploadDataCache)
      when(mockUpscanInitiateConnector.initiateV2(any(), any(), any())(any(), any())).thenReturn(Future.successful(upscanInitiateResponse))

      mutableFakeDataRetrievalAction.setDataToReturn(Some(ua))

      val submitUrl = Call("POST",upscanInitiateResponse.postTarget)
      val returnUrl = controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, versionInt).url

      val view = application.injector.instanceOf[FileUploadView].apply(
        schemeName, chargeType.toString,ChargeType.fileUploadText(chargeType), submitUrl,
        returnUrl, Some(ErrorMessage(content = Text(messages("generic.upload.error.required")))),formFieldsMap)(request, messages)

      val result = route(application, request).value

      status(result) mustEqual OK

      compareResultAndView(result, view)
    }

    "return view with REJECTED error" in {
      val url = controllers.fileUpload.routes.FileUploadController.onPageLoad(srn, startDate, accessType, versionInt, chargeType).url
      val queryString = "?errorCode=REJECTED&errorMessage=REJECTED"
      val request = FakeRequest(GET, s"$url$queryString")
      fakeUploadProgressTracker.setDataToReturn(fileUploadDataCache)
      when(mockUpscanInitiateConnector.initiateV2(any(), any(), any())(any(), any())).thenReturn(Future.successful(upscanInitiateResponse))

      mutableFakeDataRetrievalAction.setDataToReturn(Some(ua))

      val submitUrl = Call("POST",upscanInitiateResponse.postTarget)
      val returnUrl = controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, versionInt).url

      val view = application.injector.instanceOf[FileUploadView].apply(
        schemeName, chargeType.toString,ChargeType.fileUploadText(chargeType), submitUrl,
        returnUrl, Some(ErrorMessage(content = Text(messages("generic.upload.error.format")))),formFieldsMap)(request, messages)

      val result = route(application, request).value

      status(result) mustEqual OK

      compareResultAndView(result, view)
    }

    "return view with QUARANTINE error" in {
      val url = controllers.fileUpload.routes.FileUploadController.onPageLoad(srn, startDate, accessType, versionInt, chargeType).url
      val queryString = "?errorCode=QUARANTINE&errorMessage=QUARANTINE"
      val request = FakeRequest(GET, s"$url$queryString")
      fakeUploadProgressTracker.setDataToReturn(fileUploadDataCache)
      when(mockUpscanInitiateConnector.initiateV2(any(), any(), any())(any(), any())).thenReturn(Future.successful(upscanInitiateResponse))

      mutableFakeDataRetrievalAction.setDataToReturn(Some(ua))

      val submitUrl = Call("POST",upscanInitiateResponse.postTarget)
      val returnUrl = controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, versionInt).url

      val view = application.injector.instanceOf[FileUploadView].apply(
        schemeName, chargeType.toString,ChargeType.fileUploadText(chargeType), submitUrl,
        returnUrl, Some(ErrorMessage(content = Text(messages("generic.upload.error.malicious")))),formFieldsMap)(request, messages)

      val result = route(application, request).value

      status(result) mustEqual OK

      compareResultAndView(result, view)
    }

    "return view with UNKNOWN error" in {
      val url = controllers.fileUpload.routes.FileUploadController.onPageLoad(srn, startDate, accessType, versionInt, chargeType).url
      val queryString = "?errorCode=UNKNOWN&errorMessage=UNKNOWN"
      val request = FakeRequest(GET, s"$url$queryString")
      fakeUploadProgressTracker.setDataToReturn(fileUploadDataCache)
      when(mockUpscanInitiateConnector.initiateV2(any(), any(), any())(any(), any())).thenReturn(Future.successful(upscanInitiateResponse))

      mutableFakeDataRetrievalAction.setDataToReturn(Some(ua))

      val submitUrl = Call("POST",upscanInitiateResponse.postTarget)
      val returnUrl = controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, versionInt).url

      val view = application.injector.instanceOf[FileUploadView].apply(
        schemeName, chargeType.toString,ChargeType.fileUploadText(chargeType), submitUrl,
        returnUrl, Some(ErrorMessage(content = Text(messages("generic.upload.error.unknown")))),formFieldsMap)(request, messages)

      val result = route(application, request).value

      status(result) mustEqual OK

      compareResultAndView(result, view)
    }

    "return view and ignore upscan error" in {
      val url = controllers.fileUpload.routes.FileUploadController.onPageLoad(srn, startDate, accessType, versionInt, chargeType).url
      val queryString = "?errorCode=SomeOtherKey&errorMessage=UNKNOWN"
      val request = FakeRequest(GET, s"$url$queryString")
      fakeUploadProgressTracker.setDataToReturn(fileUploadDataCache)
      when(mockUpscanInitiateConnector.initiateV2(any(), any(), any())(any(), any())).thenReturn(Future.successful(upscanInitiateResponse))

      mutableFakeDataRetrievalAction.setDataToReturn(Some(ua))

      val submitUrl = Call("POST",upscanInitiateResponse.postTarget)
      val returnUrl = controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, versionInt).url

      val view = application.injector.instanceOf[FileUploadView].apply(
        schemeName, chargeType.toString,ChargeType.fileUploadText(chargeType), submitUrl,
        returnUrl, None,formFieldsMap)(request, messages)

      val result = route(application, request).value

      status(result) mustEqual OK

      compareResultAndView(result, view)
    }
  }

  "showResult" must {
    "redirect to Upload Check for success result" in {
      val fileUploadDataCache: FileUploadDataCache =
        FileUploadDataCache(
          uploadId = "uploadId",
          reference = "reference",
          status = FileUploadStatus("UploadedSuccessfully"),
          created = dateTimeNow,
          lastUpdated = dateTimeNow,
          expireAt = dateTimeNow
        )
      fakeUploadProgressTracker.setDataToReturn(fileUploadDataCache)


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

      redirectLocation(result) mustBe Some(routes.FileUploadCheckController.onPageLoad(srn, startDate, accessType, version.toInt, chargeType, uploadId).url)
    }

    "redirect to showResult for result InProgress" in {
      fakeUploadProgressTracker.setDataToReturn(fileUploadDataCache)
      mutableFakeDataRetrievalAction.setDataToReturn(Some(ua))
      val uploadId = UploadId("")
      val result = route(
        application,
        httpGETRequest(
          controllers.fileUpload.routes.FileUploadController.showResult(srn, startDate, accessType, versionInt, chargeType, uploadId).url)
      ).value

      status(result) mustEqual SEE_OTHER
      redirectLocation(result) mustBe Some(routes.FileUploadCheckController.onPageLoad(srn, startDate, accessType, version.toInt, chargeType, uploadId).url)

    }
  }

  "redirect to quarantineError for result Failed(QUARANTINE)" in {
    val fileUploadDataCache: FileUploadDataCache =
      FileUploadDataCache(
        uploadId = "uploadId",
        reference = "reference",
        status = FileUploadStatus("Failed", failureReason = Some("QUARANTINE")),
        created = dateTimeNow,
        lastUpdated = dateTimeNow,
        expireAt = dateTimeNow
      )
    fakeUploadProgressTracker.setDataToReturn(fileUploadDataCache)
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
    val fileUploadDataCache: FileUploadDataCache =
      FileUploadDataCache(
        uploadId = "uploadId",
        reference = "reference",
        status = FileUploadStatus("Failed", failureReason = Some("REJECTED")),
        created = dateTimeNow,
        lastUpdated = dateTimeNow,
        expireAt = dateTimeNow
      )
    fakeUploadProgressTracker.setDataToReturn(fileUploadDataCache)
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
    val fileUploadDataCache: FileUploadDataCache =
      FileUploadDataCache(
        uploadId = "uploadId",
        reference = "reference",
        status = FileUploadStatus("Failed", failureReason = Some("UNKNOWN")),
        created = dateTimeNow,
        lastUpdated = dateTimeNow,
        expireAt = dateTimeNow
      )
    fakeUploadProgressTracker.setDataToReturn(fileUploadDataCache)
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
