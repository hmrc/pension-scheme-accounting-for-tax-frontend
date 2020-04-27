/*
 * Copyright 2020 HM Revenue & Customs
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

import java.time.LocalDateTime

import connectors.{EmailConnector, EmailSent}
import controllers.actions.{AllowSubmissionAction, FakeAllowSubmissionAction, MutableFakeDataRetrievalAction}
import controllers.base.ControllerSpecBase
import data.SampleData._
import matchers.JsonMatchers
import models.LocalDateBinder._
import models.{GenericViewModel, Quarter, UserAnswers}
import org.mockito.Matchers.any
import org.mockito.Mockito.{times, verify, when}
import org.mockito.{ArgumentCaptor, Matchers, Mockito}
import org.scalatestplus.mockito.MockitoSugar
import pages._
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceableModule
import play.api.libs.json.{JsObject, Json}
import play.api.test.Helpers.{route, status, _}
import play.twirl.api.Html
import services.AFTService
import utils.AFTConstants.{QUARTER_END_DATE, QUARTER_START_DATE}
import utils.DateHelper.{dateFormatterDMY, dateFormatterStartDate, dateFormatterSubmittedDate}

import scala.concurrent.Future

class DeclarationControllerSpec extends ControllerSpecBase with MockitoSugar with JsonMatchers {
  import DeclarationControllerSpec._

  private val mockAFTService = mock[AFTService]
  private val mockEmailConnector = mock[EmailConnector]
  private val extraModules: Seq[GuiceableModule] = Seq[GuiceableModule](
    bind[AFTService].toInstance(mockAFTService),
    bind[AllowSubmissionAction].toInstance(new FakeAllowSubmissionAction),
    bind[EmailConnector].toInstance(mockEmailConnector)
  )
  private val mutableFakeDataRetrievalAction: MutableFakeDataRetrievalAction = new MutableFakeDataRetrievalAction()
  private val application: Application = applicationBuilderMutableRetrievalAction(mutableFakeDataRetrievalAction, extraModules).build()

  override def beforeEach: Unit = {
    super.beforeEach
    Mockito.reset(mockRenderer, mockEmailConnector, mockAFTService, mockUserAnswersCacheConnector, mockCompoundNavigator)
    when(mockRenderer.render(any(), any())(any())).thenReturn(Future.successful(Html("")))
    when(mockAppConfig.managePensionsSchemeSummaryUrl).thenReturn(dummyCall.url)
    when(mockAppConfig.amendAftReturnTemplateIdId).thenReturn(amendAftReturnTemplateIdId)
    when(mockAppConfig.fileAFTReturnTemplateId).thenReturn(fileAFTReturnTemplateId)
  }
  private def emailParams(isAmendment: Boolean = false): Map[String, String] =
    Map(
      "schemeName" -> schemeName,
      "accountingPeriod" -> messages("confirmation.table.accounting.period.value",
                                     quarter.startDate.format(dateFormatterStartDate),
                                     quarter.endDate.format(dateFormatterDMY)),
      "dateSubmitted" -> dateFormatterSubmittedDate.format(LocalDateTime.now()),
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

    "Save data to user answers, file AFT Return, send an email and redirect to next page when on submit declaration" in {
      mutableFakeDataRetrievalAction.setDataToReturn(userAnswersWithPSTREmailQuarter)

      when(mockEmailConnector.sendEmail(any(), any(), any(), any())(any(), any())).thenReturn(Future.successful(EmailSent))
      when(mockUserAnswersCacheConnector.save(any(), any())(any(), any())).thenReturn(Future.successful(Json.obj()))
      when(mockCompoundNavigator.nextPage(Matchers.eq(DeclarationPage), any(), any(), any(), any())).thenReturn(dummyCall)
      when(mockAFTService.fileAFTReturn(any(), any())(any(), any(), any())).thenReturn(Future.successful(()))

      val result = route(application, httpGETRequest(httpPathOnSubmit)).value

      status(result) mustEqual SEE_OTHER

      verify(mockAFTService, times(1)).fileAFTReturn(any(), any())(any(), any(), any())
      verify(mockUserAnswersCacheConnector, times(1)).save(any(), any())(any(), any())
      verify(mockEmailConnector, times(1)).sendEmail(journeyTypeCaptor.capture(), any(), templateCaptor.capture(), emailParamsCaptor.capture())(any(), any())

      redirectLocation(result) mustBe Some(dummyCall.url)
      journeyTypeCaptor.getValue mustEqual "AFTReturn"
      templateCaptor.getValue mustEqual fileAFTReturnTemplateId
      emailParamsCaptor.getValue mustEqual emailParams()
    }

    "Save data to user answers, file amended AFT Return, send an email and redirect to next page when on submit declaration" in {
      mutableFakeDataRetrievalAction.setDataToReturn(userAnswersWithPSTREmailQuarter.map(_.setOrException(VersionNumberQuery, versionNumber)))

      when(mockEmailConnector.sendEmail(any(), any(), any(), any())(any(), any())).thenReturn(Future.successful(EmailSent))
      when(mockUserAnswersCacheConnector.save(any(), any())(any(), any())).thenReturn(Future.successful(Json.obj()))
      when(mockCompoundNavigator.nextPage(Matchers.eq(DeclarationPage), any(), any(), any(), any())).thenReturn(dummyCall)
      when(mockAFTService.fileAFTReturn(any(), any())(any(), any(), any())).thenReturn(Future.successful(()))

      val result = route(application, httpGETRequest(httpPathOnSubmit)).value

      status(result) mustEqual SEE_OTHER
      verify(mockEmailConnector, times(1)).sendEmail(journeyTypeCaptor.capture(), any(), templateCaptor.capture(), emailParamsCaptor.capture())(any(), any())

      redirectLocation(result) mustBe Some(dummyCall.url)
      journeyTypeCaptor.getValue mustEqual "AFTAmend"
      templateCaptor.getValue mustEqual amendAftReturnTemplateIdId
      emailParamsCaptor.getValue mustEqual emailParams(isAmendment = true)
    }

    "redirect to session expired when there is no pstr on submit declaration" in {
      mutableFakeDataRetrievalAction.setDataToReturn(Some(UserAnswers(Json.obj("schemeName" -> schemeName))))

      val result = route(application, httpGETRequest(httpPathOnSubmit)).value

      status(result) mustEqual SEE_OTHER
      redirectLocation(result).value mustBe controllers.routes.SessionExpiredController.onPageLoad().url
    }
  }
}

object DeclarationControllerSpec {

  private val templateToBeRendered = "declaration.njk"
  private def httpPathGET: String = controllers.routes.DeclarationController.onPageLoad(srn, QUARTER_START_DATE).url
  private def httpPathOnSubmit: String = controllers.routes.DeclarationController.onSubmit(srn, QUARTER_START_DATE).url
  private val emailParamsCaptor = ArgumentCaptor.forClass(classOf[Map[String, String]])
  private val templateCaptor = ArgumentCaptor.forClass(classOf[String])
  private val journeyTypeCaptor = ArgumentCaptor.forClass(classOf[String])
  private val quarter = Quarter(QUARTER_START_DATE, QUARTER_END_DATE)
  private val versionNumber = 3
  private val userAnswers: Option[UserAnswers] = Some(userAnswersWithSchemeName)
  private val userAnswersWithPSTREmailQuarter: Option[UserAnswers] = userAnswers.map(
    _.set(PSTRQuery, pstr).flatMap(_.set(PSAEmailQuery, value = "psa@test.com")).flatMap(_.set(QuarterPage, quarter)).getOrElse(UserAnswers()))
  private val jsonToPassToTemplate = Json.obj(
    fields = "viewModel" -> GenericViewModel(submitUrl = routes.DeclarationController.onSubmit(srn, QUARTER_START_DATE).url,
                                             returnUrl = dummyCall.url,
                                             schemeName = schemeName)
  )
  private val amendAftReturnTemplateIdId = "pods_aft_amended_return"
  private val fileAFTReturnTemplateId = "pods_file_aft_return"
}
