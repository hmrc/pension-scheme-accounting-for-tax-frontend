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
import data.SampleData._
import forms.YearsFormProvider
import matchers.JsonMatchers
import models.StartYears.enumerable
import models.financialStatement.PenaltyType
import models.financialStatement.PenaltyType.{ContractSettlementCharges, EventReportingCharges}
import models.requests.IdentifierRequest
import models.{ChargeDetailsFilter, DisplayYear, Enumerable, FSYears, PaymentOverdue, Year}
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
import services.PenaltiesServiceSpec.{listOfSchemes, penaltiesCache}
import services.financialOverview.psa.PsaPenaltiesAndChargesServiceSpec.{psaFsERSeq, psaFsSeq, pstr}
import services.financialOverview.psa.{PenaltiesCache, PenaltiesNavigationService, PsaPenaltiesAndChargesService}
import utils.TwirlMigration
import views.html.financialOverview.psa.SelectYearView

import scala.concurrent.{ExecutionContext, Future}

class SelectPenaltiesYearControllerSpec extends ControllerSpecBase with JsonMatchers
  with BeforeAndAfterEach with Enumerable.Implicits with Results with ScalaFutures {

  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
  implicit val config: FrontendAppConfig = mockAppConfig
  private val mockPsaPenaltiesAndChargesService = mock[PsaPenaltiesAndChargesService]
  private val mockNavigationService = mock[PenaltiesNavigationService]
  private val mockListOfSchemesConn: ListOfSchemesConnector = mock[ListOfSchemesConnector]
  val extraModules: Seq[GuiceableModule] = Seq[GuiceableModule](
    bind[PsaPenaltiesAndChargesService].toInstance(mockPsaPenaltiesAndChargesService),
    bind[ListOfSchemesConnector].toInstance(mockListOfSchemesConn)
  )

  private val years: Seq[DisplayYear] = Seq(DisplayYear(2020, Some(PaymentOverdue)))

  private val mutableFakeDataRetrievalAction: MutableFakeDataRetrievalAction = new MutableFakeDataRetrievalAction()
  private val application: Application = applicationBuilderMutableRetrievalAction(mutableFakeDataRetrievalAction, extraModules).build()
  val formProvider = new YearsFormProvider()
  val form: Form[Year] = formProvider()
  val penaltyType: PenaltyType = ContractSettlementCharges
  val typeParam: String = mockPsaPenaltiesAndChargesService.getTypeParam(penaltyType)

  lazy val httpPathGET: String = routes.SelectPenaltiesYearController.onPageLoad(penaltyType, ChargeDetailsFilter.All).url
  lazy val httpPathPOST: String = routes.SelectPenaltiesYearController.onSubmit(penaltyType, ChargeDetailsFilter.All).url

  lazy val erHttpPathPOST: String = routes.SelectPenaltiesYearController.onSubmit(EventReportingCharges, ChargeDetailsFilter.All).url

  private val submitCall = controllers.financialOverview.psa.routes.SelectPenaltiesYearController.onSubmit(penaltyType, ChargeDetailsFilter.All)

  private val year = "2020"

  private val valuesValid: Map[String, Seq[String]] = Map("value" -> Seq(year))
  private val valuesInvalid: Map[String, Seq[String]] = Map("year" -> Seq("20"))

  override def beforeEach(): Unit = {
    super.beforeEach()
    when(mockUserAnswersCacheConnector.save(any(), any())(any(), any())).thenReturn(Future.successful(Json.obj()))
    when(mockAppConfig.schemeDashboardUrl(any(): IdentifierRequest[_])).thenReturn(dummyCall.url)
    when(mockPsaPenaltiesAndChargesService.isPaymentOverdue).thenReturn(_ => true)
  }

  "SelectYearController" must {
    "return OK and the correct view for a GET with the select option for Year" in {
      when(mockPsaPenaltiesAndChargesService.getPenaltiesForJourney(any(), any())(any(), any()))
        .thenReturn(Future.successful(PenaltiesCache(psaId, "psa-name", psaFsSeq)))
      when(mockPsaPenaltiesAndChargesService.getTypeParam(ContractSettlementCharges)).
        thenReturn(ContractSettlementCharges.toString)
      when(mockListOfSchemesConn.getListOfSchemes(any())(any(), any())).thenReturn(Future(Right(listOfSchemes)))

      val request = httpGETRequest(httpPathGET)
      val result = route(application, httpGETRequest(httpPathGET)).value

      status(result) mustEqual OK

      val view = application.injector.instanceOf[SelectYearView].apply(
        form = form,
        title =  messages("selectPenaltiesYear.title", typeParam),
        submitCall = submitCall,
        psaName = penaltiesCache.psaName,
        penaltyType = typeParam,
        returnUrl = mockAppConfig.managePensionsSchemeOverviewUrl,
        radios = TwirlMigration.toTwirlRadiosWithHintText(FSYears.radios(form, years)),
        journeyType = ChargeDetailsFilter.All
      )(request, messages)

      compareResultAndView(result, view)
    }
    "return OK and the correct view for a GET to select the charge history year" in {
      lazy val httpPathGET: String = routes.SelectPenaltiesYearController.onPageLoad(penaltyType, ChargeDetailsFilter.History).url
      val submitCall = controllers.financialOverview.psa.routes.SelectPenaltiesYearController.onSubmit(penaltyType, ChargeDetailsFilter.History)

      when(mockPsaPenaltiesAndChargesService.getPenaltiesForJourney(any(), any())(any(), any()))
        .thenReturn(Future.successful(PenaltiesCache(psaId, "psa-name", psaFsSeq)))
      when(mockPsaPenaltiesAndChargesService.getTypeParam(ContractSettlementCharges)).
        thenReturn(ContractSettlementCharges.toString)
      when(mockListOfSchemesConn.getListOfSchemes(any())(any(), any())).thenReturn(Future(Right(listOfSchemes)))

      val request = httpGETRequest(httpPathGET)
      val result = route(application, httpGETRequest(httpPathGET)).value

      status(result) mustEqual OK

      val view = application.injector.instanceOf[SelectYearView].apply(
        form = form,
        title =  messages("psa.financial.overview.chargeHistoryYear.title"),
        submitCall = submitCall,
        psaName = penaltiesCache.psaName,
        penaltyType = typeParam,
        returnUrl = mockAppConfig.managePensionsSchemeOverviewUrl,
        radios = TwirlMigration.toTwirlRadios(FSYears.radios(form, years)),
        journeyType = ChargeDetailsFilter.History
      )(request, messages)

      compareResultAndView(result, view)
    }
    "redirect to next page when valid data is submitted for AFT" in {
      when(mockPsaPenaltiesAndChargesService.getPenaltiesForJourney(any(), any())(any(), any())).
        thenReturn(Future.successful(PenaltiesCache(psaId, "psa-name", psaFsSeq)))
      when(mockPsaPenaltiesAndChargesService.getTypeParam(ContractSettlementCharges)).
        thenReturn(ContractSettlementCharges.toString)
      when(mockNavigationService.navFromAFTYearsPage(any(), any(), any(), any())(any(), any()))
        .thenReturn(Future.successful(Redirect(routes.SelectSchemeController.onPageLoad(ContractSettlementCharges, year))))

      val result = route(application, httpPOSTRequest(httpPathPOST, valuesValid)).value
      status(result) mustEqual SEE_OTHER
      redirectLocation(result) mustBe Some(routes.AllPenaltiesAndChargesController.onPageLoad(year, pstr, ContractSettlementCharges).url)
    }

    "redirect to next page when valid data is submitted for Event Reporting" in {
      when(mockPsaPenaltiesAndChargesService.getPenaltiesForJourney(any(), any())(any(), any())).
        thenReturn(Future.successful(PenaltiesCache(psaId, "psa-name", psaFsERSeq)))
      when(mockPsaPenaltiesAndChargesService.getTypeParam(EventReportingCharges)).
        thenReturn(EventReportingCharges.toString)
      when(mockNavigationService.navFromERYearsPage(any(), any(), any(), any()))
        .thenReturn(Future.successful(Redirect(routes.SelectSchemeController.onPageLoad(EventReportingCharges, year))))

      val result = route(application, httpPOSTRequest(erHttpPathPOST, valuesValid)).value
      status(result) mustEqual SEE_OTHER
      redirectLocation(result) mustBe Some(routes.AllPenaltiesAndChargesController.onPageLoad(year, pstr, EventReportingCharges).url)
    }

    "return a BAD REQUEST when invalid data is submitted" in {
      when(mockPsaPenaltiesAndChargesService.getPenaltiesForJourney(any(), any())(any(), any())).
        thenReturn(Future.successful(PenaltiesCache(psaId, "psa-name", psaFsSeq)))
      when(mockPsaPenaltiesAndChargesService.getTypeParam(ContractSettlementCharges)).
        thenReturn(ContractSettlementCharges.toString)

      val result = route(application, httpPOSTRequest(httpPathPOST, valuesInvalid)).value
      status(result) mustEqual BAD_REQUEST
      verify(mockUserAnswersCacheConnector, times(0)).save(any(), any())(any(), any())
    }
  }

}