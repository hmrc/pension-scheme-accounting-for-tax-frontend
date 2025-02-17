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

package controllers.financialOverview.psa

import config.FrontendAppConfig
import controllers.actions.{AllowAccessActionProviderForIdentifierRequest, FakeIdentifierAction, IdentifierAction}
import controllers.base.ControllerSpecBase
import data.SampleData
import data.SampleData.psaId
import helpers.FormatHelper
import models.financialStatement.PenaltyType
import models.financialStatement.PsaFSChargeType.AFT_INITIAL_LFP
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, when}
import play.api.Application
import play.api.i18n.Messages
import play.api.inject.bind
import play.api.inject.guice.{GuiceApplicationBuilder, GuiceableModule}
import play.api.test.Helpers.{route, _}
import services.financialOverview.psa.{PenaltiesCache, PsaPenaltiesAndChargesService}
import uk.gov.hmrc.govukfrontend.views.viewmodels.content.Text
import uk.gov.hmrc.govukfrontend.views.viewmodels.summarylist.{Key, SummaryListRow, Value}
import uk.gov.hmrc.govukfrontend.views.viewmodels.table.{HeadCell, Table, TableRow}
import utils.DateHelper.{formatDateDMY, formatStartDate}
import views.html.financialOverview.psa.ClearedPenaltyOrChargeView

import java.time.LocalDate
import scala.concurrent.Future

class ClearedPenaltyOrChargeControllerSpec extends ControllerSpecBase {
  private def httpPathGET: String =
    routes.ClearedPenaltyOrChargeController.onPageLoad("2020", PenaltyType.AccountingForTaxPenalties, 0).url

  private val mockPsaPenaltiesAndChargesService = mock[PsaPenaltiesAndChargesService]

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

  private val returnedPsaFsDetails = SampleData.psaFsSeqWithCleared

  private val chargeDetailsRow = Seq(
    SummaryListRow(
      key = Key(Text(Messages("psa.pension.scheme.tax.reference.new")), classes = "govuk-!-padding-left-0 govuk-!-width-one-half"),
      value = Value(Text(s"${returnedPsaFsDetails.head.pstr}"), classes = "govuk-!-width-one-half")
    ),
    SummaryListRow(
      key = Key(Text(Messages("psa.financial.overview.charge.reference")), classes = "govuk-!-padding-left-0 govuk-!-width-one-half"),
      value = Value(Text(s"${returnedPsaFsDetails.head.chargeReference}"), classes = "govuk-!-width-one-quarter")
    ),
    SummaryListRow(
      key = Key(Text(Messages("pension.scheme.interest.tax.period.new")), classes = "govuk-!-padding-left-0 govuk-!-width-one-half"),
      value = Value(Text(formatStartDate(returnedPsaFsDetails.head.periodStartDate) + " to " +
        formatDateDMY(returnedPsaFsDetails.head.periodEndDate)), classes = "govuk-!-width-one-half")
    )
  )

  private val table = {
    val headRow = Seq(
      HeadCell(Text(Messages("psa.pension.scheme.chargeAmount.label.new"))),
      HeadCell(Text("")),
      HeadCell(Text(s"${FormatHelper.formatCurrencyAmountAsString(returnedPsaFsDetails.head.totalAmount)}"), classes = "govuk-!-font-weight-regular")
    )

    val rows = Seq(
      TableRow(Text("Credit applied"), classes = "govuk-!-font-weight-bold"),
      TableRow(Text(formatDateDMY(LocalDate.parse("2020-08-13")))),
      TableRow(Text(s"${FormatHelper.formatCurrencyAmountAsString(80.00)}"))
    )

    Table(head = Some(headRow), rows = Seq(rows))
  }

  private def returnLink: String =
    routes.ClearedPenaltiesAndChargesController.onPageLoad("2020", PenaltyType.AccountingForTaxPenalties).url

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockPsaPenaltiesAndChargesService)
    when(mockPsaPenaltiesAndChargesService.getPenaltiesForJourney(any(), any())(any(), any())).
      thenReturn(Future.successful(PenaltiesCache(psaId, "psa-name", SampleData.psaFsSeqWithCleared)))
    when(mockPsaPenaltiesAndChargesService.getChargeDetailsForClearedCharge(any())(any()))
      .thenReturn(chargeDetailsRow)
    when(mockPsaPenaltiesAndChargesService.chargeAmountDetailsRows(any(), any(), any())(any()))
      .thenReturn(table)
    when(mockPsaPenaltiesAndChargesService.getClearingDate(any())).thenReturn("13 August 2020")
  }

  "ClearedPenaltyOrChargeController" must {
    "return OK and the correct view" in {
      val result = route(application, httpGETRequest(httpPathGET)).value
      status(result) mustEqual OK

      val view = application.injector.instanceOf[ClearedPenaltyOrChargeView].apply(
        AFT_INITIAL_LFP.toString,
        "psa-name",
        "£0.00",
        formatDateDMY(LocalDate.parse("2020-08-13")),
        chargeDetailsRow,
        table,
        returnLink
      )(messages, httpGETRequest(httpPathGET))

      compareResultAndView(result, view)
    }
  }

}
