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

import connectors.cache.FileUploadOutcomeConnector
import controllers.actions.MutableFakeDataRetrievalAction
import controllers.base.ControllerSpecBase
import matchers.JsonMatchers
import models.fileUpload.FileUploadOutcome
import models.fileUpload.FileUploadOutcomeStatus.{GeneralError, Success, UpscanInvalidHeaderOrBody, UpscanUnknownError, ValidationErrorsLessThanMax, ValidationErrorsMoreThanOrEqualToMax}
import models.{ChargeType, Draft, Enumerable}
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceableModule
import play.api.libs.json.{JsObject, Json}
import play.api.test.Helpers._
import play.twirl.api.Html
import uk.gov.hmrc.nunjucks.NunjucksSupport

import scala.concurrent.Future

class ProcessingRequestControllerSpec extends ControllerSpecBase with NunjucksSupport with JsonMatchers with Enumerable.Implicits {

  private val templateToBeRendered = "fileUpload/processingRequest.njk"
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

  private def jsonToPassToTemplate(heading: String, content: String, redirect: String): JsObject =
    Json.obj(
      "pageTitle" -> heading,
      "heading" -> heading,
      "content" -> content,
      "continueUrl" -> redirect
    )

  override def beforeEach: Unit = {
    super.beforeEach
    reset(mockFileUploadOutcomeConnector)
    when(mockRenderer.render(any(), any())(any())).thenReturn(Future.successful(Html("")))
  }

  "ProcessingRequestController" must {

    "return OK and the correct view for a GET when outcome is Success" in {
      when(mockFileUploadOutcomeConnector.getOutcome(any(), any())).thenReturn(Future.successful(Some(FileUploadOutcome(Success))))
      val templateCaptor: ArgumentCaptor[String] = ArgumentCaptor.forClass(classOf[String])
      val jsonCaptor: ArgumentCaptor[JsObject] = ArgumentCaptor.forClass(classOf[JsObject])

      val result = route(application, httpGETRequest(httpPathGET)).value

      status(result) mustEqual OK

      verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

      templateCaptor.getValue mustEqual templateToBeRendered

      jsonCaptor.getValue must containJson(jsonToPassToTemplate(
        heading = "messages__processingRequest__h1_processed",
        content = "messages__processingRequest__content_processed",
        redirect = controllers.routes.ConfirmationController.onPageLoad(srn, startDate, accessType, versionInt).url
      ))
    }

    "return OK and the correct view for a GET when outcome is None" in {
      when(mockFileUploadOutcomeConnector.getOutcome(any(), any())).thenReturn(Future.successful(None))
      val templateCaptor: ArgumentCaptor[String] = ArgumentCaptor.forClass(classOf[String])
      val jsonCaptor: ArgumentCaptor[JsObject] = ArgumentCaptor.forClass(classOf[JsObject])

      val result = route(application, httpGETRequest(httpPathGET)).value

      status(result) mustEqual OK

      verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

      templateCaptor.getValue mustEqual templateToBeRendered

      jsonCaptor.getValue must containJson(jsonToPassToTemplate(
        heading = "messages__processingRequest__h1_processing",
        content = "messages__processingRequest__content_processing",
        redirect = controllers.fileUpload.routes.
          ProcessingRequestController.onPageLoad(srn, startDate, accessType, versionInt, ChargeType.ChargeTypeAnnualAllowance).url
      ))
    }

    "return OK and the correct view for a GET when outcome is GeneralError" in {
      when(mockFileUploadOutcomeConnector.getOutcome(any(), any())).thenReturn(Future.successful(Some(FileUploadOutcome(GeneralError))))
      val templateCaptor: ArgumentCaptor[String] = ArgumentCaptor.forClass(classOf[String])
      val jsonCaptor: ArgumentCaptor[JsObject] = ArgumentCaptor.forClass(classOf[JsObject])

      val result = route(application, httpGETRequest(httpPathGET)).value

      status(result) mustEqual OK

      verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

      templateCaptor.getValue mustEqual templateToBeRendered

      jsonCaptor.getValue must containJson(jsonToPassToTemplate(
        heading = "messages__processingRequest__h1_failure",
        content = "messages__processingRequest__content_failure",
        redirect = controllers.fileUpload.routes.ProblemWithServiceController.onPageLoad(srn, startDate, accessType, versionInt).url
      ))
    }

    "return OK and the correct view for a GET when outcome is UpscanUnknownError" in {
      when(mockFileUploadOutcomeConnector.getOutcome(any(), any()))
        .thenReturn(Future.successful(Some(FileUploadOutcome(UpscanUnknownError))))
      val templateCaptor: ArgumentCaptor[String] = ArgumentCaptor.forClass(classOf[String])
      val jsonCaptor: ArgumentCaptor[JsObject] = ArgumentCaptor.forClass(classOf[JsObject])

      val result = route(application, httpGETRequest(httpPathGET)).value

      status(result) mustEqual OK

      verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

      templateCaptor.getValue mustEqual templateToBeRendered

      jsonCaptor.getValue must containJson(jsonToPassToTemplate(
        heading = "messages__processingRequest__h1_failure",
        content = "messages__processingRequest__content_failure",
        redirect = controllers.fileUpload.routes.UpscanErrorController
          .unknownError(srn, startDate, accessType, versionInt).url
      ))
    }

    "return OK and the correct view for a GET when outcome is UpscanInvalidHeaderOrBody" in {
      when(mockFileUploadOutcomeConnector.getOutcome(any(), any()))
        .thenReturn(Future.successful(Some(FileUploadOutcome(UpscanInvalidHeaderOrBody))))
      val templateCaptor: ArgumentCaptor[String] = ArgumentCaptor.forClass(classOf[String])
      val jsonCaptor: ArgumentCaptor[JsObject] = ArgumentCaptor.forClass(classOf[JsObject])

      val result = route(application, httpGETRequest(httpPathGET)).value

      status(result) mustEqual OK

      verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

      templateCaptor.getValue mustEqual templateToBeRendered

      jsonCaptor.getValue must containJson(jsonToPassToTemplate(
        heading = "messages__processingRequest__h1_failure",
        content = "messages__processingRequest__content_failure",
        redirect = controllers.fileUpload.routes.UpscanErrorController
          .invalidHeaderOrBodyError(srn, startDate, accessType, versionInt,  ChargeType.ChargeTypeAnnualAllowance).url
      ))
    }

    "return OK and the correct view for a GET when outcome is ValidationErrorsLessThanMax" in {
      when(mockFileUploadOutcomeConnector.getOutcome(any(), any()))
        .thenReturn(Future.successful(Some(FileUploadOutcome(ValidationErrorsLessThanMax))))
      val templateCaptor: ArgumentCaptor[String] = ArgumentCaptor.forClass(classOf[String])
      val jsonCaptor: ArgumentCaptor[JsObject] = ArgumentCaptor.forClass(classOf[JsObject])

      val result = route(application, httpGETRequest(httpPathGET)).value

      status(result) mustEqual OK

      verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

      templateCaptor.getValue mustEqual templateToBeRendered

      jsonCaptor.getValue must containJson(jsonToPassToTemplate(
        heading = "messages__processingRequest__h1_failure",
        content = "messages__processingRequest__content_failure",
        redirect = controllers.fileUpload.routes.ValidationErrorsAllController
          .onPageLoad(srn, startDate, accessType, versionInt,  ChargeType.ChargeTypeAnnualAllowance).url
      ))
    }

    "return OK and the correct view for a GET when outcome is ValidationErrorsMoreThanOrEqualToMax" in {
      when(mockFileUploadOutcomeConnector.getOutcome(any(), any()))
        .thenReturn(Future.successful(Some(FileUploadOutcome(ValidationErrorsMoreThanOrEqualToMax))))
      val templateCaptor: ArgumentCaptor[String] = ArgumentCaptor.forClass(classOf[String])
      val jsonCaptor: ArgumentCaptor[JsObject] = ArgumentCaptor.forClass(classOf[JsObject])

      val result = route(application, httpGETRequest(httpPathGET)).value

      status(result) mustEqual OK

      verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

      templateCaptor.getValue mustEqual templateToBeRendered

      jsonCaptor.getValue must containJson(jsonToPassToTemplate(
        heading = "messages__processingRequest__h1_failure",
        content = "messages__processingRequest__content_failure",
        redirect = controllers.fileUpload.routes.ValidationErrorsSummaryController
          .onPageLoad(srn, startDate, accessType, versionInt,  ChargeType.ChargeTypeAnnualAllowance).url
      ))
    }

  }
}
