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

package controllers.financialStatement.penalties

import config.FrontendAppConfig
import controllers.actions.MutableFakeDataRetrievalAction
import controllers.base.ControllerSpecBase
import data.SampleData._
import forms.YearsFormProvider
import matchers.JsonMatchers
import models.PenaltiesFilter.All
import models.StartYears.enumerable
import models.financialStatement.PenaltyType
import models.financialStatement.PenaltyType.{AccountingForTaxPenalties, EventReportingCharges}
import models.requests.IdentifierRequest
import models.{DisplayYear, Enumerable, FSYears, PaymentOverdue, Year}
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
import services.{PenaltiesCache, PenaltiesService}
import views.html.financialStatement.penalties.SelectYearView

import scala.concurrent.Future

class SelectPenaltiesYearControllerSpec extends ControllerSpecBase with JsonMatchers
  with BeforeAndAfterEach with Enumerable.Implicits with Results with ScalaFutures {

  implicit val config: FrontendAppConfig = mockAppConfig
  val mockPenaltiesService: PenaltiesService = mock[PenaltiesService]
  val extraModules: Seq[GuiceableModule] = Seq[GuiceableModule](
    bind[PenaltiesService].toInstance(mockPenaltiesService)
  )

  private val years: Seq[DisplayYear] = Seq(DisplayYear(2020, Some(PaymentOverdue)))

  private val mutableFakeDataRetrievalAction: MutableFakeDataRetrievalAction = new MutableFakeDataRetrievalAction()
  private val application: Application = applicationBuilderMutableRetrievalAction(mutableFakeDataRetrievalAction, extraModules).build()
  val templateToBeRendered = "financialStatement/penalties/selectYear.njk"
  val formProvider = new YearsFormProvider()
  val form: Form[Year] = formProvider()
  val penaltyAft: PenaltyType = AccountingForTaxPenalties
  val penaltyER: PenaltyType = EventReportingCharges

  lazy val aftHttpPathGET: String = routes.SelectPenaltiesYearController.onPageLoad(penaltyAft, All).url
  lazy val aftHttpPathPOST: String = routes.SelectPenaltiesYearController.onSubmit(penaltyAft, All).url

  lazy val erHttpPathGET: String = routes.SelectPenaltiesYearController.onPageLoad(penaltyER, All).url
  lazy val erHttpPathPOST: String = routes.SelectPenaltiesYearController.onSubmit(penaltyER, All).url

  private val year = "2020"

  private val valuesValid: Map[String, Seq[String]] = Map("value" -> Seq(year))
  private val valuesInvalid: Map[String, Seq[String]] = Map("year" -> Seq("20"))

  override def beforeEach(): Unit = {
    super.beforeEach()
    when(mockUserAnswersCacheConnector.save(any(), any())(any(), any())).thenReturn(Future.successful(Json.obj()))
    when(mockAppConfig.schemeDashboardUrl(any(): IdentifierRequest[_])).thenReturn(dummyCall.url)
    when(mockPenaltiesService.isPaymentOverdue).thenReturn(_ => true)
    when(mockPenaltiesService.getPenaltiesForJourney(any(), any())(any(), any())).thenReturn(Future.successful(PenaltiesCache(psaId, "psa-name", psaFsSeq)))
    when(mockPenaltiesService.getTypeParam(any())(any())).thenReturn(messages(s"penaltyType.AccountingForTaxPenalties"))
  }

  "SelectYear Controller" must {
    "return OK and the correct view for a GET with aft" in {
      val result = route(application, httpGETRequest(aftHttpPathGET)).value

      val typeParam = "penaltyType.AccountingForTaxPenalties"
      val view = application.injector.instanceOf[SelectYearView].apply(
        form,
        typeParam,
        FSYears.radios(form, years),
        routes.SelectPenaltiesYearController.onSubmit(penaltyAft, All),
        "",
        "psa-name"
      )(httpGETRequest(aftHttpPathGET), messages)

      status(result) mustEqual OK

      compareResultAndView(result, view)
    }

    "redirect to next page when valid data is submitted for aft" in {
      when(mockPenaltiesService.navFromAftYearsPage(any(), any(), any(), any())(any(), any()))
        .thenReturn(Future.successful(Redirect(routes.SelectPenaltiesQuarterController.onPageLoad(year, All))))

      val result = route(application, httpPOSTRequest(aftHttpPathPOST, valuesValid)).value

      status(result) mustEqual SEE_OTHER

      redirectLocation(result) mustBe Some(routes.SelectPenaltiesQuarterController.onPageLoad(year, All).url)
    }

    "return a BAD REQUEST when invalid data is submitted for aft" in {

      val result = route(application, httpPOSTRequest(aftHttpPathPOST, valuesInvalid)).value

      status(result) mustEqual BAD_REQUEST

      verify(mockUserAnswersCacheConnector, times(0)).save(any(), any())(any(), any())
    }
  }
}

