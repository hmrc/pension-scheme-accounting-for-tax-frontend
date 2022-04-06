/*
 * Copyright 2022 HM Revenue & Customs
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
import controllers.financialOverview.psa.PsaPaymentsAndChargesInterestControllerSpec.{chargeRef, rows}
import data.SampleData._
import matchers.JsonMatchers
import models.ChargeDetailsFilter.Overdue
import models.financialStatement.PsaFSDetail
import models.{Enumerable, SchemeDetails}
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceableModule
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.Results
import play.api.test.Helpers.{route, status, _}
import services.SchemeService
import services.financialOverview.psa.PenaltiesCache
import services.financialOverview.psa.{PenaltiesCache, PsaPenaltiesAndChargesService}
import uk.gov.hmrc.viewmodels.SummaryList.{Key, Row, Value}
import uk.gov.hmrc.viewmodels.Text.{Literal, Message}
import uk.gov.hmrc.viewmodels.{NunjucksSupport, SummaryList}
import viewmodels.Radios.MessageInterpolators

import java.time.LocalDate
import scala.concurrent.Future

class PsaPaymentsAndChargesInterestControllerSpec
  extends ControllerSpecBase
    with NunjucksSupport
    with JsonMatchers
    with BeforeAndAfterEach
    with Enumerable.Implicits
    with Results
    with ScalaFutures {

  private def httpPathGETAssociated(chargeReferenceIndex: String): String = {
    controllers.financialOverview.psa.routes.PsaPaymentsAndChargesInterestController.onPageLoad(
      identifier = pstr, chargeReferenceIndex = chargeReferenceIndex, Overdue).url
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
  private val templateToBeRendered = "financialOverview/psa/psaInterestDetails.njk"
  private val commonJson: JsObject = Json.obj(
    "heading" -> "Interest on contract settlement charge",
    "period" -> "Period: 1 January to 2 February 2021",
    "chargeReference" -> chargeRef,
    "list" -> rows
  )

  val isOverdue: PsaFSDetail => Boolean = _ => true

  override def beforeEach: Unit = {
    super.beforeEach
    reset(mockPsaPenaltiesAndChargesService, mockRenderer)
    when(mockPsaPenaltiesAndChargesService.interestRows(any())).thenReturn(rows)
    when(mockPsaPenaltiesAndChargesService.getPenaltiesFromCache(any())(any(), any())).
      thenReturn(Future.successful(PenaltiesCache(psaId, "psa-name", interestPsaFSResponse)))
    when(mockPsaPenaltiesAndChargesService.getPenaltiesForJourney(any(), any())(any(), any())).
      thenReturn(Future.successful(PenaltiesCache(psaId, "psa-name", interestPsaFSResponse)))
    when(mockPsaPenaltiesAndChargesService.isPaymentOverdue).thenReturn(isOverdue)
    when(mockPsaPenaltiesAndChargesService.setPeriod(any(), any(), any())).
      thenReturn("Period: 1 January to 2 February 2021")
    when(mockSchemeService.retrieveSchemeDetails(any(), any(), any())(any(), any()))
      .thenReturn(Future.successful(SchemeDetails(schemeDetails.schemeName, pstr, "Open", None)))
    when(mockRenderer.render(any(), any())(any())).thenReturn(Future.successful(play.twirl.api.Html("")))
  }

  "PsaPaymentsAndChargesInterestController" when {
    "on a GET" must {

      "render the correct view with details for associated interest charge type" in {

        when(mockFIConnector.fetch(any(),any())).thenReturn(Future.successful(Some(Json.toJson(interestPsaFSResponse))))

        val templateCaptor = ArgumentCaptor.forClass(classOf[String])
        val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])
        val result = route(application, httpGETRequest(httpPathGETAssociated("0"))).value
        val json = Json.obj(
          "schemeAssociated" -> true,
          "schemeName" -> schemeDetails.schemeName
        )

        status(result) mustEqual OK

        verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

        templateCaptor.getValue mustEqual templateToBeRendered

        jsonCaptor.getValue must containJson(commonJson ++ json)
      }

      "catch IndexOutOfBoundsException" in {
        when(mockFIConnector.fetch(any(),any())).thenReturn(Future.successful(Some(Json.toJson(psaFSResponse))))

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

  val rows: Seq[SummaryList.Row] =
    Seq(
      Row(
        key = Key(Message("psa.financial.overview.charge.reference"), classes = Seq("govuk-!-width-three-quarters")),
        value = Value(Literal(chargeRef),
          classes = Seq("govuk-!-width-one-quarter", "govuk-table__cell--numeric"))
      ),
      Row(
        key = Key(msg"psa.financial.overview.totalDueAsOf".withArgs(LocalDate.now), classes = Seq("govuk-table__header--numeric", "govuk-!-padding-right-0")),
        value = Value(Literal("£155.81"),
          classes = Seq("govuk-!-width-one-quarter", "govuk-table__cell--numeric"))
      )
    )
}
