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

package controllers.amend

import config.FrontendAppConfig
import connectors.AFTConnector
import controllers.base.ControllerSpecBase
import data.SampleData._
import forms.amend.AmendQuartersFormProvider
import matchers.JsonMatchers
import models.AmendQuarters._
import models.{AmendQuarters, Enumerable, GenericViewModel, Quarters, SchemeDetails, SchemeStatus}
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
import services.SchemeService
import uk.gov.hmrc.viewmodels.NunjucksSupport

import scala.concurrent.Future

class AmendQuartersControllerSpec extends ControllerSpecBase with NunjucksSupport with JsonMatchers
  with BeforeAndAfterEach with Enumerable.Implicits with Results with ScalaFutures {

  implicit val config: FrontendAppConfig = mockAppConfig
  private val mockSchemeService: SchemeService = mock[SchemeService]
  private val mockAFTConnector: AFTConnector = mock[AFTConnector]

  val extraModules: Seq[GuiceableModule] = Seq[GuiceableModule](
    bind[SchemeService].toInstance(mockSchemeService),
    bind[AFTConnector].toInstance(mockAFTConnector)
  )
  private val application: Application = applicationBuilder(extraModules = extraModules).build()
  val templateToBeRendered = "amend/amendQuarters.njk"
  private val errorKey = "quarters.error.required"
  private val year = "2022"
  val formProvider = new AmendQuartersFormProvider()
  def form(quarters: Seq[Quarters]): Form[Quarters] = formProvider(errorKey, quarters)

  lazy val httpPathGET: String = controllers.amend.routes.AmendQuartersController.onPageLoad(srn, year).url
  lazy val httpPathPOST: String = controllers.amend.routes.AmendQuartersController.onSubmit(srn, year).url

  private def jsonToPassToTemplate(quarters: Seq[Quarters]): Form[Quarters] => JsObject = form => Json.obj(
    "form" -> form,
    "radios" -> AmendQuarters.radios(form, quarters),
    "viewModel" -> GenericViewModel(
      submitUrl = controllers.amend.routes.AmendQuartersController.onSubmit(srn, year).url,
      returnUrl = dummyCall.url,
      schemeName = schemeName)
  )

  private val quarters = Seq(Q1)
  private val valuesValid: Map[String, Seq[String]] = Map("value" -> Seq("q1"))
  private val valuesInvalid: Map[String, Seq[String]] = Map("year" -> Seq("20"))

  override def beforeEach: Unit = {
    super.beforeEach
    when(mockAFTConnector.getAftOverview(any())(any(), any()))
      .thenReturn(Future.successful(Seq(overview1, overview2, overview3)))
    when(mockRenderer.render(any(), any())(any())).thenReturn(Future.successful(Html("")))
    when(mockAppConfig.managePensionsSchemeSummaryUrl).thenReturn(dummyCall.url)
    when(mockSchemeService.retrieveSchemeDetails(any(), any())(any(), any()))
      .thenReturn(Future.successful(SchemeDetails("Big Scheme", "pstr", SchemeStatus.Open.toString)))
  }

  "AmendQuarters Controller" must {
    "return OK and the correct view for a GET" in {

      val templateCaptor = ArgumentCaptor.forClass(classOf[String])
      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

      val result = route(application, httpGETRequest(httpPathGET)).value

      status(result) mustEqual OK

      verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

      templateCaptor.getValue mustEqual templateToBeRendered

      jsonCaptor.getValue must containJson(jsonToPassToTemplate(quarters).apply(form(quarters)))
    }

    "redirect to next page when valid data is submitted" in {

      val result = route(application, httpPOSTRequest(httpPathPOST, valuesValid)).value

      status(result) mustEqual SEE_OTHER

      redirectLocation(result) mustBe Some(controllers.amend.routes.ReturnHistoryController.onPageLoad(srn, "2022-01-01").url)
    }

    "return a BAD REQUEST when invalid data is submitted" in {

      val result = route(application, httpPOSTRequest(httpPathPOST, valuesInvalid)).value

      status(result) mustEqual BAD_REQUEST

      verify(mockUserAnswersCacheConnector, times(0)).save(any(), any())(any(), any())
    }
  }
}

