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
import connectors.FinancialStatementConnectorSpec.psaFSResponse
import connectors.MinimalConnector
import controllers.actions.{AllowAccessActionProviderForIdentifierRequest, FakeIdentifierAction, IdentifierAction}
import controllers.base.ControllerSpecBase
import controllers.financialOverview.psa.SelectSchemeControllerSpec.pstr
import data.SampleData.{dummyCall, emptyChargesTable, psaFsSeq, psaId, schemeDetails}
import matchers.JsonMatchers
import models.SchemeDetails
import models.requests.IdentifierRequest
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.scalatest.BeforeAndAfterEach
import play.api.Application
import play.api.http.Status.OK
import play.api.inject.bind
import play.api.inject.guice.{GuiceApplicationBuilder, GuiceableModule}
import play.api.libs.json.{JsObject, Json}
import play.api.test.Helpers.{defaultAwaitTimeout, route, status, writeableOf_AnyContentAsEmpty}
import play.twirl.api.Html
import services.SchemeService
import services.financialOverview.{PenaltiesCache, PsaPenaltiesAndChargesService}
import uk.gov.hmrc.nunjucks.NunjucksRenderer
import uk.gov.hmrc.viewmodels.NunjucksSupport

import java.time.LocalDate
import scala.concurrent.Future

class AllPenaltiesAndChargesControllerSpec extends ControllerSpecBase with NunjucksSupport with JsonMatchers with BeforeAndAfterEach {

  private val startDate = LocalDate.parse("2020-07-01")
  private def httpPathGET(startDate: String = startDate.toString): String =
    routes.AllPenaltiesAndChargesController.onPageLoadAFT(startDate, pstr).url

  private val mockSchemeService: SchemeService = mock[SchemeService]
  private val mockPsaPenaltiesAndChargesService = mock[PsaPenaltiesAndChargesService]
  private val mockMinimalConnector: MinimalConnector = mock[MinimalConnector]

  private val application: Application = new GuiceApplicationBuilder()
    .overrides(
      Seq[GuiceableModule](
        bind[IdentifierAction].to[FakeIdentifierAction],
        bind[NunjucksRenderer].toInstance(mockRenderer),
        bind[FrontendAppConfig].toInstance(mockAppConfig),
        bind[PsaPenaltiesAndChargesService].toInstance(mockPsaPenaltiesAndChargesService),
        bind[AllowAccessActionProviderForIdentifierRequest].toInstance(mockAllowAccessActionProviderForIdentifierRequest)
      ): _*
    )
    .build()

  override def beforeEach: Unit = {
    super.beforeEach
    reset(mockRenderer, mockPsaPenaltiesAndChargesService)
    when(mockAppConfig.schemeDashboardUrl(any(): IdentifierRequest[_])).thenReturn(dummyCall.url)
    when(mockPsaPenaltiesAndChargesService.getPenaltiesForJourney(any(), any())(any(), any())).
      thenReturn(Future.successful(PenaltiesCache(psaId, "psa-name", psaFSResponse)))
    when(mockPsaPenaltiesAndChargesService.getDueCharges(any())).thenReturn(psaFSResponse)
    when(mockPsaPenaltiesAndChargesService.getInterestCharges(any())).thenReturn(psaFSResponse)
    when(mockPsaPenaltiesAndChargesService.getAllPenaltiesAndCharges(any(), any(), any(), any())(any(), any(), any())).
      thenReturn(Future.successful(emptyChargesTable))
    when(mockMinimalConnector.getPsaOrPspName(any(), any(), any())).thenReturn(Future.successful("psa-name"))
    when(mockSchemeService.retrieveSchemeDetails(any(), any(), any())(any(), any()))
      .thenReturn(Future.successful(SchemeDetails(schemeDetails.schemeName, pstr, "Open", None)))
    when(mockRenderer.render(any(), any())(any())).thenReturn(Future.successful(Html("")))
    /*when(mockNavigationService.penaltySchemes(any(): Int, any(), any(), any())(any(), any())).
      thenReturn(Future.successful(penaltySchemes))*/

  }

  private def expectedJson: JsObject = Json.obj(
    fields = "paymentAndChargesTable" -> emptyChargesTable,
    "pstr" -> "24000040IN",
    "totalOutstandingCharge" -> "£3,087.15"
  )

  "AllPenaltiesAndChargesController" must {

    "return OK and the correct view with filtered penalties and charges information for All journey for a GET" in {
      val templateCaptor = ArgumentCaptor.forClass(classOf[String])
      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])
      val result = route(application, httpGETRequest(httpPathGET())).value
      status(result) mustEqual OK

      verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

      templateCaptor.getValue mustEqual "financialOverview/psaPaymentsAndCharges.njk"
      jsonCaptor.getValue must containJson(expectedJson)
    }

  }

}
