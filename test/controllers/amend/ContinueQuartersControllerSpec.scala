/*
 * Copyright 2021 HM Revenue & Customs
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
import models.{DisplayQuarter, Enumerable, GenericViewModel, Quarter, Quarters, SchemeDetails, SchemeStatus}
import org.mockito.ArgumentCaptor
import org.mockito.Matchers.any
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import play.api.Application
import play.api.data.Form
import play.api.inject.bind
import play.api.inject.guice.GuiceableModule
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.Results
import play.api.test.Helpers.{route, status, _}
import play.twirl.api.Html
import services.{QuartersService, SchemeService}
import uk.gov.hmrc.viewmodels.NunjucksSupport

import scala.concurrent.Future

class ContinueQuartersControllerSpec extends ControllerSpecBase with NunjucksSupport with JsonMatchers
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
  val templateToBeRendered = "amend/continueQuarters.njk"
  private val errorKey = "continueQuarters.error.required"
  val quarters: Seq[Quarter] = Seq(q22020, q32020, q42020)
  val displayQuarters: Seq[DisplayQuarter] = Seq(displayQuarterLocked, displayQuarterContinueAmend, displayQuarterViewPast)

  val formProvider = new QuartersFormProvider()
  def form(quarters: Seq[Quarter]): Form[Quarter] = formProvider(errorKey, quarters)

  lazy val httpPathGET: String = controllers.amend.routes.ContinueQuartersController.onPageLoad(srn).url
  lazy val httpPathPOST: String = controllers.amend.routes.ContinueQuartersController.onSubmit(srn).url

  private def jsonToPassToTemplate(quarters: Seq[DisplayQuarter]): Form[Quarter] => JsObject = form => Json.obj(
    "form" -> form,
    "radios" -> Quarters.radios(form, quarters),
    "viewModel" -> GenericViewModel(
      submitUrl = controllers.amend.routes.ContinueQuartersController.onSubmit(srn).url,
      returnUrl = dummyCall.url,
      schemeName = schemeName)
  )

  private val valuesValid: Map[String, Seq[String]] = Map("value" -> Seq(q22020.toString))
  private val valuesInvalid: Map[String, Seq[String]] = Map("year" -> Seq("20"))

  override def beforeEach: Unit = {
    super.beforeEach
    when(mockAFTConnector.getAftOverview(any(), any(), any())(any(), any()))
      .thenReturn(Future.successful(Seq(aftOverviewQ22020, aftOverviewQ32020, aftOverviewQ42020)))
    when(mockQuartersService.getInProgressQuarters(any(), any())(any(), any())).thenReturn(Future.successful(displayQuarters))
    when(mockRenderer.render(any(), any())(any())).thenReturn(Future.successful(Html("")))
    when(mockAppConfig.schemeDashboardUrl(any(): IdentifierRequest[_])).thenReturn(dummyCall.url)
    when(mockSchemeService.retrieveSchemeDetails(any(), any(), any())(any(), any()))
      .thenReturn(Future.successful(SchemeDetails("Big Scheme", "pstr", SchemeStatus.Open.toString, None)))
  }

  "ContinueQuarters Controller" must {
    "return OK and the correct view for a GET" in {

      val templateCaptor = ArgumentCaptor.forClass(classOf[String])
      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

      val result = route(application, httpGETRequest(httpPathGET)).value

      status(result) mustEqual OK

      verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

      templateCaptor.getValue mustEqual templateToBeRendered

      jsonCaptor.getValue must containJson(jsonToPassToTemplate(displayQuarters).apply(form(quarters)))
    }

    "redirect to session expired page when quarters service returns an empty list" in {
      when(mockQuartersService.getInProgressQuarters(any(), any())(any(), any())).thenReturn(Future.successful(Nil))
      val result = route(application, httpGETRequest(httpPathGET)).value

      status(result) mustEqual SEE_OTHER
      redirectLocation(result).value mustBe controllers.routes.SessionExpiredController.onPageLoad().url
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
      when(mockQuartersService.getInProgressQuarters(any(), any())(any(), any())).thenReturn(Future.successful(Nil))
      val result = route(application, httpPOSTRequest(httpPathPOST, valuesValid)).value

      status(result) mustEqual SEE_OTHER

      redirectLocation(result).value mustBe controllers.routes.SessionExpiredController.onPageLoad().url
    }
  }
}

