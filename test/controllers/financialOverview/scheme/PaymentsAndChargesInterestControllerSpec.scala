/*
 * Copyright 2025 HM Revenue & Customs
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
import models.LocalDateBinder._
import models.financialStatement.PaymentOrChargeType.AccountingForTaxCharges
import models.financialStatement.SchemeFSChargeType.{PSS_AFT_RETURN, PSS_OTC_AFT_RETURN}
import models.financialStatement.{SchemeFSChargeType, SchemeFSDetail}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, when}
import org.scalatest.BeforeAndAfterEach
import play.api.Application
import play.api.i18n.Messages
import play.api.inject.bind
import play.api.inject.guice.{GuiceApplicationBuilder, GuiceableModule}
import play.api.test.Helpers.{route, _}
import services.financialOverview.scheme.{PaymentsAndChargesService, PaymentsCache}
import uk.gov.hmrc.govukfrontend.views.Aliases.Text
import uk.gov.hmrc.govukfrontend.views.viewmodels.content.HtmlContent
import uk.gov.hmrc.govukfrontend.views.viewmodels.summarylist.{Key, SummaryListRow, Value}
import utils.AFTConstants._
import utils.DateHelper.formatDateDMY
import viewmodels.InterestDetailsViewModel
import views.html.financialOverview.scheme.PaymentsAndChargeInterestNewView

import java.time.LocalDate
import scala.concurrent.Future

class PaymentsAndChargesInterestControllerSpec extends ControllerSpecBase with JsonMatchers with BeforeAndAfterEach {

  import PaymentsAndChargesInterestControllerSpec._

  private def httpPathGET: String = routes.PaymentsAndChargesInterestController.onPageLoad(srn, startDate, "1", AccountingForTaxCharges, Some(versionInt), Some(submittedDate), Overdue).url

  private val paymentsCache: Seq[SchemeFSDetail] => PaymentsCache = schemeFSDetail => PaymentsCache(psaId, srn, schemeDetails, schemeFSDetail)

  val mockPaymentsAndChargesService: PaymentsAndChargesService = mock[PaymentsAndChargesService]

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
    when(mockAppConfig.schemeDashboardUrl(any(), any())).thenReturn(dummyCall.url)
    when(mockPaymentsAndChargesService.getReturnLinkBasedOnJourney(any(), any())(any()))
      .thenReturn(dummyCall.url)
    when(mockPaymentsAndChargesService.getReturnUrl(any(), any(), any(), any(), any()))
      .thenReturn(dummyCall.url)
    when(mockAppConfig.podsNewFinancialCredits).thenReturn(true)
  }

  "PaymentsAndChargesInterestController" must {

    "return OK and the correct view for interest accrued for aft return charge if amount is due and interest is accruing for a GET" in {
      when(mockPaymentsAndChargesService.getPaymentsForJourney(any(), any(), any())(any(), any()))
        .thenReturn(Future.successful(paymentsCache(schemeFSResponse)))
      when(mockAppConfig.podsNewFinancialCredits).thenReturn(true)

      val schemeFSDetail = createCharge(index = 1, chargeReference = "XY002610150184", chargeType = PSS_AFT_RETURN)

      val req = httpGETRequest(httpPathGET)
      val result = route(application, req).value

      status(result) mustEqual OK

      val view = application.injector.instanceOf[PaymentsAndChargeInterestNewView].apply(
        model = InterestDetailsViewModel(
          chargeType = "Accounting for Tax Return Interest submission 1",
          schemeName = schemeDetails.schemeName,
          interestDueAmount = Some("£2,000.00"),
          accruedInterest = schemeFSDetail.accruedInterestTotal,
          chargeDetailsList = getRows(schemeFSDetail),
          originalAmountUrl = routes.PaymentsAndChargeDetailsController.onPageLoad(srn, startDate, "0", AccountingForTaxCharges, Some(versionInt), Some(submittedDate), Overdue).url,
          tableHeader = Some(mockPaymentsAndChargesService.setPeriod(schemeFSDetail.chargeType,
            schemeFSDetail.periodStartDate,
            schemeFSDetail.periodEndDate)),
          insetText = HtmlContent("<p class=govuk-body>The charge reference for the interest due will show once you have paid the <span><a id='breakdown' class=govuk-link href=/manage-pension-scheme-accounting-for-tax/test-srn/financial-overview/accounting-for-tax/2020-04-01/1/2016-12-17/1/overdue-charge-details> original amount due</a></span> in full. You can only pay the interest once a charge reference has been generated.</p>"),
          returnUrl = dummyCall.url,
          returnLinkBasedOnJourney = dummyCall.url,
        )
      )(messages, req)

      compareResultAndView(result, view)
    }

    "redirect to Session Expired page when there is no data for the selected charge reference for a GET" in {
      when(mockPaymentsAndChargesService.getPaymentsForJourney(any(), any(), any())(any(), any()))
        .thenReturn(Future.successful(paymentsCache(Seq.empty)))
      val result = route(application, httpGETRequest(httpPathGET)).value
      status(result) mustEqual SEE_OTHER
      redirectLocation(result).value mustBe controllers.routes.SessionExpiredController.onPageLoad.url
    }
  }
}

object PaymentsAndChargesInterestControllerSpec {
  private val srn = "test-srn"

  private def createCharge(index: Int, chargeReference: String, chargeType: SchemeFSChargeType): SchemeFSDetail = {
    SchemeFSDetail(
      index = index,
      chargeReference = chargeReference,
      chargeType = chargeType,
      dueDate = Some(LocalDate.parse("2020-02-15")),
      totalAmount = 56432.00,
      outstandingAmount = 56049.08,
      stoodOverAmount = 25089.08,
      amountDue = 1234.00,
      accruedInterestTotal = 2000.00,
      periodStartDate = Some(LocalDate.parse(QUARTER_START_DATE)),
      periodEndDate = Some(LocalDate.parse(QUARTER_END_DATE)),
      formBundleNumber = None,
      version = None,
      receiptDate = None,
      sourceChargeRefForInterest = None,
      sourceChargeInfo = None,
      documentLineItemDetails = Nil
    )
  }

  private def getRows(schemeFSDetail: SchemeFSDetail)(implicit messages: Messages): Seq[SummaryListRow] =
    Seq(
      SummaryListRow(
        key = Key(
          content = Text(Messages("financialPaymentsAndCharges.chargeReference")),
          classes = "govuk-!-padding-left-0 govuk-!-width-one-quarter"
        ),
        value = Value(
          content = Text(Messages("paymentsAndCharges.chargeReference.toBeAssigned")),
          classes = "govuk-!-width-one-quarter"
        ),
        actions = None
      ),
      SummaryListRow(
        key = Key(
          content = Text(Messages("pension.scheme.interest.tax.period.new")),
          classes = "govuk-!-padding-left-0 govuk-!-width-three-quarters govuk-!-font-weight-bold"
        ),
        value = Value(
          content = Text(
            s"${formatDateDMY(schemeFSDetail.periodStartDate)} to ${formatDateDMY(schemeFSDetail.periodEndDate)}"
          ),
          classes = "govuk-!-width-one-quarter"
        ),
        actions = None
      )
    )

  private val schemeFSResponse: Seq[SchemeFSDetail] = Seq(
    createCharge(1, chargeReference = "XY002610150184", PSS_AFT_RETURN),
    createCharge(2, chargeReference = "XY002610150185", PSS_OTC_AFT_RETURN)
  )
}
