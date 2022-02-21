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
import fileUploadParsers.{AnnualAllowanceParser, ParserValidationError}
import matchers.JsonMatchers
import models.LocalDateBinder._
import models.{ChargeType, UploadId, UploadStatus, UploadedSuccessfully, UserAnswers}
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceableModule
import play.api.libs.json._
import play.api.test.Helpers.{route, status, _}
import play.twirl.api.Html
import services.AFTService
import services.fileUpload.{FileUploadAftReturnService, UploadProgressTracker}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.viewmodels.NunjucksSupport

import scala.concurrent.{ExecutionContext, Future}

class ValidationControllerSpec extends ControllerSpecBase with NunjucksSupport with JsonMatchers {
  private val mutableFakeDataRetrievalAction: MutableFakeDataRetrievalAction = new MutableFakeDataRetrievalAction()
  private val templateToBeRendered = "fileUpload/invalid.njk"
  private val genericTemplateToBeRendered = "fileUpload/genericErrors.njk"
  private val chargeType = ChargeType.ChargeTypeAnnualAllowance
  private def ua: UserAnswers = userAnswersWithSchemeName

  val expectedJson: JsObject = Json.obj()

  private val mockUpscanInitiateConnector: UpscanInitiateConnector = mock[UpscanInitiateConnector]
  private val mockAFTService: AFTService = mock[AFTService]
  private val mockFileUploadAftReturnService: FileUploadAftReturnService = mock[FileUploadAftReturnService]


  private val fakeUploadProgressTracker: UploadProgressTracker = new UploadProgressTracker {
    override def requestUpload(uploadId: UploadId, fileReference: Reference)(implicit ec: ExecutionContext, hc: HeaderCarrier): Future[Unit] =
      Future.successful(())

    override def registerUploadResult(reference: Reference, uploadStatus: UploadStatus)(implicit ec: ExecutionContext,
                                                                                        hc: HeaderCarrier): Future[Unit] = Future.successful(())

    override def getUploadResult(id: UploadId)(implicit ec: ExecutionContext, hc: HeaderCarrier): Future[Option[UploadStatus]] =
      Future.successful(
        Some(
          UploadedSuccessfully(
            name = "name",
            mimeType = "mime",
            downloadUrl = "/test",
            size = Some(1L)
          )))
  }

  private val mockAnnualAllowanceParser = mock[AnnualAllowanceParser]
  private val template = "fileUpload/fileUploadSuccess.njk"
  private val extraModules: Seq[GuiceableModule] = Seq[GuiceableModule](
    bind[UpscanInitiateConnector].toInstance(mockUpscanInitiateConnector),
    bind[AnnualAllowanceParser].toInstance(mockAnnualAllowanceParser),
    bind[UploadProgressTracker].toInstance(fakeUploadProgressTracker),
    bind[AFTService].toInstance(mockAFTService),
    bind[FileUploadAftReturnService].toInstance(mockFileUploadAftReturnService)
  )

  override def beforeEach: Unit = {
    super.beforeEach
    reset(mockUpscanInitiateConnector, mockAppConfig, mockRenderer, mockAnnualAllowanceParser)
    when(mockRenderer.render(any(), any())(any())).thenReturn(Future.successful(Html("")))
    when(mockUpscanInitiateConnector.download(any())(any())).thenReturn(Future.successful(HttpResponse(OK,
      "First name,Last name,National Insurance number,Tax year,Charge amount,Date,Payment type mandatory\nJoy,Smith,9717C,2020,268.28,2020-01-01,true")))
  }

  private val application: Application = applicationBuilderMutableRetrievalAction(mutableFakeDataRetrievalAction, extraModules).build()

  "onPageLoad" must {
    "return OK and the correct view for a GET where there are validation errors" in {

      mutableFakeDataRetrievalAction.setDataToReturn(Some(ua))

      val templateCaptor = ArgumentCaptor.forClass(classOf[String])
      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])
      val errors: Seq[ParserValidationError] = Seq(
        ParserValidationError(0, 0, "Cry", "firstName")
      )

      when(mockAnnualAllowanceParser.parse(any(), any(), any())(any())).thenReturn(Left(errors))

      val result = route(
        application,
        httpGETRequest(
          controllers.fileUpload.routes.ValidationController.onPageLoad(srn, startDate, accessType, versionInt, chargeType, UploadId("")).url)
      ).value

      status(result) mustEqual OK

      verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

      templateCaptor.getValue mustEqual templateToBeRendered
      val jsonToPassToTemplate = Json.obj(
        "chargeType" -> chargeType.toString,
        "chargeTypeText" -> ChargeType.fileUploadText(chargeType),
        "srn" -> srn,
        "fileDownloadInstructionsLink" -> "/manage-pension-scheme-accounting-for-tax/annual-allowance-charge/aft-annual-allowance-charge-upload-format-instructions",
        "returnToFileUploadURL" -> "",
        "returnToSchemeDetails" -> "/manage-pension-scheme-accounting-for-tax/aa/2020-04-01/draft/1/return-to-scheme-details",
        "schemeName" -> "Big Scheme"
       )
      println(jsonCaptor.getValue)
      println(jsonToPassToTemplate)
      jsonCaptor.getValue must containJson(jsonToPassToTemplate)
    }

    "return OK and the correct view for a GET where there are validation errors more than 10 errors" in {

      mutableFakeDataRetrievalAction.setDataToReturn(Some(ua))

      val templateCaptor = ArgumentCaptor.forClass(classOf[String])
      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])
      val errors: Seq[ParserValidationError] = Seq(
        ParserValidationError(1, 0, "memberDetails.error.firstName.required", "firstName"),
        ParserValidationError(2, 1, "memberDetails.error.lastName.required", "lastName"),
        ParserValidationError(3, 2, "memberDetails.error.nino.invalid", "nino"),
        ParserValidationError(4, 1, "memberDetails.error.lastName.required", "lastName"),
        ParserValidationError(5, 2, "memberDetails.error.nino.invalid", "nino"),
        ParserValidationError(6, 1, "memberDetails.error.lastName.required", "lastName"),
        ParserValidationError(7, 2, "memberDetails.error.nino.invalid", "nino"),
        ParserValidationError(8, 1, "memberDetails.error.lastName.required", "lastName"),
        ParserValidationError(9, 2, "memberDetails.error.nino.invalid", "nino"),
        ParserValidationError(10, 1, "memberDetails.error.lastName.required", "lastName"),
        ParserValidationError(11, 2, "memberDetails.error.nino.invalid", "nino"),
      )

      when(mockAnnualAllowanceParser.parse(any(), any(), any())(any())).thenReturn(Left(errors))

      val result = route(
        application,
        httpGETRequest(
          controllers.fileUpload.routes.ValidationController.onPageLoad(srn, startDate, accessType, versionInt, chargeType, UploadId("")).url)
      ).value

      status(result) mustEqual OK

      verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

      val expectedErrors = Seq("fileUpload.memberDetails.generic.error.firstName",
                               "fileUpload.memberDetails.generic.error.lastName",
                               "fileUpload.memberDetails.generic.error.nino")
      templateCaptor.getValue mustEqual genericTemplateToBeRendered
      val jsonToPassToTemplate = Json.obj(
        "chargeType" -> chargeType.toString,
        "chargeTypeText" -> ChargeType.fileUploadText(chargeType),
        "srn" -> srn,
        "totalError" -> errors.size,
        "errors" -> expectedErrors,
        "fileDownloadInstructionsLink" -> "/manage-pension-scheme-accounting-for-tax/annual-allowance-charge/aft-annual-allowance-charge-upload-format-instructions",
        "returnToFileUploadURL" -> "",
        "returnToSchemeDetails" -> "/manage-pension-scheme-accounting-for-tax/aa/2020-04-01/draft/1/return-to-scheme-details",
        "schemeName" -> "Big Scheme"
      )
      jsonCaptor.getValue must containJson(jsonToPassToTemplate)
    }

    "redirect to error page when there is a Validation error : Header invalid or File is empty" in {

      mutableFakeDataRetrievalAction.setDataToReturn(Some(ua))
      val errors: Seq[ParserValidationError] =
        Seq(ParserValidationError(0, 0, "Header invalid or File is empty"))

      when(mockAnnualAllowanceParser.parse(any(), any(), any())(any())).thenReturn(Left(errors))

      val result = route(
        application,
        httpGETRequest(
          controllers.fileUpload.routes.ValidationController.onPageLoad(srn, startDate, accessType, versionInt, chargeType, UploadId("")).url)
      ).value

      status(result) mustEqual SEE_OTHER
      redirectLocation(result) mustBe Some(routes.UpscanErrorController.invalidHeaderOrBodyError(srn, startDate, accessType, versionInt, chargeType).url)
    }

    "redirect to error page when there is a Download file error : Unknown" in {

      mutableFakeDataRetrievalAction.setDataToReturn(Some(ua))
      when(mockUpscanInitiateConnector.download(any())(any())).thenReturn(Future.successful(HttpResponse(INTERNAL_SERVER_ERROR,
        "Internal Server Error")))

      val result = route(
        application,
        httpGETRequest(
          controllers.fileUpload.routes.ValidationController.onPageLoad(srn, startDate, accessType, versionInt, chargeType, UploadId("")).url)
      ).value

      status(result) mustEqual SEE_OTHER
      redirectLocation(result) mustBe Some(routes.UpscanErrorController.unknownError(srn, startDate, accessType, versionInt).url)
    }

    "redirect OK to the next page and save items to be committed into the Mongo database when there are no validation errors" in {
      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])
      val templateCaptor = ArgumentCaptor.forClass(classOf[String])
      when(mockUserAnswersCacheConnector.save(any(), jsonCaptor.capture())(any(), any()))
        .thenReturn(Future.successful(JsNull))
      mutableFakeDataRetrievalAction.setDataToReturn(Some(ua))

      val ci = UserAnswers()
        .setOrException( JsPath \ "testNode1", JsString("test1"))
        .setOrException( JsPath \ "testNode2", JsString("test2"))


      when(mockAnnualAllowanceParser.parse(any(), any(), any())(any())).thenReturn(Right(ci))
      when(mockFileUploadAftReturnService.preProcessAftReturn(any(), any())(any(), any(), any())).thenReturn(Future.successful(ci))
      when(mockAFTService.fileCompileReturn(any(), any())(any(), any(), any())).thenReturn(Future.successful())
      when(mockCompoundNavigator.nextPage(any(), any(), any(), any(), any(), any(), any())(any())).thenReturn(dummyCall)
      val result = route(
        application,
        httpGETRequest(
          controllers.fileUpload.routes.ValidationController.onPageLoad(srn, startDate, accessType, versionInt, chargeType, UploadId("")).url)
      ).value

      status(result) mustEqual SEE_OTHER
      
      redirectLocation(result) mustBe Some(routes.FileUploadSuccessController.onPageLoad(srn, startDate, accessType, versionInt, chargeType).url)

    }
  }

}