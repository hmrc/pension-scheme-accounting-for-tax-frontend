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
import data.SampleData._
import helpers.FormatHelper
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
import uk.gov.hmrc.govukfrontend.views.viewmodels.content.Text
import uk.gov.hmrc.govukfrontend.views.viewmodels.summarylist.{Key, SummaryListRow, Value}
import uk.gov.hmrc.govukfrontend.views.viewmodels.table.TableRow
import utils.DateHelper.formatDateDMY
import viewmodels.PsaChargeDetailsViewModel
import views.html.financialOverview.psa.PsaChargeDetailsView

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
    routes.PsaPenaltiesAndChargeDetailsController.onPageLoad(
      identifier = pstr,
      index = indexValue,
      journeyType = Overdue
    ).url
  }

  val pstr = "24000040IN"
  val chargeRef = "XY002610150184"
  val clearingDate: LocalDate = LocalDate.parse("2020-06-30")

  def chargeDetailsTable()(implicit messages: Messages): Table =
    Table(Seq(Seq(
      TableRow(Text(Messages("psa.pension.scheme.chargeAmount.label.new")), classes = "govuk-!-font-weight-bold"),
      TableRow(Text("")),
      TableRow(Text(s"${FormatHelper.formatCurrencyAmountAsString(psaFSResponse.head.totalAmount)}"), classes = "govuk-!-font-weight-regular")
    )))

  def chargeHeaderDetailsRows()(implicit messages: Messages): Seq[SummaryListRow] =
    Seq(
      SummaryListRow(
        key = Key(Text(Messages("psa.pension.scheme.tax.reference.new")), classes = "govuk-!-padding-left-0 govuk-!-width-one-half"),
        value = Value(Text(s"$pstr"), classes = "govuk-!-width-one-half")
      ),
      SummaryListRow(
        key = Key(Text(Messages("psa.financial.overview.charge.reference")), classes = "govuk-!-padding-left-0 govuk-!-width-one-half"),
        value = Value(Text(s"$chargeRef"), classes = "govuk-!-width-one-half")
      ),
      SummaryListRow(
        key = Key(Text(Messages("psa.pension.scheme.tax.period.new")), classes = "govuk-!-padding-left-0 govuk-!-width-one-half"),
        value = Value(Text(s"${formatDateDMY(psaFSResponse.head.periodStartDate) + " to " + formatDateDMY(psaFSResponse.head.periodEndDate)}"), classes = "govuk-!-width-one-half")
      )
    )

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
  val isOverdue: PsaFSDetail => Boolean = _ => true

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockPsaPenaltiesAndChargesService)
    when(mockPsaPenaltiesAndChargesService.chargeAmountDetailsRows(any(), any(), any())(any))
      .thenReturn(chargeDetailsTable())
    when(mockPsaPenaltiesAndChargesService.chargeHeaderDetailsRows(any())(any))
      .thenReturn(chargeHeaderDetailsRows())
    when(mockPsaPenaltiesAndChargesService.isPaymentOverdue)
      .thenReturn(isOverdue)
    when(mockPsaPenaltiesAndChargesService.getPenaltiesForJourney(any(), any())(any(), any()))
      .thenReturn(Future.successful(PenaltiesCache(psaId, "psa-name", psaFSResponse)))
    when(mockPsaPenaltiesAndChargesService.setPeriod(any(), any(), any()))
      .thenReturn("Quarter: 1 October to 31 December 2020")
    when(mockSchemeService.retrieveSchemeDetails(any(), any())(any(), any()))
      .thenReturn(Future.successful(SchemeDetails(schemeDetails.schemeName, pstr, "Open", None)))
    when(mockFIConnector.fetch(any(), any()))
      .thenReturn(Future.successful(Some(Json.toJson(psaFSResponse))))
  }

  "PsaPenaltiesAndChargeDetailsController" when {
    "on a GET" must {

      "render the correct view with penalty details for associated" in {

        val req = httpGETRequest(httpPathGETAssociated("1"))
        val result = route(application, req).value

          status(result) mustEqual OK

          val view = application.injector.instanceOf[PsaChargeDetailsView].apply(
              model = PsaChargeDetailsViewModel(
              heading             = "Accounting for Tax Late Filing Penalty",
              psaName             = "psa-name",
              schemeName          = schemeDetails.schemeName,
              isOverdue           = true,
              paymentDueAmount    = Some("£1,029.05"),
              paymentDueDate      = None,
              chargeReference     = chargeRef,
              penaltyAmount       = 10.00,
              insetText           = HtmlContent(""),
              chargeHeaderDetails = Some(chargeHeaderDetailsRows()),
              chargeAmountDetails = Some(chargeDetailsTable()),
              isInterestPresent   = false,
              returnUrl           = routes.PsaPaymentsAndChargesController.onPageLoad(Overdue).url,
              returnUrlText       = "your Overdue payments and charges"
            )
          )(messages, req)

          compareResultAndView(result, view)
        }

      "catch IndexOutOfBoundsException" in {
        running(application) {
          val result = route(application, httpGETRequest(httpPathGETAssociated("5"))).value

          status(result) mustBe SEE_OTHER
          redirectLocation(result).value mustBe controllers.routes.SessionExpiredController.onPageLoad.url
        }
      }
    }
  }
}