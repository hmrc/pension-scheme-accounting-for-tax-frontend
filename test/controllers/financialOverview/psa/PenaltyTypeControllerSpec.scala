/*
 * Copyright 2022 HM Revenue & Customs
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
import models.{Enumerable, ListOfSchemes, ListSchemeDetails, PaymentOverdue}
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import play.api.Application
import play.api.data.Form
import play.api.http.Status.{BAD_REQUEST, OK, SEE_OTHER}
import play.api.inject.bind
import play.api.inject.guice.GuiceableModule
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.Results
import play.api.test.Helpers.{defaultAwaitTimeout, redirectLocation, route, status, writeableOf_AnyContentAsEmpty, writeableOf_AnyContentAsFormUrlEncoded}
import play.twirl.api.Html
import services.financialOverview.psa.{PenaltiesCache, PenaltiesNavigationService, PsaPenaltiesAndChargesService}
import uk.gov.hmrc.viewmodels.NunjucksSupport

import scala.concurrent.{ExecutionContext, Future}

class PenaltyTypeControllerSpec extends ControllerSpecBase with NunjucksSupport with JsonMatchers
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
    DisplayPenaltyType(ContractSettlementCharges, None))

  private val mutableFakeDataRetrievalAction: MutableFakeDataRetrievalAction = new MutableFakeDataRetrievalAction()
  private val application: Application = applicationBuilderMutableRetrievalAction(mutableFakeDataRetrievalAction, extraModules).build()
  val templateToBeRendered = "financialOverview/psa/penaltyType.njk"
  val formProvider = new PenaltyTypeFormProvider()
  val form: Form[PenaltyType] = formProvider()
  val penaltyTypes: Seq[PenaltyType] = PenaltyType.values
  val pstr = "24000041IN"

  lazy val httpPathGET: String = routes.PenaltyTypeController.onPageLoad().url
  lazy val httpPathPOST: String = routes.PenaltyTypeController.onSubmit().url

  private val jsonToPassToTemplate: Form[PenaltyType] => JsObject = form => Json.obj(
    "form" -> form,
    "radios" -> PenaltyType.radios(form, displayPenalties)
  )

  private val year = "2020"
  val listOfSchemes: ListOfSchemes = ListOfSchemes("", "", Some(List(
    ListSchemeDetails("Assoc scheme", "SRN123", "", None, Some("24000040IN"), None, None))))

  private val valuesValid: Map[String, Seq[String]] = Map("value" -> Seq(ContractSettlementCharges.toString))
  private val valuesInvalid: Map[String, Seq[String]] = Map("year" -> Seq("20"))

  override def beforeEach: Unit = {
    super.beforeEach
    when(mockUserAnswersCacheConnector.save(any(), any())(any(), any())).thenReturn(Future.successful(Json.obj()))
    when(mockRenderer.render(any(), any())(any())).thenReturn(Future.successful(Html("")))
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

      val templateCaptor = ArgumentCaptor.forClass(classOf[String])
      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

      val result = route(application, httpGETRequest(httpPathGET)).value

      status(result) mustEqual OK

      verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

      templateCaptor.getValue mustEqual templateToBeRendered

      jsonCaptor.getValue must containJson(jsonToPassToTemplate.apply(form))
    }

    "redirect to next page when valid data is submitted" in {
      when(mockNavigationService.navFromPenaltiesTypePage(any(), any(), any())(any(), any()))
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
