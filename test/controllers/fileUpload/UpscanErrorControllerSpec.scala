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
import matchers.JsonMatchers
import models.LocalDateBinder._
import models.requests.{DataRequest, IdentifierRequest}
import models.{ChargeType, UserAnswers}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import play.api.test.FakeRequest
import play.api.test.Helpers._
import utils.AFTConstants.QUARTER_START_DATE
import views.html.fileUpload.error.{InvalidHeaderOrBodyView, QuarantineView, RejectedView, UnknownView}

import java.time.LocalDate

class UpscanErrorControllerSpec extends ControllerSpecBase with JsonMatchers {

  private val startDate = LocalDate.parse(QUARTER_START_DATE)
  private val chargeType = ChargeType.ChargeTypeOverseasTransfer
  private val application = applicationBuilder(userAnswers = None).build()

  private def ua: UserAnswers = userAnswersWithSchemeName

  private val mutableFakeDataRetrievalAction: MutableFakeDataRetrievalAction = new MutableFakeDataRetrievalAction()

  override def beforeEach(): Unit = {
    super.beforeEach()
    when(mockAppConfig.schemeDashboardUrl(any(): IdentifierRequest[_])).thenReturn("")
  }

  "UpscanErrorController" must {

    "must return OK and the correct view for a GET quarantineError" in {
      val request = FakeRequest(GET, routes.UpscanErrorController.quarantineError(srn, startDate, accessType, versionInt).url)

      val view = application.injector.instanceOf[QuarantineView].apply(
        controllers.routes.ChargeTypeController.onPageLoad(srn, startDate, accessType, versionInt).url
      )(request, messages)

      val result = route(application, request).value

      status(result) mustEqual OK

      compareResultAndView(result, view)
    }

    "must return OK and the correct view for a GET rejectedError" in {
      val request = FakeRequest(GET, routes.UpscanErrorController.rejectedError(srn, startDate, accessType, versionInt).url)

      val view = application.injector.instanceOf[RejectedView].apply(
        controllers.routes.ChargeTypeController.onPageLoad(srn, startDate, accessType, versionInt).url
      )(request, messages)

      val result = route(application, request).value

      status(result) mustEqual OK

      compareResultAndView(result, view)
    }

    "must return OK and the correct view for a GET unknownError" in {
      val request = FakeRequest(GET, routes.UpscanErrorController.unknownError(srn, startDate, accessType, versionInt).url)

      val view = application.injector.instanceOf[UnknownView].apply(
        controllers.routes.ChargeTypeController.onPageLoad(srn, startDate, accessType, versionInt).url
      )(request, messages)

      val result = route(application, request).value

      status(result) mustEqual OK

      compareResultAndView(result, view)
    }

    "must return the correct view for a GET invalidHeaderOrBodyError" in {

      when(mockAppConfig.schemeDashboardUrl(any(): DataRequest[_])).thenReturn("")

      mutableFakeDataRetrievalAction.setDataToReturn(Some(ua))
      val request = FakeRequest(GET, routes.UpscanErrorController.invalidHeaderOrBodyError(srn, startDate, accessType, versionInt, chargeType).url)
      val application1 = registerApp(applicationBuilderMutableRetrievalAction(mutableFakeDataRetrievalAction).build())
      val submitUrl = routes.FileUploadController.onPageLoad(srn, startDate.toString, accessType, versionInt, chargeType).url
      val fileTemplateLink = controllers.routes.FileDownloadController.templateFile(chargeType, None).url
      val fileDownloadInstructionsLink = controllers.routes.FileDownloadController.instructionsFile(chargeType, None).url

      val view = application.injector.instanceOf[InvalidHeaderOrBodyView].apply(
        "overseas transfer charge",schemeName, submitUrl, "", fileTemplateLink,
        fileDownloadInstructionsLink
      )(request, messages)

      val result = route(application1, request).value

      status(result) mustEqual OK

      compareResultAndView(result, view)

    }
  }
}
