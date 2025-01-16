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
import data.SampleData.{dummyCall, multiplePenalties, psaId, schemeDetails, schemeName}
import matchers.JsonMatchers
import models.SchemeDetails
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
import uk.gov.hmrc.govukfrontend.views.viewmodels.content.HtmlContent
import uk.gov.hmrc.govukfrontend.views.viewmodels.summarylist.SummaryListRow
import viewmodels.PsaChargeDetailsViewModel
import views.html.financialOverview.psa.PsaChargeDetailsNewView

import scala.concurrent.Future

class AllPenaltiesAndChargesControllerSpec extends ControllerSpecBase with JsonMatchers with BeforeAndAfterEach {

  private val startDate = "2020-04-01"
  val pstr = "24000041IN"

  private def httpPathGET(startDate: String = startDate): String =
    routes.AllPenaltiesAndChargesController.onPageLoadAFT(startDate, pstr).url

  private val mockSchemeService: SchemeService = mock[SchemeService]
  private val mockPsaPenaltiesAndChargesService = mock[PsaPenaltiesAndChargesService]
  private val mockMinimalConnector: MinimalConnector = mock[MinimalConnector]

  val emptyChargesTable: Table = Table()
  val emptySummaryList: SummaryListRow = SummaryListRow()

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
    when(mockRenderer.render(any(), any())(any())).thenReturn(Future.successful(Html("")))
    /*when(mockNavigationService.penaltySchemes(any(): Int, any(), any(), any())(any(), any())).
      thenReturn(Future.successful(penaltySchemes))*/

  }

//  private def expectedJson: JsObject = Json.obj(
//    fields = "paymentAndChargesTable" -> emptyChargesTable,
//    "pstr" -> "24000041IN",
//    "totalOutstandingCharge" -> "£200.00"
//  )

  "AllPenaltiesAndChargesController" must {

    "return OK and the correct view with filtered penalties and charges information for All journey for a GET" in {
      val result = route(application, httpGETRequest(httpPathGET())).value
      status(result) mustEqual OK

      val template = PsaChargeDetailsViewModel(
        heading = "Accounting for Tax payments and charges for 1 April to 30 June 2020",
        psaName = "John Doe",
        schemeName = schemeName,
        isOverdue = false,
        chargeReference = " ",
        penaltyAmount = 0,
        insetText = HtmlContent(""),
        isInterestPresent = false,
        chargeHeaderDetails = Some(Seq(emptySummaryList)),
        chargeAmountDetails = Some(emptyChargesTable),
        returnUrl = dummyCall.url,
        returnUrlText = ""
      )

      val view = application.injector.instanceOf[PsaChargeDetailsNewView].apply(template)

      compareResultAndView(result, view)
    }
  }
}
