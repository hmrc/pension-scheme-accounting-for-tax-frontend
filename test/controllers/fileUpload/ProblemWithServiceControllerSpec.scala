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
import data.SampleData._
import matchers.JsonMatchers
import models.LocalDateBinder._
import models.UserAnswers
import models.fileUpload.FileUploadOutcome
import models.fileUpload.FileUploadOutcomeStatus.ValidationErrorsLessThanMax
import models.requests.IdentifierRequest
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceableModule
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers._
import views.html.fileUpload.ProblemWithServiceView

import scala.concurrent.Future

class ProblemWithServiceControllerSpec extends ControllerSpecBase with JsonMatchers {
  private val mutableFakeDataRetrievalAction: MutableFakeDataRetrievalAction = new MutableFakeDataRetrievalAction()

  private def httpPathGET: String = controllers.fileUpload.routes.ProblemWithServiceController.onPageLoad(srn, startDate, accessType, versionInt).url

  private val errorsJson = Json.obj("test" -> "test")


  private val mockFileUploadOutcomeConnector = mock[FileUploadOutcomeConnector]

  val extraModules: Seq[GuiceableModule] = Seq[GuiceableModule](
    bind[FileUploadOutcomeConnector].toInstance(mockFileUploadOutcomeConnector)
  )


  private def application: Application = applicationBuilderMutableRetrievalAction(mutableFakeDataRetrievalAction, extraModules).build()

  override def beforeEach(): Unit = {
    super.beforeEach()
    when(mockAppConfig.schemeDashboardUrl(any(): IdentifierRequest[?])).thenReturn(dummyCall.url)
    when(mockFileUploadOutcomeConnector.getOutcome(any(), any()))
      .thenReturn(Future.successful(Some(FileUploadOutcome(status = ValidationErrorsLessThanMax, json = errorsJson))))
  }

  private val userAnswers: Option[UserAnswers] = Some(userAnswersWithSchemeNamePstrQuarter)

  "problemWithService Controller" must {
    "return OK and the correct view for a GET" in {

      val request = FakeRequest(GET, httpPathGET)

      when(mockAppConfig.failureEndpointTarget(any(), any(), any(), any(), any())).thenReturn(dummyCall.url)
      mutableFakeDataRetrievalAction.setDataToReturn(userAnswers)

      val view = application.injector.instanceOf[ProblemWithServiceView].apply(
        controllers.routes.ChargeTypeController.onPageLoad(srn, startDate, accessType, versionInt).url)(request, messages)

      val result = route(application, request).value

      status(result) mustEqual OK

      compareResultAndView(result, view)
    }
  }
}
