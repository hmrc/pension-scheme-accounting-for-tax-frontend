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
import connectors.MinimalConnector
import controllers.actions.{AllowAccessActionProviderForIdentifierRequest, FakeIdentifierAction, IdentifierAction}
import controllers.base.ControllerSpecBase
import data.SampleData.{dummyCall, emptyChargesTable, multiplePenalties, psaId, schemeDetails}
import matchers.JsonMatchers
import models.{Quarters, SchemeDetails}
import models.requests.IdentifierRequest
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, when}
import org.scalatest.BeforeAndAfterEach
import play.api.Application
import play.api.http.Status.OK
import play.api.inject.bind
import play.api.inject.guice.{GuiceApplicationBuilder, GuiceableModule}
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import play.api.test.Helpers.{defaultAwaitTimeout, route, status, writeableOf_AnyContentAsEmpty}
import play.twirl.api.Html
import services.SchemeService
import services.financialOverview.psa.{PenaltiesCache, PsaPenaltiesAndChargesService}
import uk.gov.hmrc.govukfrontend.views.Aliases.Table
import uk.gov.hmrc.govukfrontend.views.viewmodels.summarylist.SummaryListRow
import utils.DateHelper.{dateFormatterDMY, dateFormatterStartDate}
import views.html.financialOverview.psa.PsaPaymentsAndChargesNewView

import scala.concurrent.Future

class AllPenaltiesAndChargesControllerSpec extends ControllerSpecBase with JsonMatchers with BeforeAndAfterEach {

  private val startDate = "2020-07-01"
  private val endDate = "2020-09-30"
  val pstr = "24000041IN"

  private def httpPathGET(startDate: String = startDate): String =
    routes.AllPenaltiesAndChargesController.onPageLoadAFT(startDate, pstr).url

  private val mockSchemeService: SchemeService = mock[SchemeService]
  private val mockPsaPenaltiesAndChargesService = mock[PsaPenaltiesAndChargesService]
  private val mockMinimalConnector: MinimalConnector = mock[MinimalConnector]

  val emptyChargesTable: Table = Table()

  private val application: Application = new GuiceApplicationBuilder()
    .overrides(
      Seq[GuiceableModule](
        bind[IdentifierAction].to[FakeIdentifierAction],
        bind[FrontendAppConfig].toInstance(mockAppConfig),
        bind[PsaPenaltiesAndChargesService].toInstance(mockPsaPenaltiesAndChargesService),
        bind[AllowAccessActionProviderForIdentifierRequest].toInstance(mockAllowAccessActionProviderForIdentifierRequest)
      ): _*
    )
    .build()

  private implicit val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockPsaPenaltiesAndChargesService)
    when(mockAppConfig.schemeDashboardUrl(any(): IdentifierRequest[_])).thenReturn(dummyCall.url)
    when(mockPsaPenaltiesAndChargesService.getPenaltiesForJourney(any(), any())(any(), any())).
      thenReturn(Future.successful(PenaltiesCache(psaId, "psa-name", multiplePenalties)))
    when(mockPsaPenaltiesAndChargesService.getDueCharges(any())).thenReturn(multiplePenalties)
    when(mockPsaPenaltiesAndChargesService.getInterestCharges(any())).thenReturn(multiplePenalties)
    when(mockPsaPenaltiesAndChargesService.getAllPenaltiesAndCharges(any(), any(), any())(any(), any(), any())).
      thenReturn(Future.successful(emptyChargesTable))
    when(mockMinimalConnector.getPsaOrPspName(any(), any(), any())).thenReturn(Future.successful("psa-name"))
    when(mockSchemeService.retrieveSchemeDetails(any(), any(), any())(any(), any()))
      .thenReturn(Future.successful(SchemeDetails(schemeDetails.schemeName, pstr, "Open", None)))
  }

  "AllPenaltiesAndChargesController" must {

    "return OK and the correct view with filtered penalties and charges information for All journey for a GET" in {
      val result = route(application, httpGETRequest(httpPathGET())).value
      status(result) mustEqual OK

      val view = application.injector.instanceOf[PsaPaymentsAndChargesNewView].apply(
        psaName = "psa-name",
        journeyType = "all",
        titleMessage = "Accounting for Tax penalties for 1 July to 30 September 2020",
        pstr = Some("24000041IN"),
        reflectChargeText = "Amounts due may not reflect payments made in the last 3 days.",
        totalOverdueCharge = "0",
        totalInterestAccruing = "0",
        totalUpcomingCharge = "0",
        totalOutstandingCharge = "£200.00",
        penaltiesTable = emptyChargesTable,
        paymentAndChargesTable = emptyChargesTable
      )(request, messages)

      compareResultAndView(result, view)
    }
  }
}
