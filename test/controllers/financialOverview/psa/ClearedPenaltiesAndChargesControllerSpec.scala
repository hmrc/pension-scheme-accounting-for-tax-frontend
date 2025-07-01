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
import data.SampleData.{psaFsSeqWithCleared, psaId}
import models.financialStatement.PenaltyType
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, when}
import play.api.Application
import play.api.http.Status.OK
import play.api.i18n.Messages
import play.api.inject.bind
import play.api.inject.guice.{GuiceApplicationBuilder, GuiceableModule}
import play.api.test.Helpers.{route, _}
import services.financialOverview.psa.{PenaltiesCache, PsaPenaltiesAndChargesService}
import uk.gov.hmrc.govukfrontend.views.viewmodels.content.{HtmlContent, Text}
import uk.gov.hmrc.govukfrontend.views.viewmodels.table.{HeadCell, Table, TableRow}
import views.html.financialOverview.psa.ClearedPenaltiesAndChargesView

import scala.concurrent.Future

class ClearedPenaltiesAndChargesControllerSpec extends ControllerSpecBase {

  private def httpPathGET: String =
    routes.ClearedPenaltiesAndChargesController.onPageLoad("2020", PenaltyType.AccountingForTaxPenalties).url

  private val mockPsaPenaltiesAndChargesService = mock[PsaPenaltiesAndChargesService]

  private val application: Application = new GuiceApplicationBuilder()
    .overrides(
      Seq[GuiceableModule](
        bind[IdentifierAction].to[FakeIdentifierAction],
        bind[FrontendAppConfig].toInstance(mockAppConfig),
        bind[PsaPenaltiesAndChargesService].toInstance(mockPsaPenaltiesAndChargesService),
        bind[AllowAccessActionProviderForIdentifierRequest].toInstance(mockAllowAccessActionProviderForIdentifierRequest)
      )*
    )
    .build()

  private val penaltiesCache = PenaltiesCache(psaId, "psa-name", psaFsSeqWithCleared)

  private val table = {
    val tableHeader = Seq(
      HeadCell(
        HtmlContent(
          s"<span class='govuk-visually-hidden'>${messages("psa.financial.overview.penaltyOrCharge")}</span>"
        )),
      HeadCell(Text(Messages("psa.financial.overview.datePaid")), classes = "govuk-!-font-weight-bold"),
      HeadCell(Text(Messages("psa.financial.overview.payment.charge.amount")), classes = "govuk-!-font-weight-bold")
    )

    val tableRows = Seq(
      TableRow(HtmlContent(
        s"<a id=XY002610150184 class=govuk-link href=/>" +
          "Accounting for Tax Late Filing Penalty</a></br>" +
          "schemeName" + "</br>" +
          "XY002610150184</br>" +
          "1 July to 30 September 2020"
      ), classes = "govuk-!-width-one-half"),
      TableRow(HtmlContent(s"<p>13 August 2020</p>")),
      TableRow(HtmlContent(s"<p>£80.00</p>"))
    )

    Table(head = Some(tableHeader), rows = Seq(tableRows))
  }

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockPsaPenaltiesAndChargesService)
    when(mockPsaPenaltiesAndChargesService.getPenaltiesForJourney(any(), any())(any(), any()))
      .thenReturn(Future.successful(penaltiesCache))
    when(mockPsaPenaltiesAndChargesService.getClearedPenaltiesAndCharges(any(), any(), any(), any())(any(), any(), any()))
      .thenReturn(Future.successful(table))
  }

  "ClearedPenaltiesAndChargesController" must {
    "return OK and the correct view for GET" in {
      val result = route(application, httpGETRequest(httpPathGET)).value
      status(result) mustEqual OK

      val view = application.injector.instanceOf[ClearedPenaltiesAndChargesView].apply(
        "psa-name",
        table
      )(httpGETRequest(httpPathGET), messages)

      compareResultAndView(result, view)
    }
  }



}


