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
import connectors.ListOfSchemesConnector
import controllers.actions.MutableFakeDataRetrievalAction
import controllers.base.ControllerSpecBase
import controllers.financialOverview.psa.SelectSchemeControllerSpec.penaltySchemes
import data.SampleData.{dummyCall, multiplePenalties, psaId}
import forms.financialStatement.PenaltyTypeFormProvider
import matchers.JsonMatchers
import models.financialStatement.PenaltyType.{AccountingForTaxPenalties, ContractSettlementCharges}
import models.financialStatement.{DisplayPenaltyType, PenaltyType}
import models.requests.IdentifierRequest
import models.{ChargeDetailsFilter, Enumerable, ListOfSchemes, ListSchemeDetails, PaymentOverdue}
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
import services.financialOverview.psa.{PenaltiesCache, PenaltiesNavigationService, PsaPenaltiesAndChargesService}
import utils.TwirlMigration
import views.html.financialOverview.psa.PenaltyTypeView

import scala.concurrent.{ExecutionContext, Future}

class PenaltyTypeControllerSpec extends ControllerSpecBase with JsonMatchers
  with BeforeAndAfterEach with Enumerable.Implicits with Results with ScalaFutures {

  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
  implicit val config: FrontendAppConfig = mockAppConfig
  private val mockNavigationService = mock[PenaltiesNavigationService]
  private val mockListOfSchemesConn = mock[ListOfSchemesConnector]
  private val mockPsaPenaltiesAndChargesService = mock[PsaPenaltiesAndChargesService]
  val extraModules: Seq[GuiceableModule] = Seq[GuiceableModule](
    bind[PsaPenaltiesAndChargesService].toInstance(mockPsaPenaltiesAndChargesService),
    bind[ListOfSchemesConnector].toInstance(mockListOfSchemesConn)
  )

  private val displayPenalties: Seq[DisplayPenaltyType] = Seq(
    DisplayPenaltyType(AccountingForTaxPenalties, Some(PaymentOverdue)),
    DisplayPenaltyType(ContractSettlementCharges, Some(PaymentOverdue)))

  private val mutableFakeDataRetrievalAction: MutableFakeDataRetrievalAction = new MutableFakeDataRetrievalAction()
  private val application: Application = applicationBuilderMutableRetrievalAction(mutableFakeDataRetrievalAction, extraModules).build()
  val templateToBeRendered = "financialOverview/psa/penaltyType.njk"
  val formProvider = new PenaltyTypeFormProvider()
  val form: Form[PenaltyType] = formProvider()
  val penaltyTypes: Seq[PenaltyType] = PenaltyType.values
  val pstr = "24000041IN"

  lazy val httpPathGET: String = routes.PenaltyTypeController.onPageLoad(ChargeDetailsFilter.All).url
  lazy val httpPathPOST: String = routes.PenaltyTypeController.onSubmit(ChargeDetailsFilter.All).url

  private val year = "2020"
  val listOfSchemes: ListOfSchemes = ListOfSchemes("", "", Some(List(
    ListSchemeDetails("Assoc scheme", "SRN123", "", None, Some("24000040IN"), None, None))))

  private val valuesValid: Map[String, Seq[String]] = Map("value" -> Seq(ContractSettlementCharges.toString))
  private val valuesInvalid: Map[String, Seq[String]] = Map("year" -> Seq("20"))

  override def beforeEach(): Unit = {
    super.beforeEach()
    when(mockUserAnswersCacheConnector.save(any(), any())(any(), any())).thenReturn(Future.successful(Json.obj()))
    when(mockAppConfig.schemeDashboardUrl(any(): IdentifierRequest[_])).thenReturn(dummyCall.url)
    when(mockPsaPenaltiesAndChargesService.isPaymentOverdue).thenReturn(_ => true)
    when(mockPsaPenaltiesAndChargesService.getPenaltiesForJourney(any(), any())(any(), any())).
      thenReturn(Future.successful(PenaltiesCache(psaId, "psa-name", multiplePenalties)))
    when(mockNavigationService.penaltySchemes(any(): Int, any(), any(), any())(any(), any())).
      thenReturn(Future.successful(penaltySchemes))
    when(mockListOfSchemesConn.getListOfSchemes(any())(any(), any())).thenReturn(Future(Right(listOfSchemes)))
  }

  "PenaltyTypeController" must {
    "return OK and the correct view for a GET with penalty types" in {

      val req = httpGETRequest(httpPathGET)
      val result = route(application, req).value

      status(result) mustEqual OK

      val view = application.injector.instanceOf[PenaltyTypeView].apply(
        form = form,
        title = messages("penaltyType.title"),
        psaName = "psa-name",
        submitCall = routes.PenaltyTypeController.onSubmit(ChargeDetailsFilter.All),
        buttonText = messages("site.save_and_continue"),
        returnUrl = mockAppConfig.managePensionsSchemeOverviewUrl,
        radios = TwirlMigration.toTwirlRadiosWithHintText(PenaltyType.radios(form, displayPenalties, Seq("govuk-tag govuk-tag--red govuk-!-display-inline"), areLabelsBold = false)),
        journeyType = ChargeDetailsFilter.All
      )(req, messages)

      compareResultAndView(result, view)

    }

    "redirect to next page when valid data is submitted" in {
      when(mockNavigationService.navFromPenaltiesTypePage(any(), any(), any(), any())(any(), any()))
        .thenReturn(Future.successful(Redirect(routes.SelectPenaltiesQuarterController.onPageLoad(year))))

      val result = route(application, httpPOSTRequest(httpPathPOST, valuesValid)).value

      status(result) mustEqual SEE_OTHER

      redirectLocation(result) mustBe Some(routes.AllPenaltiesAndChargesController.onPageLoad(year, pstr, ContractSettlementCharges).url)
    }

    "return a BAD REQUEST when invalid data is submitted" in {

      val result = route(application, httpPOSTRequest(httpPathPOST, valuesInvalid)).value

      status(result) mustEqual BAD_REQUEST

      verify(mockUserAnswersCacheConnector, times(0)).save(any(), any())(any(), any())
    }
  }
}
