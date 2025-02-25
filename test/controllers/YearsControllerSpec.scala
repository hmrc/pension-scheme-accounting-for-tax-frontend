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
import controllers.actions.MutableFakeDataRetrievalAction
import controllers.base.ControllerSpecBase
import data.SampleData
import data.SampleData._
import forms.YearsFormProvider
import matchers.JsonMatchers
import models.StartYears.enumerable
import models.requests.IdentifierRequest
import models.{Enumerable, SchemeDetails, SchemeStatus, StartYears, UserAnswers, Year}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{times, verify, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import play.api.Application
import play.api.data.Form
import play.api.inject.bind
import play.api.inject.guice.GuiceableModule
import play.api.libs.json.Json
import play.api.mvc.{Call, Results}
import play.api.test.Helpers.{route, status, _}
import services.SchemeService
import utils.TwirlMigration
import views.html.YearsView

import scala.concurrent.Future

class YearsControllerSpec extends ControllerSpecBase with JsonMatchers
  with BeforeAndAfterEach with Enumerable.Implicits with Results with ScalaFutures {

  implicit val config: FrontendAppConfig = mockAppConfig
  val mockSchemeService: SchemeService = mock[SchemeService]
  val extraModules: Seq[GuiceableModule] = Seq[GuiceableModule](
    bind[SchemeService].toInstance(mockSchemeService)
  )

  private val mutableFakeDataRetrievalAction: MutableFakeDataRetrievalAction = new MutableFakeDataRetrievalAction()
  private val application: Application = applicationBuilderMutableRetrievalAction(mutableFakeDataRetrievalAction, extraModules).build()
  val formProvider = new YearsFormProvider()
  val form: Form[Year] = formProvider()

  lazy val httpPathGET: String = controllers.routes.YearsController.onPageLoad(srn).url
  private val submitCall: Call = controllers.routes.YearsController.onSubmit(srn)
  lazy val httpPathPOST: String = submitCall.url

  private val year = "2020"

  private val valuesValid: Map[String, Seq[String]] = Map("value" -> Seq(year))
  private val valuesInvalid: Map[String, Seq[String]] = Map("year" -> Seq("20"))

  override def beforeEach(): Unit = {
    super.beforeEach()
    when(mockUserAnswersCacheConnector.save(any(), any())(any(), any())).thenReturn(Future.successful(Json.obj()))
    when(mockAppConfig.schemeDashboardUrl(any(): IdentifierRequest[_])).thenReturn(dummyCall.url)
    when(mockSchemeService.retrieveSchemeDetails(any(), any())(any(), any()))
      .thenReturn(Future.successful(SchemeDetails("Big Scheme", "pstr", SchemeStatus.Open.toString, None)))
  }

  private val userAnswers: Option[UserAnswers] = Some(userAnswersWithSchemeNamePstrQuarter)

  "Year Controller" must {
    "return OK and the correct view for a GET" in {
      mutableFakeDataRetrievalAction.setDataToReturn(userAnswers)

      when(mockCompoundNavigator.nextPage(any(), any(), any(), any(), any(), any(), any())(any())).thenReturn(dummyCall)

      val request = httpGETRequest(httpPathGET)
      val result = route(application, request).value

      val view = application.injector.instanceOf[YearsView].apply(
        form,
        submitCall,
        SampleData.schemeName,
        dummyCall.url,
        TwirlMigration.toTwirlRadios(StartYears.radios(form)(mockAppConfig))
      )(request, messages)

      status(result) mustEqual OK

      compareResultAndView(result, view)
    }

    "redirect to next page when valid data is submitted" in {

      val result = route(application, httpPOSTRequest(httpPathPOST, valuesValid)).value

      status(result) mustEqual SEE_OTHER

      redirectLocation(result) mustBe Some(controllers.routes.QuartersController.onPageLoad(srn, year).url)
    }

    "return a BAD REQUEST when invalid data is submitted" in {
      mutableFakeDataRetrievalAction.setDataToReturn(userAnswers)

      val result = route(application, httpPOSTRequest(httpPathPOST, valuesInvalid)).value

      status(result) mustEqual BAD_REQUEST

      verify(mockUserAnswersCacheConnector, times(0)).save(any(), any())(any(), any())
    }
  }
}

