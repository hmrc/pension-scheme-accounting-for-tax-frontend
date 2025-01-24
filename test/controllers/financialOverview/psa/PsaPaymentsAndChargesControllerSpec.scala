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
import connectors.FinancialStatementConnectorSpec.{psaFSResponse, psaFs}
import connectors.{FinancialStatementConnector, MinimalConnector}
import controllers.actions.{AllowAccessActionProviderForIdentifierRequest, FakeIdentifierAction, IdentifierAction}
import controllers.base.ControllerSpecBase
import controllers.financialOverview.psa.PsaPaymentsAndChargesControllerSpec.{responseOverdue, responseUpcoming}
import data.SampleData.{psaId, pstr, schemeName}
import matchers.JsonMatchers
import models.ChargeDetailsFilter.Overdue
import models.financialStatement.PsaFSChargeType.AFT_INITIAL_LFP
import models.financialStatement.PsaFSDetail
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, when}
import org.scalatest.BeforeAndAfterEach
import play.api.Application
import play.api.http.Status.OK
import play.api.inject.bind
import play.api.inject.guice.{GuiceApplicationBuilder, GuiceableModule}
import play.api.test.Helpers.{defaultAwaitTimeout, route, status, writeableOf_AnyContentAsEmpty}
import services.financialOverview.psa.{PenaltiesCache, PsaPenaltiesAndChargesService}
import uk.gov.hmrc.govukfrontend.views.viewmodels.table.Table
import views.html.financialOverview.psa.{PsaPaymentsAndChargesNewView, PsaPaymentsAndChargesView}

import java.time.LocalDate
import scala.concurrent.Future

class PsaPaymentsAndChargesControllerSpec extends ControllerSpecBase with JsonMatchers with BeforeAndAfterEach {

  private def httpPathGET: String =
    routes.PsaPaymentsAndChargesController.onPageLoad(Overdue).url

  private val mockPsaPenaltiesAndChargesService: PsaPenaltiesAndChargesService = mock[PsaPenaltiesAndChargesService]
  val mockFSConnector: FinancialStatementConnector = mock[FinancialStatementConnector]
  val mockMinimalConnector: MinimalConnector = mock[MinimalConnector]

  private val application: Application = new GuiceApplicationBuilder()
    .overrides(
      Seq[GuiceableModule](
        bind[IdentifierAction].to[FakeIdentifierAction],
        bind[FrontendAppConfig].toInstance(mockAppConfig),
        bind[FinancialStatementConnector].toInstance(mockFSConnector),
        bind[MinimalConnector].toInstance(mockMinimalConnector),
        bind[PsaPenaltiesAndChargesService].toInstance(mockPsaPenaltiesAndChargesService),
        bind[AllowAccessActionProviderForIdentifierRequest].toInstance(mockAllowAccessActionProviderForIdentifierRequest)
      ): _*
    )
    .build()

  private val penaltiesTable: Table = Table()

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockPsaPenaltiesAndChargesService)
    when(mockPsaPenaltiesAndChargesService.getPenaltiesAndCharges(any(), any(), any(), any())(any(), any(), any())).
      thenReturn(Future.successful(penaltiesTable))
    when(mockPsaPenaltiesAndChargesService.getPenaltiesForJourney(any(), any())(any(), any())).
      thenReturn(Future.successful(PenaltiesCache(psaId, "psa-name", psaFSResponse)))
    when(mockPsaPenaltiesAndChargesService.getOverdueCharges(any())).thenReturn(responseOverdue)
    when(mockPsaPenaltiesAndChargesService.extractUpcomingCharges(any())).thenReturn(responseUpcoming)
    when(mockMinimalConnector.getPsaOrPspName(any(), any(), any())).thenReturn(Future.successful("psa-name"))
    when(mockFSConnector.getPsaFSWithPaymentOnAccount(any())(any(), any())).thenReturn(Future.successful(psaFs))
    when(mockPsaPenaltiesAndChargesService.retrievePsaChargesAmount(any())).thenReturn(mockPsaPenaltiesAndChargesService.chargeAmount("100", "100", "100"))
  }

  "PsaPaymentsAndChargesController" must {

    "return OK and the new payments and charges information for a GET" in {
      when(mockAppConfig.podsNewFinancialCredits).thenReturn(true)

      val result = route(application, httpGETRequest(httpPathGET)).value
      status(result) mustEqual OK

      val view = application.injector.instanceOf[PsaPaymentsAndChargesNewView].apply(
        journeyType = "overdue", psaName = "psa-name", titleMessage = messages("psa.financial.overview.overdue.title.v2"), pstr = Some(pstr), reflectChargeText = "The information may not reflect payments made in the last 3 days.", totalOverdueCharge = "100", totalInterestAccruing = "100", totalUpcomingCharge = "100", totalOutstandingCharge = "100", penaltiesTable = penaltiesTable, paymentAndChargesTable = penaltiesTable
      )(fakeRequest, messages)

      compareResultAndView(result, view)
    }

    "return OK and the old payments and charges information for a GET" in {
      when(mockAppConfig.podsNewFinancialCredits).thenReturn(false)

      val result = route(application, httpGETRequest(httpPathGET)).value
      status(result) mustEqual OK

      val view = application.injector.instanceOf[PsaPaymentsAndChargesView].apply(
        journeyType = "overdue", schemeName = schemeName, psaName = "psa-name", titleMessage = messages("psa.financial.overview.overdue.title"), pstr = pstr, reflectChargeText = "The information may not reflect payments made in the last 3 days.", totalOverdueCharge = "100", totalInterestAccruing = "100", totalUpcomingCharge = "100", totalOutstandingCharge = "100", penaltiesTable = penaltiesTable, paymentAndChargesTable = penaltiesTable, returnUrl = ""
      )(messages, fakeRequest)

      compareResultAndView(result, view)
    }
  }

}

object PsaPaymentsAndChargesControllerSpec {
  private def createPsaFSCharge(chargeReference: String): PsaFSDetail = {
    PsaFSDetail(
      index = 0,
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
      sourceChargeRefForInterest = None,
      documentLineItemDetails = Nil
    )
  }

  val responseOverdue: Seq[PsaFSDetail] = Seq(
    createPsaFSCharge("XAB3497340527"),
    createPsaFSCharge("XY53243456464")
  )

  val responseUpcoming: Seq[PsaFSDetail] = Seq(
    createPsaFSCharge("XY549561095122"),
    createPsaFSCharge("XY122335465641")
  )

}