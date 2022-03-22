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

package controllers.financialOverview

import connectors.FinancialStatementConnector
import connectors.FinancialStatementConnectorSpec.psaFSResponse
import connectors.cache.FinancialInfoCacheConnector
import controllers.base.ControllerSpecBase
import controllers.financialOverview.PsaPenaltiesAndChargeDetailsControllerSpec.{chargeRef, rows}
import data.SampleData._
import matchers.JsonMatchers
import models.ChargeDetailsFilter.Overdue
import models.financialStatement.{PsaFS, PsaFSDetail}
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
import services.financialOverview.{PenaltiesCache, PsaPenaltiesAndChargesService}
import services.SchemeService
import uk.gov.hmrc.viewmodels.NunjucksSupport
import uk.gov.hmrc.viewmodels.SummaryList.{Key, Row, Value}
import uk.gov.hmrc.viewmodels.Text.Literal
import utils.DateHelper.formatDateDMY
import viewmodels.Radios.MessageInterpolators

import java.time.LocalDate
import scala.concurrent.Future

class PsaPenaltiesAndChargeDetailsControllerSpec
  extends ControllerSpecBase
    with NunjucksSupport
    with JsonMatchers
    with BeforeAndAfterEach
    with Enumerable.Implicits
    with Results
    with ScalaFutures {

  private def httpPathGETAssociated(chargeReferenceIndex: String): String = {
    controllers.financialOverview.routes.PsaPenaltiesAndChargeDetailsController.onPageLoad(
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
  private val templateToBeRendered = "financialOverview/psaChargeDetails.njk"
  private val commonJson: JsObject = Json.obj(
    "heading" -> "Accounting for Tax late filing penalty",
    "isOverdue" -> true,
    "period" -> "Quarter: 1 October to 31 December 2020",
    "chargeReference" -> chargeRef,
    "list" -> rows
  )

  val isOverdue: PsaFSDetail => Boolean = _ => true

  override def beforeEach: Unit = {
    super.beforeEach
    reset(mockPsaPenaltiesAndChargesService, mockRenderer)
    when(mockPsaPenaltiesAndChargesService.chargeDetailsRows(any(),any())).thenReturn(rows)
    when(mockPsaPenaltiesAndChargesService.isPaymentOverdue).thenReturn(isOverdue)
    when(mockPsaPenaltiesAndChargesService.getPenaltiesForJourney(any(), any())(any(), any())).
      thenReturn(Future.successful(PenaltiesCache(psaId, "psa-name", psaFSResponse)))
    when(mockPsaPenaltiesAndChargesService.setPeriod(any(), any(), any())).thenReturn("Quarter: 1 October to 31 December 2020")
    when(mockSchemeService.retrieveSchemeDetails(any(), any(), any())(any(), any()))
      .thenReturn(Future.successful(SchemeDetails(schemeDetails.schemeName, pstr, "Open", None)))
    when(mockRenderer.render(any(), any())(any())).thenReturn(Future.successful(play.twirl.api.Html("")))
  }

  "PsaPenaltiesAndChargeDetailsController" when {
    "on a GET" must {

      "render the correct view with penalty details for associated" in {

        when(mockFIConnector.fetch(any(),any())).thenReturn(Future.successful(Some(Json.toJson(psaFSResponse))))

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

object PsaPenaltiesAndChargeDetailsControllerSpec {

  val pstr = "24000040IN"
  val chargeRef = "XY002610150184"
  val clearingDate: LocalDate = LocalDate.parse("2020-06-30")

  val rows = Seq(
    Row(
      key = Key(msg"psa.financial.overview.charge.reference", classes = Seq("govuk-!-width-three-quarters")),
      value = Value(Literal(s"$chargeRef"), classes = Seq("govuk-!-width-one-quarter", "govuk-table__cell--numeric"))
    ),
    Row(
      key = Key(msg"psa.financial.overview.penaltyAmount", classes = Seq("govuk-!-width-three-quarters")),
      value = Value(Literal("£800.08"), classes = Seq("govuk-!-width-one-quarter", "govuk-table__cell--numeric"))
    ),
    Row(
      key = Key(msg"financialPaymentsAndCharges.clearingReason.c1".withArgs(formatDateDMY(clearingDate)), classes = Seq("govuk-!-width-three-quarters")),
      value = Value(Literal("£800.08"), classes = Seq("govuk-!-width-one-quarter", "govuk-table__cell--numeric"))
    ),
    Row(
      key = Key(msg"financialPaymentsAndCharges.paymentDue.overdue.dueDate".withArgs("15 July 2020"),
        classes = Seq("govuk-table__header--numeric","govuk-!-padding-right-0")),
      value = Value(Literal("£1029.05"), classes = Seq("govuk-!-width-one-quarter", "govuk-table__cell--numeric"))
    )
  )

}
