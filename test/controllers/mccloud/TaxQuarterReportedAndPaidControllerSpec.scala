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

package controllers.mccloud

import config.FrontendAppConfig
import controllers.actions.MutableFakeDataRetrievalAction
import controllers.base.ControllerSpecBase
import data.SampleData._
import forms.QuartersFormProvider
import matchers.JsonMatchers
import models.ChargeType.ChargeTypeAnnualAllowance
import models.LocalDateBinder._
import models.requests.DataRequest
import models.{AFTQuarter, ChargeType, Enumerable, GenericViewModel, NormalMode, Quarters, SchemeDetails, SchemeStatus, UserAnswers, YearRange}
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, times, verify, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import pages.mccloud.TaxYearReportedAndPaidPage
import play.api.Application
import play.api.data.Form
import play.api.inject.bind
import play.api.inject.guice.GuiceableModule
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.{Call, Results}
import play.api.test.Helpers.{route, status, _}
import play.twirl.api.Html
import services.SchemeService
import uk.gov.hmrc.viewmodels.NunjucksSupport
import utils.DateHelper

import java.time.LocalDate
import scala.concurrent.Future

class TaxQuarterReportedAndPaidControllerSpec extends ControllerSpecBase with NunjucksSupport with JsonMatchers
  with BeforeAndAfterEach with Enumerable.Implicits with Results with ScalaFutures {

  implicit val config: FrontendAppConfig = mockAppConfig
  val mockSchemeService: SchemeService = mock[SchemeService]
  val extraModules: Seq[GuiceableModule] = Seq[GuiceableModule](
    bind[SchemeService].toInstance(mockSchemeService)
  )

  private val mutableFakeDataRetrievalAction: MutableFakeDataRetrievalAction = new MutableFakeDataRetrievalAction()
  private val application: Application = applicationBuilderMutableRetrievalAction(mutableFakeDataRetrievalAction, extraModules).build()
  private val testYear = 2020
  private val errorKey = "taxQuarterReportedAndPaid.error.required"
  val templateToBeRendered = "mccloud/taxQuarterReportedAndPaid.njk"
  val formProvider = new QuartersFormProvider()
  val availableQuarters: Seq[AFTQuarter] = Seq(q22020, q32020, q42020, q12021)
  val form: Form[AFTQuarter] = formProvider(messages(errorKey, testYear), availableQuarters)

  private def httpPathGET: String = routes.TaxQuarterReportedAndPaidController
    .onPageLoadWithIndex(ChargeTypeAnnualAllowance, NormalMode, srn, startDate, accessType, versionInt, 0, schemeIndex).url

  private def httpPathPOST: String = routes.TaxQuarterReportedAndPaidController
    .onSubmitWithIndex(ChargeTypeAnnualAllowance, NormalMode, srn, startDate, accessType, versionInt, 0, schemeIndex).url

  private val jsonToPassToTemplate: Form[AFTQuarter] => JsObject = form => Json.obj(
    "form" -> form,
    "radios" -> Quarters.radios(form, xx),
    "viewModel" -> GenericViewModel(
      submitUrl = routes.TaxQuarterReportedAndPaidController
        .onSubmitWithIndex(ChargeTypeAnnualAllowance, NormalMode, srn, startDate, accessType, versionInt, 0, schemeIndex).url,
      returnUrl = dummyCall.url,
      schemeName = schemeName),
    "year" -> (testYear.toString + "-" + (testYear + 1).toString)
  )


  private val valuesValid: Map[String, Seq[String]] = Map("value" -> Seq(q12020.toString))

  private val valuesInvalid: Map[String, Seq[String]] = Map("year" -> Seq("q5"))
  private def onwardRoute = Call("GET", "/foo")
  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockUserAnswersCacheConnector)
    reset(mockRenderer)
    reset(mockAppConfig)
    reset(mockSchemeService)
    when(mockRenderer.render(any(), any())(any())).thenReturn(Future.successful(Html("")))
    when(mockAppConfig.schemeDashboardUrl(any(): DataRequest[_])).thenReturn(dummyCall.url)
    when(mockSchemeService.retrieveSchemeDetails(any(), any(), any())(any(), any()))
      .thenReturn(Future.successful(SchemeDetails("Big Scheme", "pstr", SchemeStatus.Open.toString, None)))
    when(mockUserAnswersCacheConnector.savePartial(any(), any(), any(), any())(any(), any())).thenReturn(Future.successful(Json.obj()))
    DateHelper.setDate(Some(LocalDate.of(2022, 1, 1)))
  }

  private val userAnswers: Option[UserAnswers] = Some(
    userAnswersWithSchemeName
      .setOrException(TaxYearReportedAndPaidPage(ChargeType.ChargeTypeAnnualAllowance, 0, Some(0)), YearRange("2020"))
  )

  "TaxQuarterReportedAndPaid Controller" must {

    "return OK and the correct view for a GET" in {
      mutableFakeDataRetrievalAction.setDataToReturn(userAnswers)
      val templateCaptor = ArgumentCaptor.forClass(classOf[String])
      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

      val result = route(application, httpGETRequest(httpPathGET)).value

      status(result) mustEqual OK

      verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

      templateCaptor.getValue mustEqual templateToBeRendered

      jsonCaptor.getValue must containJson(jsonToPassToTemplate.apply(form))
    }

    "redirect to next page when valid data is submitted" in {
      when(mockCompoundNavigator.nextPage(any(), any(), any(), any(), any(), any(), any())(any())).thenReturn(onwardRoute)
      val result = route(application, httpPOSTRequest(httpPathPOST, valuesValid)).value

      status(result) mustEqual SEE_OTHER

      redirectLocation(result) mustBe Some(onwardRoute.url)
    }

    "return a BAD REQUEST when invalid data is submitted" in {

      val result = route(application, httpPOSTRequest(httpPathPOST, valuesInvalid)).value

      status(result) mustEqual BAD_REQUEST

      verify(mockUserAnswersCacheConnector, times(0)).save(any(), any())(any(), any())
    }
  }
}

