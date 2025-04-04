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

package services

import base.SpecBase
import connectors.AFTConnector
import connectors.cache.UserAnswersCacheConnector
import helpers.FormatHelper
import models._
import models.financialStatement.{SchemeFSChargeType, SchemeFSDetail}
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.mockito.MockitoSugar
import play.api.i18n.Messages
import services.paymentsAndCharges.PaymentsAndChargesService
import uk.gov.hmrc.govukfrontend.views.Aliases.Text
import utils.DateHelper
import utils.DateHelper.{dateFormatterDMY, dateFormatterStartDate}
import viewmodels._

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import scala.concurrent.{ExecutionContext, Future}

class PsaSchemePartialServiceSpec extends SpecBase with MockitoSugar with BeforeAndAfterEach with ScalaFutures {

  import PsaSchemePartialServiceSpec._

  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
  private val aftConnector = mock[AFTConnector]
  private val aftCacheConnector = mock[UserAnswersCacheConnector]
  private val paymentsAndChargesService = mock[PaymentsAndChargesService]

  def service: PsaSchemePartialService =
    new PsaSchemePartialService(frontendAppConfig, paymentsAndChargesService, aftConnector, aftCacheConnector)

  "aftCardModel" must {
    "return the correct model when overview api returns multiple returns in " +
      "progress, multiple past returns and start link needs to be displayed" in {
      DateHelper.setDate(Some(LocalDate.of(2021, 4, 1)))
      when(aftConnector.getAftOverview(any(), any(), any(),  any(), any())(any(), any()))
        .thenReturn(Future.successful(allTypesMultipleReturnsPresent))
      when(aftConnector.aftOverviewStartDate).thenReturn(LocalDate.of(2020, 4, 1))
      when(aftConnector.aftOverviewEndDate).thenReturn(LocalDate.of(2021, 6, 30))
      whenReady(service.aftCardModel(schemeDetails, srn, true)) {
        _ mustBe allTypesMultipleReturnsModel
      }
    }

    "return the correct model when return no returns are in progress" in {
      when(aftConnector.getAftOverview(any(), any(), any(),  any(), any())(any(), any()))
        .thenReturn(Future.successful(noInProgress))
      when(aftConnector.aftOverviewStartDate).thenReturn(LocalDate.of(2020, 4, 1))
      when(aftConnector.aftOverviewEndDate).thenReturn(LocalDate.of(2021, 6, 30))

      whenReady(service.aftCardModel(schemeDetails, srn, true)) {
        _ mustBe noInProgressModel
      }
    }

    "return the correct model when return one return is in progress but not locked" in {
      when(aftConnector.getAftOverview(any(), any(), any(),  any(), any())(any(), any()))
        .thenReturn(Future.successful(oneInProgress))
      when(aftConnector.aftOverviewStartDate).thenReturn(LocalDate.of(2020, 4, 1))
      when(aftConnector.aftOverviewEndDate).thenReturn(LocalDate.of(2021, 6, 30))
      when(aftCacheConnector.lockDetail(any(), any())(any(), any()))
        .thenReturn(Future.successful(None))

      whenReady(service.aftCardModel(schemeDetails, srn, true)) {
        _ mustBe oneInProgressModelNotLocked
      }
    }

    "return the correct model when one return is in progress and locked by another user" in {
      when(aftConnector.getAftOverview(any(), any(), any(),  any(), any())(any(), any()))
        .thenReturn(Future.successful(oneInProgress))
      when(aftConnector.aftOverviewStartDate).thenReturn(LocalDate.of(2020, 4, 1))
      when(aftConnector.aftOverviewEndDate).thenReturn(LocalDate.of(2021, 6, 30))
      when(aftCacheConnector.lockDetail(any(), any())(any(), any()))
        .thenReturn(Future.successful(Some(LockDetail(name, psaId))))

      whenReady(service.aftCardModel(schemeDetails, srn, true)) {
        _ mustBe oneInProgressModelLocked
      }
    }

    "return a model with start link and only 2 returns in progress" when {
      "a scheme has 3 compiles in progress but one has been zeroed " +
        "out and all quarters have been initiated (ie no start link)" in {
        DateHelper.setDate(Some(LocalDate.of(2020, 12, 31)))
        when(aftConnector.getAftOverview(any(), any(), any(),  any(), any())(any(), any()))
          .thenReturn(Future.successful(oneCompileZeroedOut))
        when(aftConnector.aftOverviewStartDate).thenReturn(LocalDate.of(2020, 4, 1))
        when(aftConnector.aftOverviewEndDate).thenReturn(LocalDate.of(2021, 12, 31))
        when(aftCacheConnector.lockDetail(any(), any())(any(), any()))
          .thenReturn(Future.successful(None))
        when(aftConnector.getIsAftNonZero(any(), ArgumentMatchers.eq("2020-07-01"), any(), any(), any())(any(), any()))
          .thenReturn(Future.successful(false))
        when(aftConnector.getIsAftNonZero(any(), ArgumentMatchers.eq("2020-04-01"), any(), any(), any())(any(), any()))
          .thenReturn(Future.successful(true))

        whenReady(service.aftCardModel(schemeDetails, srn, true)) {
          _ mustBe oneCompileZeroedOutModel
        }
      }
    }

  }

  "upcomingAftChargesModel" must {
    "return the correct model when there are multiple upcoming charges and past charges" in {
      DateHelper.setDate(Some(LocalDate.of(2021, 1, 1)))
      when(paymentsAndChargesService.extractUpcomingCharges).thenReturn(_ => upcomingChargesMultiple)

      service.upcomingAftChargesModel(upcomingChargesMultiple, srn) mustBe upcomingChargesMultipleModel(pastLink = upcomingPastChargesLink())
    }

    "return the correct model when there is a single upcoming charge and no past charges" in {
      DateHelper.setDate(Some(LocalDate.of(2020, 12, 31)))
      when(paymentsAndChargesService.extractUpcomingCharges).thenReturn(_ => upcomingChargesSingle)

      service.upcomingAftChargesModel(upcomingChargesSingle, srn) mustBe upcomingChargesSingleModel
    }
  }

  "overdueChargesModel" must {
    "return the correct model when there are multiple overdue charges and past charges" in {
      DateHelper.setDate(Some(LocalDate.of(2021, 5, 1)))
      when(paymentsAndChargesService.getOverdueCharges(any())).thenReturn(upcomingChargesMultiple)
      when(paymentsAndChargesService.getInterestCharges(any())).thenReturn(upcomingChargesMultiple)

      service.overdueAftChargesModel(upcomingChargesMultiple, srn) mustBe overdueChargesModel()
    }

    "return the correct model when there is a single upcoming charge and no past charges" in {
      when(paymentsAndChargesService.getOverdueCharges(any())).thenReturn(upcomingChargesSingle)
      when(paymentsAndChargesService.getInterestCharges(any())).thenReturn(upcomingChargesSingle)

      service.overdueAftChargesModel(upcomingChargesSingle, srn) mustBe overdueChargesSingleModel
    }
  }
  "CreditBalanceAmount" must {
    "return a positive number when sum of amount due is negative" in {
      when(paymentsAndChargesService.getDueCharges(any())).thenReturn(upcomingChargesMultipleNegative)
      service.creditBalanceAmountFormatted(upcomingChargesMultiple) mustBe positiveNumberFormatted
    }
    "return zero when sum of amount due is positive" in{
      when(paymentsAndChargesService.getDueCharges(any())).thenReturn(upcomingChargesMultiple)
      service.creditBalanceAmountFormatted(any()) mustBe zeroFormatted
    }

  }
  "paymentsAndCharges" must {
    "return the correct model when there is an outstanding amount to be displayed when there are overdue charges" in {
      DateHelper.setDate(Some(LocalDate.of(2022, 3, 2)))
      when(paymentsAndChargesService.getOverdueCharges(any())).thenReturn(outstandingAmountOverdue)
      when(paymentsAndChargesService.getInterestCharges(any())).thenReturn(outstandingAmountOverdue)
      when(paymentsAndChargesService.extractUpcomingCharges).thenReturn(_ => upcomingChargesMultiple)
      service.paymentsAndCharges(outstandingAmountOverdue, srn, pstr) mustBe paymentsAndChargesModel

    }
  }
}

object PsaSchemePartialServiceSpec {
  private val startDate = "2020-10-01"
  private val endDate = "2020-12-31"
  private val dueDate = "2021-02-15"
  private val startDt: String = LocalDate.parse(startDate).format(dateFormatterStartDate)
  private val endDt: String = LocalDate.parse(endDate).format(dateFormatterDMY)
  private val smallDatePattern: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM")
  private val srn = "srn"
  private val pstr = "pstr"
  private val psaId = "A0000000"
  private val schemeDetails: SchemeDetails = SchemeDetails("test-name", pstr, "Open", None)
  private val name = "test-name"
  val minimalPsaName: Option[String] = Some("John Doe Doe")
  private val aftUrl = "http://localhost:8206/manage-pension-scheme-accounting-for-tax"
  private val aftLoginUrl: String = s"$aftUrl/srn/new-return/aft-login"
  private val amendUrl: String = s"$aftUrl/srn/previous-return/amend-select"
  private val aftSummaryUrl: String = s"$aftUrl/srn/2020-10-01/draft/2/summary"
  private val continueUrl: String = s"$aftUrl/srn/new-return/select-quarter-in-progress"
  private val viewUpcomingChargesUrl: String = s"$aftUrl/srn/upcoming-payments-logic"
  private val viewOverdueChargesUrl: String = s"$aftUrl/srn/overdue-payments-logic"
  private val viewPastChargesUrl: String = s"$aftUrl/srn/past-payments-logic"
  private val viewFinancialInfoPastChargesUrl: String = s"$aftUrl/srn/financial-overview/past-payments-logic"
  private val positiveNumberFormatted: String = s"${FormatHelper.formatCurrencyAmountAsString(900)}"
  private val zeroFormatted : String = s"${FormatHelper.formatCurrencyAmountAsString(0)}"
  private val documentLineItemDetails = Seq()

  private val charge1: SchemeFSDetail = SchemeFSDetail(index = 0, "XYZ", SchemeFSChargeType.PSS_AFT_RETURN, Some(LocalDate.parse(dueDate)), BigDecimal(100.00),
    BigDecimal(100.00), BigDecimal(100.00), BigDecimal(100.00), BigDecimal(100.00), Some(LocalDate.parse(startDate)), Some(LocalDate.parse(endDate)), None, None, None, None, None, documentLineItemDetails)

  private val charge2: SchemeFSDetail = SchemeFSDetail(index = 0, "XYZ", SchemeFSChargeType.PSS_OTC_AFT_RETURN, Some(LocalDate.parse("2021-04-15")), BigDecimal(200.00),
    BigDecimal(200.00), BigDecimal(200.00), BigDecimal(200.00), BigDecimal(200.00), Some(LocalDate.parse("2021-01-01")), Some(LocalDate.parse("2021-03-31")), None, None, None, None, None, documentLineItemDetails)

  private val charge3: SchemeFSDetail = SchemeFSDetail(index = 0, "XYZ", SchemeFSChargeType.PAYMENT_ON_ACCOUNT, Some(LocalDate.parse("2021-04-15")), BigDecimal(200.00),
    BigDecimal(-1200.00), BigDecimal(200.00), BigDecimal(200.00), BigDecimal(200.00), Some(LocalDate.parse("2021-01-01")), Some(LocalDate.parse("2021-03-31")), None, None, None, None, None, documentLineItemDetails)

  private val charge4: SchemeFSDetail = SchemeFSDetail(index = 0, "XYZ", SchemeFSChargeType.PSS_OTC_AFT_RETURN, Some(LocalDate.parse(dueDate)), BigDecimal(100.00),
    BigDecimal(600.00), BigDecimal(0.00), BigDecimal(0.00), BigDecimal(0.00), Some(LocalDate.parse("2021-01-01")), Some(LocalDate.parse("2021-03-31")), None, None, None, None, None, documentLineItemDetails)
  private val charge5: SchemeFSDetail = SchemeFSDetail(index = 0, "XYZ", SchemeFSChargeType.PSS_OTC_AFT_RETURN, Some(LocalDate.parse(dueDate)), BigDecimal(100.00),
    BigDecimal(0.00), BigDecimal(0.00), BigDecimal(0.00), BigDecimal(0.00),Some(LocalDate.parse("2021-01-01")), Some(LocalDate.parse("2021-03-31")), None, None, None, None, None, documentLineItemDetails)
  private val upcomingChargesMultiple: Seq[SchemeFSDetail] = Seq(charge1, charge2)
  private val upcomingChargesSingle: Seq[SchemeFSDetail] = Seq(charge1)
  private val upcomingChargesMultipleNegative: Seq[SchemeFSDetail] = Seq(charge1, charge2, charge3)
  private val outstandingAmountOverdue: Seq[SchemeFSDetail]= Seq(charge5, charge4)
  private def upcomingPastChargesLink()(implicit messages: Messages): Seq[Link] = Seq(Link(
    id = "past-payments-and-charges",
    url = viewPastChargesUrl,
    linkText = Text(Messages("pspDashboardUpcomingAftChargesCard.link.allPaymentsAndCharges")),
    hiddenText = None
  ))

  private def upcomingChargesSingleModel(implicit messages: Messages): Seq[CardViewModel] = upcomingChargesMultipleModel(
    Messages("pspDashboardUpcomingAftChargesCard.span.singleDueDate", LocalDate.parse(dueDate).format(dateFormatterDMY)),
    Messages("pspDashboardUpcomingAftChargesCard.link.paymentsAndChargesForPeriod.single", Seq(
      LocalDate.parse(startDate).format(smallDatePattern),
      LocalDate.parse(endDate).format(smallDatePattern))),
    Nil,
    "£100.00",
    viewUpcomingChargesUrl
  )

  private def upcomingChargesMultipleModel(upcomingChargesSubHeading: String = "pspDashboardUpcomingAftChargesCard.span.multipleDueDate",
                                   upcomingLinkText: String = "pspDashboardUpcomingAftChargesCard.link.paymentsAndChargesForPeriod.multiple",
                                   pastLink: Seq[Link],
                                   amount: String = "£300.00",
                                   upcomingLink: String = viewUpcomingChargesUrl)
                                  (implicit messages: Messages): Seq[CardViewModel] = Seq(CardViewModel(
    id = "upcoming-aft-charges",
    heading = messages("pspDashboardUpcomingAftChargesCard.h2"),
    Seq(CardSubHeading(
      subHeading = messages(upcomingChargesSubHeading),
      subHeadingClasses = "card-sub-heading",
      subHeadingParams = Seq(CardSubHeadingParam(
        subHeadingParam = amount,
        subHeadingParamClasses = "font-large bold"
      ))
    )),
    Seq(Link(
      id = "upcoming-payments-and-charges",
      url = upcomingLink,
      linkText = Text(Messages(upcomingLinkText)),
      hiddenText = None
    )) ++ pastLink)
  )
  private def paymentsAndChargesModel(implicit messages: Messages): Seq[CardViewModel] = {
    Seq(CardViewModel(
      id = "aft-overdue-charges",
      heading = messages("pspDashboardOverdueAndUpcomingAftChargesCard.h2"),
      subHeadings = Seq(subHeadingTotalOutstanding, subHeadingPaymentsOverdue),
      links = Seq(viewFinancialOverviewLink(), viewAllPaymentsAndChargesLink())
    ))
  }

  private def subHeadingTotalOutstanding(implicit messages: Messages): CardSubHeading = CardSubHeading(
    subHeading = messages("pspDashboardOverdueAftChargesCard.outstanding.span"),
    subHeadingClasses = "card-sub-heading",
    subHeadingParams = Seq(CardSubHeadingParam(
      subHeadingParam = s"${FormatHelper.formatCurrencyAmountAsString(900.00)}",
      subHeadingParamClasses = "font-large bold"
    ))
  )

  private def subHeadingPaymentsOverdue(implicit messages: Messages): CardSubHeading =
    CardSubHeading(
      subHeading = "",
      subHeadingClasses = "govuk-tag govuk-tag--red",
      subHeadingParams = Seq(CardSubHeadingParam(
        subHeadingParam = messages("pspDashboardOverdueAftChargesCard.overdue.span"),
        subHeadingParamClasses = "govuk-tag govuk-tag--red"
      ))
    )

  private def viewFinancialOverviewLink()(implicit messages: Messages): Link =
    Link(
      id = "view-your-financial-overview",
      url = overviewurl,
      linkText = Text(Messages("pspDashboardUpcomingAftChargesCard.link.financialOverview")),
      hiddenText = None
    )

  private def viewAllPaymentsAndChargesLink()(implicit messages: Messages): Link =
       Link(
      id = "past-payments-and-charges",
      url = viewFinancialInfoPastChargesUrl,
      linkText = Text(Messages("pspDashboardUpcomingAftChargesCard.link.allPaymentsAndCharges")),
      hiddenText = None
    )

  private val overviewurl: String = s"$aftUrl/srn/financial-overview"







  private def overdueChargesSingleModel(implicit messages: Messages): Seq[CardViewModel] = overdueChargesModel(
    BigDecimal(100.00), BigDecimal(100.00),
    Messages("pspDashboardOverdueAftChargesCard.viewOverduePayments.link.singlePeriod"
      , Seq(LocalDate.parse(startDate).format(smallDatePattern),
        LocalDate.parse(endDate).format(smallDatePattern)
      )),
    viewOverdueChargesUrl)


  private def overdueChargesModel(totalOverdue: BigDecimal = BigDecimal(300.00), totalInterestAccruing: BigDecimal = BigDecimal(300.00),
                          overdueLinkText: String = "pspDashboardOverdueAftChargesCard.viewOverduePayments.link.multiplePeriods",
                          link: String = viewOverdueChargesUrl)
                         (implicit messages: Messages): Seq[CardViewModel] = Seq(CardViewModel(
    id = "aft-overdue-charges",
    heading = messages("pspDashboardOverdueAftChargesCard.h2"),
    subHeadings = Seq(CardSubHeading(
      subHeading = messages("pspDashboardOverdueAftChargesCard.total.span"),
      subHeadingClasses = "card-sub-heading",
      subHeadingParams = Seq(CardSubHeadingParam(
        subHeadingParam = s"${FormatHelper.formatCurrencyAmountAsString(totalOverdue)}",
        subHeadingParamClasses = "font-large bold"
      ))
    ),
      CardSubHeading(
        subHeading = messages("pspDashboardOverdueAftChargesCard.interestAccruing.span"),
        subHeadingClasses = "card-sub-heading",
        subHeadingParams = Seq(
          CardSubHeadingParam(
            subHeadingParam = s"${FormatHelper.formatCurrencyAmountAsString(totalInterestAccruing)}",
            subHeadingParamClasses = "font-large bold inline-block"
          ),
          CardSubHeadingParam(
            subHeadingParam = messages("pspDashboardOverdueAftChargesCard.toDate.span"),
            subHeadingParamClasses = "font-xsmall inline-block"
          )
        )
      )
    ),
    links = Seq(Link(
      id = "overdue-payments-and-charges",
      url = link,
      linkText = Text(Messages(overdueLinkText)),
      hiddenText = None
    ))
  ))


  private val overviewApril20: AFTOverview = AFTOverview(
    LocalDate.of(2020, 4, 1),
    LocalDate.of(2020, 6, 30),
    tpssReportPresent = false,
    Some(AFTOverviewVersion(
      2,
      submittedVersionAvailable = true,
      compiledVersionAvailable = false
    )))

  private val overviewJuly20: AFTOverview = AFTOverview(
    LocalDate.of(2020, 7, 1),
    LocalDate.of(2020, 9, 30),
    tpssReportPresent = false,
    Some(AFTOverviewVersion(
      2,
      submittedVersionAvailable = true,
      compiledVersionAvailable = false
    )))

  private val overviewOctober20: AFTOverview = AFTOverview(
    LocalDate.of(2020, 10, 1),
    LocalDate.of(2020, 12, 31),
    tpssReportPresent = false,
    Some(AFTOverviewVersion(
      2,
      submittedVersionAvailable = true,
      compiledVersionAvailable = true
    )))

  private val overviewJan21: AFTOverview = AFTOverview(
    LocalDate.of(2021, 1, 1),
    LocalDate.of(2021, 3, 31),
    tpssReportPresent = false,
    Some(AFTOverviewVersion(
      2,
      submittedVersionAvailable = true,
      compiledVersionAvailable = true
    )))

  private val allTypesMultipleReturnsPresent = Seq(overviewApril20, overviewJuly20, overviewOctober20, overviewJan21)
  private val noInProgress = Seq(overviewApril20, overviewJuly20)
  private val oneInProgress = Seq(overviewApril20, overviewOctober20)

  private def aftModel(subHeadings: Seq[CardSubHeading], links: Seq[Link])
                      (implicit messages: Messages): CardViewModel = CardViewModel(
    id = "aft-overview",
    heading = messages("aftPartial.head"),
    subHeadings = subHeadings,
    links = links
  )

  private def allTypesMultipleReturnsModel(implicit messages: Messages): Seq[CardViewModel] =
    Seq(aftModel(Seq(multipleInProgressSubHead()), Seq(multipleInProgressLink, startLink, pastReturnsLink)))

  private def noInProgressModel(implicit messages: Messages): Seq[CardViewModel] =
    Seq(aftModel(Nil, Seq(startLink, pastReturnsLink)))

  private def oneInProgressModelLocked(implicit messages: Messages): Seq[CardViewModel] =
    Seq(aftModel(Seq(oneInProgressSubHead(messages("aftPartial.status.lockDetail", name))),
      Seq(oneInProgressLink(Text(Messages("pspDashboardAftReturnsCard.inProgressReturns.link.single.locked"))), startLink, pastReturnsLink)))

  private def oneInProgressModelNotLocked(implicit messages: Messages): Seq[CardViewModel] =
    Seq(aftModel(Seq(oneInProgressSubHead(messages("aftPartial.status.inProgress"))),
      Seq(oneInProgressLink(Text(Messages("pspDashboardAftReturnsCard.inProgressReturns.link.single"))), startLink, pastReturnsLink)))

  private def oneCompileZeroedOutModel(implicit messages: Messages): Seq[CardViewModel] =
    Seq(aftModel(Seq(multipleInProgressSubHead()), Seq(multipleInProgressLink, startLink)))


  private def startLink(implicit messages: Messages): Link = Link(id = "aftLoginLink", url = aftLoginUrl, linkText = Text(Messages("aftPartial.start.link")))

  private def pastReturnsLink(implicit messages: Messages): Link = Link(id = "aftAmendLink", url = amendUrl, linkText = Text(Messages("aftPartial.view.change.past")))

  private def oneInProgressSubHead(subHeadingParam: String)(implicit messages: Messages): CardSubHeading = {

    CardSubHeading(
      subHeading = messages("aftPartial.inProgress.forPeriod", startDt, endDt),
      subHeadingClasses = "card-sub-heading",
      subHeadingParams = Seq(CardSubHeadingParam(
        subHeadingParam = subHeadingParam,
        subHeadingParamClasses = "font-small bold"
      )))
  }

  private def oneInProgressLink(linkText: Text)(implicit messages: Messages): Link = Link(
    id = "aftSummaryLink",
    url = aftSummaryUrl,
    linkText = linkText,
    hiddenText = Some(Text(Messages("aftPartial.view.hidden.forPeriod", Seq(startDt, endDt))))
  )

  private def multipleInProgressSubHead(count: Int = 2)(implicit messages: Messages): CardSubHeading =
    CardSubHeading(
      subHeading = messages("aftPartial.multipleInProgress.text"),
      subHeadingClasses = "card-sub-heading",
      subHeadingParams = Seq(CardSubHeadingParam(
        subHeadingParam = messages("aftPartial.multipleInProgress.count", count),
        subHeadingParamClasses = "font-small bold"
      )))

  private def multipleInProgressLink(implicit messages: Messages) = Link(
    id = "aftContinueInProgressLink",
    url = continueUrl,
    linkText = Text(Messages("pspDashboardAftReturnsCard.inProgressReturns.link")),
    hiddenText = Some(Text(Messages("aftPartial.view.hidden")))
  )

  private def oneCompileZeroedOut: Seq[AFTOverview] =
    Seq(
      overviewApril20.copy(versionDetails = Some(overviewApril20.versionDetails.get.copy(numberOfVersions = 1, compiledVersionAvailable = true))),
      overviewJuly20.copy(versionDetails = Some(overviewJuly20.versionDetails.get.copy(numberOfVersions = 1, compiledVersionAvailable = true))),
      overviewOctober20.copy(versionDetails = Some(overviewOctober20.versionDetails.get.copy(numberOfVersions = 2, compiledVersionAvailable = true)))
    )



}
