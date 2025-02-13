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

package controllers.financialStatement.penalties

import connectors.FinancialStatementConnectorSpec.psaFSResponse
import controllers.base.ControllerSpecBase
import data.SampleData._
import matchers.JsonMatchers
import models.PenaltiesFilter.All
import models.financialStatement.PenaltiesViewModel
import models.{Enumerable, SchemeDetails}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import play.api.Application
import play.api.i18n.Messages
import play.api.inject.bind
import play.api.inject.guice.GuiceableModule
import play.api.mvc.Results
import play.api.test.Helpers.{route, status, _}
import services.{PenaltiesCache, PenaltiesService, SchemeService}
import uk.gov.hmrc.govukfrontend.views.viewmodels.table.{HeadCell, Table, TableRow}
import uk.gov.hmrc.govukfrontend.views.viewmodels.content.{HtmlContent, Text}
import views.html.financialStatement.penalties.PenaltiesView

import java.time.LocalDate
import scala.concurrent.Future

class PenaltiesControllerSpec extends ControllerSpecBase with JsonMatchers
  with BeforeAndAfterEach with Enumerable.Implicits with Results with ScalaFutures {

  import PenaltiesControllerSpec._

  private def httpPathGETAssociated: String =
    controllers.financialStatement.penalties.routes.PenaltiesController.onPageLoadAft(startDate, srn, All).url

  private def httpPathGETUnassociated(identifier: String): String =
    controllers.financialStatement.penalties.routes.PenaltiesController.onPageLoadAft(startDate, identifier, All).url

  val penaltyTables: Table = Table(head = Some(getHeadRow()), rows = getRows())

  val mockPenaltiesService: PenaltiesService = mock[PenaltiesService]
  val mockSchemeService: SchemeService = mock[SchemeService]

  private val extraModules: Seq[GuiceableModule] =
    Seq[GuiceableModule](
      bind[PenaltiesService].toInstance(mockPenaltiesService),
      bind[SchemeService].toInstance(mockSchemeService)
    )

  val application: Application = applicationBuilder(extraModules = extraModules).build()

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockPenaltiesService)
    when(mockPenaltiesService.getPsaFsTable(any(), any(), any(), any(), any())(any()))
      .thenReturn(penaltyTables)
    when(mockPenaltiesService.getPenaltiesForJourney(any(), any())(any(), any()))
      .thenReturn(Future.successful(PenaltiesCache(psaId, "psa-name", psaFSResponse)))

    when(mockSchemeService.retrieveSchemeDetails(any(), any(), any())(any(), any()))
      .thenReturn(Future.successful(SchemeDetails(schemeDetails.schemeName, pstr, "Open", None)))
  }

  "Penalties Controller" when {
    "on a GET" must {

      "render the correct view with penalty tables for associated" in {
        val result = route(application, httpGETRequest(httpPathGETAssociated)).value

        val viewModel = PenaltiesViewModel(
          "Accounting for Tax penalties for 1 April to 30 June 2020",
          true,
          Some(schemeDetails.schemeName),
          pstr,
          penaltyTables,
          "",
          "psa-name"
        )

        val view = application.injector.instanceOf[PenaltiesView].apply(
          viewModel
        )(httpGETRequest(httpPathGETAssociated), messages)

        status(result) mustEqual OK

        compareResultAndView(result, view)
      }

      "render the correct view with penalty tables for unassociated" in {
        when(mockPenaltiesService.unassociatedSchemes(any(), any(): LocalDate, any())(any(), any()))
          .thenReturn(Future.successful(psaFSResponse))

        val pstrIndex: String = psaFSResponse.map(_.pstr).indexOf(pstr).toString

        val viewModel = PenaltiesViewModel(
          "Accounting for Tax penalties for 1 April to 30 June 2020",
          false,
          None,
          pstr,
          penaltyTables,
          "",
          "psa-name"
        )

        val view = application.injector.instanceOf[PenaltiesView].apply(
          viewModel
        )(httpGETRequest(httpPathGETUnassociated(pstrIndex)), messages)

        val result = route(application, httpGETRequest(httpPathGETUnassociated(pstrIndex))).value

        status(result) mustEqual OK

        compareResultAndView(result, view)
      }
    }
  }
}

object PenaltiesControllerSpec {

  val startDate = "2020-04-01"
  val srn = "S2400000041"
  val pstr = "24000040IN"

  private def getHeadRow()(implicit messages: Messages) = Seq(
    HeadCell(Text(messages("penalties.column.chargeType")), classes = "govuk-!-width-one-half"),
    HeadCell(Text(messages("penalties.column.amount")), classes = "govuk-!-width-one-quarter"),
    HeadCell(Text(""))
  )

  private def getRows()(implicit messages: Messages) = Seq(Seq(
    TableRow(link, classes = "govuk-!-width-one-half"),
    TableRow(Text("£1029.05"), classes = "govuk-!-width-one-quarter govuk-table__header--numeric"),
    TableRow(Text(messages("penalties.status.paymentOverdue")), classes = "govuk-tag govuk-tag--red")
  ))

  val link: HtmlContent = HtmlContent(
    s"<a id=XY002610150184 class=govuk-link href=${routes.ChargeDetailsController.onPageLoad(srn, "XY002610150184", All).url}>" +
      s"Accounting for Tax late filing penalty </a>")
}
