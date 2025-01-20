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

package controllers.financialOverview.scheme

import connectors.{FinancialStatementConnector, MinimalConnector}
import controllers.base.ControllerSpecBase
import data.SampleData._
import matchers.JsonMatchers
import models.Enumerable
import org.mockito.ArgumentMatchers
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
import play.twirl.api.Html
import services.financialOverview.scheme.PaymentsAndChargesService
import services.{PsaSchemePartialService, SchemeService}
import uk.gov.hmrc.govukfrontend.views.Aliases.Table
import viewmodels.Radios.MessageInterpolators
import viewmodels.{CardSubHeading, CardSubHeadingParam, CardViewModel, Link}
import views.html.financialOverview.scheme.{SchemeFinancialOverviewNewView, SchemeFinancialOverviewView}

import scala.concurrent.Future

class SchemeFinancialOverviewControllerSpec
  extends ControllerSpecBase
    with JsonMatchers
    with BeforeAndAfterEach
    with Enumerable.Implicits
    with Results
    with ScalaFutures {

  private def getPartial: String = routes.SchemeFinancialOverviewController.schemeFinancialOverview(srn).url

  private val mockPsaSchemePartialService: PsaSchemePartialService = mock[PsaSchemePartialService]
  private val mockSchemeService: SchemeService = mock[SchemeService]
  private val mockFinancialStatementConnector: FinancialStatementConnector = mock[FinancialStatementConnector]
  private val mockPaymentsAndChargesService: PaymentsAndChargesService = mock[PaymentsAndChargesService]
  private val mockMinimalPsaConnector: MinimalConnector = mock[MinimalConnector]
  private val extraModules: Seq[GuiceableModule] =
    Seq[GuiceableModule](
      bind[PsaSchemePartialService].toInstance(mockPsaSchemePartialService),
      bind[SchemeService].toInstance(mockSchemeService),
      bind[FinancialStatementConnector].toInstance(mockFinancialStatementConnector),
      bind[MinimalConnector].toInstance(mockMinimalPsaConnector),
      bind[PaymentsAndChargesService].toInstance(mockPaymentsAndChargesService)
    )

  val application: Application = applicationBuilder(extraModules = extraModules).build()

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockPsaSchemePartialService)
    reset(mockPaymentsAndChargesService)
    when(mockSchemeService.retrieveSchemeDetails(any(), any(), any())(any(), any()))
      .thenReturn(Future.successful(schemeDetails))
    when(mockFinancialStatementConnector.getSchemeFS(any())(any(), any()))
      .thenReturn(Future.successful(schemeFSResponseAftAndOTC))
    when(mockPaymentsAndChargesService.getPaymentsFromCache(any(),any())(any(),any())).
      thenReturn(Future.successful(schemeToFinancial(schemeFSResponseAftAndOTC)))
    when(mockPaymentsAndChargesService.getPaymentsForJourney(any(), any(), any())(any(), any())).
      thenReturn(Future.successful(schemeToFinancial(schemeFSResponseAftAndOTC)))
    when(mockPaymentsAndChargesService.getPaymentsAndCharges(ArgumentMatchers.eq(srn),
      any(), any(), any())(any())).thenReturn(Table())
    when(mockPaymentsAndChargesService.getOverdueCharges(any())).thenReturn(schemeFSResponseAftAndOTC.seqSchemeFSDetail)
    when(mockPaymentsAndChargesService.getInterestCharges(any())).thenReturn(schemeFSResponseAftAndOTC.seqSchemeFSDetail)
    when(mockPaymentsAndChargesService.extractUpcomingCharges).thenReturn(_ => schemeFSResponseAftAndOTC.seqSchemeFSDetail)
  }

  "SchemeFinancial Controller" when {
    "schemeFinancialOverview" must {

      "return html with information received from overview api" in {
        when(mockPsaSchemePartialService.aftCardModel(any(), any())(any(), any()))
          .thenReturn(Future.successful(allTypesMultipleReturnsModel))
        when(mockPsaSchemePartialService.upcomingAftChargesModel(any(), any())(any()))
          .thenReturn(allTypesMultipleReturnsModel)
        when(mockPsaSchemePartialService.overdueAftChargesModel(any(), any())(any()))
          .thenReturn(allTypesMultipleReturnsModel)
        when(mockFinancialStatementConnector.getSchemeFSPaymentOnAccount(any())(any(), any()))
          .thenReturn(Future.successful(schemeFSResponseAftAndOTC))
        when(mockPsaSchemePartialService.creditBalanceAmountFormatted(any()))
          .thenReturn("£1,000.00")
        when(mockMinimalPsaConnector.getPsaOrPspName(any(), any(), any()))
          .thenReturn(Future.successful("John Doe"))
        when(mockAppConfig.podsNewFinancialCredits).thenReturn(true)

        val request = httpGETRequest(getPartial)
        val result = route(application, httpGETRequest(getPartial)).value

        status(result) mustEqual OK

        val view = application.injector.instanceOf[SchemeFinancialOverviewNewView].apply(
          schemeName = "Big Scheme",
          totalUpcomingCharge = "£2,058.10",
          totalOverdueCharge = "£2,058.10",
          totalInterestAccruing = "£47,000.96",
          requestRefundUrl = routes.RequestRefundController.onPageLoad(srn).url,
          allOverduePenaltiesAndInterestLink = routes.PaymentsAndChargesController.onPageLoad(srn, journeyType = "overdue").url,
          duePaymentLink = routes.PaymentsAndChargesController.onPageLoad(srn, "upcoming").url,
          allPaymentLink = routes.PaymentOrChargeTypeController.onPageLoad(srn).url,
          creditBalanceFormatted = "£0.00",
          creditBalance = 0,
          isOverdueChargeAvailable = false,
          returnUrl = mockAppConfig.managePensionsSchemeOverviewUrl
        )(messages, request)

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
    linkText = msg"pspDashboardAftReturnsCard.inProgressReturns.link",
    hiddenText = Some(msg"aftPartial.view.hidden")
  )

  private def startLink: Link = Link(id = "aftLoginLink", url = aftLoginUrl, linkText = msg"aftPartial.start.link")

  private def pastReturnsLink: Link = Link(id = "aftAmendLink", url = amendUrl, linkText = msg"aftPartial.view.change.past")

  private def retrieveCreditBalance(creditBalance: BigDecimal): String = {
    if (creditBalance >= 0) {
      BigDecimal(0.00).toString()
    }
    else {
      creditBalance.abs.toString()
    }
  }

  private val aftUrl = "http://localhost:8206/manage-pension-scheme-accounting-for-tax"
  private val amendUrl: String = s"$aftUrl/srn/previous-return/amend-select"
  private val continueUrl: String = s"$aftUrl/srn/new-return/select-quarter-in-progress"
  private val aftLoginUrl: String = s"$aftUrl/srn/new-return/aft-login"
}
