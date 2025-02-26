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

import connectors.FinancialStatementConnectorSpec.psaFSResponse
import controllers.actions.MutableFakeDataRetrievalAction
import controllers.base.ControllerSpecBase
import data.SampleData._
import forms.SelectSchemeFormProvider
import matchers.JsonMatchers
import models.PenaltiesFilter.All
import models.financialStatement.PenaltyType.ContractSettlementCharges
import models.financialStatement.{PenaltyType, PsaFSDetail}
import models.{Enumerable, PenaltySchemes}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import play.api.Application
import play.api.data.Form
import play.api.inject.bind
import play.api.inject.guice.GuiceableModule
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.Results
import play.api.test.Helpers._
import services.{PenaltiesCache, PenaltiesService}
import views.html.financialStatement.penalties.SelectSchemeView

import scala.concurrent.Future

class SelectSchemeControllerSpec extends ControllerSpecBase with JsonMatchers
  with BeforeAndAfterEach with Enumerable.Implicits with Results with ScalaFutures {

  import SelectSchemeControllerSpec._

  private val mockPenaltyService = mock[PenaltiesService]
  private val mutableFakeDataRetrievalAction: MutableFakeDataRetrievalAction = new MutableFakeDataRetrievalAction

  private def form: Form[PenaltySchemes] =
    new SelectSchemeFormProvider()(penaltySchemes,
      messages("selectScheme.error", messages(s"penaltyType.${penaltyType.toString}").toLowerCase()))

  val extraModules: Seq[GuiceableModule] = Seq[GuiceableModule](
    bind[PenaltiesService].toInstance(mockPenaltyService)
  )

  val application: Application = applicationBuilderMutableRetrievalAction(mutableFakeDataRetrievalAction, extraModules).build()

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockPenaltyService)
    when(mockPenaltyService.penaltySchemes(any(): Int, any(), any(), any())(any(), any())).thenReturn(Future.successful(penaltySchemes))
    when(mockPenaltyService.getPenaltiesForJourney(any(), any())(any(), any())).thenReturn(Future.successful(PenaltiesCache(psaId, "psa-name", psaFSResponse)))
    when(mockPenaltyService.getTypeParam(any())(any())).thenReturn(messages(s"penaltyType.ContractSettlementCharges"))
  }

  "SelectScheme Controller" when {
    "on a GET" must {
      "return OK with the correct view and call the penalties service" in {
        val result = route(application, httpGETRequest(httpPathGETVersion)).value

        val typeParam = messages(s"penaltyType.${penaltyType.toString}")

        val view = application.injector.instanceOf[SelectSchemeView].apply(
          form,
          typeParam,
          radios = PenaltySchemes.radios(form, penaltySchemes),
          submitCall = routes.SelectSchemeController.onSubmit(penaltyType, year, All),
          returnUrl = "",
          psaName = "psa-name"
        )(httpGETRequest(httpPathGETVersion), messages)

        status(result) mustEqual OK

        compareResultAndView(result,  view)
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
  private val year = "2020"
  private val ps1 = PenaltySchemes(name = Some("Assoc scheme"), pstr = "24000040IN", srn = Some(srn), None)
  private val ps2 = PenaltySchemes(name = None, pstr = "24000041IN", srn = None, None)
  val penaltySchemes: Seq[PenaltySchemes] = Seq(ps1, ps2)
  val psaFS: JsValue = Json.toJson(psaFSResponse)
  val penaltyType: PenaltyType = ContractSettlementCharges

  private def httpPathGETVersion: String = routes.SelectSchemeController.onPageLoad(penaltyType, year, All).url

  private def httpPathPOST: String = routes.SelectSchemeController.onSubmit(penaltyType, year, All).url
}
