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

import config.FrontendAppConfig
import connectors.FinancialStatementConnector
import connectors.FinancialStatementConnectorSpec.psaFs
import controllers.base.ControllerSpecBase
import helpers.FormatHelper
import matchers.JsonMatchers
import models.Enumerable
import models.PenaltiesFilter.All
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
import services.AFTPartialService
import uk.gov.hmrc.govukfrontend.views.Aliases.Text
import viewmodels._
import views.html.partials.SchemePaymentsAndChargesPartialView

import scala.concurrent.Future

class PenaltiesPartialControllerSpec extends ControllerSpecBase with JsonMatchers
  with BeforeAndAfterEach with Enumerable.Implicits with Results with ScalaFutures {

  private def httpPathGET: String = controllers.financialStatement.penalties.routes.PenaltiesPartialController.penaltiesPartial.url

  val appConfig: FrontendAppConfig = mock[FrontendAppConfig]
  val mockFSConnector: FinancialStatementConnector = mock[FinancialStatementConnector]
  val mockAFTPartialService: AFTPartialService = mock[AFTPartialService]

  private val extraModules: Seq[GuiceableModule] =
    Seq[GuiceableModule](
      bind[FinancialStatementConnector].toInstance(mockFSConnector),
      bind[AFTPartialService].toInstance(mockAFTPartialService)
    )

  def application: Application = applicationBuilder(extraModules = extraModules).build()

  def dashboardViewModel: DashboardAftViewModel = {
    val subheadings =
      Seq(
        Json.obj(
          "total" -> "£1,029.05",
          "span" -> "Total amount due:"
        ),
        Json.obj(
          "total" -> "£2,058.10",
          "span" -> "Total overdue payments:"
        )
      )

    val links = Seq(
      Link(id = "aft-penalties-id",
        url = routes.PenaltiesLogicController.onPageLoad(All).url,
        linkText = Text(Messages("psaPenaltiesCard.viewPenalties")),
        hiddenText = None)
    )
    DashboardAftViewModel(subHeadings = subheadings, links = links)
  }

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockFSConnector)
    when(mockFSConnector.getPsaFS(any())(any(), any()))
      .thenReturn(Future.successful(psaFs))
    when(mockAppConfig.viewPenaltiesUrl).thenReturn(frontendAppConfig.viewPenaltiesUrl)
  }

  "PenaltiesPartial Controller" when {
    "on a GET" must {

      "return the html with the link when data is received from PSA financial statement api" in {
        when(mockAFTPartialService.penaltiesAndCharges(any())(any()))
          .thenReturn(allTypesMultipleReturnsModel)

        val request = httpGETRequest(httpPathGET)

        val view = application.injector.instanceOf[SchemePaymentsAndChargesPartialView].apply(
          allTypesMultipleReturnsModel)(messages)

        val result = route(application, request).value

        status(result) mustEqual OK

        compareResultAndView(result, view)
      }
    }
  }

  private def allTypesMultipleReturnsModel(implicit messages: Messages): Seq[CardViewModel] =
    Seq(CardViewModel(
      id = "aft-overdue-charges",
      heading = messages("pspDashboardOverdueAndUpcomingAftChargesCard.h2"),
      subHeadings = Seq(subHeadingTotalOutstanding, subHeadingPaymentsOverdue),
      links = Seq(viewFinancialOverviewLink(), viewAllPaymentsAndChargesLink())
    ))

  private def subHeadingTotalOutstanding: CardSubHeading = CardSubHeading(
    subHeading = messages("pspDashboardOverdueAftChargesCard.outstanding.span"),
    subHeadingClasses = "card-sub-heading",
    subHeadingParams = Seq(CardSubHeadingParam(
      subHeadingParam = s"${FormatHelper.formatCurrencyAmountAsString(3087.15)}",
      subHeadingParamClasses = "font-large bold"
    ))
  )

  private def subHeadingPaymentsOverdue: CardSubHeading = CardSubHeading(
    subHeading = "",
    subHeadingClasses = "govuk-tag govuk-tag--red",
    subHeadingParams = Seq(CardSubHeadingParam(
      subHeadingParam = messages("pspDashboardOverdueAftChargesCard.overdue.span"),
      subHeadingParamClasses = "govuk-tag govuk-tag--red"
    ))
  )

  private def viewFinancialOverviewLink(): Link =
    Link(
      id = "view-your-financial-overview",
      url = overviewurl,
      linkText = Text(Messages("pspDashboardUpcomingAftChargesCard.link.financialOverview")),
      hiddenText = None
    )

  private def viewAllPaymentsAndChargesLink(): Link =
    Link(
      id = "past-payments-and-charges",
      url = chargesurl,
      linkText = Text(Messages("pspDashboardUpcomingAftChargesCard.link.allPaymentsAndCharges")),
      hiddenText = None
    )

  private val aftUrl = "http://localhost:8206/manage-pension-scheme-accounting-for-tax"
  private val overviewurl: String = s"$aftUrl/financial-overview"
  private val chargesurl: String = s"$aftUrl/view-penalties"
}
