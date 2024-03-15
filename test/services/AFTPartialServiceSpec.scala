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
import data.SampleData.multiplePenalties
import helpers.FormatHelper
import models._
import models.financialStatement.SchemeFSChargeType.PSS_AFT_RETURN
import models.financialStatement.{SchemeFSChargeType, SchemeFSDetail}
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.{reset, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.mockito.MockitoSugar
import play.api.i18n.Messages
import play.api.libs.json.Json
import services.paymentsAndCharges.PaymentsAndChargesService
import uk.gov.hmrc.viewmodels._
import utils.DateHelper
import viewmodels._

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import scala.concurrent.{ExecutionContext, Future}

class AFTPartialServiceSpec
  extends SpecBase
    with MockitoSugar
    with BeforeAndAfterEach
    with ScalaFutures {

  import AFTPartialServiceSpec._

  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
  private val aftConnector = mock[AFTConnector]

  private val aftCacheConnector = mock[UserAnswersCacheConnector]
  private val paymentsAndChargesService = mock[PaymentsAndChargesService]
  private val documentLineItemDetails = Seq()

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(paymentsAndChargesService, aftConnector, aftCacheConnector)
  }

  def service: AFTPartialService =
    new AFTPartialService(frontendAppConfig, paymentsAndChargesService, aftConnector, aftCacheConnector)

  "retrievePspDashboardAftReturnsModel" must {
    "return overview api returns multiple returns in progress, " +
      "multiple past returns and start link needs to be displayed" in {
      DateHelper.setDate(Some(LocalDate.of(2021, 4, 1)))
      when(aftConnector.getAftOverview(any(), any(), any())(any(), any()))
        .thenReturn(Future.successful(allTypesMultipleReturnsPresent))
      when(aftConnector.aftOverviewStartDate).thenReturn(LocalDate.of(2020, 4, 1))
      when(aftConnector.aftOverviewEndDate).thenReturn(LocalDate.of(2021, 6, 30))

      whenReady(service.retrievePspDashboardAftReturnsModel(srn, pstr, psaId)) {
        _ mustBe pspDashboardAftReturnsViewModel
      }
    }

    "return the correct model when return one return is in progress but not locked" in {
      when(aftConnector.getAftOverview(any(), any(), any())(any(), any()))
        .thenReturn(Future.successful(oneInProgress))
      when(aftConnector.aftOverviewStartDate).thenReturn(LocalDate.of(2020, 4, 1))
      when(aftConnector.aftOverviewEndDate).thenReturn(LocalDate.of(2021, 6, 30))
      when(aftCacheConnector.lockDetail(any(), any())(any(), any()))
        .thenReturn(Future.successful(None))

      whenReady(service.retrievePspDashboardAftReturnsModel(srn, pstr, psaId)) {
        _ mustBe pspDashboardOneInProgressModelWithLocking(
          locked = false,
          h3 = "In progress",
          span = "AFT return 1 October to 31 December 2020:",
          linkText = "pspDashboardAftReturnsCard.inProgressReturns.link.single"
        )
      }
    }

    "return the correct model when one return is in progress and locked by another user" in {
      when(aftConnector.getAftOverview(any(), any(), any())(any(), any()))
        .thenReturn(Future.successful(oneInProgress))
      when(aftConnector.aftOverviewStartDate).thenReturn(LocalDate.of(2020, 4, 1))
      when(aftConnector.aftOverviewEndDate).thenReturn(LocalDate.of(2021, 6, 30))
      when(aftCacheConnector.lockDetail(any(), any())(any(), any()))
        .thenReturn(Future.successful(Some(LockDetail(name, psaId))))

      whenReady(service.retrievePspDashboardAftReturnsModel(srn, pstr, psaId)) {
        _ mustBe pspDashboardOneInProgressModelWithLocking(
          locked = true,
          h3 = "Locked by test-name",
          span = "AFT return 1 October to 31 December 2020:",
          linkText = "pspDashboardAftReturnsCard.inProgressReturns.link.single.locked"
        )
      }
    }

    "return a model with start link and only 2 returns in progress" when {
      "a scheme has 3 compiles in progress but " +
        "one has been zeroed out and all quarters have been initiated (ie no start link)" in {
        DateHelper.setDate(Some(LocalDate.of(2020, 12, 31)))
        when(aftConnector.getAftOverview(any(), any(), any())(any(), any()))
          .thenReturn(Future.successful(oneCompileZeroedOut))
        when(aftConnector.aftOverviewStartDate).thenReturn(LocalDate.of(2020, 4, 1))
        when(aftConnector.aftOverviewEndDate).thenReturn(LocalDate.of(2021, 12, 31))
        when(aftCacheConnector.lockDetail(any(), any())(any(), any()))
          .thenReturn(Future.successful(None))
        when(aftConnector.getIsAftNonZero(any(), ArgumentMatchers.eq("2020-07-01"), any())(any(), any()))
          .thenReturn(Future.successful(false))
        when(aftConnector.getIsAftNonZero(any(), ArgumentMatchers.eq("2020-04-01"), any())(any(), any()))
          .thenReturn(Future.successful(true))

        whenReady(service.retrievePspDashboardAftReturnsModel(srn, pstr, psaId)) {
          _ mustBe pspDashboardOneCompileZeroedOutModel
        }
      }
    }
  }

  "retrievePspDashboardUpcomingAftCharges" must {
    "return a model for a single period upcoming charges with no past charges" in {
      val service = app.injector.instanceOf[AFTPartialService]

      service.retrievePspDashboardUpcomingAftChargesModel(schemeFSResponseSinglePeriod(), srn) mustBe
        DashboardAftViewModel(
          subHeadings = Seq(Json.obj(
            "total" -> "£3,087.15",
            "span" -> "Payment due by 15 February 2021:"
          )),
          links = Seq(
            Link(
              id = "upcoming-payments-and-charges",
              url = viewUpcomingChargesUrl,
              linkText =
                msg"pspDashboardUpcomingAftChargesCard.link.paymentsAndChargesForPeriod.single"
                  .withArgs("1 October", "31 December"),
              hiddenText = None
            )
          )
        )
    }


    "return a model for multiple period upcoming charges with no past charges" in {
      val service = app.injector.instanceOf[AFTPartialService]

      service.retrievePspDashboardUpcomingAftChargesModel(schemeFSResponseMultiplePeriods(), srn) mustBe
        DashboardAftViewModel(
          subHeadings = Seq(Json.obj(
            "total" -> "£3,087.15",
            "span" -> "Total upcoming payments:"
          )),
          links = Seq(
            Link(
              id = "upcoming-payments-and-charges",
              url = viewUpcomingChargesUrl,
              linkText =
                msg"pspDashboardUpcomingAftChargesCard.link.paymentsAndChargesForPeriod.multiple",
              hiddenText = None
            )
          )
        )
    }

    "return a model for a single period upcoming charges with past charges" in {
      val service = app.injector.instanceOf[AFTPartialService]
      val schemeFSDetail = schemeFSResponseSinglePeriod() ++ pastCharges
      service.retrievePspDashboardUpcomingAftChargesModel(schemeFSDetail, srn) mustBe
        DashboardAftViewModel(
          subHeadings = Seq(Json.obj(
            "total" -> "£3,087.15",
            "span" -> "Payment due by 15 February 2021:"
          )),
          links = Seq(
            Link(
              id = "upcoming-payments-and-charges",
              url = viewUpcomingChargesUrl,
              linkText =
                msg"pspDashboardUpcomingAftChargesCard.link.paymentsAndChargesForPeriod.single"
                  .withArgs("1 October", "31 December"),
              hiddenText = None
            ),
            Link(
              id = "past-payments-and-charges",
              url = viewPastChargesUrl,
              linkText = msg"pspDashboardUpcomingAftChargesCard.link.allPaymentsAndCharges",
              hiddenText = None
            )
          )
        )
    }


    "return a model for multiple period upcoming charges with past charges" in {
      val service = app.injector.instanceOf[AFTPartialService]
      val schemeFSDetail = schemeFSResponseMultiplePeriods() ++ pastCharges
      service.retrievePspDashboardUpcomingAftChargesModel(schemeFSDetail, srn) mustBe
        DashboardAftViewModel(
          subHeadings = Seq(Json.obj(
            "total" -> "£3,087.15",
            "span" -> "Total upcoming payments:"
          )),
          links = Seq(
            Link(
              id = "upcoming-payments-and-charges",
              url = viewUpcomingChargesUrl,
              linkText =
                msg"pspDashboardUpcomingAftChargesCard.link.paymentsAndChargesForPeriod.multiple",
              hiddenText = None
            ),
            Link(
              id = "past-payments-and-charges",
              url = viewPastChargesUrl,
              linkText = msg"pspDashboardUpcomingAftChargesCard.link.allPaymentsAndCharges",
              hiddenText = None
            )
          )
        )
    }
  }

  "retrievePspDashboardOverdueAftCharges" must {
    "return a model for a single period overdue charges with no interest accruing" in {
      service.retrievePspDashboardOverdueAftChargesModel(schemeFSResponseSinglePeriod(), srn) mustBe
        DashboardAftViewModel(
          subHeadings = Seq(
            Json.obj(
              "total" -> "£3,087.15",
              "span" -> "Total overdue payments:"
            ),
            Json.obj(
              "total" -> "£0.00",
              "span" -> "Interest accruing:"
            )
          ),
          links = Seq(
            Link(
              id = "overdue-payments-and-charges",
              url = viewOverdueChargesUrl,
              linkText =
                msg"pspDashboardOverdueAftChargesCard.viewOverduePayments.link.singlePeriod"
                  .withArgs("1 October", "31 December"),
              hiddenText = None
            )
          )
        )
    }


    "return a model for a single period overdue charges with interest accruing" in {
      service.retrievePspDashboardOverdueAftChargesModel(
        schemeFSResponseSinglePeriod(123.00), srn
      ) mustBe
        DashboardAftViewModel(
          subHeadings = Seq(
            Json.obj(
              "total" -> "£3,087.15",
              "span" -> "Total overdue payments:"
            ),
            Json.obj(
              "total" -> "£369.00",
              "span" -> "Interest accruing:"
            )
          ),
          links = Seq(
            Link(
              id = "overdue-payments-and-charges",
              url = viewOverdueChargesUrl,
              linkText =
                msg"pspDashboardOverdueAftChargesCard.viewOverduePayments.link.singlePeriod"
                  .withArgs("1 October", "31 December"),
              hiddenText = None
            )
          )
        )
    }

    "return a model for a multiple periods overdue charges with no interest accruing" in {
      service.retrievePspDashboardOverdueAftChargesModel(schemeFSResponseMultiplePeriods(), srn) mustBe
        DashboardAftViewModel(
          subHeadings = Seq(
            Json.obj(
              "total" -> "£3,087.15",
              "span" -> "Total overdue payments:"
            ),
            Json.obj(
              "total" -> "£0.00",
              "span" -> "Interest accruing:"
            )
          ),
          links = Seq(
            Link(
              id = "overdue-payments-and-charges",
              url = viewOverdueChargesUrl,
              linkText =
                msg"pspDashboardOverdueAftChargesCard.viewOverduePayments.link.multiplePeriods",
              hiddenText = None
            )
          )
        )
    }


    "return a model for a multiple periods overdue charges with interest accruing" in {
      service.retrievePspDashboardOverdueAftChargesModel(schemeFSResponseMultiplePeriods(123.00), srn) mustBe
        DashboardAftViewModel(
          subHeadings = Seq(Json.obj(
            "total" -> "£3,087.15",
            "span" -> "Total overdue payments:"
          ),
            Json.obj(
              "total" -> "£369.00",
              "span" -> "Interest accruing:"
            )
          ),
          links = Seq(
            Link(
              id = "overdue-payments-and-charges",
              url = viewOverdueChargesUrl,
              linkText =
                msg"pspDashboardOverdueAftChargesCard.viewOverduePayments.link.multiplePeriods",
              hiddenText = None
            )
          )
        )
    }
  }

  "retrievePsaPenaltiesCardModel" must {
    "return the correct viewmodel" when {
      "there are no upcoming payments" in {
        val penalties = Seq(
          multiplePenalties(0).copy(amountDue = BigDecimal(0.00)),
          multiplePenalties(1).copy(amountDue = BigDecimal(0.00))
        )

        service.retrievePsaPenaltiesCardModel(penalties) mustBe
          aftViewModel(upcomingLink = Nil, upcomingAmount = "£0.00", overdueAmount = "£0.00")
      }

      "there are upcoming payments for a single due date" in {
        val dueDate: LocalDate = LocalDate.now().plusDays(5)
        val penalties = Seq(
          multiplePenalties(0).copy(dueDate = Some(dueDate)),
          multiplePenalties(1).copy(dueDate = Some(dueDate))
        )
        val message = msg"pspDashboardUpcomingAftChargesCard.span.singleDueDate".withArgs(
          dueDate.format(DateTimeFormatter.ofPattern("d MMMM yyyy")))

        service.retrievePsaPenaltiesCardModel(penalties) mustBe aftViewModel(message)
      }

      "there are upcoming payments for multiple due dates" in {
        val penalties = Seq(
          multiplePenalties(0).copy(dueDate = Some(LocalDate.now().plusDays(5))),
          multiplePenalties(1).copy(dueDate = Some(LocalDate.now().plusMonths(5)))
        )
        service.retrievePsaPenaltiesCardModel(penalties) mustBe aftViewModel()
      }
    }
  }

  "retrievePsaChargesAmount" must {
    "return the correct value for charge" when {
      "Upcoming charge is Zero" in {
        val penalties = Seq(
          multiplePenalties(0).copy( dueDate = Some(LocalDate.parse("2020-11-15"))),
          multiplePenalties(1).copy( dueDate = Some(LocalDate.parse("2020-11-15")))
        )

        service.retrievePsaChargesAmount(penalties) mustBe
          Tuple3("£0.00", "£200.00","£0.00")
      }

      "Upcoming charge is Exists and OverDue charge Exists" in {
        val penalties = Seq(
          multiplePenalties(0).copy( dueDate = Some(LocalDate.now())),
          multiplePenalties(1).copy( dueDate = Some(LocalDate.parse("2020-11-15")))
        )

        service.retrievePsaChargesAmount(penalties) mustBe
          Tuple3("£100.00", "£100.00","£0.00")
      }

      "Upcoming charge is Exists and OverDue charge is Zero" in {
        val penalties = Seq(
          multiplePenalties(0).copy( dueDate = Some(LocalDate.now())),
          multiplePenalties(1).copy( dueDate = Some(LocalDate.now()))
        )

        service.retrievePsaChargesAmount(penalties) mustBe
          Tuple3("£200.00", "£0.00","£0.00")
      }

      "Interest on charge exists" in {
        val penalties = Seq(
          multiplePenalties(0).copy( dueDate = Some(LocalDate.parse("2020-11-15")),accruedInterestTotal = BigDecimal("100")),
          multiplePenalties(1).copy( dueDate = Some(LocalDate.now()))
        )

        service.retrievePsaChargesAmount(penalties) mustBe
          Tuple3("£100.00", "£100.00","£100.00")
      }

    }
  }

  "getCreditBalanceAmount" must {
    "return the correct value for Credit" when {
      "Zero creditBalance if due date not exists" in {
        val penalties = Seq(
          multiplePenalties(0).copy( dueDate = None),
          multiplePenalties(1).copy( dueDate = None)
        )

        service.getCreditBalanceAmount(penalties) mustBe BigDecimal("0.00")
      }

      "creditBalance amount zero if positive due amount " in {
        val penalties = Seq(
          multiplePenalties(0).copy( dueDate = Some(LocalDate.parse("2020-11-15"))),
          multiplePenalties(1).copy( dueDate = Some(LocalDate.parse("2020-11-15")))
        )

        service.getCreditBalanceAmount(penalties) mustBe BigDecimal("0.00")
      }

      "creditBalance amount if negative due amount" in {
        val penalties = Seq(
          multiplePenalties(0).copy( dueDate = Some(LocalDate.parse("2020-11-15")),amountDue = BigDecimal("-200")),
          multiplePenalties(1).copy( dueDate = Some(LocalDate.parse("2020-11-15")))
        )

        service.getCreditBalanceAmount(penalties) mustBe BigDecimal("100.00")
      }

    }
  }

  "payments and charges dashboard" must {
    "return the correct model when there is an outstanding amount to be displayed but there are no overdue charges" in {
      DateHelper.setDate(Some(LocalDate.of(2022, 3, 2)))
      when(paymentsAndChargesService.extractUpcomingCharges).thenReturn(_ => upcomingChargesMultiple)
      when(paymentsAndChargesService.getOverdueCharges(any())).thenReturn(outstandingAmountOverdue)
      when(paymentsAndChargesService.getInterestCharges(any())).thenReturn(outstandingAmountOverdue)
      service.retrievePspDashboardPaymentsAndChargesModel(upcomingChargesMultiple, srn, pstr) mustBe paymentsAndChargesModel

    }
  }


  private val charge1: SchemeFSDetail = SchemeFSDetail(index = 0,"XYZ", SchemeFSChargeType.PSS_AFT_RETURN, Some(LocalDate.parse("2021-04-15")), BigDecimal(100.00),
    BigDecimal(100.00), BigDecimal(100.00), BigDecimal(100.00), BigDecimal(100.00), Some(LocalDate.parse(startDate)), Some(LocalDate.parse(endDate)), None, None, None, None, None, documentLineItemDetails)

  private val charge2: SchemeFSDetail = SchemeFSDetail(index = 0,"XYZ", SchemeFSChargeType.PSS_OTC_AFT_RETURN, Some(LocalDate.parse("2021-04-15")), BigDecimal(200.00),
    BigDecimal(200.00), BigDecimal(200.00), BigDecimal(200.00), BigDecimal(200.00), Some(LocalDate.parse("2021-01-01")), Some(LocalDate.parse("2021-03-31")), None, None, None, None, None, documentLineItemDetails)
  private val upcomingChargesMultiple: Seq[SchemeFSDetail] = Seq(charge1, charge2)
  private val outstandingAmountOverdue: Seq[SchemeFSDetail]= Seq(charge1)
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
      subHeadingParam = s"${FormatHelper.formatCurrencyAmountAsString(500.00)}",
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

  private def viewFinancialOverviewLink(): Link =
    Link(
      id = "view-your-financial-overview",
      url = overviewurl,
      linkText = msg"pspDashboardUpcomingAftChargesCard.link.financialOverview",
      hiddenText = None
    )
  private val overviewurl: String = s"$aftUrl/srn/financial-overview"
  private def viewAllPaymentsAndChargesLink(): Link =
    Link(
      id = "past-payments-and-charges",
      url = viewFinancialInfoPastChargesUrl,
      linkText = msg"pspDashboardUpcomingAftChargesCard.link.allPaymentsAndCharges",
      hiddenText = None
    )

}


object AFTPartialServiceSpec {
  private val startDate = "2020-04-01"
  private val endDate = "2020-06-30"
  private val dateFormatterYMD: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
  private val formattedStartDate: String =
    LocalDate.parse(startDate, dateFormatterYMD).format(DateTimeFormatter.ofPattern("d MMMM"))
  private val formattedEndDate: String =
    LocalDate.parse(endDate, dateFormatterYMD).format(DateTimeFormatter.ofPattern("d MMMM yyyy"))
  private val srn = "srn"
  private val pstr = "pstr"
  private val psaId = "A0000000"
  private val name = "test-name"
  val minimalPsaName: Option[String] = Some("John Doe Doe")
  private val viewPenaltiesUrl = "http://localhost:8206/manage-pension-scheme-accounting-for-tax/view-penalties"
  private val viewUpcomingPenaltiesUrl = "http://localhost:8206/manage-pension-scheme-accounting-for-tax/view-upcoming-penalties"

  private val upcomingLink = Seq(Link("outstanding-penalties-id", viewUpcomingPenaltiesUrl, msg"psaPenaltiesCard.paymentsDue.linkText", None))

  def aftViewModel(message: Text = msg"pspDashboardUpcomingAftChargesCard.span.multipleDueDate",
                   upcomingLink: Seq[Link] = upcomingLink,
                   upcomingAmount: String = "£200.00",
                   overdueAmount: String = "£0.00",
                  )(implicit messages: Messages): DashboardAftViewModel = DashboardAftViewModel(
    subHeadings = Seq(
      Json.obj(
        "total" -> upcomingAmount,
        "span" -> message
      ),
      Json.obj(
        "total" -> overdueAmount,
        "span" -> msg"pspDashboardOverdueAftChargesCard.total.span"
      )
    ),
    links = upcomingLink :+ Link("past-penalties-id", viewPenaltiesUrl, msg"psaPenaltiesCard.viewPastPenalties", None)
  )

  def lockedAftModel: Seq[AFTViewModel] = Seq(
    AFTViewModel(
      Some(msg"aftPartial.inProgress.forPeriod".withArgs(formattedStartDate, formattedEndDate)),
      Some(msg"aftPartial.status.lockDetail".withArgs(name)),
      Link(
        id = "aftSummaryPageLink",
        url = s"http://localhost:8206/manage-pension-scheme-accounting-for-tax/$srn/$startDate/$Draft/1/summary",
        linkText = msg"aftPartial.view.link",
        hiddenText = Some(msg"aftPartial.view.hidden.forPeriod".withArgs(formattedStartDate, formattedEndDate)))
    )
  )

  def unlockedEmptyAftModel: Seq[AFTViewModel] = Seq(
    AFTViewModel(
      None,
      None,
      Link(
        id = "aftChargeTypePageLink",
        url = s"http://localhost:8206/manage-pension-scheme-accounting-for-tax/$srn/new-return/aft-login",
        linkText = msg"aftPartial.startLink.forPeriod".withArgs(formattedStartDate, formattedEndDate))
    )
  )

  def lockedAftModelWithNoVersion: Seq[AFTViewModel] = Seq(
    AFTViewModel(
      Some(msg"aftPartial.inProgress.forPeriod".withArgs(formattedStartDate, formattedEndDate)),
      Some(msg"aftPartial.status.lockDetail".withArgs(name)),
      Link(
        id = "aftSummaryPageLink",
        url = s"http://localhost:8206/manage-pension-scheme-accounting-for-tax/$srn/$startDate/$Draft/1/summary",
        linkText = msg"aftPartial.view.link",
        hiddenText = Some(msg"aftPartial.view.hidden.forPeriod".withArgs(formattedStartDate, formattedEndDate)))
    )
  )

  def inProgressUnlockedAftModel: Seq[AFTViewModel] = Seq(
    AFTViewModel(
      Some(msg"aftPartial.inProgress.forPeriod".withArgs(formattedStartDate, formattedEndDate)),
      Some(msg"aftPartial.status.inProgress"),
      Link(
        id = "aftSummaryPageLink",
        url = s"http://localhost:8206/manage-pension-scheme-accounting-for-tax/$srn/$startDate/$Draft/1/summary",
        linkText = msg"aftPartial.view.link",
        hiddenText = Some(msg"aftPartial.view.hidden.forPeriod".withArgs(formattedStartDate, formattedEndDate)))
    )
  )


  val overviewApril20: AFTOverview = AFTOverview(
    LocalDate.of(2020, 4, 1),
    LocalDate.of(2020, 6, 30),
    tpssReportPresent = false,
    Some(AFTOverviewVersion(
      2,
      submittedVersionAvailable = true,
      compiledVersionAvailable = false
    )))

  val overviewJuly20: AFTOverview = AFTOverview(
    LocalDate.of(2020, 7, 1),
    LocalDate.of(2020, 9, 30),
    tpssReportPresent = false,
    Some(AFTOverviewVersion(
      2,
      submittedVersionAvailable = true,
      compiledVersionAvailable = false
    )))

  val overviewOctober20: AFTOverview = AFTOverview(
    LocalDate.of(2020, 10, 1),
    LocalDate.of(2020, 12, 31),
    tpssReportPresent = false,
    Some(AFTOverviewVersion(
      2,
      submittedVersionAvailable = true,
      compiledVersionAvailable = true
    )))

  val overviewJan21: AFTOverview = AFTOverview(
    LocalDate.of(2021, 1, 1),
    LocalDate.of(2021, 3, 31),
    tpssReportPresent = false,
    Some(AFTOverviewVersion(
      2,
      submittedVersionAvailable = true,
      compiledVersionAvailable = true
    )))

  private val aftUrl = "http://localhost:8206/manage-pension-scheme-accounting-for-tax"

  val aftLoginUrl: String = s"$aftUrl/srn/new-return/aft-login"
  val amendUrl: String = s"$aftUrl/srn/previous-return/amend-select"
  val returnHistoryUrl: String = s"$aftUrl/srn/previous-return/2020-10-01/amend-previous"
  val aftSummaryUrl: String = s"$aftUrl/srn/2020-10-01/draft/2/summary"
  val continueUrl: String = s"$aftUrl/srn/new-return/select-quarter-in-progress"
  val viewUpcomingChargesUrl: String = s"$aftUrl/srn/upcoming-payments-logic"
  val viewOverdueChargesUrl: String = s"$aftUrl/srn/overdue-payments-logic"
  val viewPastChargesUrl: String = s"$aftUrl/srn/past-payments-logic"
  val viewFinancialInfoPastChargesUrl: String = s"$aftUrl/srn/financial-overview/past-payments-logic"

  def startModel: AFTViewModel = AFTViewModel(None, None,
    Link(id = "aftLoginLink", url = aftLoginUrl,
      linkText = msg"aftPartial.start.link"))

  def pastReturnsModel: AFTViewModel = AFTViewModel(None, None,
    Link(
      id = "aftAmendLink",
      url = amendUrl,
      linkText = msg"aftPartial.view.change.past"))

  def multipleInProgressModel(count: Int, linkText: String = "aftPartial.view.link"): AFTViewModel =
    AFTViewModel(
      Some(msg"aftPartial.multipleInProgress.text"),
      Some(msg"aftPartial.multipleInProgress.count".withArgs(count)),
      Link(
        id = "aftContinueInProgressLink",
        url = continueUrl,
        linkText = msg"$linkText",
        hiddenText = Some(msg"aftPartial.view.hidden"))
    )

  def oneInProgressModel(locked: Boolean, linkText: String = "aftPartial.view.link"): AFTViewModel = AFTViewModel(
    Some(msg"aftPartial.inProgress.forPeriod".withArgs("1 October", "31 December 2020")),
    if (locked) {
      Some(msg"aftPartial.status.lockDetail".withArgs(name))
    }
    else {
      Some(msg"aftPartial.status.inProgress")
    },
    Link(id = "aftSummaryLink", url = aftSummaryUrl,
      linkText = msg"$linkText",
      hiddenText = Some(msg"aftPartial.view.hidden.forPeriod".withArgs("1 October", "31 December 2020")))
  )

  val allTypesMultipleReturnsPresent = Seq(overviewApril20, overviewJuly20, overviewOctober20, overviewJan21)
  val noInProgress = Seq(overviewApril20, overviewJuly20)
  val oneInProgress = Seq(overviewApril20, overviewOctober20)

  def allTypesMultipleReturnsModel: Seq[AFTViewModel] =
    Seq(multipleInProgressModel(2), startModel, pastReturnsModel)

  def noInProgressModel: Seq[AFTViewModel] =
    Seq(startModel, pastReturnsModel)

  def oneInProgressModelLocked: Seq[AFTViewModel] =
    Seq(oneInProgressModel(locked = true), startModel, pastReturnsModel)

  def oneInProgressModelNotLocked: Seq[AFTViewModel] =
    Seq(oneInProgressModel(locked = false), startModel, pastReturnsModel)

  def oneCompileZeroedOut: Seq[AFTOverview] =
    Seq(
      overviewApril20.copy(versionDetails = Some(overviewApril20.versionDetails.get.copy(numberOfVersions = 1, compiledVersionAvailable = true))),
      overviewJuly20.copy(versionDetails = Some(overviewJuly20.versionDetails.get.copy(numberOfVersions = 1, compiledVersionAvailable = true))),
      overviewOctober20.copy(versionDetails = Some(overviewOctober20.versionDetails.get.copy(numberOfVersions = 2, compiledVersionAvailable = true)))
    )

  def oneCompileZeroedOutModel: Seq[AFTViewModel] =
    Seq(multipleInProgressModel(2), startModel)

  def pspDashboardAftReturnsViewModel: DashboardAftViewModel =
    DashboardAftViewModel(
      subHeadings = Seq(Json.obj(
        "h3" -> "2 in progress",
        "span" -> "AFT returns:"
      )),
      links = Seq(
        multipleInProgressModel(3, "pspDashboardAftReturnsCard.inProgressReturns.link"),
        startModel,
        pastReturnsModel
      ).map(_.link)
    )


  def pspDashboardOneInProgressModelWithLocking(
                                                 locked: Boolean,
                                                 h3: String,
                                                 span: String,
                                                 linkText: String
                                               ): DashboardAftViewModel =
    DashboardAftViewModel(
      subHeadings = Seq(Json.obj(
        "span" -> span,
        "h3" -> h3
      )),
      links = Seq(
        oneInProgressModel(locked = locked, linkText = linkText),
        startModel,
        pastReturnsModel
      ).map(_.link)
    )

  def pspDashboardOneCompileZeroedOutModel: DashboardAftViewModel =
    DashboardAftViewModel(
      subHeadings = Seq(Json.obj(
        "h3" -> "3 in progress",
        "span" -> "AFT returns:"
      )),
      links = Seq(
        multipleInProgressModel(2, "pspDashboardAftReturnsCard.inProgressReturns.link"),
        startModel
      ).map(_.link)
    )

  private def createCharge(
                            startDate: String,
                            endDate: String,
                            dueDate: Option[LocalDate] = Option(LocalDate.parse("2021-02-15")),
                            chargeReference: String,
                            accruedInterestTotal: BigDecimal = 0.00
                          ): SchemeFSDetail = {
    SchemeFSDetail(
      index = 0,
      chargeReference = chargeReference,
      chargeType = PSS_AFT_RETURN,
      dueDate = dueDate,
      totalAmount = 56432.00,
      amountDue = 1029.05,
      outstandingAmount = 56049.08,
      accruedInterestTotal = accruedInterestTotal,
      stoodOverAmount = 25089.08,
      periodStartDate = Some(LocalDate.parse(startDate)),
      periodEndDate = Some(LocalDate.parse(endDate)),
      formBundleNumber = None,
      version = Some(1),
      receiptDate = Some(LocalDate.now),
      sourceChargeRefForInterest = None,
      sourceChargeInfo = None,
      documentLineItemDetails = Seq()
    )
  }

  private def schemeFSResponseSinglePeriod(accruedInterestTotal: BigDecimal = 0.00): Seq[SchemeFSDetail] = Seq(
    createCharge(
      startDate = "2020-10-01",
      endDate = "2020-12-31",
      chargeReference = "XY002610150184",
      accruedInterestTotal = accruedInterestTotal
    ),
    createCharge(
      startDate = "2020-10-01",
      endDate = "2020-12-31",
      chargeReference = "AYU3494534632",
      accruedInterestTotal = accruedInterestTotal
    ),
    createCharge(
      startDate = "2020-10-01",
      endDate = "2020-12-31",
      chargeReference = "XY002610150185",
      accruedInterestTotal = accruedInterestTotal
    )
  )

  private def schemeFSResponseMultiplePeriods(accruedInterestTotal: BigDecimal = 0.00): Seq[SchemeFSDetail] = Seq(
    createCharge(
      startDate = "2020-10-01",
      endDate = "2020-12-31",
      chargeReference = "XY002610150184",
      accruedInterestTotal = accruedInterestTotal
    ),
    createCharge(
      startDate = "2020-10-01",
      endDate = "2020-12-31",
      chargeReference = "AYU3494534632",
      accruedInterestTotal = accruedInterestTotal
    ),
    createCharge(
      startDate = "2021-01-01",
      endDate = "2021-03-31",
      chargeReference = "XY002610150185",
      accruedInterestTotal = accruedInterestTotal,
      dueDate = Option(LocalDate.parse("2021-05-15"))
    )
  )

  private val pastCharges: Seq[SchemeFSDetail] = Seq(
    createCharge(
      startDate = "2020-06-01",
      endDate = "2020-09-30",
      chargeReference = "XY002610150185",
      dueDate = None
    ),
    createCharge(
      startDate = "2020-06-01",
      endDate = "2020-09-30",
      chargeReference = "AYU3494534636",
      dueDate = None
    ),
    createCharge(
      startDate = "2020-06-01",
      endDate = "2020-09-30",
      chargeReference = "XY002610150187",
      dueDate = None
    )
  )
}
