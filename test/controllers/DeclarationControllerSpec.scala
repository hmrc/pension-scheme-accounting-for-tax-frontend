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

import controllers.actions.{AllowSubmissionAction, FakeAllowSubmissionAction, MutableFakeDataRetrievalAction}
import controllers.base.ControllerSpecBase
import data.SampleData._
import matchers.JsonMatchers
import models.{GenericViewModel, UserAnswers}
import org.mockito.Matchers.any
import org.mockito.Mockito.{times, verify, when}
import org.mockito.{ArgumentCaptor, Matchers}
import org.scalatestplus.mockito.MockitoSugar
import pages.{DeclarationPage, PSTRQuery}
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceableModule
import play.api.libs.json.{JsObject, Json}
import play.api.test.Helpers.{route, status, _}
import play.twirl.api.Html
import services.AFTService

import scala.concurrent.Future

class DeclarationControllerSpec extends ControllerSpecBase with MockitoSugar with JsonMatchers {

  private val mockAFTService = mock[AFTService]
  private val extraModules: Seq[GuiceableModule] = Seq[GuiceableModule](bind[AFTService].toInstance(mockAFTService),
    bind[AllowSubmissionAction].toInstance(new FakeAllowSubmissionAction))
  private val mutableFakeDataRetrievalAction: MutableFakeDataRetrievalAction = new MutableFakeDataRetrievalAction()
  private val application: Application = applicationBuilderMutableRetrievalAction(mutableFakeDataRetrievalAction, extraModules).build()

  private val templateToBeRendered = "declaration.njk"
  private def httpPathGET: String = controllers.routes.DeclarationController.onPageLoad(srn).url
  private def httpPathOnSubmit: String = controllers.routes.DeclarationController.onSubmit(srn).url

  private val jsonToPassToTemplate = Json.obj(
    fields = "viewModel" -> GenericViewModel(
      submitUrl = routes.DeclarationController.onSubmit(srn).url,
      returnUrl = dummyCall.url,
      schemeName = schemeName)
  )

  override def beforeEach: Unit = {
    super.beforeEach
    when(mockRenderer.render(any(), any())(any())).thenReturn(Future.successful(Html("")))
    when(mockAppConfig.managePensionsSchemeSummaryUrl).thenReturn(dummyCall.url)
  }

  private val userAnswers: Option[UserAnswers] = Some(userAnswersWithSchemeName)

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

    "Save data to user answers, file AFT Return and redirect to next page when valid data is submitted" in {
      mutableFakeDataRetrievalAction.setDataToReturn(userAnswers.map(_.set(PSTRQuery, pstr).getOrElse(UserAnswers())))

      when(mockUserAnswersCacheConnector.save(any(), any())(any(), any())).thenReturn(Future.successful(Json.obj()))
      when(mockCompoundNavigator.nextPage(Matchers.eq(DeclarationPage), any(), any(), any())).thenReturn(dummyCall)
      when(mockAFTService.fileAFTReturn(any(), any())(any(), any(), any())).thenReturn(Future.successful(()))

      val result = route(application, httpGETRequest(httpPathOnSubmit)).value

      status(result) mustEqual SEE_OTHER

      verify(mockAFTService, times(1)).fileAFTReturn(any(), any())(any(), any(), any())
      verify(mockUserAnswersCacheConnector, times(1)).save(any(), any())(any(), any())

      redirectLocation(result) mustBe Some(dummyCall.url)
    }
  }
}
