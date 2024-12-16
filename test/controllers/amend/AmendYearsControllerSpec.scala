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
import controllers.base.ControllerSpecBase
import data.SampleData._
import forms.amend.AmendYearsFormProvider
import matchers.JsonMatchers
import models.requests.IdentifierRequest
import models.{AmendYears, Enumerable, SchemeDetails, SchemeStatus, Year}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{times, verify, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import play.api.Application
import play.api.data.Form
import play.api.inject.bind
import play.api.inject.guice.GuiceableModule
import play.api.mvc.Results
import play.api.test.Helpers.{route, status, _}
import play.twirl.api.Html
import services.{QuartersService, SchemeService}
import utils.TwirlMigration
import views.html.amend.AmendYearsView

import scala.concurrent.Future

class AmendYearsControllerSpec extends ControllerSpecBase with JsonMatchers
  with BeforeAndAfterEach with Enumerable.Implicits with Results with ScalaFutures {

  implicit val config: FrontendAppConfig = mockAppConfig
  private val mockSchemeService: SchemeService = mock[SchemeService]
  private val mockQuartersService: QuartersService = mock[QuartersService]

  val extraModules: Seq[GuiceableModule] = Seq[GuiceableModule](
    bind[QuartersService].toInstance(mockQuartersService),
    bind[SchemeService].toInstance(mockSchemeService),
  )
  private val application: Application = applicationBuilder(extraModules = extraModules).build()
  val templateToBeRendered = "amend/amendYears.njk"
  val formProvider = new AmendYearsFormProvider()

  def form(years: Seq[Int]): Form[Year] = formProvider(years)

  lazy val httpPathGET: String = controllers.amend.routes.AmendYearsController.onPageLoad(srn).url
  lazy val httpPathPOST: String = controllers.amend.routes.AmendYearsController.onSubmit(srn).url
  private val submitCall = controllers.amend.routes.AmendYearsController.onSubmit(srn)

  private val year = "2020"
  private val valuesValid: Map[String, Seq[String]] = Map("value" -> Seq(year))
  private val valuesInvalid: Map[String, Seq[String]] = Map("year" -> Seq("20"))
  //scalastyle.off: magic.number
  private val displayYears = Seq(2020, 2022)

  override def beforeEach(): Unit = {
    super.beforeEach()
    when(mockQuartersService.getPastYears(any())(any(), any())).thenReturn(Future.successful(displayYears))
    when(mockRenderer.render(any(), any())(any())).thenReturn(Future.successful(Html("")))
    when(mockAppConfig.schemeDashboardUrl(any(): IdentifierRequest[_])).thenReturn(dummyCall.url)
    when(mockSchemeService.retrieveSchemeDetails(any(), any(), any())(any(), any()))
      .thenReturn(Future.successful(SchemeDetails("Big Scheme", "pstr", SchemeStatus.Open.toString, None)))
  }

  "AmendYears Controller" must {
    "return OK and the correct view for a GET when more than one year" in {
      val result = route(application, httpGETRequest(httpPathGET)).value

      status(result) mustEqual OK

      val yearsForm = form(displayYears)

      val view = application.injector.instanceOf[AmendYearsView].apply(
        yearsForm,
        TwirlMigration.toTwirlRadios(AmendYears.radios(yearsForm, displayYears)),
        submitCall,
        dummyCall.url,
        schemeName
      )(httpGETRequest(httpPathGET), messages)

      compareResultAndView(result, view)
    }

    "redirect to amend quarters page when only one year" in {
      val years = displayYears.filter(_ == 2020)
      when(mockQuartersService.getPastYears(any())(any(), any())).thenReturn(Future.successful(years))

      val result = route(application, httpGETRequest(httpPathGET)).value
      status(result) mustEqual SEE_OTHER
      redirectLocation(result) mustBe Some(controllers.amend.routes.AmendQuartersController.onPageLoad(srn, "2020").url)
    }

    "redirect to session expired page when there is no data returned from overview api for a GET" in {
      when(mockQuartersService.getPastYears(any())(any(), any())).thenReturn(Future.successful(Nil))
      val result = route(application, httpGETRequest(httpPathGET)).value

      redirectLocation(result).value mustBe controllers.routes.SessionExpiredController.onPageLoad.url
    }

    "redirect to next page when valid data is submitted" in {

      val result = route(application, httpPOSTRequest(httpPathPOST, valuesValid)).value

      status(result) mustEqual SEE_OTHER

      redirectLocation(result) mustBe Some(controllers.amend.routes.AmendQuartersController.onPageLoad(srn, year).url)
    }

    "return a BAD REQUEST when invalid data is submitted" in {

      val result = route(application, httpPOSTRequest(httpPathPOST, valuesInvalid)).value

      status(result) mustEqual BAD_REQUEST

      verify(mockUserAnswersCacheConnector, times(0)).save(any(), any())(any(), any())
    }

    "redirect to session expired page when there is no data returned from overview api for a POST" in {
      when(mockQuartersService.getPastYears(any())(any(), any())).thenReturn(Future.successful(Nil))
      val result = route(application, httpPOSTRequest(httpPathPOST, valuesValid)).value

      redirectLocation(result).value mustBe controllers.routes.SessionExpiredController.onPageLoad.url
    }
  }
}

