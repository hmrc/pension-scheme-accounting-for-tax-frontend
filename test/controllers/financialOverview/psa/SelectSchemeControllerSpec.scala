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

import connectors.FinancialStatementConnector
import connectors.FinancialStatementConnectorSpec.psaFSResponse
import connectors.cache.FinancialInfoCacheConnector
import controllers.actions.MutableFakeDataRetrievalAction
import controllers.base.ControllerSpecBase
import controllers.financialOverview.psa.routes.AllPenaltiesAndChargesController
import data.SampleData.psaId
import forms.SelectSchemeFormProvider
import matchers.JsonMatchers
import models.financialStatement.PenaltyType
import models.financialStatement.PenaltyType.ContractSettlementCharges
import models.{Enumerable, PenaltySchemes}
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, times, verify, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import play.api.Application
import play.api.data.Form
import play.api.http.Status.{OK, SEE_OTHER}
import play.api.inject.bind
import play.api.inject.guice.GuiceableModule
import play.api.libs.json.{JsObject, JsValue, Json}
import play.api.mvc.Results
import play.api.test.Helpers.{defaultAwaitTimeout, redirectLocation, route, status, writeableOf_AnyContentAsEmpty, writeableOf_AnyContentAsFormUrlEncoded}
import play.twirl.api.Html
import services.financialOverview.psa.{PenaltiesCache, PenaltiesNavigationService, PsaPenaltiesAndChargesService}
import uk.gov.hmrc.viewmodels.NunjucksSupport

import scala.concurrent.Future

class SelectSchemeControllerSpec extends ControllerSpecBase with NunjucksSupport with JsonMatchers
  with BeforeAndAfterEach with Enumerable.Implicits with Results with ScalaFutures {

  import SelectSchemeControllerSpec._

  private val mockNavigationService = mock[PenaltiesNavigationService]
  private val mockPsaPenaltiesAndChargesService = mock[PsaPenaltiesAndChargesService]
  private val mockFICacheConnector = mock[FinancialInfoCacheConnector]
  private val mockFSConnector = mock[FinancialStatementConnector]
  private val mutableFakeDataRetrievalAction: MutableFakeDataRetrievalAction = new MutableFakeDataRetrievalAction

  private def form: Form[PenaltySchemes] =
    new SelectSchemeFormProvider()(penaltySchemes,
      messages("selectScheme.error", messages(s"penaltyType.${penaltyType.toString}").toLowerCase()))

  val extraModules: Seq[GuiceableModule] = Seq[GuiceableModule](
    bind[PsaPenaltiesAndChargesService].toInstance(mockPsaPenaltiesAndChargesService),
    bind[FinancialInfoCacheConnector].toInstance(mockFICacheConnector),
    bind[FinancialStatementConnector].toInstance(mockFSConnector),
    bind[PenaltiesNavigationService].toInstance(mockNavigationService)
  )

  val application: Application = applicationBuilderMutableRetrievalAction(mutableFakeDataRetrievalAction, extraModules).build()

  private val jsonToTemplate: Form[PenaltySchemes] => JsObject = form => Json.obj(
    fields = "form" -> form,
    "radios" -> PenaltySchemes.radios(form, penaltySchemes, Seq("govuk-tag govuk-tag--red govuk-!-display-inline"), areLabelsBold = false)
  )

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockPsaPenaltiesAndChargesService)
    reset(mockAppConfig)
    reset(mockFICacheConnector)
    reset(mockFSConnector)
    when(mockRenderer.render(any(), any())(any())).thenReturn(Future.successful(Html("")))
    when(mockPsaPenaltiesAndChargesService.getPenaltiesForJourney(any(), any())(any(), any())).
      thenReturn(Future.successful(PenaltiesCache(psaId, "psa-name", psaFSResponse)))
    when(mockNavigationService.penaltySchemes(any(): Int, any(), any(), any())(any(), any())).
      thenReturn(Future.successful(penaltySchemes))

  }

  "SelectSchemeController" when {
    "on a GET" must {

      "return OK with the correct view" in {

        val templateCaptor = ArgumentCaptor.forClass(classOf[String])
        val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

        val result = route(application, httpGETRequest(httpPathGETVersion)).value

        status(result) mustEqual OK

        verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

        templateCaptor.getValue mustEqual template
        jsonCaptor.getValue must containJson(jsonToTemplate.apply(form))
      }
    }

    "on a POST" must {
      "redirect to penalties page when valid data with scheme is submitted" in {
        val result = route(application, httpPOSTRequest(httpPathPOST, Map("value" -> Seq(ps1.pstr)))).value

        status(result) mustEqual SEE_OTHER

        redirectLocation(result) mustBe Some(AllPenaltiesAndChargesController.onPageLoad(year, pstr, penaltyType).url)

      }
    }
  }

}

object SelectSchemeControllerSpec {
  private val template = "financialOverview/psa/selectScheme.njk"
  private val year = "2020"
  val pstr = "24000040IN"
  private val ps1 = PenaltySchemes(name = Some("Scheme1"), pstr = "24000040IN", srn = None, None)
  private val ps2 = PenaltySchemes(name = None, pstr = "24000041IN", srn = None, None)
  val penaltySchemes: Seq[PenaltySchemes] = Seq(ps1, ps2)
  val psaFS: JsValue = Json.toJson(psaFSResponse)
  val penaltyType: PenaltyType = ContractSettlementCharges

  private def httpPathGETVersion: String = routes.SelectSchemeController.onPageLoad(penaltyType, year).url

  private def httpPathPOST: String = routes.SelectSchemeController.onSubmit(penaltyType, year).url
}
