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

import controllers.actions.{AllowAccessActionProviderForIdentifierRequest, FakeIdentifierAction, IdentifierAction}
import controllers.base.ControllerSpecBase
import data.SampleData.{psaId, schemeDetails, schemeFSResponseWithClearedPayments, schemeName, srn}
import models.financialStatement.PaymentOrChargeType
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, when}
import play.api.Application
import play.api.http.Status.OK
import play.api.i18n.Messages
import play.api.inject.bind
import play.api.inject.guice.{GuiceApplicationBuilder, GuiceableModule}
import play.api.test.Helpers.{route, _}
import services.financialOverview.scheme.{PaymentsAndChargesService, PaymentsCache}
import uk.gov.hmrc.govukfrontend.views.Aliases.{Table, Text}
import uk.gov.hmrc.govukfrontend.views.viewmodels.content.HtmlContent
import uk.gov.hmrc.govukfrontend.views.viewmodels.table.{HeadCell, TableRow}
import views.html.financialOverview.scheme.ClearedPaymentsAndChargesView

import scala.concurrent.Future

class ClearedPaymentsAndChargesControllerSpec extends ControllerSpecBase {
  private def httpPathGET: String =
    routes.ClearedPaymentsAndChargesController.onPageLoad(srn, "2020", PaymentOrChargeType.AccountingForTaxCharges).url

  private val mockPaymentsAndChargesService = mock[PaymentsAndChargesService]

  private val application: Application = new GuiceApplicationBuilder()
    .overrides(
      Seq[GuiceableModule](
        bind[IdentifierAction].to[FakeIdentifierAction],
        bind[PaymentsAndChargesService].toInstance(mockPaymentsAndChargesService),
        bind[AllowAccessActionProviderForIdentifierRequest].toInstance(mockAllowAccessActionProviderForIdentifierRequest)
      ): _*
    )
    .build()

  private val schemeFSDetails = schemeFSResponseWithClearedPayments.seqSchemeFSDetail
  private val samplePaymentsCache = PaymentsCache(psaId, srn, schemeDetails, schemeFSDetails)

  val table = {
    val tableHeader = {
      Seq(
        HeadCell(
          HtmlContent(
            s"<span class='govuk-visually-hidden'>${messages("scheme.financial.overview.paymentOrCharge")}</span>"
          )),
        HeadCell(Text(Messages("scheme.financial.overview.clearedPaymentsAndCharges.datePaid")), classes = "govuk-!-font-weight-bold"),
        HeadCell(Text(Messages("financial.overview.payment.charge.amount")), classes = "govuk-!-font-weight-bold")
      )
    }

    val rows =
      Seq(
        TableRow(HtmlContent(
          s"<a id=XY002610150184 class=govuk-link href=/>" +
            "Accounting for Tax return</a></br>" +
            "XY002610150184</br>" +
            "1 April to 30 June 2020"
        ), classes = "govuk-!-width-one-half"),
        TableRow(HtmlContent(s"<p>13 May 2020</p>")),
        TableRow(HtmlContent(s"<p>£80.00</p>"))
      )

    Table(head = Some(tableHeader), rows = Seq(rows))
  }
  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockPaymentsAndChargesService)
    when(mockPaymentsAndChargesService.getPaymentsForJourney(any(), any(), any())(any(), any()))
      .thenReturn(Future.successful(samplePaymentsCache))
    when(mockPaymentsAndChargesService.getClearedPaymentsAndCharges(any())(any(), any(), any()))
      .thenReturn(table)
  }

  "ClearedPaymentsAndChargesController" must {
    "return OK and the correct view" in {
      val result = route(application, httpGETRequest(httpPathGET)).value
      status(result) mustEqual OK

      val view = application.injector.instanceOf[ClearedPaymentsAndChargesView].apply(
        schemeName,
        table
      )(httpGETRequest(httpPathGET), messages)

      compareResultAndView(result, view)
    }
  }
}
