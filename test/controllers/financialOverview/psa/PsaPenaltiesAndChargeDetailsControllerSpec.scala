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

import connectors.FinancialStatementConnector
import connectors.FinancialStatementConnectorSpec.psaFSResponse
import connectors.cache.FinancialInfoCacheConnector
import controllers.base.ControllerSpecBase
import controllers.financialOverview.psa.PsaPenaltiesAndChargeDetailsControllerSpec.{chargeRef, getRows}
import data.SampleData._
import matchers.JsonMatchers
import models.ChargeDetailsFilter.Overdue
import models.financialStatement.PsaFSDetail
import models.{Enumerable, SchemeDetails}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import play.api.Application
import play.api.i18n.Messages
import play.api.inject.bind
import play.api.inject.guice.GuiceableModule
import play.api.libs.json.Json
import play.api.mvc.Results
import play.api.test.Helpers.{route, status, _}
import services.SchemeService
import services.financialOverview.psa.{PenaltiesCache, PsaPenaltiesAndChargesService}
import uk.gov.hmrc.govukfrontend.views.Aliases.{HtmlContent, Table}
import uk.gov.hmrc.govukfrontend.views.viewmodels.summarylist.{Key, SummaryListRow, Value}
import uk.gov.hmrc.govukfrontend.views.viewmodels.content.Text
import utils.DateHelper.formatDateDMY
import viewmodels.PsaChargeDetailsViewModel
import views.html.financialOverview.psa.{PsaChargeDetailsNewView, PsaChargeDetailsView}

import java.time.LocalDate
import scala.concurrent.Future

class PsaPenaltiesAndChargeDetailsControllerSpec
  extends ControllerSpecBase
    with JsonMatchers
    with BeforeAndAfterEach
    with Enumerable.Implicits
    with Results
    with ScalaFutures {

  private def httpPathGETAssociated(indexValue: String): String = {
    routes.PsaPenaltiesAndChargeDetailsController.onPageLoad(identifier = pstr,
      index = indexValue, Overdue).url
  }

  private val mockPsaPenaltiesAndChargesService = mock[PsaPenaltiesAndChargesService]
  val mockSchemeService: SchemeService = mock[SchemeService]
  val mockFSConnector: FinancialStatementConnector = mock[FinancialStatementConnector]
  val mockFIConnector: FinancialInfoCacheConnector = mock[FinancialInfoCacheConnector]

  private val extraModules: Seq[GuiceableModule] =
    Seq[GuiceableModule](
      bind[PsaPenaltiesAndChargesService].toInstance(mockPsaPenaltiesAndChargesService),
      bind[SchemeService].toInstance(mockSchemeService),
      bind[FinancialStatementConnector].toInstance(mockFSConnector),
      bind[FinancialInfoCacheConnector].toInstance(mockFIConnector)
    )

  val application: Application = applicationBuilder(extraModules = extraModules).build()
  val emptyChargesTable: Table = Table()
  val isOverdue: PsaFSDetail => Boolean = _ => true

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockPsaPenaltiesAndChargesService)
    when(mockPsaPenaltiesAndChargesService.chargeDetailsRows(any(), any())(any)).thenReturn(getRows())
    when(mockPsaPenaltiesAndChargesService.isPaymentOverdue).thenReturn(isOverdue)
    when(mockPsaPenaltiesAndChargesService.getPenaltiesForJourney(any(), any())(any(), any()))
      .thenReturn(Future.successful(PenaltiesCache(psaId, "psa-name", psaFSResponse)))
    when(mockPsaPenaltiesAndChargesService.setPeriod(any(), any(), any())).thenReturn("Quarter: 1 October to 31 December 2020")
    when(mockSchemeService.retrieveSchemeDetails(any(), any(), any())(any(), any()))
      .thenReturn(Future.successful(SchemeDetails(schemeDetails.schemeName, pstr, "Open", None)))
    when(mockRenderer.render(any(), any())(any())).thenReturn(Future.successful(play.twirl.api.Html("")))
  }

  "PsaPenaltiesAndChargeDetailsController" when {
    "on a GET" must {

      "render the correct view with penalty details for associated" in {

        when(mockFIConnector.fetch(any(), any())).thenReturn(Future.successful(Some(Json.toJson(psaFSResponse))))

        val result = route(application, httpGETRequest(httpPathGETAssociated("1"))).value

        status(result) mustEqual OK

        val view = application.injector.instanceOf[PsaChargeDetailsView].apply(
          model = PsaChargeDetailsViewModel(
            heading = "Accounting for Tax Late Filing Penalty",
            psaName = "psa-name",
            schemeName = schemeDetails.schemeName,
            isOverdue = true,
            period = Some("Quarter: 1 October to 31 December 2020"),
            paymentDueAmount = Some("0"),
            paymentDueDate = Some("0"),
            chargeReference = chargeRef,
            penaltyAmount = 10.00,
            insetText = HtmlContent(""),
            isInterestPresent = false,
            list = Some(mockPsaPenaltiesAndChargesService.chargeDetailsRows(psaFSResponse.head, "Overdue")),
            chargeHeaderDetails = None,
            chargeAmountDetails = Some(emptyChargesTable),
            returnUrl = routes.PsaPaymentsAndChargesController.onPageLoad(Overdue).url,
            returnUrlText = "your Overdue payments and charges"
          )
        )(messages, fakeRequest)

        compareResultAndView(result, view)
      }

      "catch IndexOutOfBoundsException" in {
        when(mockFIConnector.fetch(any(), any())).thenReturn(Future.successful(Some(Json.toJson(psaFSResponse))))

        val result = route(application, httpGETRequest(httpPathGETAssociated("5"))).value

        status(result) mustBe SEE_OTHER
        redirectLocation(result).value mustBe controllers.routes.SessionExpiredController.onPageLoad.url
      }
    }
  }
}

object PsaPenaltiesAndChargeDetailsControllerSpec {

  val pstr = "24000040IN"
  val chargeRef = "XY002610150184"
  val clearingDate: LocalDate = LocalDate.parse("2020-06-30")

  private def getRows()(implicit messages: Messages): Seq[SummaryListRow] =
    Seq(
      SummaryListRow(
        key = Key(Text(messages("psa.financial.overview.charge.reference")), classes = "govuk-!-width-three-quarters"),
        value = Value(Text(s"$chargeRef"), classes = "govuk-!-width-one-quarter govuk-table__cell--numeric")
      ),
      SummaryListRow(
        key = Key(Text(messages("psa.financial.overview.penaltyAmount")), classes = "govuk-!-width-three-quarters"),
        value = Value(Text("£800.08"), classes = "govuk-!-width-one-quarter govuk-table__cell--numeric")
      ),
      SummaryListRow(
        key = Key(Text(messages("financialPaymentsAndCharges.clearingReason.c1", formatDateDMY(clearingDate))), classes = "govuk-!-width-three-quarters"),
        value = Value(Text("£800.08"), classes = "govuk-!-width-one-quarter govuk-table__cell--numeric")
      ),
      SummaryListRow(
        key = Key(Text(messages("financialPaymentsAndCharges.paymentDue.overdue.dueDate", "15 July 2020")), classes = "govuk-table__header--numeric govuk-!-padding-right-0"),
        value = Value(Text("£1029.05"), classes = "govuk-!-width-one-quarter govuk-table__cell--numeric")
      )
    )


}
