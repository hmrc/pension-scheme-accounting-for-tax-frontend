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
import connectors.AFTConnector
import controllers.actions.MutableFakeDataRetrievalAction
import controllers.base.ControllerSpecBase
import data.SampleData._
import forms.QuartersFormProvider
import matchers.JsonMatchers
import models.LocalDateBinder._
import models.requests.IdentifierRequest
import models.{AFTQuarter, DisplayQuarter, Enumerable, LockedHint, Quarters, SchemeDetails, SchemeStatus, UserAnswers}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{times, verify, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import play.api.Application
import play.api.data.Form
import play.api.inject.bind
import play.api.inject.guice.GuiceableModule
import play.api.libs.json.Json
import play.api.mvc.Results
import play.api.test.Helpers._
import play.twirl.api.Html
import services.{QuartersService, SchemeService}
import utils.DateHelper
import views.html.QuartersView

import java.time.LocalDate
import scala.concurrent.Future

class QuartersControllerSpec extends ControllerSpecBase with JsonMatchers
  with BeforeAndAfterEach with Enumerable.Implicits with Results with ScalaFutures {

  implicit val config: FrontendAppConfig = mockAppConfig
  val mockSchemeService: SchemeService = mock[SchemeService]
  val mockAFTConnector: AFTConnector = mock[AFTConnector]
  val mockQuartersService: QuartersService = mock[QuartersService]
  val extraModules: Seq[GuiceableModule] = Seq[GuiceableModule](
    bind[SchemeService].toInstance(mockSchemeService),
    bind[QuartersService].toInstance(mockQuartersService),
    bind[AFTConnector].toInstance(mockAFTConnector)
  )

  private val mutableFakeDataRetrievalAction: MutableFakeDataRetrievalAction = new MutableFakeDataRetrievalAction()
  private val application: Application = applicationBuilderMutableRetrievalAction(mutableFakeDataRetrievalAction, extraModules).build()
  private val testYear = 2020
  private val errorKey = "quarters.error.required"
  val formProvider = new QuartersFormProvider()
  val availableQuarters: Seq[AFTQuarter] = Seq(q22020, q32020, q42020, q12021)
  val form: Form[AFTQuarter] = formProvider(messages(errorKey, testYear), availableQuarters)

  lazy val httpPathGET: String = controllers.routes.QuartersController.onPageLoad(srn, testYear.toString).url
  lazy val httpPathPOST: String = controllers.routes.QuartersController.onSubmit(srn, testYear.toString).url

  private val valuesValid: Map[String, Seq[String]] = Map("value" -> Seq(q12021.toString))

  private val valuesInvalid: Map[String, Seq[String]] = Map("year" -> Seq("q5"))

  override def beforeEach(): Unit = {
    super.beforeEach()
    when(mockUserAnswersCacheConnector.save(any(), any())(any(), any())).thenReturn(Future.successful(Json.obj()))
    when(mockAppConfig.schemeDashboardUrl(any(): IdentifierRequest[_])).thenReturn(dummyCall.url)
    when(mockSchemeService.retrieveSchemeDetails(any(), any(), any())(any(), any()))
      .thenReturn(Future.successful(SchemeDetails("Big Scheme", "pstr", SchemeStatus.Open.toString, None)))
    DateHelper.setDate(Some(LocalDate.of(2021, 1, 1)))
  }

  private val userAnswers: Option[UserAnswers] = Some(userAnswersWithSchemeName)

  "Quarters Controller" must {

    "return OK and the correct view for a GET" in {
      when(mockQuartersService.getStartQuarters(any(), any(), any())(any(), any())).thenReturn(Future.successful(Seq(displayQuarterStart)))

      mutableFakeDataRetrievalAction.setDataToReturn(userAnswers)

      val result = route(application, httpGETRequest(httpPathGET)).value

      status(result) mustEqual OK

      val view = application.injector.instanceOf[QuartersView].apply(
        testYear.toString,
        form,
        Quarters.radios(form, Seq(displayQuarterStart)),
        controllers.routes.QuartersController.onSubmit(srn, testYear.toString),
        dummyCall.url,
        schemeName
      )(httpGETRequest(httpPathGET), messages)

      compareResultAndView(result, view)
    }

    "redirect to next page when valid data is submitted" in {
      when(mockQuartersService.getStartQuarters(any(), any(), any())(any(), any())).thenReturn(Future.successful(Seq(displayQuarterStart)))

      when(mockAFTConnector.getAftOverview(any(), any(), any())(any(), any())).thenReturn(Future.successful(Seq(aftOverviewQ12021)))
      val result = route(application, httpPOSTRequest(httpPathPOST, valuesValid)).value

      status(result) mustEqual SEE_OTHER

      redirectLocation(result) mustBe Some(controllers.routes.ChargeTypeController.onPageLoad(srn, q12021.startDate, accessType, versionInt).url)
    }

    "redirect when there are no displayQuarters" in {
      when(mockQuartersService.getStartQuarters(any(), any(), any())(any(), any())).thenReturn(Future.successful(Seq()))

      when(mockAFTConnector.getAftOverview(any(), any(), any())(any(), any())).thenReturn(Future.successful(Seq(aftOverviewQ12021)))
      val result = route(application, httpPOSTRequest(httpPathPOST, valuesValid)).value

      status(result) mustEqual SEE_OTHER

      redirectLocation(result) mustBe Some(controllers.routes.SessionExpiredController.onPageLoad.url)
    }

    "redirect to locked page when AFT return is locked but there is no overview data" in {
      when(mockQuartersService.getStartQuarters(any(), any(), any())(any(), any()))
        .thenReturn(Future.successful(Seq(DisplayQuarter(q12021, displayYear = false, None, Some(LockedHint)))))
      when(mockAFTConnector.getAftOverview(any(), any(), any())(any(), any())).thenReturn(Future.successful(Nil))

      val result = route(application, httpPOSTRequest(httpPathPOST, valuesValid)).value

      status(result) mustEqual SEE_OTHER

      redirectLocation(result) mustBe Some(controllers.routes.AFTReturnLockedController.onPageLoad(srn, q12021.startDate).url)
    }

    "return a BAD REQUEST when invalid data is submitted" in {
      when(mockQuartersService.getStartQuarters(any(), any(), any())(any(), any())).thenReturn(Future.successful(Seq(displayQuarterStart)))

      when(mockAFTConnector.getAftOverview(any(), any(), any())(any(), any())).thenReturn(Future.successful(Seq(aftOverviewQ12021)))

      val result = route(application, httpPOSTRequest(httpPathPOST, valuesInvalid)).value

      status(result) mustEqual BAD_REQUEST

      verify(mockUserAnswersCacheConnector, times(0)).save(any(), any())(any(), any())
    }
  }
}

