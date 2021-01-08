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

package services

import java.time.LocalDate
import java.time.format.DateTimeFormatter

import base.SpecBase
import connectors.AFTConnector
import connectors.cache.UserAnswersCacheConnector
import models._
import models.financialStatement.SchemeFS
import models.financialStatement.SchemeFSChargeType.PSS_AFT_RETURN
import org.mockito.Matchers
import org.mockito.Matchers.any
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.mockito.MockitoSugar
import play.api.libs.json.Json
import services.paymentsAndCharges.PaymentsAndChargesService
import uk.gov.hmrc.viewmodels._
import utils.DateHelper
import viewmodels.{Link, AFTViewModel, PspDashboardAftViewModel}

import scala.concurrent.{Future, ExecutionContext}

class AFTPartialServiceSpec
  extends SpecBase
    with MockitoSugar
    with BeforeAndAfterEach
    with ScalaFutures {

  import AFTPartialServiceSpec._

  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
  private val aftConnector = mock[AFTConnector]

  private val aftCacheConnector = mock[UserAnswersCacheConnector]
  private val schemeService = mock[SchemeService]
  private val paymentsAndChargesService = mock[PaymentsAndChargesService]

  def service: AFTPartialService =
    new AFTPartialService(frontendAppConfig, schemeService, paymentsAndChargesService, aftConnector, aftCacheConnector)

  "retrieveOptionAFTViewModel after overviewApiEnablement" must {
    "return the correct model when overview api returns multiple returns in " +
      "progress, multiple past returns and start link needs to be displayed" in {
      DateHelper.setDate(Some(LocalDate.of(2021, 4, 1)))
      when(aftConnector.getAftOverview(any(), any(), any())(any(), any()))
        .thenReturn(Future.successful(allTypesMultipleReturnsPresent))
      when(aftConnector.aftOverviewStartDate).thenReturn(LocalDate.of(2020, 4, 1))
      when(aftConnector.aftOverviewEndDate).thenReturn(LocalDate.of(2021, 6, 30))
      when(schemeService.retrieveSchemeDetails(any(), any(), any())(any(), any()))
        .thenReturn(Future.successful(SchemeDetails("test-name", pstr, "Open", None)))

      whenReady(service.retrieveOptionAFTViewModel(srn, psaId, "srn")) {
        _ mustBe allTypesMultipleReturnsModel
      }
    }

    "return the correct model when return no returns are in progress" in {
      when(aftConnector.getAftOverview(any(), any(), any())(any(), any()))
        .thenReturn(Future.successful(noInProgress))
      when(aftConnector.aftOverviewStartDate).thenReturn(LocalDate.of(2020, 4, 1))
      when(aftConnector.aftOverviewEndDate).thenReturn(LocalDate.of(2021, 6, 30))

      whenReady(service.retrieveOptionAFTViewModel(srn, psaId, "srn")) {
        _ mustBe noInProgressModel
      }
    }

    "return the correct model when return one return is in progress but not locked" in {
      when(aftConnector.getAftOverview(any(), any(), any())(any(), any()))
        .thenReturn(Future.successful(oneInProgress))
      when(aftConnector.aftOverviewStartDate).thenReturn(LocalDate.of(2020, 4, 1))
      when(aftConnector.aftOverviewEndDate).thenReturn(LocalDate.of(2021, 6, 30))
      when(aftCacheConnector.lockDetail(any(), any())(any(), any()))
        .thenReturn(Future.successful(None))

      whenReady(service.retrieveOptionAFTViewModel(srn, psaId, "srn")) {
        _ mustBe oneInProgressModelNotLocked
      }
    }

    "return the correct model when one return is in progress and locked by another user" in {
      when(aftConnector.getAftOverview(any(), any(), any())(any(), any()))
        .thenReturn(Future.successful(oneInProgress))
      when(aftConnector.aftOverviewStartDate).thenReturn(LocalDate.of(2020, 4, 1))
      when(aftConnector.aftOverviewEndDate).thenReturn(LocalDate.of(2021, 6, 30))
      when(aftCacheConnector.lockDetail(any(), any())(any(), any()))
        .thenReturn(Future.successful(Some(LockDetail(name, psaId))))

      whenReady(service.retrieveOptionAFTViewModel(srn, psaId, "srn")) {
        _ mustBe oneInProgressModelLocked
      }
    }

    "return a model with start link and only 2 returns in progress" when {
      "a scheme has 3 compiles in progress but one has been zeroed " +
        "out and all quarters have been initiated (ie no start link)" in {
        DateHelper.setDate(Some(LocalDate.of(2020, 12, 31)))
        when(aftConnector.getAftOverview(any(), any(), any())(any(), any()))
          .thenReturn(Future.successful(oneCompileZeroedOut))
        when(aftConnector.aftOverviewStartDate).thenReturn(LocalDate.of(2020, 4, 1))
        when(aftConnector.aftOverviewEndDate).thenReturn(LocalDate.of(2021, 12, 31))
        when(aftCacheConnector.lockDetail(any(), any())(any(), any()))
          .thenReturn(Future.successful(None))
        when(aftConnector.getIsAftNonZero(any(), Matchers.eq("2020-07-01"), any())(any(), any()))
          .thenReturn(Future.successful(false))
        when(aftConnector.getIsAftNonZero(any(), Matchers.eq("2020-04-01"), any())(any(), any()))
          .thenReturn(Future.successful(true))

        whenReady(service.retrieveOptionAFTViewModel(srn, psaId, "srn")) {
          _ mustBe oneCompileZeroedOutModel
        }
      }
    }

  }

  "retrievePspDashboardAftReturnsModel" must {
    "return overview api returns multiple returns in progress, " +
      "multiple past returns and start link needs to be displayed" in {
      DateHelper.setDate(Some(LocalDate.of(2021, 4, 1)))
      when(aftConnector.getAftOverview(any(), any(), any())(any(), any()))
        .thenReturn(Future.successful(allTypesMultipleReturnsPresent))
      when(aftConnector.aftOverviewStartDate).thenReturn(LocalDate.of(2020, 4, 1))
      when(aftConnector.aftOverviewEndDate).thenReturn(LocalDate.of(2021, 6, 30))
      when(schemeService.retrieveSchemeDetails(any(), any(), any())(any(), any()))
        .thenReturn(Future.successful(SchemeDetails("test-name", pstr, "Open", None)))

      whenReady(service.retrievePspDashboardAftReturnsModel(srn, pspId, "srn", psaId)) {
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

      whenReady(service.retrievePspDashboardAftReturnsModel(srn, pspId, "srn", psaId)) {
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

      whenReady(service.retrievePspDashboardAftReturnsModel(srn, pspId, "srn", psaId)) {
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
        when(aftConnector.getIsAftNonZero(any(), Matchers.eq("2020-07-01"), any())(any(), any()))
          .thenReturn(Future.successful(false))
        when(aftConnector.getIsAftNonZero(any(), Matchers.eq("2020-04-01"), any())(any(), any()))
          .thenReturn(Future.successful(true))

        whenReady(service.retrievePspDashboardAftReturnsModel(srn, pspId, "srn", psaId)) {
          _ mustBe pspDashboardOneCompileZeroedOutModel
        }
      }
    }
  }

  "retrievePspDashboardUpcomingAftCharges" must {
    "return a model for a single period upcoming charges with no past charges" in {
      val service = app.injector.instanceOf[AFTPartialService]

      service.retrievePspDashboardUpcomingAftChargesModel(schemeFSResponseSinglePeriod(), srn) mustBe
        PspDashboardAftViewModel(
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
        PspDashboardAftViewModel(
          subHeadings = Seq(Json.obj(
            "total" -> "£3,087.15",
            "span" -> "Total amount due:"
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
      val schemeFS = schemeFSResponseSinglePeriod() ++ pastCharges
      service.retrievePspDashboardUpcomingAftChargesModel(schemeFS, srn) mustBe
        PspDashboardAftViewModel(
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
              linkText = msg"pspDashboardUpcomingAftChargesCard.link.pastPaymentsAndCharges",
              hiddenText = None
            )
          )
        )
    }


    "return a model for multiple period upcoming charges with past charges" in {
      val service = app.injector.instanceOf[AFTPartialService]
      val schemeFS = schemeFSResponseMultiplePeriods() ++ pastCharges
      service.retrievePspDashboardUpcomingAftChargesModel(schemeFS, srn) mustBe
        PspDashboardAftViewModel(
          subHeadings = Seq(Json.obj(
            "total" -> "£3,087.15",
            "span" -> "Total amount due:"
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
              linkText = msg"pspDashboardUpcomingAftChargesCard.link.pastPaymentsAndCharges",
              hiddenText = None
            )
          )
        )
    }
  }

  "retrievePspDashboardOverdueAftCharges" must {
    "return a model for a single period overdue charges with no interest accruing" in {
      val service = app.injector.instanceOf[AFTPartialService]

      service.retrievePspDashboardOverdueAftChargesModel(schemeFSResponseSinglePeriod(), srn) mustBe
        PspDashboardAftViewModel(
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
      val service = app.injector.instanceOf[AFTPartialService]

      service.retrievePspDashboardOverdueAftChargesModel(
        schemeFSResponseSinglePeriod(123.00), srn
      ) mustBe
        PspDashboardAftViewModel(
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
      val service = app.injector.instanceOf[AFTPartialService]

      service.retrievePspDashboardOverdueAftChargesModel(schemeFSResponseMultiplePeriods(), srn) mustBe
        PspDashboardAftViewModel(
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
      val service = app.injector.instanceOf[AFTPartialService]

      service.retrievePspDashboardOverdueAftChargesModel(schemeFSResponseMultiplePeriods(123.00), srn) mustBe
        PspDashboardAftViewModel(
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
  private val pspId = "20000000"
  private val name = "test-name"
  val minimalPsaName: Option[String] = Some("John Doe Doe")

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
    2,
    submittedVersionAvailable = true,
    compiledVersionAvailable = false
  )

  val overviewJuly20: AFTOverview = AFTOverview(
    LocalDate.of(2020, 7, 1),
    LocalDate.of(2020, 9, 30),
    2,
    submittedVersionAvailable = true,
    compiledVersionAvailable = false
  )

  val overviewOctober20: AFTOverview = AFTOverview(
    LocalDate.of(2020, 10, 1),
    LocalDate.of(2020, 12, 31),
    2,
    submittedVersionAvailable = true,
    compiledVersionAvailable = true
  )

  val overviewJan21: AFTOverview = AFTOverview(
    LocalDate.of(2021, 1, 1),
    LocalDate.of(2021, 3, 31),
    2,
    submittedVersionAvailable = true,
    compiledVersionAvailable = true
  )

  private val aftUrl = "http://localhost:8206/manage-pension-scheme-accounting-for-tax"

  val aftLoginUrl: String = s"$aftUrl/srn/new-return/aft-login"
  val amendUrl: String = s"$aftUrl/srn/previous-return/amend-select"
  val returnHistoryUrl: String = s"$aftUrl/srn/previous-return/2020-10-01/amend-previous"
  val aftSummaryUrl: String = s"$aftUrl/srn/2020-10-01/draft/2/summary"
  val continueUrl: String = s"$aftUrl/srn/new-return/select-quarter-in-progress"
  val viewUpcomingChargesUrl: String = s"$aftUrl/srn/payments-and-charges/2020-10-01/upcoming-payments-and-charges"
  val viewOverdueChargesUrl: String = s"$aftUrl/srn/payments-and-charges/2020-10-01/overdue-payments-and-charges"
  val viewPastChargesUrl: String = s"$aftUrl/srn/2020/payments-and-charges"

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
      overviewApril20.copy(numberOfVersions = 1, compiledVersionAvailable = true),
      overviewJuly20.copy(numberOfVersions = 1, compiledVersionAvailable = true),
      overviewOctober20.copy(numberOfVersions = 2, compiledVersionAvailable = true)
    )

  def oneCompileZeroedOutModel: Seq[AFTViewModel] =
    Seq(multipleInProgressModel(2), startModel)

  def pspDashboardAftReturnsViewModel: PspDashboardAftViewModel =
    PspDashboardAftViewModel(
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
                                               ): PspDashboardAftViewModel =
    PspDashboardAftViewModel(
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

  def pspDashboardOneCompileZeroedOutModel: PspDashboardAftViewModel =
    PspDashboardAftViewModel(
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
                          ): SchemeFS = {
    SchemeFS(
      chargeReference = chargeReference,
      chargeType = PSS_AFT_RETURN,
      dueDate = dueDate,
      totalAmount = 56432.00,
      outstandingAmount = 56049.08,
      stoodOverAmount = 25089.08,
      amountDue = 1029.05,
      accruedInterestTotal = accruedInterestTotal,
      periodStartDate = LocalDate.parse(startDate),
      periodEndDate = LocalDate.parse(endDate)
    )
  }

  private def schemeFSResponseSinglePeriod(accruedInterestTotal: BigDecimal = 0.00): Seq[SchemeFS] = Seq(
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

  private def schemeFSResponseMultiplePeriods(accruedInterestTotal: BigDecimal = 0.00): Seq[SchemeFS] = Seq(
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

  private val pastCharges: Seq[SchemeFS] = Seq(
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
