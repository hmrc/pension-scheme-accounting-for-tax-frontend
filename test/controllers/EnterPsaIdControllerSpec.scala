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

import config.FrontendAppConfig
import connectors.SchemeDetailsConnector
import connectors.cache.UserAnswersCacheConnector
import controllers.actions._
import controllers.base.ControllerSpecBase
import data.SampleData
import data.SampleData._
import forms.EnterPsaIdFormProvider
import matchers.JsonMatchers
import models.LocalDateBinder._
import models.{Enumerable, GenericViewModel, SchemeDetails, SchemeStatus, UserAnswers}
import navigators.CompoundNavigator
import org.mockito.ArgumentMatchers.any
import org.mockito.{ArgumentCaptor, ArgumentMatchers}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import pages.EnterPsaIdPage
import play.api.Application
import play.api.data.Form
import play.api.inject.bind
import play.api.inject.guice.GuiceableModule
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.Results
import play.api.test.Helpers.{route, status, _}
import play.twirl.api.Html
import services.AFTService
import uk.gov.hmrc.nunjucks.NunjucksRenderer
import uk.gov.hmrc.viewmodels.NunjucksSupport
import utils.AFTConstants.QUARTER_START_DATE

import scala.concurrent.Future

class EnterPsaIdControllerSpec extends ControllerSpecBase with NunjucksSupport with JsonMatchers
  with BeforeAndAfterEach with Enumerable.Implicits with Results with ScalaFutures {

  import EnterPsaIdControllerSpec._

  private val mockAFTService = mock[AFTService]
  private val mutableFakeDataRetrievalAction: MutableFakeDataRetrievalAction = new MutableFakeDataRetrievalAction
  private val fakeDataSetupAction: MutableFakeDataSetupAction = new MutableFakeDataSetupAction
  private val mockSchemeDetailsConnector = mock[SchemeDetailsConnector]

  override def modules: Seq[GuiceableModule] = Seq(
    bind[DataRequiredAction].to[DataRequiredActionImpl],
    bind[NunjucksRenderer].toInstance(mockRenderer),
    bind[FrontendAppConfig].toInstance(mockAppConfig),
    bind[UserAnswersCacheConnector].toInstance(mockUserAnswersCacheConnector),
    bind[CompoundNavigator].toInstance(mockCompoundNavigator),
    bind[AllowAccessActionProvider].toInstance(mockAllowAccessActionProvider)
  )

  val extraModules: Seq[GuiceableModule] = Seq[GuiceableModule](
    bind[AFTService].toInstance(mockAFTService),
    bind[DataSetupAction].toInstance(fakeDataSetupAction),
    bind[SchemeDetailsConnector].toInstance(mockSchemeDetailsConnector),
    bind[IdentifierAction].to[FakeIdentifierActionPSP]
  )

  val application: Application = applicationBuilderMutableRetrievalAction(mutableFakeDataRetrievalAction, extraModules).build()

  private val jsonToTemplate: Form[String] => JsObject = form => Json.obj(
    fields = "form" -> form,
    "viewModel" -> GenericViewModel(
      submitUrl = controllers.routes.EnterPsaIdController.onSubmit(srn, startDate, accessType, versionInt).url,
      returnUrl = controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, QUARTER_START_DATE, accessType, versionInt).url,
      schemeName = SampleData.schemeName)
  )

  private def schemeDetails(authorisingPsaId:Option[String]) = SchemeDetails(schemeName, pstr, SchemeStatus.Open.toString, authorisingPsaId)

  override def beforeEach: Unit = {
    super.beforeEach
    reset(mockUserAnswersCacheConnector, mockRenderer, mockAFTService, mockAppConfig, mockSchemeDetailsConnector)
    when(mockUserAnswersCacheConnector.save(any(), any())(any(), any())).thenReturn(Future.successful(Json.obj()))
    when(mockRenderer.render(any(), any())(any())).thenReturn(Future.successful(Html("")))
    when(mockAppConfig.managePensionsSchemeSummaryUrl).thenReturn(dummyCall.url)
    when(mockSchemeDetailsConnector.getPspSchemeDetails(ArgumentMatchers.eq(pspId), any())(any(), any()))
      .thenReturn(Future.successful(schemeDetails(Some(psaId))))
  }

  "EnterPsaId Controller" when {
    "on a GET" must {

      "return OK with the correct view" in {
        mutableFakeDataRetrievalAction.setDataToReturn(Some(userAnswersWithSchemeName))
        mutableFakeDataRetrievalAction.setSessionData(SampleData.sessionData())

        val templateCaptor = ArgumentCaptor.forClass(classOf[String])
        val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

        val result = route(application, httpGETRequest(httpPathGETVersion)).value

        status(result) mustEqual OK

        verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

        templateCaptor.getValue mustEqual template
        jsonCaptor.getValue must containJson(jsonToTemplate.apply(form))
      }

      "return OK and the correct view for a GET when the question has previously been answered" in {

        val ua = SampleData.userAnswersWithSchemeName.set(EnterPsaIdPage, psaId).get
        mutableFakeDataRetrievalAction.setDataToReturn(Some(ua))
        mutableFakeDataRetrievalAction.setSessionData(SampleData.sessionData())

        val templateCaptor = ArgumentCaptor.forClass(classOf[String])
        val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

        val result = route(application, httpGETRequest(httpPathGETVersion)).value

        status(result) mustEqual OK

        verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

        templateCaptor.getValue mustEqual template
        jsonCaptor.getValue must containJson(jsonToTemplate.apply(form.fill(psaId)))
      }
    }

    "on a POST" must {
      "for a logged-in PSP save data to user answers, call psp get scheme details and redirect to next page when valid data is submitted" in {
        mutableFakeDataRetrievalAction.setDataToReturn(Some(userAnswersWithSchemeName))
        val expectedJson = Json.obj(EnterPsaIdPage.toString -> psaId)

        when(mockCompoundNavigator.nextPage(ArgumentMatchers.eq(EnterPsaIdPage), any(), any(), any(), any(), any(), any())(any())).thenReturn(SampleData.dummyCall)

        val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

        val result = route(application, httpPOSTRequest(httpPathPOST, valuesValid)).value

        status(result) mustEqual SEE_OTHER

        verify(mockUserAnswersCacheConnector, times(1)).save(any(), jsonCaptor.capture)(any(), any())
        verify(mockSchemeDetailsConnector, times(1)).getPspSchemeDetails(any(), any())(any(), any())

        jsonCaptor.getValue must containJson(expectedJson)

        redirectLocation(result) mustBe Some(SampleData.dummyCall.url)
      }

      "return a BAD REQUEST when invalid data is submitted" in {

        val result = route(application, httpPOSTRequest(httpPathPOST, valuesInvalid)).value

        status(result) mustEqual BAD_REQUEST

        verify(mockUserAnswersCacheConnector, times(0)).save(any(), any())(any(), any())

      }

      "redirect to Session Expired page for a POST when there is no data" in {
        mutableFakeDataRetrievalAction.setDataToReturn(Some(UserAnswers()))

        val result = route(application, httpPOSTRequest(httpPathPOST, valuesValid)).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustBe controllers.routes.SessionExpiredController.onPageLoad.url
      }
    }
  }
}

object EnterPsaIdControllerSpec {
  private val template = "enterPsaId.njk"

  private def form = new EnterPsaIdFormProvider().apply(authorisingPSAID = None)

  private def httpPathGETVersion: String = controllers.routes.EnterPsaIdController.onPageLoad(srn, startDate, accessType, versionInt).url

  private def httpPathPOST: String = controllers.routes.EnterPsaIdController.onSubmit(srn, startDate, accessType, versionInt).url

  private val valuesValid: Map[String, Seq[String]] = Map(
    "value" -> Seq(psaId)
  )
  private val valuesInvalid: Map[String, Seq[String]] = Map(
    "value" -> Seq("invalid")
  )
}
