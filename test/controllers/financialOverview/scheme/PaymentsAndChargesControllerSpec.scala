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

package controllers.financialOverview.scheme

import config.FrontendAppConfig
import controllers.actions.{AllowAccessActionProviderForIdentifierRequest, FakeIdentifierAction, IdentifierAction}
import controllers.base.ControllerSpecBase
import data.SampleData._
import matchers.JsonMatchers
import models.ChargeDetailsFilter.Overdue
import models.financialStatement.SchemeFSChargeType.PSS_AFT_RETURN
import models.financialStatement.SchemeFSDetail
import models.requests.IdentifierRequest
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, when}
import org.scalatest.BeforeAndAfterEach
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.{GuiceApplicationBuilder, GuiceableModule}
import play.api.test.Helpers.{route, _}
import services.financialOverview.scheme.{PaymentsAndChargesService, PaymentsCache}
import uk.gov.hmrc.govukfrontend.views.viewmodels.table.Table
import views.html.financialOverview.scheme.{PaymentsAndChargesNewView, PaymentsAndChargesView}

import java.time.LocalDate
import scala.concurrent.Future

class PaymentsAndChargesControllerSpec extends ControllerSpecBase with JsonMatchers with BeforeAndAfterEach {

  import PaymentsAndChargesControllerSpec._

  private def httpPathGET: String =
    routes.PaymentsAndChargesController.onPageLoad(srn, Overdue).url

  private val paymentsCache: Seq[SchemeFSDetail] => PaymentsCache = schemeFSDetail => PaymentsCache(psaId, srn, schemeDetails, schemeFSDetail)

  private val penaltiesTable: Table = Table()

  private val mockPaymentsAndChargesService: PaymentsAndChargesService = mock[PaymentsAndChargesService]
  private val application: Application = new GuiceApplicationBuilder()
    .overrides(
      Seq[GuiceableModule](
        bind[IdentifierAction].to[FakeIdentifierAction],
        bind[FrontendAppConfig].toInstance(mockAppConfig),
        bind[PaymentsAndChargesService].toInstance(mockPaymentsAndChargesService),
        bind[AllowAccessActionProviderForIdentifierRequest].toInstance(mockAllowAccessActionProviderForIdentifierRequest)
      ): _*
    )
    .build()

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockPaymentsAndChargesService)
    when(mockAppConfig.schemeDashboardUrl(any(): IdentifierRequest[_])).thenReturn(dummyCall.url)
    when(mockPaymentsAndChargesService.getPaymentsForJourney(any(), any(), any(), any())(any(), any())).
      thenReturn(Future.successful(paymentsCache(schemeFSResponseOverdue)))
    when(mockPaymentsAndChargesService.getPaymentsAndCharges(ArgumentMatchers.eq(srn),
      any(), any(), any())(any())).thenReturn(penaltiesTable)
    when(mockPaymentsAndChargesService.getOverdueCharges(any())).thenReturn(schemeFSResponseOverdue)
    when(mockPaymentsAndChargesService.getInterestCharges(any())).thenReturn(schemeFSResponseOverdue)
    when(mockPaymentsAndChargesService.extractUpcomingCharges).thenReturn(_ => schemeFSResponseUpcoming)
  }

  "PaymentsAndChargesController" must {

    "return OK and the new view with filtered payments and charges information for a GET" in {
      when(mockAppConfig.podsNewFinancialCredits).thenReturn(true)
      val req = httpGETRequest(httpPathGET)
      val result = route(application, req).value
      status(result) mustEqual OK

      val view = application.injector.instanceOf[PaymentsAndChargesNewView].apply(
        journeyType = "overdue",
        schemeName = schemeDetails.schemeName,
        titleMessage = messages("schemeFinancial.overview.overdue.title.v2"),
        pstr = pstr,
        reflectChargeText = "This information may not reflect payments made in the last 3 days.",
        totalOverdue = "£3,087.15",
        totalInterestAccruing = "£0.00",
        totalUpcoming = "£0.00",
        totalDue = "£0.00",
        penaltiesTable = penaltiesTable,
        paymentAndChargesTable = penaltiesTable,
        returnUrl = "/financial-overview/test-srn",
        returnDashboardUrl = Option(mockAppConfig.managePensionsSchemeSummaryUrl).getOrElse("/pension-scheme-summary/%s").format(srn)
      )(req, messages)

      compareResultAndView(result, view)

    }

    "return OK and the old view with filtered payments and charges information for a GET" in {
      when(mockAppConfig.podsNewFinancialCredits).thenReturn(false)
      val req = httpGETRequest(httpPathGET)
      val result = route(application, req).value
      status(result) mustEqual OK

      val view = application.injector.instanceOf[PaymentsAndChargesView].apply(
        journeyType = "overdue",
        schemeName = schemeDetails.schemeName,
        titleMessage = messages("schemeFinancial.overview.overdue.title"),
        pstr = pstr,
        reflectChargeText = "The information may not reflect payments made in the last 3 days.",
        totalDue = "£2,058.10",
        totalInterestAccruing = "£0.00",
        totalUpcoming = "£0.00",
        penaltiesTable = penaltiesTable,
        paymentAndChargesTable = penaltiesTable,
        returnUrl = "/financial-overview/test-srn",
        returnDashboardUrl = Option(mockAppConfig.managePensionsSchemeSummaryUrl).getOrElse("/pension-scheme-summary/%s").format(srn),
      )(messages, req)

      compareResultAndView(result, view)

    }

    "redirect to Session Expired page when there is no data for a GET" in {
      when(mockPaymentsAndChargesService.getPaymentsForJourney(any(), any(), any(), any())(any(), any())).thenReturn(Future.successful(paymentsCache(Nil)))
      val result = route(application, httpGETRequest(httpPathGET)).value
      status(result) mustEqual SEE_OTHER
      redirectLocation(result).value mustBe controllers.routes.SessionExpiredController.onPageLoad.url
    }
  }
}

object PaymentsAndChargesControllerSpec {
  private val srn = "test-srn"
  private def createCharge(startDate: String, endDate: String, chargeReference: String): SchemeFSDetail = {
    SchemeFSDetail(
      index = 0,
      chargeReference = chargeReference,
      chargeType = PSS_AFT_RETURN,
      dueDate = Some(LocalDate.parse("2020-02-15")),
      totalAmount = 56432.00,
      outstandingAmount = 56049.08,
      stoodOverAmount = 25089.08,
      amountDue = 1029.05,
      accruedInterestTotal = 0.00,
      periodStartDate = Some(LocalDate.parse(startDate)),
      periodEndDate = Some(LocalDate.parse(endDate)),
      formBundleNumber = None,
      version = None,
      receiptDate = None,
      sourceChargeRefForInterest = None,
      sourceChargeInfo = None,
      documentLineItemDetails = Nil
    )
  }

  private val schemeFSResponseOverdue: Seq[SchemeFSDetail] = Seq(
    createCharge(startDate = "2020-04-01", endDate = "2020-06-30", chargeReference = "XY002610150184"),
    createCharge(startDate = "2020-01-01", endDate = "2020-03-31", chargeReference = "AYU3494534632"),
    createCharge(startDate = "2021-04-01", endDate = "2021-06-30", chargeReference = "XY002610150185")
  )

  private val schemeFSResponseUpcoming: Seq[SchemeFSDetail] = Seq(
    createCharge(startDate = "2022-04-01", endDate = "2022-06-30", chargeReference = "XY002610150184"),
    createCharge(startDate = "2023-01-01", endDate = "2023-03-31", chargeReference = "AYU3494534632")
  )
}
