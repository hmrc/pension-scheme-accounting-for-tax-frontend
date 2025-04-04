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

package controllers.partials

import connectors.FinancialStatementConnector
import controllers.base.ControllerSpecBase
import data.SampleData._
import matchers.JsonMatchers
import models.Enumerable
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
import services.{PsaSchemePartialService, SchemeService}
import uk.gov.hmrc.govukfrontend.views.Aliases.Text
import viewmodels.{CardSubHeading, CardSubHeadingParam, CardViewModel, Link}
import views.html.partials.SchemePaymentsAndChargesPartialView

import scala.concurrent.Future

class PsaSchemeDashboardPartialsControllerSpec
  extends ControllerSpecBase
    with JsonMatchers
    with BeforeAndAfterEach
    with Enumerable.Implicits
    with Results
    with ScalaFutures {


  private def getPartial: String = routes.PsaSchemeDashboardPartialsController.psaSchemeDashboardAFTTilePartial(srn).url

  private val mockPsaSchemePartialService: PsaSchemePartialService = mock[PsaSchemePartialService]
  private val mockSchemeService: SchemeService = mock[SchemeService]
  private val mockFinancialStatementConnector: FinancialStatementConnector = mock[FinancialStatementConnector]
  private val extraModules: Seq[GuiceableModule] =
    Seq[GuiceableModule](
      bind[PsaSchemePartialService].toInstance(mockPsaSchemePartialService),
      bind[SchemeService].toInstance(mockSchemeService),
      bind[FinancialStatementConnector].toInstance(mockFinancialStatementConnector)
    )
  val application: Application = applicationBuilder(extraModules = extraModules).build()

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockPsaSchemePartialService)
    when(mockSchemeService.retrieveSchemeDetails(any(), any())(any(), any()))
      .thenReturn(Future.successful(schemeDetails))
    when(mockFinancialStatementConnector.getSchemeFS(any(), any(), any())(any(), any()))
      .thenReturn(Future.successful(schemeFSResponseAftAndOTC))
  }

  "PsaSchemeDashboardPartials Controller" when {
    "aftPartial" must {

      "return the html with information received from overview api" in {
        when(mockPsaSchemePartialService.aftCardModel(any(), any(), any())(any(), any()))
          .thenReturn(Future.successful(allTypesMultipleReturnsModel))
        when(mockPsaSchemePartialService.upcomingAftChargesModel(any(), any())(any()))
          .thenReturn(allTypesMultipleReturnsModel)
        when(mockPsaSchemePartialService.overdueAftChargesModel(any(), any())(any()))
          .thenReturn(allTypesMultipleReturnsModel)
        when(mockPsaSchemePartialService.paymentsAndCharges(any(), any(), any())(any()))
          .thenReturn(allTypesMultipleReturnsModel)

        val request = httpGETRequest(getPartial)

        val view = application.injector.instanceOf[SchemePaymentsAndChargesPartialView].apply(
          allTypesMultipleReturnsModel)(messages)

        val result = route(application, request).value

        status(result) mustEqual OK

        compareResultAndView(result, view)
      }

    }
  }

  private def aftModel(subHeadings: Seq[CardSubHeading], links: Seq[Link])
                      (implicit messages: Messages): CardViewModel = CardViewModel(
    id = "aft-overview",
    heading = messages("aftPartial.head"),
    subHeadings = subHeadings,
    links = links
  )

  private def allTypesMultipleReturnsModel(implicit messages: Messages): Seq[CardViewModel] =
    Seq(aftModel(Seq(multipleInProgressSubHead()), Seq(multipleInProgressLink, startLink, pastReturnsLink)))

  private def multipleInProgressSubHead(count: Int = 2)(implicit messages: Messages): CardSubHeading =
    CardSubHeading(
      subHeading = messages("aftPartial.multipleInProgress.text"),
      subHeadingClasses = "card-sub-heading",
      subHeadingParams = Seq(CardSubHeadingParam(
        subHeadingParam = messages("aftPartial.multipleInProgress.count", count),
        subHeadingParamClasses = "font-small bold"
      )))

  private def multipleInProgressLink = Link(
    id = "aftContinueInProgressLink",
    url = continueUrl,
    linkText = Text(Messages("pspDashboardAftReturnsCard.inProgressReturns.link")),
    hiddenText = Some(Text(Messages("aftPartial.view.hidden")))
  )

  private def startLink: Link = Link(id = "aftLoginLink", url = aftLoginUrl, linkText = Text(Messages("aftPartial.start.link")))

  private def pastReturnsLink: Link = Link(id = "aftAmendLink", url = amendUrl, linkText = Text(Messages("aftPartial.view.change.past")))

  private val aftUrl = "http://localhost:8206/manage-pension-scheme-accounting-for-tax"
  private val amendUrl: String = s"$aftUrl/srn/previous-return/amend-select"
  private val continueUrl: String = s"$aftUrl/srn/new-return/select-quarter-in-progress"
  private val aftLoginUrl: String = s"$aftUrl/srn/new-return/aft-login"
}
