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
import forms.financialStatement.PenaltyTypeFormProvider
import matchers.JsonMatchers
import models.PenaltiesFilter.All
import models.financialStatement.PenaltyType.{AccountingForTaxPenalties, ContractSettlementCharges}
import models.financialStatement.{DisplayPenaltyType, PenaltyType}
import models.requests.IdentifierRequest
import models.{Enumerable, PaymentOverdue}
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
import services.{PenaltiesCache, PenaltiesService}
import views.html.financialStatement.penalties.PenaltyTypeView

import scala.concurrent.Future

class PenaltyTypeControllerSpec extends ControllerSpecBase with JsonMatchers
  with BeforeAndAfterEach with Enumerable.Implicits with Results with ScalaFutures {

  implicit val config: FrontendAppConfig = mockAppConfig
  val mockPenaltiesService: PenaltiesService = mock[PenaltiesService]
  val extraModules: Seq[GuiceableModule] = Seq[GuiceableModule](
    bind[PenaltiesService].toInstance(mockPenaltiesService)
  )

  private val displayPenalties: Seq[DisplayPenaltyType] = Seq(
    DisplayPenaltyType(AccountingForTaxPenalties, Some(PaymentOverdue)),
    DisplayPenaltyType(ContractSettlementCharges, Some(PaymentOverdue)))

  private val mutableFakeDataRetrievalAction: MutableFakeDataRetrievalAction = new MutableFakeDataRetrievalAction()
  private val application: Application = applicationBuilderMutableRetrievalAction(mutableFakeDataRetrievalAction, extraModules).build()
  val templateToBeRendered = "financialStatement/penalties/penaltyType.njk"
  val formProvider = new PenaltyTypeFormProvider()
  val form: Form[PenaltyType] = formProvider()
  val penaltyTypes: Seq[PenaltyType] = PenaltyType.values

  lazy val httpPathGET: String = routes.PenaltyTypeController.onPageLoad(All).url
  lazy val httpPathPOST: String = routes.PenaltyTypeController.onSubmit(All).url

  private val year = "2020"

  private val valuesValid: Map[String, Seq[String]] = Map("value" -> Seq(AccountingForTaxPenalties.toString))
  private val valuesInvalid: Map[String, Seq[String]] = Map("year" -> Seq("20"))

  override def beforeEach(): Unit = {
    super.beforeEach()
    when(mockUserAnswersCacheConnector.save(any(), any())(any(), any())).thenReturn(Future.successful(Json.obj()))
    when(mockAppConfig.schemeDashboardUrl(any(): IdentifierRequest[_])).thenReturn(dummyCall.url)
    when(mockPenaltiesService.isPaymentOverdue).thenReturn(_ => true)
    when(mockPenaltiesService.getPenaltiesForJourney(any(), any())(any(), any()))
      .thenReturn(Future.successful(PenaltiesCache(psaId, "psa-name", multiplePenalties)))
  }

  "PenaltyTypeController" must {
    "return OK and the correct view for a GET" in {
      val result = route(application, httpGETRequest(httpPathGET)).value

      val view = application.injector.instanceOf[PenaltyTypeView].apply(
        form,
        PenaltyType.radios(form, displayPenalties, Seq("govuk-tag govuk-tag--red govuk-!-display-inline"), areLabelsBold = false),
        routes.PenaltyTypeController.onSubmit(All),
        "",
        "psa-name"
      )(httpGETRequest(httpPathGET), messages)

      status(result) mustEqual OK

      compareResultAndView(result, view)
    }

    "redirect to next page when valid data is submitted" in {
      when(mockPenaltiesService.navFromPenaltiesTypePage(any(), any(), any(), any())(any(), any()))
        .thenReturn(Future.successful(Redirect(routes.SelectPenaltiesQuarterController.onPageLoad(year, All))))

      val result = route(application, httpPOSTRequest(httpPathPOST, valuesValid)).value

      status(result) mustEqual SEE_OTHER

      redirectLocation(result) mustBe Some(routes.SelectPenaltiesQuarterController.onPageLoad(year, All).url)
    }

    "return a BAD REQUEST when invalid data is submitted" in {

      val result = route(application, httpPOSTRequest(httpPathPOST, valuesInvalid)).value

      status(result) mustEqual BAD_REQUEST

      verify(mockUserAnswersCacheConnector, times(0)).save(any(), any())(any(), any())
    }
  }
}

