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

import controllers.actions.DataSetupAction
import controllers.actions.MutableFakeDataRetrievalAction
import controllers.actions.MutableFakeDataSetupAction
import controllers.base.ControllerSpecBase
import data.SampleData
import data.SampleData._
import forms.EnterPsaIdFormProvider
import matchers.JsonMatchers
import models.ChargeType.ChargeTypeAnnualAllowance
import models.ChargeType
import models.Enumerable
import models.GenericViewModel
import models.LocalDateBinder._
import models.NormalMode
import models.UserAnswers
import org.mockito.ArgumentCaptor
import org.mockito.Matchers
import org.mockito.Matchers.any
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import pages.ChargeTypePage
import play.api.Application
import play.api.data.Form
import play.api.inject.bind
import play.api.inject.guice.GuiceableModule
import play.api.libs.json.JsObject
import play.api.libs.json.Json
import play.api.mvc.Results
import play.api.test.Helpers.route
import play.api.test.Helpers.status
import play.api.test.Helpers._
import play.twirl.api.Html
import services.AFTService
import uk.gov.hmrc.viewmodels.NunjucksSupport
import utils.AFTConstants.QUARTER_START_DATE

import scala.concurrent.Future

class EnterPsaIdControllerSpec extends ControllerSpecBase with NunjucksSupport with JsonMatchers
  with BeforeAndAfterEach with Enumerable.Implicits with Results with ScalaFutures {

  import EnterPsaIdControllerSpec._

  private val mockAFTService = mock[AFTService]
  private val mutableFakeDataRetrievalAction: MutableFakeDataRetrievalAction = new MutableFakeDataRetrievalAction
  private val fakeDataSetupAction: MutableFakeDataSetupAction = new MutableFakeDataSetupAction

  val extraModules: Seq[GuiceableModule] = Seq[GuiceableModule](
    bind[AFTService].toInstance(mockAFTService),
    bind[DataSetupAction].toInstance(fakeDataSetupAction)
  )

  val application: Application = applicationBuilderMutableRetrievalAction(mutableFakeDataRetrievalAction, extraModules).build()

  private val jsonToTemplate: Form[String] => JsObject = form => Json.obj(
    fields = "form" -> form,
    "viewModel" -> GenericViewModel(
      submitUrl = controllers.routes.EnterPsaIdController.onSubmit(NormalMode, srn, startDate, accessType, versionInt).url,
      returnUrl = controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, QUARTER_START_DATE, accessType, versionInt).url,
      schemeName = SampleData.schemeName)
  )

  override def beforeEach: Unit = {
    super.beforeEach
    reset(mockUserAnswersCacheConnector, mockRenderer, mockAFTService, mockAppConfig)
    when(mockUserAnswersCacheConnector.save(any(), any())(any(), any())).thenReturn(Future.successful(Json.obj()))
    when(mockRenderer.render(any(), any())(any())).thenReturn(Future.successful(Html("")))
    when(mockAppConfig.managePensionsSchemeSummaryUrl).thenReturn(dummyCall.url)
  }

  "EnterPsaId Controller" when {
    "on a GET" must {

      "return OK with the correct view and call the aft service" in {
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

      //"return OK and the correct view for a GET when the question has previously been answered" in {
      //  val ua = SampleData.userAnswersWithSchemeName.set(ChargeTypePage, ChargeTypeAnnualAllowance).get
      //
      //  fakeDataSetupAction.setDataToReturn(Some(ua))
      //  val templateCaptor = ArgumentCaptor.forClass(classOf[String])
      //  val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])
      //
      //  val result = route(application, httpGETRequest(httpPathGETVersion)).value
      //
      //  status(result) mustEqual OK
      //
      //  verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())
      //
      //  templateCaptor.getValue mustEqual template
      //  jsonCaptor.getValue must containJson(jsonToTemplate.apply(form.fill(ChargeTypeAnnualAllowance)))
      //}

    }

    //"on a POST" must {
    //  "Save data to user answers and redirect to next page when valid data is submitted" in {
    //    mutableFakeDataRetrievalAction.setDataToReturn(Some(userAnswersWithSchemeName))
    //    val expectedJson = Json.obj(ChargeTypePage.toString -> Json.toJson(ChargeTypeAnnualAllowance)(writes(ChargeType.enumerable)))
    //
    //    when(mockCompoundNavigator.nextPage(Matchers.eq(ChargeTypePage), any(), any(), any(), any(), any(), any())(any())).thenReturn(SampleData.dummyCall)
    //
    //    val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])
    //
    //    val result = route(application, httpPOSTRequest(httpPathPOST, valuesValid)).value
    //
    //    status(result) mustEqual SEE_OTHER
    //
    //    verify(mockUserAnswersCacheConnector, times(1)).save(any(), jsonCaptor.capture)(any(), any())
    //
    //    jsonCaptor.getValue must containJson(expectedJson)
    //
    //    redirectLocation(result) mustBe Some(SampleData.dummyCall.url)
    //
    //  }
    //
    //  "return a BAD REQUEST when invalid data is submitted" in {
    //    val application = applicationBuilder(userAnswers = userAnswers).build()
    //
    //    val result = route(application, httpPOSTRequest(httpPathPOST, valuesInvalid)).value
    //
    //    status(result) mustEqual BAD_REQUEST
    //
    //    verify(mockUserAnswersCacheConnector, times(0)).save(any(), any())(any(), any())
    //
    //  }
    //
    //  "redirect to Session Expired page for a POST when there is no data" in {
    //    val application = applicationBuilder(userAnswers = None).build()
    //
    //    val result = route(application, httpPOSTRequest(httpPathPOST, valuesValid)).value
    //
    //    status(result) mustEqual SEE_OTHER
    //    redirectLocation(result).value mustBe controllers.routes.SessionExpiredController.onPageLoad().url
    //  }
    //}
  }
}

object EnterPsaIdControllerSpec {
  private val template = "enterPsaId.njk"

  private def form = new EnterPsaIdFormProvider()()

  private def httpPathGETVersion: String = controllers.routes.EnterPsaIdController.onPageLoad(NormalMode, srn, startDate, accessType, versionInt).url

  private def httpPathPOST: String = controllers.routes.EnterPsaIdController.onSubmit(NormalMode, srn, startDate, accessType, versionInt).url

  private val valuesValid: Map[String, Seq[String]] = Map(
    "value" -> Seq("A1111111")
  )
  private val valuesInvalid: Map[String, Seq[String]] = Map(
    "value" -> Seq("Unknown Charge")
  )
  private val userAnswers: Option[UserAnswers] = Some(SampleData.userAnswersWithSchemeName)
}
