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

import connectors.cache.FileUploadOutcomeConnector
import controllers.actions.MutableFakeDataRetrievalAction
import controllers.base.ControllerSpecBase
import data.SampleData
import data.SampleData.userAnswersWithSchemeName
import matchers.JsonMatchers
import models.fileUpload.FileUploadOutcome
import models.fileUpload.FileUploadOutcomeStatus._
import models.{ChargeType, Draft, Enumerable, UserAnswers}
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, when}
import pages.chargeE.CheckYourAnswersPage
import play.api.Application
import play.api.i18n.Messages
import play.api.inject.bind
import play.api.inject.guice.GuiceableModule
import play.api.test.FakeRequest
import play.api.test.Helpers._
import views.html.fileUpload.ProcessingRequestView

import scala.concurrent.Future

class ProcessingRequestControllerSpec extends ControllerSpecBase with JsonMatchers with Enumerable.Implicits {

  private val mockFileUploadOutcomeConnector = mock[FileUploadOutcomeConnector]
  private val mutableFakeDataRetrievalAction: MutableFakeDataRetrievalAction = new MutableFakeDataRetrievalAction()
  private val extraModules: Seq[GuiceableModule] = Seq(
    bind[FileUploadOutcomeConnector].to(mockFileUploadOutcomeConnector)
  )
  private val application: Application = applicationBuilderMutableRetrievalAction(mutableFakeDataRetrievalAction, extraModules).build()

  private val startDate = "2020-04-01"
  private val srn = "test-srn"
  private val accessType = Draft
  private val versionInt = 1

  private def httpPathGET: String = controllers.fileUpload.routes.ProcessingRequestController
    .onPageLoad(srn, startDate, accessType, versionInt, ChargeType.ChargeTypeAnnualAllowance).url

  private def ua: UserAnswers = userAnswersWithSchemeName

  val request = FakeRequest(GET,httpPathGET)

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockFileUploadOutcomeConnector)
    when(mockCompoundNavigator.nextPage(ArgumentMatchers.eq(CheckYourAnswersPage), any(), any(), any(), any(), any(), any())(any()))
      .thenReturn(SampleData.dummyCall)
    mutableFakeDataRetrievalAction.setDataToReturn(Some(ua))
  }

  "ProcessingRequestController" must {

    "return OK and the correct view for a GET when outcome is Success and can get file name" in {
      val heading = "messages__processingRequest__h1_processed"
      val testFile = "test-file.csv"
      when(mockFileUploadOutcomeConnector.getOutcome(any(), any()))
        .thenReturn(Future.successful(Some(FileUploadOutcome(status = Success, fileName = Some(testFile)))))

      val view = application.injector.instanceOf[ProcessingRequestView].apply(
        heading, heading, Messages("messages__processingRequest__content_processed", testFile), SampleData.dummyCall.url)(request, messages)

      val result = route(application, request).value

      status(result) mustEqual OK

      compareResultAndView(result, view)
    }

    "return OK and the correct view for a GET when outcome is Success but can't get file name" in {
      val heading = "messages__processingRequest__h1_processed"
      val request = FakeRequest(GET,httpPathGET)
      when(mockFileUploadOutcomeConnector.getOutcome(any(), any())).thenReturn(Future.successful(Some(FileUploadOutcome(Success))))
      val view = application.injector.instanceOf[ProcessingRequestView].apply(
        heading, heading, Messages("messages__processingRequest__content_processed",
          Messages("messages__theFile")), SampleData.dummyCall.url)(request, messages)

      val result = route(application, request).value

      status(result) mustEqual OK

      compareResultAndView(result, view)
    }

    "return OK and the correct view for a GET when outcome is None" in {
      when(mockFileUploadOutcomeConnector.getOutcome(any(), any())).thenReturn(Future.successful(None))
      val heading = "messages__processingRequest__h1_processing"
      val redirect = controllers.fileUpload.routes.
        ProcessingRequestController.onPageLoad(srn, startDate, accessType, versionInt, ChargeType.ChargeTypeAnnualAllowance).url
      val view = application.injector.instanceOf[ProcessingRequestView].apply(
        heading, heading, "messages__processingRequest__content_processing", redirect)(request, messages)

      val result = route(application, request).value

      status(result) mustEqual OK

      compareResultAndView(result, view)
    }

    "return OK and the correct view for a GET when outcome is GeneralError" in {
      when(mockFileUploadOutcomeConnector.getOutcome(any(), any())).thenReturn(Future.successful(Some(FileUploadOutcome(GeneralError))))
      val heading = "messages__processingRequest__h1_failure"
      val redirect = controllers.fileUpload.routes.ProblemWithServiceController.onPageLoad(srn, startDate, accessType, versionInt).url
      val view = application.injector.instanceOf[ProcessingRequestView].apply(
        heading, heading, "messages__processingRequest__content_failure", redirect)(request, messages)

      val result = route(application, request).value

      status(result) mustEqual OK

      compareResultAndView(result, view)
    }

    "return OK and the correct view for a GET when outcome is UpscanUnknownError" in {
      when(mockFileUploadOutcomeConnector.getOutcome(any(), any()))
        .thenReturn(Future.successful(Some(FileUploadOutcome(UpscanUnknownError))))
      val heading = "messages__processingRequest__h1_failure"
      val redirect = controllers.fileUpload.routes.UpscanErrorController
        .unknownError(srn, startDate, accessType, versionInt).url
      val view = application.injector.instanceOf[ProcessingRequestView].apply(
        heading, heading, "messages__processingRequest__content_failure", redirect)(request, messages)

      val result = route(application, request).value

      status(result) mustEqual OK

      compareResultAndView(result, view)
    }

    "return OK and the correct view for a GET when outcome is UpscanInvalidHeaderOrBody" in {
      when(mockFileUploadOutcomeConnector.getOutcome(any(), any()))
        .thenReturn(Future.successful(Some(FileUploadOutcome(UpscanInvalidHeaderOrBody))))
      val heading = "messages__processingRequest__h1_failure"
      val redirect = controllers.fileUpload.routes.UpscanErrorController
        .invalidHeaderOrBodyError(srn, startDate, accessType, versionInt, ChargeType.ChargeTypeAnnualAllowance).url
      val view = application.injector.instanceOf[ProcessingRequestView].apply(
        heading, heading, "messages__processingRequest__content_failure", redirect)(request, messages)

      val result = route(application, request).value

      status(result) mustEqual OK

      compareResultAndView(result, view)
    }

    "return OK and the correct view for a GET when outcome is ValidationErrorsLessThanMax" in {
      when(mockFileUploadOutcomeConnector.getOutcome(any(), any()))
        .thenReturn(Future.successful(Some(FileUploadOutcome(ValidationErrorsLessThanMax))))
      val heading = "messages__processingRequest__h1_failure"
      val redirect = controllers.fileUpload.routes.ValidationErrorsAllController
        .onPageLoad(srn, startDate, accessType, versionInt, ChargeType.ChargeTypeAnnualAllowance).url
      val view = application.injector.instanceOf[ProcessingRequestView].apply(
        heading, heading, "messages__processingRequest__content_failure", redirect)(request, messages)

      val result = route(application, request).value

      status(result) mustEqual OK

      compareResultAndView(result, view)
    }

    "return OK and the correct view for a GET when outcome is ValidationErrorsMoreThanOrEqualToMax" in {
      when(mockFileUploadOutcomeConnector.getOutcome(any(), any()))
        .thenReturn(Future.successful(Some(FileUploadOutcome(ValidationErrorsMoreThanOrEqualToMax))))
      val heading = "messages__processingRequest__h1_failure"
      val redirect = controllers.fileUpload.routes.ValidationErrorsSummaryController
        .onPageLoad(srn, startDate, accessType, versionInt, ChargeType.ChargeTypeAnnualAllowance).url
      val view = application.injector.instanceOf[ProcessingRequestView].apply(
        heading, heading, "messages__processingRequest__content_failure", redirect)(request, messages)

      val result = route(application, request).value

      status(result) mustEqual OK

      compareResultAndView(result, view)
    }

  }
}
