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
import data.SampleData.multiplePenalties
import helpers.FormatHelper
import models._
import models.financialStatement.{SchemeFSChargeType, SchemeFSDetail}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.mockito.MockitoSugar
import play.api.i18n.Messages
import play.api.libs.json.Json
import services.paymentsAndCharges.PaymentsAndChargesService
import uk.gov.hmrc.govukfrontend.views.Aliases.Text
import utils.DateHelper
import viewmodels._

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import scala.concurrent.ExecutionContext

class AFTPartialServiceSpec
  extends SpecBase
    with MockitoSugar
    with BeforeAndAfterEach
    with ScalaFutures {

  import AFTPartialServiceSpec._

  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

  private val paymentsAndChargesService = mock[PaymentsAndChargesService]
  private val documentLineItemDetails = Seq()

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(paymentsAndChargesService)
  }

  def service: AFTPartialService =
    new AFTPartialService(frontendAppConfig, paymentsAndChargesService)

  "retrievePsaPenaltiesCardModel" must {
    "return the correct viewmodel" when {
      "there are no upcoming payments" in {
        val penalties = Seq(
          multiplePenalties(0).copy(amountDue = BigDecimal(0.00)),
          multiplePenalties(1).copy(amountDue = BigDecimal(0.00))
        )

        service.retrievePsaPenaltiesCardModel(penalties) mustBe
          aftViewModel(upcomingLink = Nil, upcomingAmount = "£0.00")
      }

      "there are upcoming payments for a single due date" in {
        val dueDate: LocalDate = LocalDate.now().plusDays(5)
        val penalties = Seq(
          multiplePenalties(0).copy(dueDate = Some(dueDate)),
          multiplePenalties(1).copy(dueDate = Some(dueDate))
        )
        val message = Messages("pspDashboardUpcomingAftChargesCard.span.singleDueDate", Seq(
          dueDate.format(DateTimeFormatter.ofPattern("d MMMM yyyy"))))

        service.retrievePsaPenaltiesCardModel(penalties) mustBe aftViewModel(message, upcomingLink = upcomingLinkDefault)
      }

      "there are upcoming payments for multiple due dates" in {
        val penalties = Seq(
          multiplePenalties(0).copy(dueDate = Some(LocalDate.now().plusDays(5))),
          multiplePenalties(1).copy(dueDate = Some(LocalDate.now().plusMonths(5)))
        )
        service.retrievePsaPenaltiesCardModel(penalties) mustBe aftViewModel(upcomingLink = upcomingLinkDefault)
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
      linkText = Text(Messages("pspDashboardUpcomingAftChargesCard.link.financialOverview")),
      hiddenText = None
    )
  private val overviewurl: String = s"$aftUrl/srn/financial-overview"
  private def viewAllPaymentsAndChargesLink(): Link =
    Link(
      id = "past-payments-and-charges",
      url = viewFinancialInfoPastChargesUrl,
      linkText = Text(Messages("pspDashboardUpcomingAftChargesCard.link.allPaymentsAndCharges")),
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
  private val name = "test-name"
  val minimalPsaName: Option[String] = Some("John Doe Doe")
  private val viewPenaltiesUrl = "http://localhost:8206/manage-pension-scheme-accounting-for-tax/view-penalties"
  private val viewUpcomingPenaltiesUrl = "http://localhost:8206/manage-pension-scheme-accounting-for-tax/view-upcoming-penalties"

  private def upcomingLinkDefault(implicit messages: Messages) = Seq(Link("outstanding-penalties-id", viewUpcomingPenaltiesUrl,
    Text(Messages("psaPenaltiesCard.paymentsDue.linkText")), None))

  def aftViewModel(message: String = "pspDashboardUpcomingAftChargesCard.span.multipleDueDate",
                   upcomingLink: Seq[Link] = Nil,
                   upcomingAmount: String = "£200.00"
                  )(implicit messages: Messages): DashboardAftViewModel = DashboardAftViewModel(
    subHeadings = Seq(
      Json.obj(
        "total" -> upcomingAmount,
        "span" -> Messages(message)
      ),
      Json.obj(
        "total" -> "£0.00",
        "span" -> Messages("pspDashboardOverdueAftChargesCard.total.span")
      )
    ),
    links = upcomingLink :+ Link("past-penalties-id", viewPenaltiesUrl, Text(Messages("psaPenaltiesCard.viewPastPenalties")), None)
  )

  def lockedAftModel(implicit messages: Messages): Seq[AFTViewModel] = Seq(
    AFTViewModel(
      Some(Text(Messages("aftPartial.inProgress.forPeriod", formattedStartDate, formattedEndDate))),
      Some(Text(Messages("aftPartial.status.lockDetail",name))),
      Link(
        id = "aftSummaryPageLink",
        url = s"http://localhost:8206/manage-pension-scheme-accounting-for-tax/$srn/$startDate/$Draft/1/summary",
        linkText = Text(Messages("aftPartial.view.link")),
        hiddenText = Some(Text(Messages("aftPartial.view.hidden.forPeriod", Seq(formattedStartDate, formattedEndDate)))))
    )
  )

  def unlockedEmptyAftModel(implicit messages: Messages): Seq[AFTViewModel] = Seq(
    AFTViewModel(
      None,
      None,
      Link(
        id = "aftChargeTypePageLink",
        url = s"http://localhost:8206/manage-pension-scheme-accounting-for-tax/$srn/new-return/aft-login",
        linkText = Text(Messages("aftPartial.startLink.forPeriod", Seq(formattedStartDate, formattedEndDate))))
    )
  )

  def lockedAftModelWithNoVersion(implicit messages: Messages): Seq[AFTViewModel] = Seq(
    AFTViewModel(
      Some(Text(Messages("aftPartial.inProgress.forPeriod", formattedStartDate, formattedEndDate))),
      Some(Text(Messages("aftPartial.status.lockDetail", name))),
      Link(
        id = "aftSummaryPageLink",
        url = s"http://localhost:8206/manage-pension-scheme-accounting-for-tax/$srn/$startDate/$Draft/1/summary",
        linkText = Text(Messages("aftPartial.view.link")),
        hiddenText = Some(Text(Messages("aftPartial.view.hidden.forPeriod", Seq(formattedStartDate, formattedEndDate)))))
    )
  )

  def inProgressUnlockedAftModel(implicit messages: Messages): Seq[AFTViewModel] = Seq(
    AFTViewModel(
      Some(Text(Messages("aftPartial.inProgress.forPeriod", formattedStartDate, formattedEndDate))),
      Some(Text(Messages("aftPartial.status.inProgress"))),
      Link(
        id = "aftSummaryPageLink",
        url = s"http://localhost:8206/manage-pension-scheme-accounting-for-tax/$srn/$startDate/$Draft/1/summary",
        linkText = Text(Messages("aftPartial.view.link")),
        hiddenText = Some(Text(Messages("aftPartial.view.hidden.forPeriod", Seq(formattedStartDate, formattedEndDate)))))
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

  def startModel(implicit messages: Messages): AFTViewModel = AFTViewModel(None, None,
    Link(id = "aftLoginLink", url = aftLoginUrl,
      linkText = Text(Messages("aftPartial.start.link"))))

  def pastReturnsModel(implicit messages: Messages): AFTViewModel = AFTViewModel(None, None,
    Link(
      id = "aftAmendLink",
      url = amendUrl,
      linkText = Text(Messages("aftPartial.view.change.past"))))

  def multipleInProgressModel(count: Int, linkText: String = "aftPartial.view.link")(implicit messages: Messages): AFTViewModel =
    AFTViewModel(
      Some(Text(Messages("aftPartial.multipleInProgress.text"))),
      Some(Text(Messages("aftPartial.multipleInProgress.count", count))),
      Link(
        id = "aftContinueInProgressLink",
        url = continueUrl,
        linkText = Text(Messages(s"$linkText")),
        hiddenText = Some(Text(Messages("aftPartial.view.hidden"))))
    )

  def oneInProgressModel(locked: Boolean, linkText: String = "aftPartial.view.link")(implicit messages: Messages): AFTViewModel = AFTViewModel(
    Some(Text(Messages("aftPartial.inProgress.forPeriod", "1 October", "31 December 2020"))),
    if (locked) {
      Some(Text(Messages("aftPartial.status.lockDetail", name)))
    }
    else {
      Some(Text(Messages("aftPartial.status.inProgress")))
    },
    Link(id = "aftSummaryLink", url = aftSummaryUrl,
      linkText = Text(Messages(s"$linkText")),
      hiddenText = Some(Text(Messages("aftPartial.view.hidden.forPeriod", Seq("1 October", "31 December 2020")))))
  )

  val allTypesMultipleReturnsPresent = Seq(overviewApril20, overviewJuly20, overviewOctober20, overviewJan21)
  val noInProgress = Seq(overviewApril20, overviewJuly20)
  val oneInProgress = Seq(overviewApril20, overviewOctober20)

  def allTypesMultipleReturnsModel(implicit messages: Messages): Seq[AFTViewModel] =
    Seq(multipleInProgressModel(2), startModel, pastReturnsModel)

  def noInProgressModel(implicit messages: Messages): Seq[AFTViewModel] =
    Seq(startModel, pastReturnsModel)

  def oneInProgressModelLocked(implicit messages: Messages): Seq[AFTViewModel] =
    Seq(oneInProgressModel(locked = true), startModel, pastReturnsModel)

  def oneInProgressModelNotLocked(implicit messages: Messages): Seq[AFTViewModel] =
    Seq(oneInProgressModel(locked = false), startModel, pastReturnsModel)

  def oneCompileZeroedOut: Seq[AFTOverview] =
    Seq(
      overviewApril20.copy(versionDetails = Some(overviewApril20.versionDetails.get.copy(numberOfVersions = 1, compiledVersionAvailable = true))),
      overviewJuly20.copy(versionDetails = Some(overviewJuly20.versionDetails.get.copy(numberOfVersions = 1, compiledVersionAvailable = true))),
      overviewOctober20.copy(versionDetails = Some(overviewOctober20.versionDetails.get.copy(numberOfVersions = 2, compiledVersionAvailable = true)))
    )

  def oneCompileZeroedOutModel(implicit messages: Messages): Seq[AFTViewModel] =
    Seq(multipleInProgressModel(2), startModel)

  def pspDashboardAftReturnsViewModel(implicit messages: Messages): DashboardAftViewModel =
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
                                               )(implicit messages: Messages): DashboardAftViewModel =
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

  def pspDashboardOneCompileZeroedOutModel(implicit messages: Messages): DashboardAftViewModel =
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

}
