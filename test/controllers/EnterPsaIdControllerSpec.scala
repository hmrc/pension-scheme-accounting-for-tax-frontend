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
import models.{Enumerable, SchemeDetails, SchemeStatus, UserAnswers}
import navigators.CompoundNavigator
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, times, verify, when}
import org.mockito.ArgumentMatchers
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import pages.EnterPsaIdPage
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceableModule
import play.api.libs.json.Json
import play.api.mvc.Results
import play.api.test.Helpers.{route, status, _}
import services.AFTService
import utils.AFTConstants.QUARTER_START_DATE
import views.html.EnterPsaIdView

import scala.concurrent.Future

class EnterPsaIdControllerSpec extends ControllerSpecBase with JsonMatchers
  with BeforeAndAfterEach with Enumerable.Implicits with Results with ScalaFutures {

  import EnterPsaIdControllerSpec._

  private val mockAFTService = mock[AFTService]
  private val mutableFakeDataRetrievalAction: MutableFakeDataRetrievalAction = new MutableFakeDataRetrievalAction
  private val fakeDataSetupAction: MutableFakeDataSetupAction = new MutableFakeDataSetupAction
  private val mockSchemeDetailsConnector = mock[SchemeDetailsConnector]

  override def modules: Seq[GuiceableModule] = Seq(
    bind[DataRequiredAction].to[DataRequiredActionImpl],
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

  private def schemeDetails(authorisingPsaId: Option[String]) = SchemeDetails(schemeName, pstr, SchemeStatus.Open.toString, authorisingPsaId)

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockUserAnswersCacheConnector)
    reset(mockAFTService)
    reset(mockSchemeDetailsConnector)
    when(mockUserAnswersCacheConnector.savePartial(any(), any(), any(), any())(any(), any())).thenReturn(Future.successful(Json.obj()))
    when(mockSchemeDetailsConnector.getPspSchemeDetails(ArgumentMatchers.eq(pspId), any())(any(), any()))
      .thenReturn(Future.successful(schemeDetails(Some(psaId))))
  }

  "EnterPsaId Controller" when {
    "on a GET" must {

      "return OK with the correct view" in {
        mutableFakeDataRetrievalAction.setDataToReturn(Some(userAnswersWithSchemeName))
        mutableFakeDataRetrievalAction.setSessionData(SampleData.sessionData())

        val result = route(application, httpGETRequest(httpPathGETVersion)).value

        status(result) mustEqual OK

        val view = application.injector.instanceOf[EnterPsaIdView].apply(
          form,
          controllers.routes.EnterPsaIdController.onSubmit(srn, startDate, accessType, versionInt),
          controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, QUARTER_START_DATE, accessType, versionInt).url,
          schemeName
        )(httpGETRequest(httpPathGETVersion), messages)

        compareResultAndView(result, view)
      }

      "return OK and the correct view for a GET when the question has previously been answered" in {
        val ua = SampleData.userAnswersWithSchemeName.set(EnterPsaIdPage, psaId).get
        mutableFakeDataRetrievalAction.setDataToReturn(Some(ua))
        mutableFakeDataRetrievalAction.setSessionData(SampleData.sessionData())

        val result = route(application, httpGETRequest(httpPathGETVersion)).value

        status(result) mustEqual OK

        val view = application.injector.instanceOf[EnterPsaIdView].apply(
          form.fill(psaId),
          controllers.routes.EnterPsaIdController.onSubmit(srn, startDate, accessType, versionInt),
          controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, QUARTER_START_DATE, accessType, versionInt).url,
          schemeName
        )(httpGETRequest(httpPathGETVersion), messages)

        compareResultAndView(result, view)
      }
    }

    "on a POST" must {
      "for a logged-in PSP save data to user answers, call psp get scheme details and redirect to next page when valid data is submitted" in {
        mutableFakeDataRetrievalAction.setDataToReturn(Some(userAnswersWithSchemeName))

        when(mockCompoundNavigator.nextPage(ArgumentMatchers.eq(EnterPsaIdPage), any(), any(), any(), any(), any(), any())(any()))
          .thenReturn(SampleData.dummyCall)

        val result = route(application, httpPOSTRequest(httpPathPOST, valuesValid)).value

        status(result) mustEqual SEE_OTHER

        verify(mockSchemeDetailsConnector, times(1)).getPspSchemeDetails(any(), any())(any(), any())

        redirectLocation(result) mustBe Some(SampleData.dummyCall.url)
      }

      "return a BAD REQUEST when invalid data is submitted" in {

        val result = route(application, httpPOSTRequest(httpPathPOST, valuesInvalid)).value

        status(result) mustEqual BAD_REQUEST

        verify(mockUserAnswersCacheConnector, times(0)).savePartial(any(), any(), any(), any())(any(), any())

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
