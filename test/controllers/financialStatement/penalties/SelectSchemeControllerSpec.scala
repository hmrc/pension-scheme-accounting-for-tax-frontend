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

import connectors.FinancialStatementConnector
import connectors.FinancialStatementConnectorSpec.psaFSResponse
import connectors.cache.FinancialInfoCacheConnector
import controllers.actions.MutableFakeDataRetrievalAction
import controllers.base.ControllerSpecBase
import data.SampleData._
import forms.SelectSchemeFormProvider
import matchers.JsonMatchers
import models.PenaltiesFilter.All
import models.financialStatement.PenaltyType.ContractSettlementCharges
import models.financialStatement.{PenaltyType, PsaFSDetail}
import models.{Enumerable, PenaltySchemes}
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, times, verify, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import play.api.Application
import play.api.data.Form
import play.api.inject.bind
import play.api.inject.guice.GuiceableModule
import play.api.libs.json.{JsObject, JsValue, Json}
import play.api.mvc.Results
import play.api.test.Helpers.{route, status, _}
import play.twirl.api.Html
import services.{PenaltiesCache, PenaltiesService}
import uk.gov.hmrc.viewmodels.NunjucksSupport

import scala.concurrent.Future

class SelectSchemeControllerSpec extends ControllerSpecBase with NunjucksSupport with JsonMatchers
  with BeforeAndAfterEach with Enumerable.Implicits with Results with ScalaFutures {

  import SelectSchemeControllerSpec._

  private val mockPenaltyService = mock[PenaltiesService]
  private val mockFICacheConnector = mock[FinancialInfoCacheConnector]
  private val mockFSConnector = mock[FinancialStatementConnector]
  private val mutableFakeDataRetrievalAction: MutableFakeDataRetrievalAction = new MutableFakeDataRetrievalAction

  private def form: Form[PenaltySchemes] =
    new SelectSchemeFormProvider()(penaltySchemes,
      messages("selectScheme.error", messages(s"penaltyType.${penaltyType.toString}").toLowerCase()))

  val extraModules: Seq[GuiceableModule] = Seq[GuiceableModule](
    bind[PenaltiesService].toInstance(mockPenaltyService),
    bind[FinancialInfoCacheConnector].toInstance(mockFICacheConnector),
    bind[FinancialStatementConnector].toInstance(mockFSConnector)
  )

  val application: Application = applicationBuilderMutableRetrievalAction(mutableFakeDataRetrievalAction, extraModules).build()

  private val jsonToTemplate: Form[PenaltySchemes] => JsObject = form => Json.obj(
    fields = "form" -> form,
    "radios" -> PenaltySchemes.radios(form, penaltySchemes)
  )

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockPenaltyService)
    reset(mockAppConfig)
    reset(mockFICacheConnector)
    reset(mockFSConnector)
    when(mockRenderer.render(any(), any())(any())).thenReturn(Future.successful(Html("")))
    when(mockPenaltyService.penaltySchemes(any(): Int, any(), any(), any())(any(), any())).thenReturn(Future.successful(penaltySchemes))
    when(mockPenaltyService.getPenaltiesForJourney(any(), any())(any(), any())).thenReturn(Future.successful(PenaltiesCache(psaId, "psa-name", psaFSResponse)))
  }

  "SelectScheme Controller" when {
    "on a GET" must {

      "return OK with the correct view and call the penalties service" in {

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
      "redirect to penalties page when valid data with associated scheme is submitted" in {
        val result = route(application, httpPOSTRequest(httpPathPOST, Map("value" -> Seq(ps1.pstr)))).value

        status(result) mustEqual SEE_OTHER

        redirectLocation(result) mustBe Some(routes.PenaltiesController.onPageLoadContract(year, srn, All).url)

      }

      "redirect to penalties page when valid data with unassociated scheme is submitted" in {


        val pstrIndex: String = psaFS.as[Seq[PsaFSDetail]].map(_.pstr).indexOf(ps2.pstr).toString

        val result = route(application, httpPOSTRequest(httpPathPOST, Map("value" -> Seq(ps2.pstr)))).value

        status(result) mustEqual SEE_OTHER

        redirectLocation(result) mustBe Some(routes.PenaltiesController.onPageLoadContract(year, pstrIndex, All).url)

      }

      "return a BAD REQUEST when invalid data is submitted" in {

        val result = route(application, httpPOSTRequest(httpPathPOST, Map("value" -> Seq("")))).value

        status(result) mustEqual BAD_REQUEST

      }
    }
  }
}

object SelectSchemeControllerSpec {
  private val template = "financialStatement/penalties/selectScheme.njk"
  private val year = "2020"
  private val ps1 = PenaltySchemes(name = Some("Assoc scheme"), pstr = "24000040IN", srn = Some(srn), None)
  private val ps2 = PenaltySchemes(name = None, pstr = "24000041IN", srn = None, None)
  val penaltySchemes: Seq[PenaltySchemes] = Seq(ps1, ps2)
  val psaFS: JsValue = Json.toJson(psaFSResponse)
  val penaltyType: PenaltyType = ContractSettlementCharges

  private def httpPathGETVersion: String = routes.SelectSchemeController.onPageLoad(penaltyType, year, All).url

  private def httpPathPOST: String = routes.SelectSchemeController.onSubmit(penaltyType, year, All).url
}
