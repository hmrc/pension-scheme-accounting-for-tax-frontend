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
import connectors.FinancialStatementConnectorSpec.psaFs
import connectors.{FinancialStatementConnector, MinimalConnector}
import controllers.actions.{AllowAccessActionProviderForIdentifierRequest, FakeIdentifierAction, IdentifierAction}
import controllers.base.ControllerSpecBase
import models.financialStatement.PsaFSDetail
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import play.api.Application
import play.api.http.Status.OK
import play.api.i18n.Messages
import play.api.inject.bind
import play.api.inject.guice.{GuiceApplicationBuilder, GuiceableModule}
import play.api.test.Helpers.{route, _}
import services.AFTPartialService
import uk.gov.hmrc.govukfrontend.views.Aliases.{HeadCell, Table, TableRow, Text}
import uk.gov.hmrc.govukfrontend.views.viewmodels.content.HtmlContent
import utils.DateHelper.formatDateDMY
import views.html.financialOverview.psa.RefundsView

import scala.concurrent.Future

class RefundsControllerSpec extends ControllerSpecBase {

  private def httpPathGET: String = routes.RefundsController.onPageLoad().url

  private val mockAFTPartialService = mock[AFTPartialService]
  private val mockFinancialStatementConnector = mock[FinancialStatementConnector]
  private val mockMinimalConnector = mock[MinimalConnector]

  private val application: Application = new GuiceApplicationBuilder()
    .overrides(
      Seq[GuiceableModule](
        bind[IdentifierAction].to[FakeIdentifierAction],
        bind[FrontendAppConfig].toInstance(mockAppConfig),
        bind[AFTPartialService].toInstance(mockAFTPartialService),
        bind[FinancialStatementConnector].toInstance(mockFinancialStatementConnector),
        bind[MinimalConnector].toInstance(mockMinimalConnector),
        bind[AllowAccessActionProviderForIdentifierRequest].toInstance(mockAllowAccessActionProviderForIdentifierRequest)
      )*
    )
    .build()

  val requestRefundUrl = controllers.financialOverview.routes.RefundUnavailableController.onPageLoad.url

  override def beforeEach(): Unit = {
    super.beforeEach()
    when(mockMinimalConnector.getPsaOrPspName(any(), any(), any())).thenReturn(Future.successful("psa-name"))
    when(mockFinancialStatementConnector.getPsaFSWithPaymentOnAccount(any())(any(), any())).thenReturn(Future.successful(psaFs))
    when(mockAFTPartialService.getCreditBalanceAmount(any())).thenReturn(BigDecimal("100.00"))
    when(mockAFTPartialService.getLatestCreditsDetails(any())(any())).thenReturn(createCreditsAndRefundTable(psaFs.seqPsaFSDetail))
  }

  "RefundsController" must {
    "return OK and the correct view for GET" in {
      val result = route(application, httpGETRequest(httpPathGET)).value
      status(result) mustEqual OK

      val view = application.injector.instanceOf[RefundsView].apply(
        "psa-name",
        "£100.00",
        requestRefundUrl,
        createCreditsAndRefundTable(psaFs.seqPsaFSDetail)
      )(httpGETRequest(httpPathGET), messages)

      compareResultAndView(result, view)
    }
  }

  def createCreditsAndRefundTable(latestCredits: Seq[PsaFSDetail]): Table = {

    val head: Seq[HeadCell] = Seq(
      HeadCell(Text("")),
      HeadCell(Text(Messages("refunds.aft.date"))),
      HeadCell(Text(Messages("refunds.aft.credit.value"))))

    val rows = latestCredits.map { psaFSDetail =>
      Seq(
        TableRow(HtmlContent(
          s"${psaFSDetail.chargeType.toString}</br>" +
            formatDateDMY(psaFSDetail.periodStartDate) + " to " + formatDateDMY(psaFSDetail.periodEndDate)
        ), classes = "govuk-!-width-one-half"),
        TableRow(Text(formatDateDMY(psaFSDetail.dueDate.get)), classes = "govuk-!-width-one-quarter"),
        TableRow(Text(psaFSDetail.amountDue.abs.toString), classes = "govuk-!-width-one-quarter"))
    }

    Table(head = Some(head), rows = rows , attributes = Map("role" -> "table"))
  }

}
