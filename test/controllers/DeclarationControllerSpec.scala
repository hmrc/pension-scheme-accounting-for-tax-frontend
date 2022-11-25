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

package controllers

import audit.{AFTReturnEmailAuditEvent, AuditService}
import config.FrontendAppConfig
import connectors.cache.UserAnswersCacheConnector
import connectors.{EmailConnector, EmailSent, ReturnAlreadySubmittedException}
import controllers.actions._
import controllers.base.ControllerSpecBase
import data.SampleData
import data.SampleData._
import matchers.JsonMatchers
import models.JourneyType.{AFT_SUBMIT_AMEND, AFT_SUBMIT_RETURN}
import models.LocalDateBinder._
import models.ValueChangeType.{ChangeTypeDecrease, ChangeTypeIncrease, ChangeTypeSame}
import models.requests.IdentifierRequest
import models.{AFTQuarter, AccessMode, AdministratorOrPractitioner, Declaration, GenericViewModel, JourneyType, SessionAccessData, UserAnswers}
import navigators.CompoundNavigator
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{times, verify, when}
import org.mockito.{ArgumentCaptor, ArgumentMatchers, Mockito}
import org.scalatestplus.mockito.MockitoSugar
import pages._
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceableModule
import play.api.libs.json.{JsObject, Json}
import play.api.test.Helpers.{route, status, _}
import play.twirl.api.Html
import services.AFTService
import uk.gov.hmrc.http.UpstreamErrorResponse
import uk.gov.hmrc.nunjucks.NunjucksRenderer
import utils.AFTConstants.{QUARTER_END_DATE, QUARTER_START_DATE}
import utils.DateHelper.{dateFormatterDMY, dateFormatterStartDate, formatSubmittedDate}

import java.time.{ZoneId, ZonedDateTime}
import scala.concurrent.Future

class DeclarationControllerSpec extends ControllerSpecBase with MockitoSugar with JsonMatchers {

  import DeclarationControllerSpec._

  private val mockAuditService = mock[AuditService]
  private val mockAFTService = mock[AFTService]
  private val mockEmailConnector = mock[EmailConnector]
  private val extraModules: Seq[GuiceableModule] = Seq[GuiceableModule](
    bind[AFTService].toInstance(mockAFTService),
    bind[IdentifierAction].to[FakeIdentifierAction],
    bind[AllowSubmissionAction].toInstance(new FakeAllowSubmissionAction),
    bind[EmailConnector].toInstance(mockEmailConnector)
  )

  override def modules: Seq[GuiceableModule] =
    Seq(
      bind[DataRequiredAction].to[DataRequiredActionImpl],
      bind[NunjucksRenderer].toInstance(mockRenderer),
      bind[FrontendAppConfig].toInstance(mockAppConfig),
      bind[UserAnswersCacheConnector].toInstance(mockUserAnswersCacheConnector),
      bind[CompoundNavigator].toInstance(mockCompoundNavigator),
      bind[AllowAccessActionProvider].toInstance(mockAllowAccessActionProvider),
      bind[AuditService].toInstance(mockAuditService)
    )

  private val extraModulesPsp: Seq[GuiceableModule] = Seq[GuiceableModule](
    bind[AFTService].toInstance(mockAFTService),
    bind[AllowSubmissionAction].toInstance(new FakeAllowSubmissionAction),
    bind[EmailConnector].toInstance(mockEmailConnector),
    bind[IdentifierAction].to[FakeIdentifierActionPSP]
  )
  private val mutableFakeDataRetrievalAction: MutableFakeDataRetrievalAction = new MutableFakeDataRetrievalAction()
  private val application: Application = applicationBuilderMutableRetrievalAction(mutableFakeDataRetrievalAction, extraModules).build()
  private val applicationPsp: Application = applicationBuilderMutableRetrievalAction(mutableFakeDataRetrievalAction, extraModulesPsp).build()

  private val templateToBeRendered = "declaration.njk"

  private def httpPathGET: String = controllers.routes.DeclarationController.onPageLoad(srn, QUARTER_START_DATE, accessType, versionInt).url

  private def httpPathOnSubmit: String = controllers.routes.DeclarationController.onSubmit(srn, QUARTER_START_DATE, accessType, versionInt).url

  private val jsonToPassToTemplate = Json.obj(
    fields = "viewModel" -> GenericViewModel(
      submitUrl = routes.DeclarationController.onSubmit(srn, QUARTER_START_DATE, accessType, versionInt).url,
      returnUrl = controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, QUARTER_START_DATE, accessType, versionInt).url,
      schemeName = schemeName)
  )

  override def beforeEach(): Unit = {
    super.beforeEach()
    Mockito.reset(mockRenderer)
    Mockito.reset(mockEmailConnector)
    Mockito.reset(mockAFTService)
    Mockito.reset(mockUserAnswersCacheConnector)
    Mockito.reset(mockCompoundNavigator)
    Mockito.reset(mockAuditService)
    when(mockRenderer.render(any(), any())(any())).thenReturn(Future.successful(Html("")))
    when(mockAppConfig.schemeDashboardUrl(any(): IdentifierRequest[_])).thenReturn(dummyCall.url)
    when(mockAppConfig.amendAftReturnDecreaseTemplateIdId).thenReturn(amendAftReturnDecreaseTemplateIdId)
    when(mockAppConfig.amendAftReturnNoChangeTemplateIdId).thenReturn(amendAftReturnNoChangeTemplateIdId)
    when(mockAppConfig.amendAftReturnIncreaseTemplateIdId).thenReturn(amendAftReturnIncreaseTemplateIdId)
    when(mockAppConfig.fileAFTReturnTemplateId).thenReturn(fileAFTReturnTemplateId)
  }

  private def emailParams(isAmendment: Boolean = false): Map[String, String] =
    Map(
      "schemeName" -> schemeName,
      "accountingPeriod" -> messages("confirmation.table.accounting.period.value",
        quarter.startDate.format(dateFormatterStartDate),
        quarter.endDate.format(dateFormatterDMY)),
      "dateSubmitted" -> formatSubmittedDate(ZonedDateTime.now(ZoneId.of("Europe/London"))),
      "psaName" -> psaName,
      "hmrcEmail" -> messages("confirmation.whatNext.send.to.email.id")
    ) ++ (if (isAmendment) Map("submissionNumber" -> s"$versionNumber") else Map.empty)

  "Declaration Controller" must {
    "return OK and the correct view for a GET" in {
      mutableFakeDataRetrievalAction.setDataToReturn(userAnswers)
      val templateCaptor = ArgumentCaptor.forClass(classOf[String])
      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

      val result = route(application, httpGETRequest(httpPathGET)).value

      status(result) mustEqual OK
      verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

      templateCaptor.getValue mustEqual templateToBeRendered
      jsonCaptor.getValue must containJson(jsonToPassToTemplate)
    }

    "return OK and the correct view for a GET for PSP" in {
      mutableFakeDataRetrievalAction.setDataToReturn(userAnswers)
      val templateCaptor = ArgumentCaptor.forClass(classOf[String])
      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

      val result = route(applicationPsp, httpGETRequest(httpPathGET)).value

      status(result) mustEqual OK
      verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

      templateCaptor.getValue mustEqual templateToBeRenderedPsp
      jsonCaptor.getValue must containJson(jsonToPassToTemplate)
    }

    "Save data to user answers, file AFT Return, send an email (with audit event) and redirect to next page when on submit declaration by PSA" in {
      mutableFakeDataRetrievalAction.setDataToReturn(userAnswersWithPSTREmailQuarter)
      val uaCaptor = ArgumentCaptor.forClass(classOf[UserAnswers])
      when(mockEmailConnector.sendEmail(any(), any(), any(), any(), any(), any(), any())(any(), any())).thenReturn(Future.successful(EmailSent))
      when(mockUserAnswersCacheConnector.savePartial(any(), any(), any(), any())(any(), any())).thenReturn(Future.successful(Json.obj()))
      when(mockCompoundNavigator.nextPage(ArgumentMatchers.eq(DeclarationPage), any(), any(), any(), any(), any(), any())(any())).thenReturn(dummyCall)
      when(mockAFTService.fileSubmitReturn(any(), uaCaptor.capture())(any(), any(), any())).thenReturn(Future.successful(()))

      val result = route(application, httpGETRequest(httpPathOnSubmit)).value

      status(result) mustEqual SEE_OTHER

      verify(mockAFTService, times(1)).fileSubmitReturn(any(), any())(any(), any(), any())
      verify(mockUserAnswersCacheConnector, times(1)).savePartial(any(), any(), any(), any())(any(), any())
      uaCaptor.getValue.get(DeclarationPage) mustBe Some(Declaration(
        submittedBy = "PSA",
        submittedID = psaId,
        hasAgreed = true
      ))
      verify(mockEmailConnector, times(1)).sendEmail(
        ArgumentMatchers.eq(AdministratorOrPractitioner.Administrator),
        any(), any(), journeyTypeCaptor.capture(), any(), templateCaptor.capture(), emailParamsCaptor.capture())(any(), any())

      redirectLocation(result) mustBe Some(dummyCall.url)
      journeyTypeCaptor.getValue mustEqual AFT_SUBMIT_RETURN
      templateCaptor.getValue mustEqual fileAFTReturnTemplateId
      emailParamsCaptor.getValue mustEqual emailParams()

      val expectedAuditEvent = AFTReturnEmailAuditEvent(psaId, AFT_SUBMIT_RETURN, AdministratorOrPractitioner.Administrator, email)
      val auditEventCaptor = ArgumentCaptor.forClass(classOf[AFTReturnEmailAuditEvent])
      verify(mockAuditService, times(1)).sendEvent(auditEventCaptor.capture())(any(), any())
      auditEventCaptor.getValue mustBe expectedAuditEvent
    }

    "Save data to user answers, file AFT Return, send an email (and splunk event) and redirect to next page when on submit declaration by PSP" in {
      mutableFakeDataRetrievalAction.setDataToReturn(userAnswersWithPSTREmailQuarter)
      val uaCaptor = ArgumentCaptor.forClass(classOf[UserAnswers])
      when(mockEmailConnector.sendEmail(any(), any(), any(), any(), any(), any(), any())(any(), any())).thenReturn(Future.successful(EmailSent))
      when(mockUserAnswersCacheConnector.savePartial(any(), any(), any(), any())(any(), any())).thenReturn(Future.successful(Json.obj()))
      when(mockCompoundNavigator.nextPage(ArgumentMatchers.eq(DeclarationPage), any(), any(), any(), any(), any(), any())(any())).thenReturn(dummyCall)
      when(mockAFTService.fileSubmitReturn(any(), uaCaptor.capture())(any(), any(), any())).thenReturn(Future.successful(()))

      val result = route(applicationPsp, httpGETRequest(httpPathOnSubmit)).value

      status(result) mustEqual SEE_OTHER

      verify(mockAFTService, times(1)).fileSubmitReturn(any(), any())(any(), any(), any())
      verify(mockUserAnswersCacheConnector, times(1)).savePartial(any(), any(), any(), any())(any(), any())
      uaCaptor.getValue.get(DeclarationPage) mustBe Some(Declaration(
        submittedBy = "PSP",
        submittedID = pspId,
        hasAgreed = true
      ))
      verify(mockEmailConnector, times(1)).sendEmail(
        ArgumentMatchers.eq(AdministratorOrPractitioner.Practitioner),
        any(), any(), journeyTypeCaptor.capture(), any(), templateCaptor.capture(), emailParamsCaptor.capture())(any(), any())

      redirectLocation(result) mustBe Some(dummyCall.url)
      journeyTypeCaptor.getValue mustEqual AFT_SUBMIT_RETURN
      templateCaptor.getValue mustEqual fileAFTReturnTemplateId
      emailParamsCaptor.getValue mustEqual emailParams()

      val expectedAuditEvent = AFTReturnEmailAuditEvent(pspId, AFT_SUBMIT_RETURN, AdministratorOrPractitioner.Practitioner, email)
      val auditEventCaptor = ArgumentCaptor.forClass(classOf[AFTReturnEmailAuditEvent])
      verify(mockAuditService, times(1)).sendEvent(auditEventCaptor.capture())(any(), any())
      auditEventCaptor.getValue mustBe expectedAuditEvent
    }

    "Save data to user answers, file amended AFT Return (decreased value), " +
      "send an email with correct template ID and redirect to next page when on submit declaration" in {
      mutableFakeDataRetrievalAction.setDataToReturn(
        userAnswersWithPSTREmailQuarter.map(_.setOrException(ConfirmSubmitAFTAmendmentValueChangeTypePage, ChangeTypeDecrease))
      )
      mutableFakeDataRetrievalAction.setSessionData(SampleData.sessionData(sessionAccessData =
        SessionAccessData(versionNumber, AccessMode.PageAccessModeCompile, areSubmittedVersionsAvailable = false)))
      when(mockEmailConnector.sendEmail(any(), any(), any(), any(), any(), any(), any())(any(), any())).thenReturn(Future.successful(EmailSent))
      when(mockUserAnswersCacheConnector.savePartial(any(), any(), any(), any())(any(), any())).thenReturn(Future.successful(Json.obj()))
      when(mockCompoundNavigator.nextPage(ArgumentMatchers.eq(DeclarationPage), any(), any(), any(), any(), any(), any())(any())).thenReturn(dummyCall)
      when(mockAFTService.fileSubmitReturn(any(), any())(any(), any(), any())).thenReturn(Future.successful(()))

      val result = route(application, httpGETRequest(httpPathOnSubmit)).value

      status(result) mustEqual SEE_OTHER

      verify(mockEmailConnector, times(1)).sendEmail(
        ArgumentMatchers.eq(AdministratorOrPractitioner.Administrator),
        any(), any(), journeyTypeCaptor.capture(), any(), templateCaptor.capture(), emailParamsCaptor.capture())(any(), any())


      redirectLocation(result) mustBe Some(dummyCall.url)
      journeyTypeCaptor.getValue mustEqual AFT_SUBMIT_AMEND
      templateCaptor.getValue mustEqual amendAftReturnDecreaseTemplateIdId
      emailParamsCaptor.getValue mustEqual emailParams(isAmendment = true)
    }

    "Save data to user answers, file amended AFT Return (no change in value), " +
      "send an email with correct template ID and redirect to next page when on submit declaration" in {
      mutableFakeDataRetrievalAction.setDataToReturn(
        userAnswersWithPSTREmailQuarter.map(_.setOrException(ConfirmSubmitAFTAmendmentValueChangeTypePage, ChangeTypeSame))
      )
      mutableFakeDataRetrievalAction.setSessionData(SampleData.sessionData(sessionAccessData =
        SessionAccessData(versionNumber, AccessMode.PageAccessModeCompile, areSubmittedVersionsAvailable = false)))
      when(mockEmailConnector.sendEmail(any(), any(), any(), any(), any(), any(), any())(any(), any())).thenReturn(Future.successful(EmailSent))
      when(mockUserAnswersCacheConnector.savePartial(any(), any(), any(), any())(any(), any())).thenReturn(Future.successful(Json.obj()))
      when(mockCompoundNavigator.nextPage(ArgumentMatchers.eq(DeclarationPage), any(), any(), any(), any(), any(), any())(any())).thenReturn(dummyCall)
      when(mockAFTService.fileSubmitReturn(any(), any())(any(), any(), any())).thenReturn(Future.successful(()))

      val result = route(application, httpGETRequest(httpPathOnSubmit)).value

      status(result) mustEqual SEE_OTHER

      verify(mockEmailConnector, times(1)).sendEmail(
        ArgumentMatchers.eq(AdministratorOrPractitioner.Administrator),
        any(), any(), journeyTypeCaptor.capture(), any(), templateCaptor.capture(), emailParamsCaptor.capture())(any(), any())

      redirectLocation(result) mustBe Some(dummyCall.url)
      journeyTypeCaptor.getValue mustEqual AFT_SUBMIT_AMEND
      templateCaptor.getValue mustEqual amendAftReturnNoChangeTemplateIdId
      emailParamsCaptor.getValue mustEqual emailParams(isAmendment = true)
    }

    "Save data to user answers, file amended AFT Return (increased value), " +
      "send an email with correct template ID and splunk event and redirect to next page when on submit declaration" in {
      mutableFakeDataRetrievalAction.setDataToReturn(
        userAnswersWithPSTREmailQuarter.map(_.setOrException(ConfirmSubmitAFTAmendmentValueChangeTypePage, ChangeTypeIncrease))
      )
      mutableFakeDataRetrievalAction.setSessionData(SampleData.sessionData(sessionAccessData =
        SessionAccessData(versionNumber, AccessMode.PageAccessModeCompile, areSubmittedVersionsAvailable = false)))
      when(mockEmailConnector.sendEmail(any(), any(), any(), any(), any(), any(), any())(any(), any())).thenReturn(Future.successful(EmailSent))
      when(mockUserAnswersCacheConnector.savePartial(any(), any(), any(), any())(any(), any())).thenReturn(Future.successful(Json.obj()))
      when(mockCompoundNavigator.nextPage(ArgumentMatchers.eq(DeclarationPage), any(), any(), any(), any(), any(), any())(any())).thenReturn(dummyCall)
      when(mockAFTService.fileSubmitReturn(any(), any())(any(), any(), any())).thenReturn(Future.successful(()))

      val result = route(application, httpGETRequest(httpPathOnSubmit)).value

      status(result) mustEqual SEE_OTHER

      verify(mockEmailConnector, times(1)).sendEmail(
        ArgumentMatchers.eq(AdministratorOrPractitioner.Administrator),
        any(), any(), journeyTypeCaptor.capture(), any(), templateCaptor.capture(), emailParamsCaptor.capture())(any(), any())

      redirectLocation(result) mustBe Some(dummyCall.url)
      journeyTypeCaptor.getValue mustEqual AFT_SUBMIT_AMEND
      templateCaptor.getValue mustEqual amendAftReturnIncreaseTemplateIdId
      emailParamsCaptor.getValue mustEqual emailParams(isAmendment = true)

      val expectedAuditEvent = AFTReturnEmailAuditEvent(psaId, AFT_SUBMIT_AMEND, AdministratorOrPractitioner.Administrator, email)
      val auditEventCaptor = ArgumentCaptor.forClass(classOf[AFTReturnEmailAuditEvent])
      verify(mockAuditService, times(1)).sendEvent(auditEventCaptor.capture())(any(), any())
      auditEventCaptor.getValue mustBe expectedAuditEvent
    }

    "redirect to session expired when there is no pstr on submit declaration" in {
      mutableFakeDataRetrievalAction.setDataToReturn(Some(UserAnswers(Json.obj("schemeName" -> schemeName))))

      val result = route(application, httpGETRequest(httpPathOnSubmit)).value

      status(result) mustEqual SEE_OTHER
      redirectLocation(result).value mustBe controllers.routes.SessionExpiredController.onPageLoad.url
    }

    "redirect to your action was not processed page for a POST if 5XX error is thrown" in {
      mutableFakeDataRetrievalAction.setDataToReturn(
        userAnswersWithPSTREmailQuarter.map(_.setOrException(ConfirmSubmitAFTAmendmentValueChangeTypePage, ChangeTypeIncrease))
      )
      when(mockUserAnswersCacheConnector.savePartial(any(), any(), any(), any())(any(), any())).thenReturn(Future.successful(Json.obj()))
      when(mockAFTService.fileSubmitReturn(any(), any())(any(), any(), any())).
        thenReturn(Future.failed(UpstreamErrorResponse("serviceUnavailable", SERVICE_UNAVAILABLE, SERVICE_UNAVAILABLE)))
      val result = route(application, httpGETRequest(httpPathOnSubmit)).value

      status(result) mustEqual SEE_OTHER
      redirectLocation(result).value mustBe controllers.routes.YourActionWasNotProcessedController.onPageLoad(srn, startDate).url
    }

    "redirect to cannot submit AFT page for a POST if 403 error is thrown" in {
      mutableFakeDataRetrievalAction.setDataToReturn(
        userAnswersWithPSTREmailQuarter.map(_.setOrException(ConfirmSubmitAFTAmendmentValueChangeTypePage, ChangeTypeIncrease))
      )
      when(mockUserAnswersCacheConnector.savePartial(any(), any(), any(), any())(any(), any())).thenReturn(Future.successful(Json.obj()))
      when(mockAFTService.fileSubmitReturn(any(), any())(any(), any(), any())).
        thenReturn(Future.failed(ReturnAlreadySubmittedException()))
      val result = route(application, httpGETRequest(httpPathOnSubmit)).value

      status(result) mustEqual SEE_OTHER
      redirectLocation(result).value mustBe controllers.routes.CannotSubmitAFTController.onPageLoad(srn, startDate).url
    }
  }
}

object DeclarationControllerSpec {
  private val templateToBeRenderedPsp = "pspDeclaration.njk"
  private val emailParamsCaptor = ArgumentCaptor.forClass(classOf[Map[String, String]])
  private val templateCaptor = ArgumentCaptor.forClass(classOf[String])
  private val journeyTypeCaptor = ArgumentCaptor.forClass(classOf[JourneyType.Value])
  private val quarter = AFTQuarter(QUARTER_START_DATE, QUARTER_END_DATE)
  private val versionNumber = 3
  private val psaName = "test ltd"
  private val userAnswers: Option[UserAnswers] = Some(userAnswersWithSchemeName)
  private val email = "psa@test.com"
  private val userAnswersWithPSTREmailQuarter: Option[UserAnswers] = userAnswers.map(
    _.set(PSTRQuery, pstr).flatMap(_.set(EmailQuery, value = email)).flatMap(_.set(NameQuery, psaName))
      .flatMap(_.set(QuarterPage, quarter)).getOrElse(UserAnswers()))
  private val amendAftReturnDecreaseTemplateIdId = "pods_aft_amended_return_decrease"
  private val amendAftReturnNoChangeTemplateIdId = "pods_aft_amended_return_no_change"
  private val amendAftReturnIncreaseTemplateIdId = "pods_aft_amended_return_increase"
  private val fileAFTReturnTemplateId = "pods_file_aft_return"
}
