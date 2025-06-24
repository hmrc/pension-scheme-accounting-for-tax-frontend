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

package controllers.amend

import config.FrontendAppConfig
import connectors.AFTConnector
import controllers.base.ControllerSpecBase
import data.SampleData._
import forms.QuartersFormProvider
import matchers.JsonMatchers
import models.requests.IdentifierRequest
import models.{AFTQuarter, DisplayQuarter, Enumerable, Quarters, SchemeDetails, SchemeStatus}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{times, verify, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import play.api.Application
import play.api.data.Form
import play.api.inject.bind
import play.api.inject.guice.GuiceableModule
import play.api.mvc.Results
import play.api.test.Helpers._
import services.{QuartersService, SchemeService}
import views.html.amend.AmendQuartersView

import scala.concurrent.Future

class AmendQuartersControllerSpec extends ControllerSpecBase with JsonMatchers
  with BeforeAndAfterEach with Enumerable.Implicits with Results with ScalaFutures {

  implicit val config: FrontendAppConfig = mockAppConfig
  private val mockSchemeService: SchemeService = mock[SchemeService]
  private val mockAFTConnector: AFTConnector = mock[AFTConnector]
  private val mockQuartersService: QuartersService = mock[QuartersService]

  val extraModules: Seq[GuiceableModule] = Seq[GuiceableModule](
    bind[SchemeService].toInstance(mockSchemeService),
    bind[QuartersService].toInstance(mockQuartersService),
    bind[AFTConnector].toInstance(mockAFTConnector)
  )
  private val application: Application = applicationBuilder(extraModules = extraModules).build()
  val templateToBeRendered = "amend/amendQuarters.njk"
  private val errorKey = "quarters.error.required"
  private val year = "2022"
  val quarters: Seq[AFTQuarter] = Seq(q22020, q32020, q42020)
  val displayQuarters: Seq[DisplayQuarter] = Seq(displayQuarterLocked, displayQuarterContinueAmend, displayQuarterViewPast)

  val formProvider = new QuartersFormProvider()

  def form(quarters: Seq[AFTQuarter]): Form[AFTQuarter] = formProvider(errorKey, quarters)

  lazy val httpPathGET: String = controllers.amend.routes.AmendQuartersController.onPageLoad(srn, year).url
  lazy val httpPathPOST: String = controllers.amend.routes.AmendQuartersController.onSubmit(srn, year).url
  private val submitCall = controllers.amend.routes.AmendQuartersController.onSubmit(srn, year)

  private val valuesValid: Map[String, Seq[String]] = Map("value" -> Seq(q22020.toString))
  private val valuesInvalid: Map[String, Seq[String]] = Map("year" -> Seq("20"))

  override def beforeEach(): Unit = {
    super.beforeEach()
    when(mockAFTConnector.getAftOverview(any(), any(), any(),  any(), any())(any(), any()))
      .thenReturn(Future.successful(Seq(aftOverviewQ22020, aftOverviewQ32020, aftOverviewQ42020)))
    when(mockQuartersService.getPastQuarters(any(), any(), any(), any())(any(), any())).thenReturn(Future.successful(displayQuarters))
    when(mockAppConfig.schemeDashboardUrl(any(): IdentifierRequest[?])).thenReturn(dummyCall.url)
    when(mockSchemeService.retrieveSchemeDetails(any(), any())(any(), any()))
      .thenReturn(Future.successful(SchemeDetails("Big Scheme", "pstr", SchemeStatus.Open.toString, None)))
  }

  "AmendQuarters Controller" must {
    "return OK and the correct view for a GET" in {
      val result = route(application, httpGETRequest(httpPathGET)).value

      status(result) mustEqual OK

      val quartersForm = form(quarters)

      val view = application.injector.instanceOf[AmendQuartersView].apply(
        quartersForm,
        Quarters.radios(quartersForm, displayQuarters),
        submitCall,
        dummyCall.url,
        schemeName
      )(httpGETRequest(httpPathGET), messages)

      compareResultAndView(result, view)
    }

    "redirect to return history page when only one quarter for a GET" in {
      val displayQuarters: Seq[DisplayQuarter] = Seq(displayQuarterViewPast)
      when(mockQuartersService.getPastQuarters(any(), any(), any(), any())(any(), any())).thenReturn(Future.successful(displayQuarters))

      val result = route(application, httpGETRequest(httpPathGET)).value

      status(result) mustEqual SEE_OTHER
      redirectLocation(result) mustBe Some(controllers.amend.routes.ReturnHistoryController
        .onPageLoad(srn, displayQuarterViewPast.quarter.startDate.toString).url)
    }

    "redirect to session expired page when quarters service returns an empty list" in {
      when(mockQuartersService.getPastQuarters(any(), any(), any(), any())(any(), any())).thenReturn(Future.successful(Nil))
      val result = route(application, httpGETRequest(httpPathGET)).value

      status(result) mustEqual SEE_OTHER
      redirectLocation(result).value mustBe controllers.routes.SessionExpiredController.onPageLoad.url
    }

    "redirect to next page when valid data is submitted" in {

      val result = route(application, httpPOSTRequest(httpPathPOST, valuesValid)).value

      status(result) mustEqual SEE_OTHER

      redirectLocation(result) mustBe Some(controllers.amend.routes.ReturnHistoryController.onPageLoad(srn, q22020.startDate.toString).url)
    }

    "return a BAD REQUEST when invalid data is submitted" in {

      val result = route(application, httpPOSTRequest(httpPathPOST, valuesInvalid)).value

      status(result) mustEqual BAD_REQUEST


      verify(mockUserAnswersCacheConnector, times(0)).save(any(), any())(any(), any())
    }

    "redirect to session expired page when there quarters service returns an empty list for a POST" in {
      when(mockQuartersService.getPastQuarters(any(), any(), any(), any())(any(), any())).thenReturn(Future.successful(Nil))
      val result = route(application, httpPOSTRequest(httpPathPOST, valuesValid)).value

      status(result) mustEqual SEE_OTHER

      redirectLocation(result).value mustBe controllers.routes.SessionExpiredController.onPageLoad.url
    }
  }
}

