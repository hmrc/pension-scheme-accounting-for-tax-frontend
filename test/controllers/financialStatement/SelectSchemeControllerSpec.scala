/*
 * Copyright 2020 HM Revenue & Customs
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

package controllers.financialStatement

import java.time.LocalDate

import connectors.FinancialStatementConnector
import connectors.FinancialStatementConnectorSpec.psaFSResponse
import connectors.cache.FinancialInfoCacheConnector
import controllers.actions.MutableFakeDataRetrievalAction
import controllers.base.ControllerSpecBase
import data.SampleData._
import forms.SelectSchemeFormProvider
import matchers.JsonMatchers
import models.financialStatement.PsaFS
import models.financialStatement.PsaFSChargeType.AFT_INITIAL_LFP
import models.{Enumerable, PenaltySchemes}
import org.mockito.ArgumentCaptor
import org.mockito.Matchers.any
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import play.api.Application
import play.api.data.Form
import play.api.inject.bind
import play.api.inject.guice.GuiceableModule
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.Results
import play.api.test.Helpers.{route, status, _}
import play.twirl.api.Html
import services.PenaltiesService
import uk.gov.hmrc.viewmodels.NunjucksSupport

import scala.concurrent.Future

class SelectSchemeControllerSpec extends ControllerSpecBase with NunjucksSupport with JsonMatchers
  with BeforeAndAfterEach with Enumerable.Implicits with Results with ScalaFutures {

  import SelectSchemeControllerSpec._

  private val mockPenaltyService = mock[PenaltiesService]
  private val mockFICacheConnector = mock[FinancialInfoCacheConnector]
  private val mockFSConnector = mock[FinancialStatementConnector]
  private val mutableFakeDataRetrievalAction: MutableFakeDataRetrievalAction = new MutableFakeDataRetrievalAction

  val extraModules: Seq[GuiceableModule] = Seq[GuiceableModule](
    bind[PenaltiesService].toInstance(mockPenaltyService),
    bind[FinancialInfoCacheConnector].toInstance(mockFICacheConnector),
    bind[FinancialStatementConnector].toInstance(mockFSConnector)
  )

  val application: Application = applicationBuilderMutableRetrievalAction(mutableFakeDataRetrievalAction, extraModules).build()

  private val jsonToTemplate: Form[PenaltySchemes] => JsObject = form => Json.obj(
    fields = "form" -> form,
    "radios" -> PenaltySchemes.radios(form, penaltySchemes),
    "submitUrl" -> routes.SelectSchemeController.onSubmit(year).url
  )

  override def beforeEach: Unit = {
    super.beforeEach
    reset(mockPenaltyService, mockAppConfig, mockFICacheConnector, mockFSConnector)
    when(mockRenderer.render(any(), any())(any())).thenReturn(Future.successful(Html("")))
    when(mockPenaltyService.penaltySchemes(any(), any())(any(), any())).thenReturn(Future.successful(penaltySchemes))
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
        when(mockFICacheConnector.fetch(any(), any())).thenReturn(Future.successful(Some(psaFS)))

        val result = route(application, httpPOSTRequest(httpPathPOST, Map("value" -> Seq(ps1.pstr)))).value

        status(result) mustEqual SEE_OTHER

        redirectLocation(result) mustBe Some(routes.PenaltiesController.onPageLoad(year, srn).url)

      }

      "redirect to penalties page when valid data with unassociated scheme is submitted" in {

        when(mockFICacheConnector.fetch(any(), any())).thenReturn(Future.successful(Some(psaFS)))

        val pstrIndex: String = (psaFS \ "psaFS").as[Seq[PsaFS]].map(_.pstr).indexOf(ps2.pstr).toString

        val result = route(application, httpPOSTRequest(httpPathPOST, Map("value" -> Seq(ps2.pstr)))).value

        status(result) mustEqual SEE_OTHER

        redirectLocation(result) mustBe Some(routes.PenaltiesController.onPageLoad(year, pstrIndex).url)

      }

      "redirect to sessionExpired page when valid data with unassociated scheme is submitted " +
        "but no pstrs return from fiCacheConnector" in {

        when(mockFICacheConnector.fetch(any(), any())).thenReturn(Future.successful(None))

        val result = route(application, httpPOSTRequest(httpPathPOST, Map("value" -> Seq(ps2.pstr)))).value

        status(result) mustEqual SEE_OTHER

        redirectLocation(result) mustBe Some(controllers.routes.SessionExpiredController.onPageLoad().url)

      }

      "return a BAD REQUEST when invalid data is submitted" in {

        val result = route(application, httpPOSTRequest(httpPathPOST, Map("value" -> Seq("")))).value

        status(result) mustEqual BAD_REQUEST

      }
    }
  }
}

object SelectSchemeControllerSpec {
  private val template = "financialStatement/selectScheme.njk"
  private val year = "2020"
  private val ps1 = PenaltySchemes(name = Some("Assoc scheme"), pstr = "24000040IN", srn = Some(srn))
  private val ps2 = PenaltySchemes(name = None, pstr = "24000041IN", srn = None)
  val penaltySchemes: Seq[PenaltySchemes] = Seq(ps1, ps2)
  val psaFS: JsObject = Json.obj("psaFS" -> psaFSResponse)

  private def form = new SelectSchemeFormProvider()(penaltySchemes)
  private def httpPathGETVersion: String = routes.SelectSchemeController.onPageLoad(year).url

  private def httpPathPOST: String = routes.SelectSchemeController.onSubmit(year).url
}
