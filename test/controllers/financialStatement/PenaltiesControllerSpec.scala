/*
 * Copyright 2020 HM Revenue & Customs
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

package controllers.financialStatement

import connectors.FinancialStatementConnector
import connectors.FinancialStatementConnectorSpec.psaFSResponse
import controllers.base.ControllerSpecBase
import data.SampleData._
import matchers.JsonMatchers
import models.{Enumerable, SchemeDetails}
import org.mockito.ArgumentCaptor
import org.mockito.Matchers.any
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceableModule
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.Results
import play.api.test.Helpers.{route, status, _}
import services.{PenaltiesService, SchemeService}
import uk.gov.hmrc.viewmodels.Table.Cell
import uk.gov.hmrc.viewmodels.Text.Literal
import uk.gov.hmrc.viewmodels.{Html, NunjucksSupport, _}

import scala.concurrent.Future

class PenaltiesControllerSpec extends ControllerSpecBase with NunjucksSupport with JsonMatchers
  with BeforeAndAfterEach with Enumerable.Implicits with Results with ScalaFutures {

  import PenaltiesControllerSpec._

  private def httpPathGET: String = controllers.financialStatement.routes.PenaltiesController.onPageLoad(year, srn).url

  val penaltyTables: Seq[Table] = Seq(
    Table(caption = Some(msg"penalties.period".withArgs("1 April", "30 June 2020")), captionClasses= Seq("govuk-heading-m"),
      head = head, rows = rows("2020-04-01")),
    Table(caption = Some(msg"penalties.period".withArgs("1 July", "30 September 2020")), captionClasses= Seq("govuk-heading-m"),
      head = head, rows = rows("2020-07-01"))
  )

  val mockPenaltiesService: PenaltiesService = mock[PenaltiesService]
  val mockSchemeService: SchemeService = mock[SchemeService]
  val mockFSConnector: FinancialStatementConnector = mock[FinancialStatementConnector]

  private val extraModules: Seq[GuiceableModule] =
    Seq[GuiceableModule](
      bind[PenaltiesService].toInstance(mockPenaltiesService),
      bind[SchemeService].toInstance(mockSchemeService),
      bind[FinancialStatementConnector].toInstance(mockFSConnector)
    )

  val application: Application = applicationBuilder(extraModules = extraModules).build()

  private val templateToBeRendered = "financialStatement/penalties.njk"
  private val jsonToPassToTemplate: JsObject = Json.obj("year" -> "2020",
    "schemeName" -> schemeDetails.schemeName,
    "tables" -> Json.toJson(penaltyTables))

  override def beforeEach: Unit = {
    super.beforeEach
    reset(mockPenaltiesService, mockRenderer)
    when(mockPenaltiesService.getPsaFsJson(any(), any(), any())(any())).thenReturn(penaltyTables)
    when(mockFSConnector.getPsaFS(any())(any(), any())).thenReturn(Future.successful(psaFSResponse))
    when(mockSchemeService.retrieveSchemeDetails(any(), any())(any(), any()))
      .thenReturn(Future.successful(SchemeDetails(schemeDetails.schemeName, pstr, "Open")))
    when(mockRenderer.render(any(), any())(any())).thenReturn(Future.successful(play.twirl.api.Html("")))

  }

  "Penalties Controller" when {
    "on a GET" must {

      "render the correct view with penalty tables" in {

        val templateCaptor = ArgumentCaptor.forClass(classOf[String])
        val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])
        val result = route(application, httpGETRequest(httpPathGET)).value

        status(result) mustEqual OK

        verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

        templateCaptor.getValue mustEqual templateToBeRendered

        jsonCaptor.getValue must containJson(jsonToPassToTemplate)
      }

    }

  }
}

object PenaltiesControllerSpec {
  val year = "2020"
  val srn = "S2400000041"
  val pstr = "24000040IN"

  val head = Seq(
    Cell(msg"penalties.column.penalty", classes = Seq("govuk-!-width-one-half")),
    Cell(msg"penalties.column.amount", classes = Seq("govuk-!-width-one-quarter")),
    Cell(msg"")
  )

  def rows(startDate: String) = Seq(Seq(
      Cell(link(startDate), classes = Seq("govuk-!-width-one-half")),
      Cell(Literal("£1029.05"), classes = Seq("govuk-!-width-one-quarter", "govuk-table__header--numeric")),
      Cell(msg"penalties.status.paymentOverdue", classes = Seq("govuk-tag govuk-tag--red"))
    ))

  def link(startDate: String): Html = Html(
    s"<a id=XY002610150184 href=${controllers.financialStatement.routes.ChargeDetailsController.onPageLoad(srn, startDate, "XY002610150184").url}>" +
      s"Accounting for Tax late filing penalty </a>")


}
