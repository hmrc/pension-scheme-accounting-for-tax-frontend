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

import connectors.FinancialStatementConnector
import connectors.FinancialStatementConnectorSpec.{interestPsaFSResponse, psaFSResponse}
import connectors.cache.FinancialInfoCacheConnector
import controllers.base.ControllerSpecBase
import controllers.financialOverview.psa.PsaPaymentsAndChargesInterestControllerSpec.{chargeRef, getRows}
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
import uk.gov.hmrc.govukfrontend.views.Aliases.HtmlContent
import uk.gov.hmrc.govukfrontend.views.viewmodels.summarylist.{Key, SummaryListRow, Value}
import uk.gov.hmrc.govukfrontend.views.viewmodels.content.Text
import viewmodels.PsaInterestDetailsViewModel
import views.html.financialOverview.psa.PsaInterestDetailsNewView

import java.time.LocalDate
import scala.concurrent.Future

class PsaPaymentsAndChargesInterestControllerSpec
  extends ControllerSpecBase
    with JsonMatchers
    with BeforeAndAfterEach
    with Enumerable.Implicits
    with Results
    with ScalaFutures {

  private def httpPathGETAssociated(indexVal: String): String = {
    controllers.financialOverview.psa.routes.PsaPaymentsAndChargesInterestController.onPageLoad(
      identifier = pstr, index = indexVal, Overdue).url
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

  val isOverdue: PsaFSDetail => Boolean = _ => true

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockPsaPenaltiesAndChargesService)
    when(mockPsaPenaltiesAndChargesService.interestRowsNew(any())(any())).thenReturn(getRows())
    when(mockPsaPenaltiesAndChargesService.getPenaltiesFromCache(any())(any(), any())).
      thenReturn(Future.successful(PenaltiesCache(psaId, "psa-name", interestPsaFSResponse)))
    when(mockPsaPenaltiesAndChargesService.getPenaltiesForJourney(any(), any())(any(), any())).
      thenReturn(Future.successful(PenaltiesCache(psaId, "psa-name", interestPsaFSResponse)))
    when(mockPsaPenaltiesAndChargesService.isPaymentOverdue).thenReturn(isOverdue)
    when(mockSchemeService.retrieveSchemeDetails(any(), any())(any(), any()))
      .thenReturn(Future.successful(SchemeDetails(schemeDetails.schemeName, pstr, "Open", None)))
    when(mockAppConfig.podsNewFinancialCredits).thenReturn(true)
  }

  "PsaPaymentsAndChargesInterestController" when {
    "on a GET" must {

      "render the correct view with details for associated interest charge type" in {

        when(mockFIConnector.fetch(any(), any())).thenReturn(Future.successful(Some(Json.toJson(interestPsaFSResponse))))

        val result = route(application, httpGETRequest(httpPathGETAssociated("0"))).value

        status(result) mustEqual OK

        val view = application.injector.instanceOf[PsaInterestDetailsNewView].apply(
          PsaInterestDetailsViewModel(
            psaName = "psa-name",
            heading = "Interest on contract settlement charge",
            isOverdue = true,
            schemeName = schemeName,
            chargeReference = chargeRef,
            interestDueAmount = Some("£0.00"),
            penaltyAmount = 10,
            returnUrl = "/manage-pension-scheme-accounting-for-tax/financial-overview/all-overdue-penalties-interest",
            list = getRows(),
            htmlInsetText = HtmlContent("<p class=govuk-body>The charge reference for the interest due will show once you have paid the <span><a id='breakdown' class=govuk-link href=/manage-pension-scheme-accounting-for-tax/financial-overview/24000040IN/0/due-charge-details> original amount due</a></span> in full. You can only pay the interest once a charge reference has been generated.</p>"),
            returnUrlText = "your Overdue payments and charges"
          )
        )(messages, httpGETRequest(httpPathGETAssociated("0")))

        compareResultAndView(result, view)
      }

      "catch IndexOutOfBoundsException" in {
        when(mockFIConnector.fetch(any(), any())).thenReturn(Future.successful(Some(Json.toJson(psaFSResponse))))

        val result = route(application, httpGETRequest(httpPathGETAssociated("3"))).value

        status(result) mustBe SEE_OTHER
        redirectLocation(result).value mustBe controllers.routes.SessionExpiredController.onPageLoad.url
      }
    }
  }
}

object PsaPaymentsAndChargesInterestControllerSpec {

  val pstr = "24000040IN"
  val chargeRef = "To be assigned"

  private def getRows()(implicit messages: Messages): Seq[SummaryListRow] =
    Seq(
      SummaryListRow(
        key = Key(Text(messages("psa.financial.overview.charge.reference")), classes = "govuk-!-width-three-quarters"),
        value = Value(Text(chargeRef), classes = "govuk-!-width-one-quarter govuk-table__cell--numeric")
      ),
      SummaryListRow(
        key = Key(Text(messages("psa.financial.overview.totalDueAsOf", LocalDate.now)), classes = "govuk-table__header--numeric govuk-!-padding-right-0"),
        value = Value(Text("£155.81")), classes = "govuk-!-width-one-quarter govuk-table__cell--numeric")
      )
}