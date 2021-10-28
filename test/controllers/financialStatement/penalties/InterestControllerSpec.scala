/*
 * Copyright 2021 HM Revenue & Customs
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

package controllers.financialStatement.penalties

import connectors.FinancialStatementConnector
import connectors.FinancialStatementConnectorSpec.psaFSResponse
import connectors.cache.FinancialInfoCacheConnector
import controllers.base.ControllerSpecBase
import data.SampleData._
import matchers.JsonMatchers
import models.{SchemeDetails, Enumerable, PenaltiesFilter}
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceableModule
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.Results
import play.api.test.Helpers.{route, status, _}
import services.{PenaltiesService, SchemeService, PenaltiesCache}
import uk.gov.hmrc.viewmodels.SummaryList.{Value, Row, Key}
import uk.gov.hmrc.viewmodels.Text.{Message, Literal}
import uk.gov.hmrc.viewmodels.{NunjucksSupport, _}

import java.time.LocalDate
import scala.concurrent.Future

class InterestControllerSpec
  extends ControllerSpecBase
    with NunjucksSupport
    with JsonMatchers
    with BeforeAndAfterEach
    with Enumerable.Implicits
    with Results
    with ScalaFutures {

  import InterestControllerSpec._

  private def httpPathGETAssociated(chargeReferenceIndex: String): String =
    controllers.financialStatement.penalties.routes.InterestController.onPageLoad(
      identifier = srn, chargeReferenceIndex = chargeReferenceIndex
    ).url

  private def httpPathGETUnassociated: String =
    controllers.financialStatement.penalties.routes.InterestController.onPageLoad(
      identifier = "0", chargeReferenceIndex = "0"
    ).url

  val mockPenaltiesService: PenaltiesService = mock[PenaltiesService]
  val mockSchemeService: SchemeService = mock[SchemeService]
  val mockFSConnector: FinancialStatementConnector = mock[FinancialStatementConnector]
  val mockFIConnector: FinancialInfoCacheConnector = mock[FinancialInfoCacheConnector]

  private val extraModules: Seq[GuiceableModule] =
    Seq[GuiceableModule](
      bind[PenaltiesService].toInstance(mockPenaltiesService),
      bind[SchemeService].toInstance(mockSchemeService),
      bind[FinancialStatementConnector].toInstance(mockFSConnector),
      bind[FinancialInfoCacheConnector].toInstance(mockFIConnector)
    )

  val application: Application = applicationBuilder(extraModules = extraModules).build()
  private val templateToBeRendered = "financialStatement/penalties/interest.njk"
  private val commonJson: JsObject = Json.obj(
    "heading" -> "Interest on accounting for tax late filing penalty",
    "period" -> msg"penalties.period".withArgs("1 April", "30 June 2020"),
    "chargeReference" -> chargeRef,
    "list" -> rows
  )

  override def beforeEach: Unit = {
    super.beforeEach
    reset(mockPenaltiesService, mockRenderer)
    when(mockPenaltiesService.interestRows(any())).thenReturn(rows)
    when(mockPenaltiesService.getPenaltiesFromCache(any())(any(), any())).thenReturn(Future.successful(PenaltiesCache(psaId, "psa-name", psaFSResponse)))
    when(mockSchemeService.retrieveSchemeDetails(any(), any(), any())(any(), any()))
      .thenReturn(Future.successful(SchemeDetails(schemeDetails.schemeName, pstr, "Open", None)))
    when(mockRenderer.render(any(), any())(any())).thenReturn(Future.successful(play.twirl.api.Html("")))
  }

  "Interest Controller" when {
    "on a GET" must {

      "render the correct view with penalty tables for associated" in {

        when(mockFIConnector.fetch(any(),any())).thenReturn(Future.successful(Some(Json.toJson(psaFSResponse))))

        val templateCaptor = ArgumentCaptor.forClass(classOf[String])
        val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])
        val result = route(application, httpGETRequest(httpPathGETAssociated("0"))).value
        val json = Json.obj(
          "schemeAssociated" -> true,
          "schemeName" -> schemeDetails.schemeName,
          "originalAmountURL" -> controllers.financialStatement.penalties.routes.ChargeDetailsController
            .onPageLoad(srn, "0", PenaltiesFilter.All).url
        )

        status(result) mustEqual OK

        verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

        templateCaptor.getValue mustEqual templateToBeRendered

        jsonCaptor.getValue must containJson(commonJson ++ json)
      }

      "render the correct view with penalty tables for unassociated" in {

        val templateCaptor = ArgumentCaptor.forClass(classOf[String])
        val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])
        val result = route(application, httpGETRequest(httpPathGETUnassociated)).value
        val json = Json.obj(
          "schemeAssociated" -> false,
          "originalAmountURL" -> controllers.financialStatement.penalties.routes.ChargeDetailsController
            .onPageLoad("0", "0", PenaltiesFilter.All).url
        )

        status(result) mustEqual OK

        verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

        templateCaptor.getValue mustEqual templateToBeRendered

        jsonCaptor.getValue must containJson(commonJson ++ json)
      }

      "catch IndexOutOfBoundsException" in {
        when(mockFIConnector.fetch(any(),any())).thenReturn(Future.successful(Some(Json.toJson(psaFSResponse))))

        val result = route(application, httpGETRequest(httpPathGETAssociated("2"))).value

        status(result) mustBe SEE_OTHER
        redirectLocation(result).value mustBe controllers.routes.SessionExpiredController.onPageLoad.url
      }
    }
  }
}

object InterestControllerSpec {
  val srn = "S2400000041"
  val pstr = "24000040IN"
  val chargeRef = "To be assigned"

  val rows: Seq[SummaryList.Row] =
    Seq(
      Row(
        key = Key(Message("penalties.status.interestAccruing"), classes = Seq("govuk-!-width-three-quarters")),
        value = Value(Literal("£33.44"),
          classes = Seq("govuk-!-width-one-quarter", "govuk-table__cell--numeric"))
      ),
      Row(
        key = Key(msg"penalties.interest.totalDueAsOf".withArgs(LocalDate.now), classes = Seq("govuk-table__header--numeric", "govuk-!-padding-right-0")),
        value = Value(Literal("£33.44"),
          classes = Seq("govuk-!-width-one-quarter", "govuk-table__cell--numeric"))
      )
    )

}
