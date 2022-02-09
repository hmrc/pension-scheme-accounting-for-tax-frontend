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
import data.SampleData
import data.SampleData._
import fileUploadParsers.{AnnualAllowanceParser, ParserValidationError}
import matchers.JsonMatchers
import models.LocalDateBinder._
import models.SponsoringEmployerType.SponsoringEmployerTypeIndividual
import models.chargeA.{ChargeDetails => ChargeADetails}
import models.chargeB.ChargeBDetails
import models.chargeF.{ChargeDetails => ChargeFDetails}
import models.{ChargeType, UploadId, UploadStatus, UploadedSuccessfully, UserAnswers}
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import pages.chargeA.{ChargeDetailsPage => ChargeADetailsPage}
import pages.chargeB.ChargeBDetailsPage
import pages.chargeC.{ChargeCDetailsPage, SponsoringIndividualDetailsPage, WhichTypeOfSponsoringEmployerPage}
import pages.chargeD.{ChargeDetailsPage => ChargeDDetailsPage, MemberDetailsPage => MemberDDetailsPage}
import pages.chargeE.{ChargeDetailsPage => ChargeEDetailsPage, MemberDetailsPage => MemberEDetailsPage}
import pages.chargeF.{ChargeDetailsPage => ChargeFDetailsPage}
import pages.chargeG.{ChargeAmountsPage, MemberDetailsPage => MemberGDetailsPage}
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

import java.time.LocalDate
import scala.concurrent.{ExecutionContext, Future}

class ValidationControllerSpec extends ControllerSpecBase with NunjucksSupport with JsonMatchers {

  import ValidationControllerSpec._

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
        ParserValidationError(0, 0, "Cry")
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
        "chargeTypeText" -> chargeType.toString,
        "srn" -> srn,
        "errors" -> errors
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
      when(mockUserAnswersCacheConnector.save(any(), jsonCaptor.capture())(any(), any()))
        .thenReturn(Future.successful(JsNull))
      mutableFakeDataRetrievalAction.setDataToReturn(Some(ua))

      val ci = UserAnswers()
        .setOrException(JsPath \ "testNode1", JsString("test1"))
        .setOrException(JsPath \ "testNode2", JsString("test2"))

      when(mockAnnualAllowanceParser.parse(any(), any(), any())(any())).thenReturn(Right(ci))
      when(mockFileUploadAftReturnService.preProcessAftReturn(any(), any())(any(), any(), any())).thenReturn(Future.successful(ci))
      when(mockAFTService.fileCompileReturn(any(), any())(any(), any(), any())).thenReturn(Future.successful(()))
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

object ValidationControllerSpec extends ControllerSpecBase with NunjucksSupport with JsonMatchers {
  private val mutableFakeDataRetrievalAction: MutableFakeDataRetrievalAction = new MutableFakeDataRetrievalAction()
  private val templateToBeRendered = "fileUpload/invalid.njk"
  private val chargeType = ChargeType.ChargeTypeAnnualAllowance

  private def ua: UserAnswers = userAnswersWithSchemeName

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
  val uaAllChargeTypes: UserAnswers = UserAnswers()
    .setOrException(ChargeADetailsPage, ChargeADetails(2, Some(200.00), Some(200.00), 400.00))
    .setOrException(ChargeBDetailsPage, ChargeBDetails(2, 400.00))
    .setOrException(WhichTypeOfSponsoringEmployerPage(0), SponsoringEmployerTypeIndividual)
    .setOrException(SponsoringIndividualDetailsPage(0), SampleData.sponsoringIndividualDetails)
    .setOrException(ChargeCDetailsPage(0), SampleData.chargeCDetails)
    .setOrException(MemberDDetailsPage(0), SampleData.memberDetails)
    .setOrException(ChargeDDetailsPage(0), SampleData.chargeDDetails)
    .setOrException(MemberEDetailsPage(0), SampleData.memberDetails)
    .setOrException(ChargeEDetailsPage(0), SampleData.chargeEDetails)
    .setOrException(ChargeFDetailsPage, ChargeFDetails(LocalDate.now(), 400.00))
    .setOrException(MemberGDetailsPage(0), SampleData.memberGDetails)
    .setOrException(ChargeAmountsPage(0), SampleData.chargeAmounts)
}