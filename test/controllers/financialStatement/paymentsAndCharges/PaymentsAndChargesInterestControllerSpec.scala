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

package controllers.financialStatement.paymentsAndCharges

import config.FrontendAppConfig
import controllers.actions.{AllowAccessActionProviderForIdentifierRequest, FakeIdentifierAction, IdentifierAction}
import controllers.base.ControllerSpecBase
import data.SampleData._
import helpers.FormatHelper
import matchers.JsonMatchers
import models.ChargeDetailsFilter.All
import models.LocalDateBinder._
import models.financialStatement.PaymentOrChargeType.AccountingForTaxCharges
import models.financialStatement.SchemeFSChargeType.{PSS_AFT_RETURN, PSS_AFT_RETURN_INTEREST, PSS_OTC_AFT_RETURN, PSS_OTC_AFT_RETURN_INTEREST}
import models.financialStatement.{SchemeFSChargeType, SchemeFSDetail}
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{times, verify, when}
import org.scalatest.BeforeAndAfterEach
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.{GuiceApplicationBuilder, GuiceableModule}
import play.api.libs.json.{JsObject, Json}
import play.api.test.Helpers.{route, _}
import services.paymentsAndCharges.PaymentsAndChargesService
import uk.gov.hmrc.govukfrontend.views.viewmodels.content.Text
import uk.gov.hmrc.govukfrontend.views.viewmodels.summarylist.{Key, SummaryListRow, Value}
import utils.AFTConstants._
import utils.DateHelper

import java.time.LocalDate
import scala.concurrent.Future
import views.html.financialStatement.paymentsAndCharges.PaymentsAndChargeInterestView

class PaymentsAndChargesInterestControllerSpec extends ControllerSpecBase with JsonMatchers with BeforeAndAfterEach {

  import PaymentsAndChargesInterestControllerSpec._

  private def httpPathGET(startDate: LocalDate = QUARTER_START_DATE, index: String): String =
    routes.PaymentsAndChargesInterestController.onPageLoad(srn, startDate, index, AccountingForTaxCharges, All).url

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
    when(mockAppConfig.schemeDashboardUrl(any(), any())).thenReturn(dummyCall.url)
    when(mockRenderer.render(any(), any())(any())).thenReturn(Future.successful(play.twirl.api.Html("")))
  }

  private def chargeDetailsList(schemeFSDetail: SchemeFSDetail) = Seq(
    SummaryListRow(
      key = Key(Text(messages("paymentsAndCharges.interest")), classes = "govuk-!-padding-left-0 govuk-!-width-three-quarters"),
      value = Value(
        Text(s"${FormatHelper.formatCurrencyAmountAsString(schemeFSDetail.accruedInterestTotal)}"),
        classes = "govuk-!-width-one-quarter govuk-table__cell--numeric"
      )
    ),
    SummaryListRow(
      key = Key(
        Text(messages("paymentsAndCharges.interestFrom", DateHelper.formatDateDMY(schemeFSDetail.periodEndDate.map(_.plusDays(46))))),
        classes = "govuk-table__cell--numeric govuk-!-padding-right-0 govuk-!-width-three-quarters govuk-!-font-weight-bold"
      ),
      value = Value(
        Text(s"${FormatHelper.formatCurrencyAmountAsString(schemeFSDetail.accruedInterestTotal)}"),
        classes = "govuk-!-width-one-quarter govuk-table__cell--numeric govuk-!-font-weight-bold"
      )
    )
  )

  "PaymentsAndChargesInterestController" must {

    "return OK and the correct view for interest accrued for aft return charge if amount is due and interest is accruing for a GET" in {
      when(mockPaymentsAndChargesService.getPaymentsForJourney(any(), any(), any())(any(), any()))
        .thenReturn(Future.successful(paymentsCache(schemeFSResponse)))

      val schemeFSDetail = createCharge(chargeReference = "XY002610150184", chargeType = PSS_AFT_RETURN)

      val view = application.injector.instanceOf[PaymentsAndChargeInterestView].apply(
        PSS_AFT_RETURN_INTEREST.toString,
        tableHeader = messages("paymentsAndCharges.caption",
          DateHelper.formatStartDate(schemeFSDetail.periodStartDate),
          DateHelper.formatDateDMY(schemeFSDetail.periodEndDate)),
        chargeDetailsList = chargeDetailsList(schemeFSDetail),
        accruedInterest = schemeFSDetail.accruedInterestTotal,
        originalAmountUrl = controllers.financialStatement.paymentsAndCharges.routes.PaymentsAndChargeDetailsController
          .onPageLoad(srn, schemeFSDetail.periodStartDate.get, index = "0", AccountingForTaxCharges, All).url,
        returnUrl = dummyCall.url,
        schemeName
      )(httpGETRequest(httpPathGET(index = "0")), messages)

      val result = route(application, httpGETRequest(httpPathGET(index = "0"))).value
      status(result) mustEqual OK

      compareResultAndView(result, view)
    }

    "return OK and the correct view for interest accrued for overseas transfer charge if amount is due and interest is accruing for a GET" in {
      when(mockPaymentsAndChargesService.getPaymentsForJourney(any(), any(), any())(any(), any()))
        .thenReturn(Future.successful(paymentsCache(schemeFSResponse)))

      val schemeFSDetail = createCharge(chargeReference = "XY002610150185", chargeType = PSS_OTC_AFT_RETURN)

      val view = application.injector.instanceOf[PaymentsAndChargeInterestView].apply(
        PSS_OTC_AFT_RETURN_INTEREST.toString,
        tableHeader = messages("paymentsAndCharges.caption",
          DateHelper.formatStartDate(schemeFSDetail.periodStartDate),
          DateHelper.formatDateDMY(schemeFSDetail.periodEndDate)),
        chargeDetailsList = chargeDetailsList(schemeFSDetail),
        accruedInterest = schemeFSDetail.accruedInterestTotal,
        originalAmountUrl = controllers.financialStatement.paymentsAndCharges.routes.PaymentsAndChargeDetailsController
          .onPageLoad(srn, schemeFSDetail.periodStartDate.get, index = "1", AccountingForTaxCharges, All).url,
        returnUrl = dummyCall.url,
        schemeName
      )(httpGETRequest(httpPathGET(index = "1")), messages)

      val result = route(application, httpGETRequest(httpPathGET(index = "1"))).value
      status(result) mustEqual OK

      compareResultAndView(result, view)
    }

    "redirect to Session Expired page when there is no data for the selected charge reference for a GET" in {
      when(mockPaymentsAndChargesService.getPaymentsForJourney(any(), any(), any())(any(), any()))
        .thenReturn(Future.successful(paymentsCache(Seq.empty)))
      val result = route(application, httpGETRequest(httpPathGET(index = "0"))).value
      status(result) mustEqual SEE_OTHER
      redirectLocation(result).value mustBe controllers.routes.SessionExpiredController.onPageLoad.url
    }
  }
}

object PaymentsAndChargesInterestControllerSpec {
  private val srn = "test-srn"

  private def createCharge(chargeReference: String, chargeType: SchemeFSChargeType): SchemeFSDetail = {
    SchemeFSDetail(
      index = 0,
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

  private val schemeFSResponse: Seq[SchemeFSDetail] = Seq(
    createCharge(chargeReference = "XY002610150184", PSS_AFT_RETURN),
    createCharge(chargeReference = "XY002610150185", PSS_OTC_AFT_RETURN)
  )
}
