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
import models.fileUpload.FileUploadOutcome
import models.fileUpload.FileUploadOutcomeStatus.ValidationErrorsLessThanMax
import models.requests.IdentifierRequest
import models.{ChargeType, UserAnswers}
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{times, verify, when}
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceableModule
import play.api.libs.json.{JsObject, Json}
import play.api.test.Helpers._
import play.twirl.api.Html
import uk.gov.hmrc.viewmodels.NunjucksSupport

import scala.concurrent.Future

class ValidationErrorsAllControllerSpec extends ControllerSpecBase with NunjucksSupport with JsonMatchers {
  private val mutableFakeDataRetrievalAction: MutableFakeDataRetrievalAction = new MutableFakeDataRetrievalAction()

  private val templateToBeRendered = "fileUpload/invalid.njk"
  private val chargeType = ChargeType.ChargeTypeOverseasTransfer

  private def httpPathGET: String = controllers.fileUpload.routes.ValidationErrorsAllController.onPageLoad(srn, startDate, accessType, versionInt, chargeType).url

  private def fileDownloadInstructionLink = controllers.routes.FileDownloadController.instructionsFile(chargeType, None).url

  private def returnToSchemeDetails = controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate.toString, accessType, versionInt).url

  private val errorsJson = Json.obj("test" -> "test")

  private def jsonToPassToTemplate = Json.obj(
    "chargeType" -> chargeType.toString,
    "chargeTypeText" -> ChargeType.fileUploadText(chargeType),
    "srn" -> srn,
    "fileDownloadInstructionsLink" -> fileDownloadInstructionLink,
    "returnToFileUploadURL" -> dummyCall.url,
    "returnToSchemeDetails" -> returnToSchemeDetails,
    "schemeName" -> schemeName
  ) ++ errorsJson

  private val mockFileUploadOutcomeConnector = mock[FileUploadOutcomeConnector]

  val extraModules: Seq[GuiceableModule] = Seq[GuiceableModule](
    bind[FileUploadOutcomeConnector].toInstance(mockFileUploadOutcomeConnector)
  )


  private def application: Application = applicationBuilderMutableRetrievalAction(mutableFakeDataRetrievalAction, extraModules).build()

  override def beforeEach(): Unit = {
    super.beforeEach()
    when(mockRenderer.render(any(), any())(any())).thenReturn(Future.successful(Html("")))
    when(mockAppConfig.schemeDashboardUrl(any(): IdentifierRequest[_])).thenReturn(dummyCall.url)
    when(mockFileUploadOutcomeConnector.getOutcome(any(), any()))
      .thenReturn(Future.successful(Some(FileUploadOutcome(status = ValidationErrorsLessThanMax, json = errorsJson))))
  }

  private val userAnswers: Option[UserAnswers] = Some(userAnswersWithSchemeNamePstrQuarter)

  "validationErrorsAll Controller" must {
    "return OK and the correct view for a GET" in {

      when(mockAppConfig.failureEndpointTarget(any(), any(), any(), any(), any())).thenReturn(dummyCall.url)
      mutableFakeDataRetrievalAction.setDataToReturn(userAnswers)
      val templateCaptor = ArgumentCaptor.forClass(classOf[String])
      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

      val result = route(application, httpGETRequest(httpPathGET)).value

      status(result) mustEqual OK

      verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

      templateCaptor.getValue mustEqual templateToBeRendered

      jsonCaptor.getValue must containJson(jsonToPassToTemplate)
    }
  }
}
