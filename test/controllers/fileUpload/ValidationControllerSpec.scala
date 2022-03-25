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

import audit.{AFTFileValidationCheckAuditEvent, AuditEvent, AuditService}
import connectors.{Reference, UpscanInitiateConnector}
import controllers.actions.MutableFakeDataRetrievalAction
import controllers.base.ControllerSpecBase
import controllers.fileUpload.FileUploadHeaders.AnnualAllowanceFieldNames
import data.SampleData
import data.SampleData._
import fileUploadParsers.Parser.FileLevelParserValidationErrorTypeHeaderInvalidOrFileEmpty
import fileUploadParsers.{AnnualAllowanceParser, LifetimeAllowanceParser, OverseasTransferParser, ParserValidationError}
import matchers.JsonMatchers
import models.AdministratorOrPractitioner.Administrator
import models.ChargeType.{ChargeTypeAnnualAllowance, ChargeTypeLifetimeAllowance}
import models.LocalDateBinder._
import models.SponsoringEmployerType.SponsoringEmployerTypeIndividual
import models.chargeA.{ChargeDetails => ChargeADetails}
import models.chargeB.ChargeBDetails
import models.chargeF.{ChargeDetails => ChargeFDetails}
import models.{ChargeType, FileUploadDataCache, FileUploadStatus, UploadId, UploadStatus, UserAnswers}
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import pages.PSTRQuery
import pages.chargeA.{ChargeDetailsPage => ChargeADetailsPage}
import pages.chargeB.ChargeBDetailsPage
import pages.chargeC.{ChargeCDetailsPage, SponsoringIndividualDetailsPage, WhichTypeOfSponsoringEmployerPage}
import pages.chargeD.{ChargeDetailsPage => ChargeDDetailsPage, MemberDetailsPage => MemberDDetailsPage}
import pages.chargeE.{ChargeDetailsPage => ChargeEDetailsPage, MemberDetailsPage => MemberEDetailsPage}
import pages.chargeF.{ChargeDetailsPage => ChargeFDetailsPage}
import pages.chargeG.{ChargeAmountsPage, ChargeDetailsPage => ChargeGDetailsPage, MemberDetailsPage => MemberGDetailsPage}
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceableModule
import play.api.libs.json._
import play.api.mvc.Result
import play.api.test.Helpers.{route, status, _}
import play.twirl.api.Html
import services.AFTService
import services.fileUpload.{FileUploadAftReturnService, UploadProgressTracker}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.viewmodels.NunjucksSupport

import java.time.{LocalDate, LocalDateTime}
import scala.collection.JavaConverters.asScalaBufferConverter
import scala.concurrent.{ExecutionContext, Future}

class ValidationControllerSpec extends ControllerSpecBase with NunjucksSupport with JsonMatchers {
  private val mutableFakeDataRetrievalAction: MutableFakeDataRetrievalAction = new MutableFakeDataRetrievalAction()
  private val templateToBeRendered = "fileUpload/invalid.njk"
  private val genericTemplateToBeRendered = "fileUpload/genericErrors.njk"
  private val chargeType = ChargeType.ChargeTypeAnnualAllowance
  private val mockAuditService = mock[AuditService]

  private def ua: UserAnswers = userAnswersWithSchemeName

  val expectedJson: JsObject = Json.obj()

  import ValidationControllerSpec._

  private val extraModules: Seq[GuiceableModule] = Seq[GuiceableModule](
    bind[UpscanInitiateConnector].toInstance(mockUpscanInitiateConnector),
    bind[AnnualAllowanceParser].toInstance(mockAnnualAllowanceParser),
    bind[LifetimeAllowanceParser].toInstance(mockLifetimeAllowanceParser),
    bind[OverseasTransferParser].toInstance(mockOverseasTransferParser),
    bind[UploadProgressTracker].toInstance(fakeUploadProgressTracker),
    bind[AFTService].toInstance(mockAFTService),
    bind[FileUploadAftReturnService].toInstance(mockFileUploadAftReturnService),
    bind[AuditService].toInstance(mockAuditService),

  )

  override def beforeEach: Unit = {
    super.beforeEach
    reset(mockUpscanInitiateConnector, mockAppConfig, mockRenderer, mockAnnualAllowanceParser, mockLifetimeAllowanceParser,
      mockOverseasTransferParser, mockAuditService)
    when(mockRenderer.render(any(), any())(any())).thenReturn(Future.successful(Html("")))
    when(mockUpscanInitiateConnector.download(any())(any())).thenReturn(Future.successful(HttpResponse(OK,
      "First name,Last name,National Insurance number,Tax year,Charge amount,Date,Payment type mandatory\nJoy,Smith,9717C,2020,268.28,2020-01-01,true")))
    doNothing.when(mockAuditService).sendEvent(any())(any(), any())
  }

  private val application: Application = applicationBuilderMutableRetrievalAction(mutableFakeDataRetrievalAction, extraModules).build()

  def runValidation(errors: Seq[ParserValidationError]): Future[Result] = {
    when(mockUpscanInitiateConnector.download(any())(any())).thenReturn(Future.successful(HttpResponse(OK,
      "First name,Last name,National Insurance number,Tax year,Charge amount,Date,Payment type mandatory\n" +
        "Joy,Smith,9717C,2020,268.28,2020-01-01,true\nJoy,Smath,9717C,2020,268.28,2020-01-01,true")))
    mutableFakeDataRetrievalAction.setDataToReturn(Some(ua))
    when(mockAnnualAllowanceParser.parse(any(), any(), any())(any())).thenReturn(Left(errors))
    route(
      application,
      httpGETRequest(
        controllers.fileUpload.routes.ValidationController.onPageLoad(srn, startDate, accessType, versionInt, chargeType, UploadId("")).url)
    ).value
  }

  "onPageLoad" must {
    "send correct audit events for failure when header invalid" in {
      status(runValidation(Seq(FileLevelParserValidationErrorTypeHeaderInvalidOrFileEmpty))) mustEqual SEE_OTHER
      val captorAuditEvents: ArgumentCaptor[AuditEvent] =
        ArgumentCaptor.forClass(classOf[AuditEvent])

      verify(mockAuditService, times(2))
        .sendEvent(captorAuditEvents.capture())(any(), any())

      val auditEventsSent = captorAuditEvents.getAllValues.asScala

      val foundAFTFileValidationCheck = auditEventsSent.find(_.auditType == "AFTFileValidationCheck")
      foundAFTFileValidationCheck.isDefined mustBe true
      foundAFTFileValidationCheck.map { actualAuditEvent =>
        actualAuditEvent.details mustBe Map(
          "failureReason" -> "Header invalid or File is empty",
          "numberOfEntries" -> "2",
          "psaId" -> psaId,
          "pstr" -> pstr,
          "fileValidationTimeInSeconds" -> "0",
          "chargeType" -> "annualAllowance",
          "validationCheckStatus" -> "Failure",
          "numberOfFailures" -> "1"
        )
      }

      val foundAFTFileUpscanDownloadCheck = auditEventsSent.find(_.auditType == "AFTFileUpscanDownloadCheck")
      foundAFTFileUpscanDownloadCheck.isDefined mustBe true
      foundAFTFileUpscanDownloadCheck.map { actualAuditEvent =>
        actualAuditEvent.details mustBe Map(
          "reference" -> "reference",
          "downloadStatus" -> "UploadedSuccessfully",
          "psaId" -> psaId,
          "pstr" -> pstr,
          "chargeType" -> "annualAllowance",
          "downloadTimeInSeconds" -> "0"
        )
      }
    }

    "send correct audit event for failure when less than 10 (non-header) errors" in {
      status(runValidation(Seq(
        ParserValidationError(1, 1, "Field error one", AnnualAllowanceFieldNames.chargeAmount),
        ParserValidationError(2, 1, "Field error two", AnnualAllowanceFieldNames.dateNoticeReceived)
      ))) mustEqual OK
      val auditCaptor: ArgumentCaptor[AFTFileValidationCheckAuditEvent] = ArgumentCaptor.forClass(classOf[AFTFileValidationCheckAuditEvent])
      verify(mockAuditService, times(2)).sendEvent(auditCaptor.capture())(any(), any())
      val auditEventSent = auditCaptor.getValue

      auditEventSent.administratorOrPractitioner mustBe Administrator
      auditEventSent.id mustBe psaId
      auditEventSent.pstr mustBe pstr
      auditEventSent.numberOfEntries mustBe 2
      auditEventSent.chargeType mustBe ChargeTypeAnnualAllowance
      auditEventSent.validationCheckSuccessful mustBe false
      auditEventSent.failureReason mustBe Some("Field Validation failure(Less than 10)")
      auditEventSent.numberOfFailures mustBe 2
      val expectedFailureContent =
        """B2: Field error one
          |B3: Field error two""".stripMargin
      auditEventSent.validationFailureContent mustBe Some(expectedFailureContent)
    }

    "send correct audit event for failure when more than 10 (non-header) errors" in {
      status(runValidation(Seq(
        ParserValidationError(1, 1, "Field error 1", AnnualAllowanceFieldNames.chargeAmount),
        ParserValidationError(1, 1, "Field error 2", AnnualAllowanceFieldNames.dateNoticeReceived),
        ParserValidationError(1, 1, "Field error 3", AnnualAllowanceFieldNames.isPaymentMandatory),
        ParserValidationError(1, 1, "Field error 4", AnnualAllowanceFieldNames.chargeAmount),
        ParserValidationError(1, 1, "Field error 5", AnnualAllowanceFieldNames.taxYear),
        ParserValidationError(1, 1, "Field error 6", AnnualAllowanceFieldNames.dateNoticeReceivedDay),
        ParserValidationError(1, 1, "Field error 7", AnnualAllowanceFieldNames.chargeAmount),
        ParserValidationError(1, 1, "Field error 8", AnnualAllowanceFieldNames.chargeAmount),
        ParserValidationError(1, 1, "Field error 9", AnnualAllowanceFieldNames.chargeAmount),
        ParserValidationError(1, 1, "Field error 10", AnnualAllowanceFieldNames.chargeAmount),
        ParserValidationError(1, 1, "Field error 11", AnnualAllowanceFieldNames.chargeAmount)
      ))) mustEqual OK
      val auditCaptor: ArgumentCaptor[AFTFileValidationCheckAuditEvent] = ArgumentCaptor.forClass(classOf[AFTFileValidationCheckAuditEvent])
      verify(mockAuditService, times(2)).sendEvent(auditCaptor.capture())(any(), any())
      val auditEventSent = auditCaptor.getValue

      auditEventSent.administratorOrPractitioner mustBe Administrator
      auditEventSent.id mustBe psaId
      auditEventSent.pstr mustBe pstr
      auditEventSent.numberOfEntries mustBe 2
      auditEventSent.chargeType mustBe ChargeTypeAnnualAllowance
      auditEventSent.validationCheckSuccessful mustBe false
      auditEventSent.failureReason mustBe Some("Generic failure (more than 10)")
      auditEventSent.numberOfFailures mustBe 11
      auditEventSent.validationFailureContent mustBe Some(
        """charge amount is missing or in the wrong format
          |date you received notice to pay the charge is missing or in the wrong format
          |payment type is missing or in the wrong format
          |tax year to which the annual allowance charge relates is missing or in the wrong format""".stripMargin)
    }

    "send correct audit event for when there are no failures" in {
      val chargeType = ChargeType.ChargeTypeLifetimeAllowance
      val uaCaptorPassedIntoParse: ArgumentCaptor[UserAnswers] = ArgumentCaptor.forClass(classOf[UserAnswers])
      when(mockUserAnswersCacheConnector.save(any(), any())(any(), any()))
        .thenReturn(Future.successful(JsNull))
      mutableFakeDataRetrievalAction.setDataToReturn(Some(uaAllChargeTypes))

      val uaUpdatedWithParsedItems = uaAllChargeTypes

        .setOrException(MemberDDetailsPage(0).path, Json.toJson(memberDetails3))
        .setOrException(ChargeDDetailsPage(0).path, Json.toJson(chargeDDetails))

      when(mockLifetimeAllowanceParser.parse(any(), any(), uaCaptorPassedIntoParse.capture())(any())).thenReturn(Right(uaUpdatedWithParsedItems))
      when(mockFileUploadAftReturnService.preProcessAftReturn(any(), any())(any(), any(), any())).thenReturn(Future.successful(uaUpdatedWithParsedItems))
      when(mockAFTService.fileCompileReturn(any(), any())(any(), any(), any())).thenReturn(Future.successful(()))
      when(mockCompoundNavigator.nextPage(any(), any(), any(), any(), any(), any(), any())(any())).thenReturn(dummyCall)
      val result = route(
        application,
        httpGETRequest(
          controllers.fileUpload.routes.ValidationController.onPageLoad(srn, startDate, accessType, versionInt, chargeType, UploadId("")).url)
      ).value

      status(result) mustEqual SEE_OTHER
      val auditCaptor: ArgumentCaptor[AFTFileValidationCheckAuditEvent] = ArgumentCaptor.forClass(classOf[AFTFileValidationCheckAuditEvent])
      verify(mockAuditService, times(2)).sendEvent(auditCaptor.capture())(any(), any())
      val auditEventSent = auditCaptor.getValue

      auditEventSent.administratorOrPractitioner mustBe Administrator
      auditEventSent.id mustBe psaId
      auditEventSent.pstr mustBe pstr
      auditEventSent.numberOfEntries mustBe 1
      auditEventSent.chargeType mustBe ChargeTypeLifetimeAllowance
      auditEventSent.validationCheckSuccessful mustBe true
      auditEventSent.failureReason mustBe None
      auditEventSent.numberOfFailures mustBe 0
      auditEventSent.validationFailureContent mustBe None
    }

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
      //here
      verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

      templateCaptor.getValue mustEqual templateToBeRendered
      val jsonToPassToTemplate = Json.obj(
        "chargeType" -> chargeType.toString,
        "chargeTypeText" -> ChargeType.fileUploadText(chargeType),
        "srn" -> srn,
        "fileDownloadInstructionsLink" ->
          "/manage-pension-scheme-accounting-for-tax/annual-allowance-charge/aft-annual-allowance-charge-upload-format-instructions",
        "returnToFileUploadURL" -> "",
        "returnToSchemeDetails" -> "/manage-pension-scheme-accounting-for-tax/aa/2020-04-01/draft/1/return-to-scheme-details",
        "schemeName" -> "Big Scheme"
      )
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
      //here
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
        "fileDownloadInstructionsLink" ->
          "/manage-pension-scheme-accounting-for-tax/annual-allowance-charge/aft-annual-allowance-charge-upload-format-instructions",
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

    "for charge type D when there are no validation errors " + "" +
      "redirect to next page, remove existing data for charge type but leave other " +
      "charge types intact then save items to be committed into Mongo" in {
      val chargeType = ChargeType.ChargeTypeLifetimeAllowance
      val uaCaptorPassedIntoParse: ArgumentCaptor[UserAnswers] = ArgumentCaptor.forClass(classOf[UserAnswers])
      when(mockUserAnswersCacheConnector.save(any(), any())(any(), any()))
        .thenReturn(Future.successful(JsNull))
      mutableFakeDataRetrievalAction.setDataToReturn(Some(uaAllChargeTypes))

      val uaUpdatedWithParsedItems = uaAllChargeTypes
        .setOrException(MemberDDetailsPage(0).path, Json.toJson(memberDetails3))
        .setOrException(ChargeDDetailsPage(0).path, Json.toJson(chargeDDetails))

      when(mockLifetimeAllowanceParser.parse(any(), any(), uaCaptorPassedIntoParse.capture())(any())).thenReturn(Right(uaUpdatedWithParsedItems))
      when(mockFileUploadAftReturnService.preProcessAftReturn(any(), any())(any(), any(), any())).thenReturn(Future.successful(uaUpdatedWithParsedItems))
      when(mockAFTService.fileCompileReturn(any(), any())(any(), any(), any())).thenReturn(Future.successful(()))
      when(mockCompoundNavigator.nextPage(any(), any(), any(), any(), any(), any(), any())(any())).thenReturn(dummyCall)
      val result = route(
        application,
        httpGETRequest(
          controllers.fileUpload.routes.ValidationController.onPageLoad(srn, startDate, accessType, versionInt, chargeType, UploadId("")).url)
      ).value

      status(result) mustEqual SEE_OTHER

      redirectLocation(result) mustBe Some(routes.FileUploadSuccessController.onPageLoad(srn, startDate, accessType, versionInt, chargeType).url)

      retrieveChargeCount(uaCaptorPassedIntoParse.getValue) mustBe Seq(1, 1, 1, 0, 2, 1, 2)
    }

    "for charge type E when there are no validation errors " + "" +
      "redirect to next page, remove existing data for charge type but leave other " +
      "charge types intact then save items to be committed into Mongo" in {
      val uaCaptorPassedIntoParse: ArgumentCaptor[UserAnswers] = ArgumentCaptor.forClass(classOf[UserAnswers])
      when(mockUserAnswersCacheConnector.save(any(), any())(any(), any()))
        .thenReturn(Future.successful(JsNull))
      mutableFakeDataRetrievalAction.setDataToReturn(Some(uaAllChargeTypes))

      val uaUpdatedWithParsedItems = uaAllChargeTypes
        .setOrException(MemberEDetailsPage(0).path, Json.toJson(memberDetails3))
        .setOrException(ChargeEDetailsPage(0).path, Json.toJson(chargeEDetails2))

      when(mockAnnualAllowanceParser.parse(any(), any(), uaCaptorPassedIntoParse.capture())(any())).thenReturn(Right(uaUpdatedWithParsedItems))
      when(mockFileUploadAftReturnService.preProcessAftReturn(any(), any())(any(), any(), any())).thenReturn(Future.successful(uaUpdatedWithParsedItems))
      when(mockAFTService.fileCompileReturn(any(), any())(any(), any(), any())).thenReturn(Future.successful(()))
      when(mockCompoundNavigator.nextPage(any(), any(), any(), any(), any(), any(), any())(any())).thenReturn(dummyCall)
      val result = route(
        application,
        httpGETRequest(
          controllers.fileUpload.routes.ValidationController.onPageLoad(srn, startDate, accessType, versionInt, chargeType, UploadId("")).url)
      ).value

      status(result) mustEqual SEE_OTHER

      redirectLocation(result) mustBe Some(routes.FileUploadSuccessController.onPageLoad(srn, startDate, accessType, versionInt, chargeType).url)

      retrieveChargeCount(uaCaptorPassedIntoParse.getValue) mustBe Seq(1, 1, 1, 2, 0, 1, 2)
    }

    "for charge type G when there are no validation errors " + "" +
      "redirect to next page, remove existing data for charge type but leave other " +
      "charge types intact then save items to be committed into Mongo" in {
      val chargeType = ChargeType.ChargeTypeOverseasTransfer
      val uaCaptorPassedIntoParse: ArgumentCaptor[UserAnswers] = ArgumentCaptor.forClass(classOf[UserAnswers])
      when(mockUserAnswersCacheConnector.save(any(), any())(any(), any()))
        .thenReturn(Future.successful(JsNull))
      mutableFakeDataRetrievalAction.setDataToReturn(Some(uaAllChargeTypes))

      val uaUpdatedWithParsedItems = uaAllChargeTypes
        .setOrException(MemberGDetailsPage(0).path, Json.toJson(memberGDetails2))
        .setOrException(ChargeGDetailsPage(0).path, Json.toJson(chargeGDetails))
        .setOrException(ChargeAmountsPage(0).path, Json.toJson(chargeAmounts2))

      when(mockOverseasTransferParser.parse(any(), any(), uaCaptorPassedIntoParse.capture())(any())).thenReturn(Right(uaUpdatedWithParsedItems))
      when(mockFileUploadAftReturnService.preProcessAftReturn(any(), any())(any(), any(), any())).thenReturn(Future.successful(uaUpdatedWithParsedItems))
      when(mockAFTService.fileCompileReturn(any(), any())(any(), any(), any())).thenReturn(Future.successful(()))
      when(mockCompoundNavigator.nextPage(any(), any(), any(), any(), any(), any(), any())(any())).thenReturn(dummyCall)
      val result = route(
        application,
        httpGETRequest(
          controllers.fileUpload.routes.ValidationController.onPageLoad(srn, startDate, accessType, versionInt, chargeType, UploadId("")).url)
      ).value

      status(result) mustEqual SEE_OTHER

      redirectLocation(result) mustBe Some(routes.FileUploadSuccessController.onPageLoad(srn, startDate, accessType, versionInt, chargeType).url)

      retrieveChargeCount(uaCaptorPassedIntoParse.getValue) mustBe Seq(1, 1, 1, 2, 2, 1, 0)
    }
  }
}

object ValidationControllerSpec extends ControllerSpecBase with NunjucksSupport with JsonMatchers {
  private val mockUpscanInitiateConnector: UpscanInitiateConnector = mock[UpscanInitiateConnector]
  private val mockAFTService: AFTService = mock[AFTService]
  private val mockFileUploadAftReturnService: FileUploadAftReturnService = mock[FileUploadAftReturnService]
  private val mockFileUploadStatus: FileUploadStatus = mock[FileUploadStatus]
  private val fakeUploadProgressTracker: UploadProgressTracker = new UploadProgressTracker {
    override def requestUpload(uploadId: UploadId, fileReference: Reference)(implicit ec: ExecutionContext, hc: HeaderCarrier): Future[Unit] =
      Future.successful(())

    override def registerUploadResult(reference: Reference, uploadStatus: UploadStatus)(implicit ec: ExecutionContext,
                                                                                        hc: HeaderCarrier): Future[Unit] = Future.successful(())

    override def getUploadResult(id: UploadId)(implicit ec: ExecutionContext, hc: HeaderCarrier): Future[Option[FileUploadDataCache]] = {

      Future.successful(
        Some(
          FileUploadDataCache(
            uploadId = "uploadID",
            reference = "reference",
            status = FileUploadStatus(_type = "UploadedSuccessfully"),
            created = LocalDateTime.now,
            lastUpdated = LocalDateTime.now,
            expireAt = LocalDateTime.now
          )))
    }
  }

  private val mockAnnualAllowanceParser = mock[AnnualAllowanceParser]
  private val mockLifetimeAllowanceParser = mock[LifetimeAllowanceParser]
  private val mockOverseasTransferParser = mock[OverseasTransferParser]

  val uaAllChargeTypes: UserAnswers = UserAnswers()
    .setOrException(ChargeADetailsPage, ChargeADetails(2, Some(200.00), Some(200.00), 400.00))
    .setOrException(ChargeBDetailsPage, ChargeBDetails(2, 400.00))
    .setOrException(WhichTypeOfSponsoringEmployerPage(0), SponsoringEmployerTypeIndividual)
    .setOrException(SponsoringIndividualDetailsPage(0), SampleData.sponsoringIndividualDetails)
    .setOrException(ChargeCDetailsPage(0), SampleData.chargeCDetails)

    .setOrException(MemberDDetailsPage(0), SampleData.memberDetails)
    .setOrException(ChargeDDetailsPage(0), SampleData.chargeDDetails)

    .setOrException(MemberDDetailsPage(1), SampleData.memberDetails)
    .setOrException(ChargeDDetailsPage(1), SampleData.chargeDDetails)

    .setOrException(MemberEDetailsPage(0), SampleData.memberDetails)
    .setOrException(ChargeEDetailsPage(0), SampleData.chargeEDetails)

    .setOrException(MemberEDetailsPage(1), SampleData.memberDetails)
    .setOrException(ChargeEDetailsPage(1), SampleData.chargeEDetails)

    .setOrException(ChargeFDetailsPage, ChargeFDetails(LocalDate.now(), 400.00))
    .setOrException(MemberGDetailsPage(0), SampleData.memberGDetails)
    .setOrException(ChargeAmountsPage(0), SampleData.chargeAmounts)
    .setOrException(ChargeGDetailsPage(0), SampleData.chargeGDetails)

    .setOrException(MemberGDetailsPage(1), SampleData.memberGDetails)
    .setOrException(ChargeAmountsPage(1), SampleData.chargeAmounts)
    .setOrException(ChargeGDetailsPage(1), SampleData.chargeGDetails)
    .setOrException(PSTRQuery, pstr)

  def retrieveChargeCount(ua: UserAnswers): Seq[Int] = {
    val chargeACount = ua.get(ChargeADetailsPage).map(_ => 1).getOrElse(0)
    val chargeBCount = ua.get(ChargeBDetailsPage).map(_ => 1).getOrElse(0)
    val chargeCCount =
      ua.get(ChargeCDetailsPage(0)).map(_ => 1).getOrElse(0) + ua.get(ChargeCDetailsPage(1)).map(_ => 1).getOrElse(0)
    val chargeDCount =
      ua.get(ChargeDDetailsPage(0)).map(_ => 1).getOrElse(0) + ua.get(ChargeDDetailsPage(1)).map(_ => 1).getOrElse(0)
    val chargeECount =
      ua.get(ChargeEDetailsPage(0)).map(_ => 1).getOrElse(0) + ua.get(ChargeEDetailsPage(1)).map(_ => 1).getOrElse(0)
    val chargeFCount = ua.get(ChargeFDetailsPage).map(_ => 1).getOrElse(0)
    val chargeGCount =
      ua.get(ChargeAmountsPage(0)).map(_ => 1).getOrElse(0) + ua.get(ChargeAmountsPage(1)).map(_ => 1).getOrElse(0)

    Seq(chargeACount, chargeBCount, chargeCCount, chargeDCount, chargeECount, chargeFCount, chargeGCount)
  }
}
