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

import connectors.{Reference, UpscanInitiateConnector}
import controllers.actions.MutableFakeDataRetrievalAction
import controllers.base.ControllerSpecBase
import data.SampleData._
import fileUploadParsers.{AnnualAllowanceParser, ParserValidationErrors, ValidationResult}
import forms.fileUpload.InputSelectionFormProvider
import matchers.JsonMatchers
import models.LocalDateBinder._
import models.fileUpload.InputSelection
import models.{AccessType, ChargeType, GenericViewModel, UploadId, UploadStatus, UploadedSuccessfully, UserAnswers}
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import pages.SchemeNameQuery
import play.api.Application
import play.api.data.Form
import play.api.inject.bind
import play.api.inject.guice.GuiceableModule
import play.api.libs.json.{JsNull, JsObject, Json}
import play.api.test.Helpers.{route, status, _}
import play.twirl.api.Html
import services.fileUpload.UploadProgressTracker
import uk.gov.hmrc.http.HttpResponse
import uk.gov.hmrc.viewmodels.NunjucksSupport
import viewmodels.Radios

import scala.concurrent.Future

class InputSelectionControllerSpec extends ControllerSpecBase with NunjucksSupport with JsonMatchers {
  private val mutableFakeDataRetrievalAction: MutableFakeDataRetrievalAction = new MutableFakeDataRetrievalAction()
  private val templateToBeRendered = "fileUpload/inputSelection.njk"
  private val chargeType = ChargeType.ChargeTypeAnnualAllowance

  private def ua: UserAnswers = userAnswersWithSchemeName

  val expectedJson: JsObject = Json.obj()

  private val mockUpscanInitiateConnector: UpscanInitiateConnector = mock[UpscanInitiateConnector]

  private val fakeUploadProgressTracker: UploadProgressTracker = new UploadProgressTracker {
    override def requestUpload(uploadId: UploadId, fileReference: Reference): Future[Unit] = Future.successful(())

    override def registerUploadResult(reference: Reference, uploadStatus: UploadStatus): Future[Unit] = Future.successful(())

    override def getUploadResult(id: UploadId): Future[Option[UploadStatus]] = Future.successful(Some(UploadedSuccessfully(
      name = "name",
      mimeType = "mime",
      downloadUrl = "/test",
      size = Some(1L)
    )))
  }

  private val mockAnnualAllowanceParser = mock[AnnualAllowanceParser]

  private val extraModules: Seq[GuiceableModule] = Seq[GuiceableModule](
    bind[UpscanInitiateConnector].toInstance(mockUpscanInitiateConnector),
    bind[AnnualAllowanceParser].toInstance(mockAnnualAllowanceParser),
    bind[UploadProgressTracker].toInstance(fakeUploadProgressTracker)
  )


  override def beforeEach: Unit = {
    super.beforeEach
    reset(mockUpscanInitiateConnector, mockAppConfig, mockRenderer, mockAnnualAllowanceParser)
    when(mockRenderer.render(any(), any())(any())).thenReturn(Future.successful(Html("")))
    when(mockUpscanInitiateConnector.download(any())(any())).thenReturn(Future.successful(HttpResponse(OK, "Joy,Smith,9717C,2020,268.28,2020-01-01,true")))
  }

  private val application: Application = applicationBuilderMutableRetrievalAction(mutableFakeDataRetrievalAction, extraModules).build()
  private val formProvider = new InputSelectionFormProvider
  private val form:Form[InputSelection] = formProvider()
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
  val jsonToPassToTemplate= Json.obj(
        "chargeType" -> chargeType.toString,
        "form" -> form ,
        "startDate" -> Some("2020-04-01"),
        "chargeTypeText" -> chargeType.toString,
        "srn" -> srn,
        "radios" -> InputSelection.radios(form),
        "viewModel" -> viewModel(srn, startDate, accessType, versionInt, ua)
      )

      jsonCaptor.getValue must containJson(jsonToPassToTemplate)
    }
//    "redirect OK to the next page and save into the Mongo database" in {
//      when(mockUserAnswersCacheConnector.save(any(), any())(any(), any()))
//        .thenReturn(Future.successful(JsNull))
//      mutableFakeDataRetrievalAction.setDataToReturn(Some(ua))
//
//
//      val errors : List[ParserValidationErrors]= List()
//
//      when(mockAnnualAllowanceParser.parse(any(),any())).thenReturn(ValidationResult(UserAnswers(), errors))
//      when(mockCompoundNavigator.nextPage(any(), any(),any(), any(),any(), any(),any())(any())).thenReturn(dummyCall)
//      val result = route(application,
//        httpGETRequest(controllers.fileUpload
//          .routes.ValidationController.onPageLoad(srn, startDate, accessType, versionInt, chargeType, UploadId("")).url)).value
//
//      status(result) mustEqual SEE_OTHER
//      redirectLocation(result).value mustEqual dummyCall.url
//
//    }
  }

  def viewModel(srn: String, startDate: String, accessType: AccessType, version: Int, ua: UserAnswers) = GenericViewModel(
    submitUrl = "",
    returnUrl = controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, version).url,
    schemeName = ua.get(SchemeNameQuery).getOrElse("the scheme") // TODO: error handling
  )
  
}
