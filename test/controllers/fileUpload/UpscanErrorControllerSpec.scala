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

import config.FrontendAppConfig
import controllers.actions.MutableFakeDataRetrievalAction
import controllers.base.ControllerSpecBase
import data.SampleData.{accessType, dummyCall, schemeName, srn, userAnswersWithSchemeName, versionInt}
import fileUploadParsers.Parser.FileLevelParserValidationErrorTypeHeaderInvalidOrFileEmpty
import fileUploadParsers.ParserValidationError
import models.LocalDateBinder._
import matchers.JsonMatchers
import models.requests.IdentifierRequest
import models.{ChargeType, GenericViewModel, UserAnswers}
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import play.api.inject.guice.GuiceableModule
import play.api.libs.json.{JsObject, Json}
import play.api.test.FakeRequest
import play.api.inject.bind
import play.api.test.Helpers._
import play.twirl.api.Html
import uk.gov.hmrc.viewmodels.NunjucksSupport
import utils.AFTConstants.QUARTER_START_DATE

import java.time.LocalDate
import scala.concurrent.Future

class UpscanErrorControllerSpec extends ControllerSpecBase with NunjucksSupport with JsonMatchers{

  private val startDate = LocalDate.parse(QUARTER_START_DATE)
  private val chargeType = ChargeType.ChargeTypeAnnualAllowance
  private val application = applicationBuilder(userAnswers = None).build()
  private def ua: UserAnswers = userAnswersWithSchemeName
  private val mutableFakeDataRetrievalAction: MutableFakeDataRetrievalAction = new MutableFakeDataRetrievalAction()
  private val viewModel = GenericViewModel(
    submitUrl = routes.FileUploadController.onPageLoad(srn, startDate.toString, accessType, versionInt, chargeType).url,
    returnUrl = "",
    schemeName = schemeName
  )

  override def beforeEach: Unit = {
    super.beforeEach
    reset(mockRenderer, mockAppConfig)
    when(mockRenderer.render(any(), any())(any()))
      .thenReturn(Future.successful(Html("")))
    when(mockAppConfig.schemeDashboardUrl(any(): IdentifierRequest[_])).thenReturn("")
  }

  "UpscanErrorController" must {

    "must return OK and the correct view for a GET quarantineError" in {
      val request = FakeRequest(GET, routes.UpscanErrorController.quarantineError(srn, startDate, accessType, versionInt).url)
      val result = route(application, request).value
      val templateCaptor = ArgumentCaptor.forClass(classOf[String])
      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

      status(result) mustEqual OK
      verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())
      templateCaptor.getValue mustEqual "fileUpload/error/quarantine.njk"
      val jsonToPassToTemplate = Json.obj(
        "returnUrl" -> controllers.routes.ChargeTypeController.onPageLoad(srn, startDate, accessType, versionInt).url
      )
      jsonCaptor.getValue must containJson(jsonToPassToTemplate)
    }

    "must return OK and the correct view for a GET rejectedError" in {
      val request = FakeRequest(GET, routes.UpscanErrorController.rejectedError(srn, startDate, accessType, versionInt).url)
      val result = route(application, request).value
      val templateCaptor = ArgumentCaptor.forClass(classOf[String])
      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

      status(result) mustEqual OK
      verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())
      templateCaptor.getValue mustEqual "fileUpload/error/rejected.njk"
      val jsonToPassToTemplate = Json.obj(
        "returnUrl" -> controllers.routes.ChargeTypeController.onPageLoad(srn, startDate, accessType, versionInt).url
      )
      jsonCaptor.getValue must containJson(jsonToPassToTemplate)
    }

    "must return OK and the correct view for a GET unknownError" in {
      val request = FakeRequest(GET, routes.UpscanErrorController.unknownError(srn, startDate, accessType, versionInt).url)
      val result = route(application, request).value
      val templateCaptor = ArgumentCaptor.forClass(classOf[String])
      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

      status(result) mustEqual OK
      verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())
      templateCaptor.getValue mustEqual "fileUpload/error/unknown.njk"
      val jsonToPassToTemplate = Json.obj(
        "returnUrl" -> controllers.routes.ChargeTypeController.onPageLoad(srn, startDate, accessType, versionInt).url
      )
      jsonCaptor.getValue must containJson(jsonToPassToTemplate)
    }

    "must return the correct view for a GET invalidHeaderOrBodyError" in {
      mutableFakeDataRetrievalAction.setDataToReturn(Some(ua))
      val request = FakeRequest(GET, routes.UpscanErrorController.invalidHeaderOrBodyError(srn, startDate, accessType, versionInt, chargeType).url)
      val application1 = applicationBuilderMutableRetrievalAction(mutableFakeDataRetrievalAction).build()
      val result = route(application1, request).value
      val templateCaptor = ArgumentCaptor.forClass(classOf[String])
      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

      status(result) mustEqual OK
      verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())
      templateCaptor.getValue mustEqual "fileUpload/error/invalidHeaderOrBody.njk"
      val jsonToPassToTemplate = Json.obj(
        "chargeTypeText" -> "annual allowance charge",
        "fileTemplateLink" -> controllers.routes.FileDownloadController.templateFile(chargeType).url,
        "fileDownloadInstructionsLink" -> controllers.routes.FileDownloadController.instructionsFile(chargeType).url,
        "viewModel" -> viewModel
      )
      jsonCaptor.getValue must containJson(jsonToPassToTemplate)
    }
  }
}
