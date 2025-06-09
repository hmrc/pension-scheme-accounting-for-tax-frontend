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
import models.financialStatement.PaymentOrChargeType.AccountingForTaxCharges
import models.financialStatement.SchemeFSChargeType.PSS_AFT_RETURN
import models.financialStatement.SchemeFSDetail
import models.requests.IdentifierRequest
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, when}
import org.mockito.ArgumentMatchers
import org.scalatest.BeforeAndAfterEach
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.{GuiceApplicationBuilder, GuiceableModule}
import play.api.test.Helpers.{route, _}
import services.financialOverview.scheme.{PaymentsAndChargesService, PaymentsCache}
import uk.gov.hmrc.govukfrontend.views.Aliases.Table
import views.html.financialOverview.scheme.PaymentsAndChargesView

import java.time.LocalDate
import scala.concurrent.Future

class AllPaymentsAndChargesControllerSpec extends ControllerSpecBase with JsonMatchers with BeforeAndAfterEach {

  import AllPaymentsAndChargesControllerSpec._

  private val startDate = "2020-04-01"
  val pstr = "24000041IN"

  private def httpPathGET(startDate: String = startDate): String =
    routes.AllPaymentsAndChargesController.onPageLoad(srn, startDate, AccountingForTaxCharges).url

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

  val emptyChargesTable: Table = Table()

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockPaymentsAndChargesService)
    when(mockAppConfig.schemeDashboardUrl(any(): IdentifierRequest[_])).thenReturn(dummyCall.url)
    when(mockPaymentsAndChargesService.getPaymentsForJourney(any(), any(), any(), any())(any(), any()))
      .thenReturn(Future.successful(paymentsCache(schemeFSResponse)))
    when(mockPaymentsAndChargesService.getDueCharges(any()))
      .thenReturn(schemeFSResponse)
    when(mockPaymentsAndChargesService.getInterestCharges(any()))
      .thenReturn(schemeFSResponse)
    when(mockPaymentsAndChargesService.getPaymentsAndCharges(ArgumentMatchers.eq(srn), any(), any())(any())).thenReturn(emptyChargesTable)
  }

  "AllPaymentsAndChargesController" must {

    "return OK and the correct view with filtered payments and charges information for a GET" in {
      val req = httpGETRequest(httpPathGET())
      val result = route(application, req).value

      status(result) mustEqual OK

      val view = application.injector.instanceOf[PaymentsAndChargesView].apply(
        journeyType = "all",
        schemeName = schemeDetails.schemeName,
        titleMessage = "Accounting for Tax payments and charges for 1 April to 30 June 2020",
        pstr = "pstr",
        reflectChargeText = "Amounts due may not reflect payments made in the last 3 days.",
        totalOverdue = "£56049.08",
        totalInterestAccruing = "0",
        totalUpcoming = "£1,029.05",
        totalDue = "£3,087.15",
        penaltiesTable = emptyChargesTable,
        paymentAndChargesTable = emptyChargesTable,
        returnUrl = "/financial-overview/test-srn",
        returnDashboardUrl = Option(mockAppConfig.managePensionsSchemeSummaryUrl).getOrElse("/pension-scheme-summary/%s").format(srn)
      )(req, messages)

      compareResultAndView(result, view)
    }

    "redirect to Session Expired page when there is no data for the selected year for a GET" in {
      val result = route(application, httpGETRequest(httpPathGET(startDate = "2022-01-01"))).value
      status(result) mustEqual SEE_OTHER
      redirectLocation(result).value mustBe controllers.routes.SessionExpiredController.onPageLoad.url
    }
  }
}

object AllPaymentsAndChargesControllerSpec {
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

  private val schemeFSResponse: Seq[SchemeFSDetail] = Seq(
    createCharge(startDate = "2020-04-01", endDate = "2020-06-30", chargeReference = "XY002610150184"),
    createCharge(startDate = "2020-01-01", endDate = "2020-03-31", chargeReference = "AYU3494534632"),
    createCharge(startDate = "2021-04-01", endDate = "2021-06-30", chargeReference = "XY002610150185")
  )

  private val paymentsCache: Seq[SchemeFSDetail] => PaymentsCache = schemeFSDetail => PaymentsCache(psaId, srn, schemeDetails, schemeFSDetail)
}


