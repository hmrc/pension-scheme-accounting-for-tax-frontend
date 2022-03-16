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

package controllers.financialOverview

import config.FrontendAppConfig
import connectors.{FinancialStatementConnector, MinimalConnector}
import connectors.FinancialStatementConnectorSpec.psaFSResponse
import connectors.cache.FinancialInfoCacheConnector
import controllers.actions.{AllowAccessActionProviderForIdentifierRequest, FakeIdentifierAction, IdentifierAction}
import controllers.base.ControllerSpecBase
import controllers.financialOverview.PaymentsAndChargesControllerSpec.srn
import controllers.financialOverview.PsaPaymentsAndChargesControllerSpec.{responseOverdue, responseUpcoming}
import controllers.financialOverview.routes.PsaPaymentsAndChargesController
import controllers.financialStatement.penalties.SelectSchemeControllerSpec.psaFS
import data.SampleData.{dummyCall, emptyChargesTable, psaFsSeq, psaId, schemeDetails, srn}
import matchers.JsonMatchers
import models.ChargeDetailsFilter.Overdue
import models.financialStatement.{PsaFS, SchemeFS}
import models.financialStatement.PsaFSChargeType.AFT_INITIAL_LFP
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.scalatest.BeforeAndAfterEach
import play.api.Application
import play.api.http.Status.{OK, SEE_OTHER}
import play.api.inject.bind
import play.api.inject.guice.{GuiceApplicationBuilder, GuiceableModule}
import play.api.libs.json.{JsObject, Json}
import play.api.test.Helpers.{defaultAwaitTimeout, redirectLocation, route, status, writeableOf_AnyContentAsEmpty}
import play.twirl.api.Html
import services.financialOverview.{PaymentsCache, PenaltiesCache, PsaPenaltiesAndChargesService}
import services.paymentsAndCharges.PaymentsCache
import uk.gov.hmrc.nunjucks.NunjucksRenderer
import uk.gov.hmrc.viewmodels.NunjucksSupport
import viewmodels.Table

import java.time.LocalDate
import scala.concurrent.Future

class PsaPaymentsAndChargesControllerSpec extends ControllerSpecBase with NunjucksSupport with JsonMatchers with BeforeAndAfterEach {

  private def httpPathGET: String =
    PsaPaymentsAndChargesController.onPageLoad(Overdue).url

  private val mockPsaPenaltiesAndChargesService: PsaPenaltiesAndChargesService = mock[PsaPenaltiesAndChargesService]
  val mockFSConnector: FinancialStatementConnector = mock[FinancialStatementConnector]
  val mockMinimalConnector: MinimalConnector = mock[MinimalConnector]

  private val application: Application = new GuiceApplicationBuilder()
    .overrides(
      Seq[GuiceableModule](
        bind[IdentifierAction].to[FakeIdentifierAction],
        bind[NunjucksRenderer].toInstance(mockRenderer),
        bind[FrontendAppConfig].toInstance(mockAppConfig),
        bind[FinancialStatementConnector].toInstance(mockFSConnector),
        bind[MinimalConnector].toInstance(mockMinimalConnector),
        bind[PsaPenaltiesAndChargesService].toInstance(mockPsaPenaltiesAndChargesService),
        bind[AllowAccessActionProviderForIdentifierRequest].toInstance(mockAllowAccessActionProviderForIdentifierRequest)
      ): _*
    )
    .build()

  val penaltiesCache = PenaltiesCache(psaId, "psa-name", psaFSResponse)
  val noDataCache = PenaltiesCache("", "", Nil)

  val penaltiesTable: Table = Table(None, Nil, firstCellIsHeader = false, Nil, Nil, Nil)

  override def beforeEach: Unit = {
    super.beforeEach
    reset(mockRenderer, mockPsaPenaltiesAndChargesService)
    when(mockPsaPenaltiesAndChargesService.getAllPaymentsAndCharges(any(), any(), any(), any())(any(), any(), any())).
      thenReturn(Future.successful(penaltiesTable))
    when(mockPsaPenaltiesAndChargesService.getPenaltiesForJourney(any(), any())(any(), any())).
      thenReturn(Future.successful(PenaltiesCache(psaId, "psa-name", psaFSResponse)))
    when(mockRenderer.render(any(), any())(any())).thenReturn(Future.successful(Html("")))
    when(mockPsaPenaltiesAndChargesService.getOverdueCharges(any())).thenReturn(responseOverdue)
    when(mockPsaPenaltiesAndChargesService.extractUpcomingCharges(any())).thenReturn(responseUpcoming)
    when(mockMinimalConnector.getPsaOrPspName(any(), any(), any())).thenReturn(Future.successful("psa-name"))
    when(mockFSConnector.getPsaFSWithPaymentOnAccount(any())(any(), any())).thenReturn(Future.successful(psaFSResponse))
    when(mockPsaPenaltiesAndChargesService.retrievePsaChargesAmount(any())(any())).thenReturn(("100","100","100"))

  }

  private def expectedJson: JsObject = Json.obj(
    fields = "paymentAndChargesTable" -> penaltiesTable,
    "schemeName" -> schemeDetails.schemeName,
    "returnUrl" -> dummyCall.url
  )

  "PsaPaymentsAndChargesController" must {

    "return OK and the payments and charges information for a GET" in {

      val templateCaptor = ArgumentCaptor.forClass(classOf[String])
      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])
      val result = route(application, httpGETRequest(httpPathGET)).value
      status(result) mustEqual OK

      verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

      templateCaptor.getValue mustEqual "financialOverview/psaPaymentsAndCharges.njk"
      //jsonCaptor.getValue must containJson(expectedJson)
    }

    "redirect to Session Expired page when there is no data for a GET" in {
      when(mockPsaPenaltiesAndChargesService.getPenaltiesFromCache(any())(any(), any())).
        thenReturn(Future.successful(noDataCache))

      val result = route(application, httpGETRequest(httpPathGET)).value
      status(result) mustEqual SEE_OTHER
      redirectLocation(result).value mustBe controllers.routes.SessionExpiredController.onPageLoad.url
    }
  }

}

object PsaPaymentsAndChargesControllerSpec {

  private val pstr = "test-pstr"
  private def createPsaFSCharge(chargeReference: String): PsaFS = {
    PsaFS(
      chargeReference = chargeReference,
      chargeType = AFT_INITIAL_LFP,
      dueDate = Some(LocalDate.parse("2020-07-15")),
      totalAmount = 80000.00,
      outstandingAmount = 56049.08,
      stoodOverAmount = 25089.08,
      accruedInterestTotal = 0.00,
      amountDue = 1029.05,
      periodStartDate = LocalDate.parse("2020-04-01"),
      periodEndDate = LocalDate.parse("2020-06-30"),
      pstr = "24000040IN",
      documentLineItemDetails = Nil
    )
  }

  val responseOverdue: Seq[PsaFS] = Seq(
    createPsaFSCharge("XAB3497340527"),
    createPsaFSCharge("XY53243456464")
  )

  val responseUpcoming: Seq[PsaFS] = Seq(
    createPsaFSCharge("XY549561095122"),
    createPsaFSCharge("XY122335465641")
  )

}