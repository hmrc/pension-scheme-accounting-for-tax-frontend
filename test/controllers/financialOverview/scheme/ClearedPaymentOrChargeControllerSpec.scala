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
import data.SampleData.{psaId, schemeDetails, schemeFSResponseWithClearedPayments, schemeName, srn}
import helpers.FormatHelper
import models.financialStatement.{PaymentOrChargeType, SchemeFSChargeType}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, when}
import play.api.Application
import play.api.i18n.Messages
import play.api.inject.guice.{GuiceApplicationBuilder, GuiceableModule}
import play.api.inject.bind
import play.api.test.Helpers.{route, _}
import services.financialOverview.scheme.{PaymentsAndChargesService, PaymentsCache}
import uk.gov.hmrc.govukfrontend.views.Aliases.{Key, Text, Value}
import uk.gov.hmrc.govukfrontend.views.viewmodels.summarylist.SummaryListRow
import uk.gov.hmrc.govukfrontend.views.viewmodels.table.{HeadCell, Table, TableRow}
import utils.DateHelper.{formatDateDMY, formatStartDate}
import views.html.financialOverview.scheme.ClearedPaymentOrChargeView

import java.time.LocalDate
import scala.concurrent.Future

class ClearedPaymentOrChargeControllerSpec extends ControllerSpecBase {
  private def httpPathGET: String =
    routes.ClearedPaymentOrChargeController.onPageLoad(srn, "2020", PaymentOrChargeType.AccountingForTaxCharges, 0).url

  private val mockPaymentsAndChargesService = mock[PaymentsAndChargesService]

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

  private val schemeFSDetails = schemeFSResponseWithClearedPayments.seqSchemeFSDetail
  private val samplePaymentsCache = PaymentsCache(psaId, srn, schemeDetails, schemeFSDetails)

  private val chargeDetailsRow = Seq(
      SummaryListRow(
        key = Key(Text(Messages("pension.scheme.tax.reference.new")), classes = "govuk-!-padding-left-0 govuk-!-width-one-half"),
        value = Value(Text(s"${schemeDetails.pstr}"), classes = "govuk-!-width-one-half")
      ),
      SummaryListRow(
        key = Key(Text(Messages("financialPaymentsAndCharges.chargeReference")), classes = "govuk-!-padding-left-0 govuk-!-width-one-half"),
        value = Value(Text(schemeFSDetails.head.chargeReference), classes = "govuk-!-width-one-quarter")
      ),
      SummaryListRow(
        key = Key(Text(Messages("pension.scheme.interest.tax.period.new")), classes = "govuk-!-padding-left-0 govuk-!-width-one-half"),
        value = Value(Text(formatStartDate(schemeFSDetails.head.periodStartDate) + " to " +
          formatDateDMY(schemeFSDetails.head.periodEndDate)), classes = "govuk-!-width-one-half")
      )
  )

  private val table = {
    val headRow = Seq(
      HeadCell(Text(Messages("pension.scheme.chargeAmount.label.new"))),
      HeadCell(Text("")),
      HeadCell(Text(s"${FormatHelper.formatCurrencyAmountAsString(schemeFSDetails.head.totalAmount)}"), classes = "govuk-!-font-weight-regular govuk-!-text-align-right")
    )

    val rows = Seq(
      TableRow(Text(Messages("pension.scheme.financialPaymentsAndCharges.clearingReason.c2.new")), classes = "govuk-!-font-weight-bold govuk-!-width-one-half"),
      TableRow(Text(formatDateDMY(LocalDate.parse("2020-05-13")))),
      TableRow(Text(s"${FormatHelper.formatCurrencyAmountAsString(80.00)}"),
        classes = "govuk-!-font-weight-regular govuk-!-text-align-right")
    )

    Table(head = Some(headRow), rows = Seq(rows))
  }

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockPaymentsAndChargesService)
    when(mockPaymentsAndChargesService.getPaymentsForJourney(any(), any(), any(), any())(any(), any()))
      .thenReturn(Future.successful(samplePaymentsCache))
    when(mockPaymentsAndChargesService.getChargeDetailsForSelectedChargeV2(any(), any(), any())(any()))
      .thenReturn(chargeDetailsRow)
    when(mockPaymentsAndChargesService.chargeAmountDetailsRowsV2(any())(any()))
      .thenReturn(table)
  }

  "ClearedPaymentOrChargeController" must {
    "return OK and the correct view" in {
      val result = route(application, httpGETRequest(httpPathGET)).value
      status(result) mustEqual OK

      val view = application.injector.instanceOf[ClearedPaymentOrChargeView].apply(
        SchemeFSChargeType.PSS_AFT_RETURN.toString,
        schemeName,
        formatDateDMY(LocalDate.parse("2020-05-13")),
        chargeDetailsRow,
        table,
        returnDashboardUrl = Option(mockAppConfig.managePensionsSchemeSummaryUrl).getOrElse("/pension-scheme-summary/%s").format(srn),
        "/manage-pension-scheme-accounting-for-tax/aa/financial-overview/accounting-for-tax/2020/cleared-payments-and-charges"
      )(messages, httpGETRequest(httpPathGET))

      compareResultAndView(result, view)
    }
  }
}
