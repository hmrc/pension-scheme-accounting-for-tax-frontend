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

import controllers.actions.{DataSetupAction, MutableFakeDataRetrievalAction, MutableFakeDataSetupAction}
import controllers.base.ControllerSpecBase
import data.SampleData
import data.SampleData._
import forms.ChargeTypeFormProvider
import matchers.JsonMatchers
import models.ChargeType.ChargeTypeAnnualAllowance
import models.LocalDateBinder._
import models.{ChargeType, Enumerable, UserAnswers}
import models.requests.IdentifierRequest
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, times, verify, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import pages.ChargeTypePage
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceableModule
import play.api.libs.json.Json
import play.api.mvc.Results
import play.api.test.Helpers._
import services.{AFTService, SchemeService}
import utils.AFTConstants.QUARTER_START_DATE
import views.html.ChargeTypeView

import scala.concurrent.Future

class ChargeTypeControllerSpec extends ControllerSpecBase with JsonMatchers
  with BeforeAndAfterEach with Enumerable.Implicits with Results with ScalaFutures {

  import ChargeTypeControllerSpec._

  private val mockAFTService = mock[AFTService]
  private val mutableFakeDataRetrievalAction: MutableFakeDataRetrievalAction = new MutableFakeDataRetrievalAction
  private val fakeDataSetupAction: MutableFakeDataSetupAction = new MutableFakeDataSetupAction
  private val mockSchemeService = mock[SchemeService]

  val extraModules: Seq[GuiceableModule] = Seq[GuiceableModule](
    bind[AFTService].toInstance(mockAFTService),
    bind[DataSetupAction].toInstance(fakeDataSetupAction),
    bind[SchemeService].toInstance(mockSchemeService)
  )

  val application: Application = applicationBuilderMutableRetrievalAction(mutableFakeDataRetrievalAction, extraModules).build()


  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockUserAnswersCacheConnector)
    reset(mockAFTService)
    when(mockUserAnswersCacheConnector.save(any(), any())(any(), any())).thenReturn(Future.successful(Json.obj()))
    when(mockAppConfig.schemeDashboardUrl(any(): IdentifierRequest[_])).thenReturn(dummyCall.url)
    when(mockSchemeService.retrieveSchemeDetails(any(), any(), any())(any(), any())).thenReturn(Future.successful(schemeDetails))
  }

  "ChargeType Controller" when {
    "on a GET" must {

      "return OK with the correct view and call the aft service" in {
        fakeDataSetupAction.setDataToReturn(Some(userAnswersWithSchemeName))
        fakeDataSetupAction.setSessionData(SampleData.sessionData())

        val result = route(application, httpGETRequest(httpPathGETVersion)).value

        status(result) mustEqual OK

        val view = application.injector.instanceOf[ChargeTypeView].apply(
          form,
          ChargeType.radios(form),
          controllers.routes.ChargeTypeController.onSubmit(srn, startDate, accessType, versionInt),
          controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, QUARTER_START_DATE, accessType, versionInt).url,
          SampleData.schemeName
        )(httpGETRequest(httpPathGETVersion), messages)

        compareResultAndView(result,  view)
      }

      "return OK and the correct view for a GET when the question has previously been answered" in {
        val ua = SampleData.userAnswersWithSchemeName.set(ChargeTypePage, ChargeTypeAnnualAllowance).get

        fakeDataSetupAction.setDataToReturn(Some(ua))

        val result = route(application, httpGETRequest(httpPathGETVersion)).value

        status(result) mustEqual OK

        val view = application.injector.instanceOf[ChargeTypeView].apply(
          form.fill(ChargeTypeAnnualAllowance),
          ChargeType.radios(form.fill(ChargeTypeAnnualAllowance)),
          controllers.routes.ChargeTypeController.onSubmit(srn, startDate, accessType, versionInt),
          controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, QUARTER_START_DATE, accessType, versionInt).url,
          SampleData.schemeName
        )(httpGETRequest(httpPathGETVersion), messages)

        compareResultAndView(result,  view)
      }

    }

    "on a POST" must {
      "Save data to user answers and redirect to next page when valid data is submitted" in {
        mutableFakeDataRetrievalAction.setDataToReturn(Some(userAnswersWithSchemeName))

        when(mockCompoundNavigator.nextPage(ArgumentMatchers.eq(ChargeTypePage), any(), any(), any(), any(), any(), any())(any()))
          .thenReturn(SampleData.dummyCall)

        val result = route(application, httpPOSTRequest(httpPathPOST, valuesValid)).value

        status(result) mustEqual SEE_OTHER

        redirectLocation(result) mustBe Some(SampleData.dummyCall.url)
      }

      "return a BAD REQUEST when invalid data is submitted" in {
        val application = applicationBuilder(userAnswers = userAnswers).build()

        val result = route(application, httpPOSTRequest(httpPathPOST, valuesInvalid)).value

        status(result) mustEqual BAD_REQUEST

        verify(mockUserAnswersCacheConnector, times(0)).save(any(), any())(any(), any())

      }

      "redirect to Session Expired page for a POST when there is no data" in {
        val application = applicationBuilder(userAnswers = None).build()

        val result = route(application, httpPOSTRequest(httpPathPOST, valuesValid)).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustBe controllers.routes.SessionExpiredController.onPageLoad.url
      }
    }
  }
}

object ChargeTypeControllerSpec {

  private def form = new ChargeTypeFormProvider()()

  private def httpPathGETVersion: String = controllers.routes.ChargeTypeController.onPageLoad(srn, startDate, accessType, versionInt).url

  private def httpPathPOST: String = controllers.routes.ChargeTypeController.onSubmit(srn, startDate, accessType, versionInt).url

  private val valuesValid: Map[String, Seq[String]] = Map(
    "value" -> Seq(ChargeTypeAnnualAllowance.toString)
  )
  private val valuesInvalid: Map[String, Seq[String]] = Map(
    "value" -> Seq("Unknown Charge")
  )
  private val userAnswers: Option[UserAnswers] = Some(SampleData.userAnswersWithSchemeName)
}
