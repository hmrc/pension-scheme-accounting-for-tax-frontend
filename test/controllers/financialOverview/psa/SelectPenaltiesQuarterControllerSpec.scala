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

package controllers.financialOverview.psa

import config.FrontendAppConfig
import controllers.actions.MutableFakeDataRetrievalAction
import controllers.base.ControllerSpecBase
import controllers.financialOverview.psa.SelectSchemeControllerSpec.{penaltySchemes, pstr}
import data.SampleData
import data.SampleData.{dummyCall, psaId, q32020, q42020}
import forms.QuartersFormProvider
import matchers.JsonMatchers
import models.requests.IdentifierRequest
import models.{AFTQuarter, DisplayQuarter, Enumerable, PaymentOverdue, Quarters}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{times, verify, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import play.api.Application
import play.api.data.Form
import play.api.http.Status.{BAD_REQUEST, OK, SEE_OTHER}
import play.api.inject.bind
import play.api.inject.guice.GuiceableModule
import play.api.libs.json.Json
import play.api.mvc.Results
import play.api.test.Helpers.{defaultAwaitTimeout, redirectLocation, route, status, writeableOf_AnyContentAsEmpty, writeableOf_AnyContentAsFormUrlEncoded}
import services.PenaltiesServiceSpec.penaltiesCache
import services.financialOverview.psa.{PenaltiesCache, PenaltiesNavigationService, PsaPenaltiesAndChargesService}
import views.html.financialOverview.psa.SelectQuarterView

import scala.concurrent.Future

class SelectPenaltiesQuarterControllerSpec extends ControllerSpecBase with JsonMatchers
  with BeforeAndAfterEach with Enumerable.Implicits with Results with ScalaFutures {

  implicit val config: FrontendAppConfig = mockAppConfig
  private val mockPsaPenaltiesAndChargesService = mock[PsaPenaltiesAndChargesService]
  private val mockNavigationService = mock[PenaltiesNavigationService]
  val extraModules: Seq[GuiceableModule] = Seq[GuiceableModule](
    bind[PsaPenaltiesAndChargesService].toInstance(mockPsaPenaltiesAndChargesService),
    bind[PenaltiesNavigationService].toInstance(mockNavigationService)
  )

  private val year = "2020"

  private val quarters: Seq[AFTQuarter] = Seq(q32020, q42020)
  private val displayQuarters: Seq[DisplayQuarter] = Seq(
    DisplayQuarter(q32020, displayYear = false, None, Some(PaymentOverdue)),
    DisplayQuarter(q42020, displayYear = false, None, Some(PaymentOverdue))
  )

  private val mutableFakeDataRetrievalAction: MutableFakeDataRetrievalAction = new MutableFakeDataRetrievalAction()
  private val application: Application = applicationBuilderMutableRetrievalAction(mutableFakeDataRetrievalAction, extraModules).build()
  val formProvider = new QuartersFormProvider()
  val form: Form[AFTQuarter] = formProvider("selectPenaltiesQuarter.error", quarters)

  lazy val httpPathGET: String = routes.SelectPenaltiesQuarterController.onPageLoad(year).url
  lazy val httpPathPOST: String = routes.SelectPenaltiesQuarterController.onSubmit(year).url

  private val submitCall = controllers.financialOverview.psa.routes.SelectPenaltiesQuarterController.onSubmit(year)

  private val valuesValid: Map[String, Seq[String]] = Map("value" -> Seq(q32020.toString))
  private val valuesInvalid: Map[String, Seq[String]] = Map("year" -> Seq("20"))

  override def beforeEach(): Unit = {
    super.beforeEach()
    when(mockUserAnswersCacheConnector.save(any(), any())(any(), any())).thenReturn(Future.successful(Json.obj()))
    when(mockAppConfig.schemeDashboardUrl(any(): IdentifierRequest[_])).thenReturn(dummyCall.url)
    when(mockPsaPenaltiesAndChargesService.isPaymentOverdue).thenReturn(_ => true)
    when(mockPsaPenaltiesAndChargesService.getPenaltiesForJourney(any(), any())(any(), any())).
      thenReturn(Future.successful(PenaltiesCache(psaId, "psa-name", SampleData.psaFsSeq)))
  }

  "SelectPenaltiesQuarter Controller" must {
    "return OK and the correct view for a GET" in {

      val request = httpGETRequest(httpPathGET)
      val result = route(application, httpGETRequest(httpPathGET)).value

      status(result) mustEqual OK

      val view = application.injector.instanceOf[SelectQuarterView].apply(
        form = form,
        submitCall = submitCall,
        psaName = penaltiesCache.psaName,
        returnUrl = mockAppConfig.managePensionsSchemeOverviewUrl,
        radios = Quarters.radios(
            form,
            displayQuarters,
            Seq("govuk-tag govuk-tag--red govuk-!-display-inline-block"),
            areLabelsBold = false
          ),
        year
      )(request, messages)

      compareResultAndView(result, view)
    }

    "return a BAD REQUEST when invalid data is submitted" in {

      val result = route(application, httpPOSTRequest(httpPathPOST, valuesInvalid)).value

      status(result) mustEqual BAD_REQUEST

      verify(mockUserAnswersCacheConnector, times(0)).save(any(), any())(any(), any())

    }
  }

  "redirect to next page when valid data is submitted" in {
    when(mockNavigationService.penaltySchemes(any(): Int, any(), any(), any())(any(), any())).
      thenReturn(Future.successful(penaltySchemes))
    when(mockNavigationService.navFromQuartersPage(any(), any(), any())(any(), any()))
      .thenReturn(Future.successful(Redirect(routes.AllPenaltiesAndChargesController.onPageLoadAFT(q32020.startDate.toString, pstr))))

    val result = route(application, httpPOSTRequest(httpPathPOST, valuesValid)).value
    status(result) mustEqual SEE_OTHER
    redirectLocation(result) mustBe Some(routes.AllPenaltiesAndChargesController.onPageLoadAFT(q32020.startDate.toString, pstr).url)
  }

}
