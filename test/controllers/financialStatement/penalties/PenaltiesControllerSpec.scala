/*
 * Copyright 2023 HM Revenue & Customs
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

import connectors.FinancialStatementConnectorSpec.psaFSResponse
import controllers.base.ControllerSpecBase
import data.SampleData._
import matchers.JsonMatchers
import models.PenaltiesFilter.All
import models.{Enumerable, SchemeDetails}
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, times, verify, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceableModule
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.Results
import play.api.test.Helpers.{route, status, _}
import services.{PenaltiesCache, PenaltiesService, SchemeService}
import uk.gov.hmrc.viewmodels.Table.Cell
import uk.gov.hmrc.viewmodels.Text.Literal
import uk.gov.hmrc.viewmodels.{Html, NunjucksSupport, _}

import java.time.LocalDate
import scala.concurrent.Future

class PenaltiesControllerSpec extends ControllerSpecBase with NunjucksSupport with JsonMatchers
  with BeforeAndAfterEach with Enumerable.Implicits with Results with ScalaFutures {

  import PenaltiesControllerSpec._

  private def httpPathGETAssociated: String =
    controllers.financialStatement.penalties.routes.PenaltiesController.onPageLoadAft(startDate, srn, All).url

  private def httpPathGETUnassociated(identifier: String): String =
    controllers.financialStatement.penalties.routes.PenaltiesController.onPageLoadAft(startDate, identifier, All).url

  val penaltyTables: JsObject =
    Json.obj("penaltyTable" -> Table(head = head, rows = rows("2020-04-01")))

  val mockPenaltiesService: PenaltiesService = mock[PenaltiesService]
  val mockSchemeService: SchemeService = mock[SchemeService]

  private val extraModules: Seq[GuiceableModule] =
    Seq[GuiceableModule](
      bind[PenaltiesService].toInstance(mockPenaltiesService),
      bind[SchemeService].toInstance(mockSchemeService)
    )

  val application: Application = applicationBuilder(extraModules = extraModules).build()

  private val templateToBeRendered = "financialStatement/penalties/penalties.njk"
  private val jsonToPassToTemplateAssociatedScheme: JsObject = Json.obj(
    "pstr" -> pstr,
    "schemeAssociated" -> true,
    "schemeName" -> Json.arr(schemeDetails.schemeName),
    "table" -> penaltyTables)

  private val jsonToPassToTemplateUnassociatedScheme: JsObject = Json.obj(
    "pstr" -> pstr,
    "schemeAssociated" -> false,
    "schemeName" -> Json.arr(),
    "table" -> penaltyTables)

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockPenaltiesService)
    reset(mockRenderer)
    when(mockPenaltiesService.getPsaFsJson(any(), any(), any(), any(), any())(any()))
      .thenReturn(penaltyTables)
    when(mockPenaltiesService.getPenaltiesForJourney(any(), any())(any(), any()))
      .thenReturn(Future.successful(PenaltiesCache(psaId, "psa-name", psaFSResponse)))

    when(mockSchemeService.retrieveSchemeDetails(any(), any(), any())(any(), any()))
      .thenReturn(Future.successful(SchemeDetails(schemeDetails.schemeName, pstr, "Open", None)))
    when(mockRenderer.render(any(), any())(any()))
      .thenReturn(Future.successful(play.twirl.api.Html("")))
  }

  "Penalties Controller" when {
    "on a GET" must {

      "render the correct view with penalty tables for associated" in {

        val templateCaptor = ArgumentCaptor.forClass(classOf[String])
        val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])
        val result = route(application, httpGETRequest(httpPathGETAssociated)).value

        status(result) mustEqual OK

        verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

        templateCaptor.getValue mustEqual templateToBeRendered

        jsonCaptor.getValue must containJson(jsonToPassToTemplateAssociatedScheme)
      }

      "render the correct view with penalty tables for unassociated" in {
        when(mockPenaltiesService.unassociatedSchemes(any(), any(): LocalDate, any())(any(), any()))
          .thenReturn(Future.successful(psaFSResponse))

        val pstrIndex: String = psaFSResponse.map(_.pstr).indexOf(pstr).toString
        val templateCaptor = ArgumentCaptor.forClass(classOf[String])
        val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])
        val result = route(application, httpGETRequest(httpPathGETUnassociated(pstrIndex))).value

        status(result) mustEqual OK

        verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

        templateCaptor.getValue mustEqual templateToBeRendered

        jsonCaptor.getValue must containJson(jsonToPassToTemplateUnassociatedScheme)
      }
    }
  }
}

object PenaltiesControllerSpec {

  val startDate = "2020-04-01"
  val srn = "S2400000041"
  val pstr = "24000040IN"

  val head = Seq(
    Cell(msg"penalties.column.chargeType", classes = Seq("govuk-!-width-one-half")),
    Cell(msg"penalties.column.amount", classes = Seq("govuk-!-width-one-quarter")),
    Cell(msg"")
  )

  def rows(startDate: String) = Seq(Seq(
    Cell(link(startDate), classes = Seq("govuk-!-width-one-half")),
    Cell(Literal("£1029.05"), classes = Seq("govuk-!-width-one-quarter", "govuk-table__header--numeric")),
    Cell(msg"penalties.status.paymentOverdue", classes = Seq("govuk-tag govuk-tag--red"))
  ))

  def link(startDate: String): Html = Html(
    s"<a id=XY002610150184 class=govuk-link href=${routes.ChargeDetailsController.onPageLoad(srn, "XY002610150184", All).url}>" +
      s"Accounting for Tax late filing penalty </a>")
}
