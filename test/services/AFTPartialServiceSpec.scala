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

package services

import java.time.LocalDate
import java.time.format.DateTimeFormatter

import base.SpecBase
import connectors.AFTConnector
import connectors.cache.UserAnswersCacheConnector
import models._
import org.mockito.Matchers
import org.mockito.Matchers.any
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.mockito.MockitoSugar
import play.api.i18n.Messages
import uk.gov.hmrc.viewmodels._
import utils.DateHelper
import viewmodels.{AFTViewModel, Link}

import scala.concurrent.{ExecutionContext, Future}

class AFTPartialServiceSpec extends SpecBase with MockitoSugar with BeforeAndAfterEach with ScalaFutures {

  import AFTPartialServiceSpec._

  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
  private val aftConnector = mock[AFTConnector]

  private val aftCacheConnector = mock[UserAnswersCacheConnector]
  private val schemeService = mock[SchemeService]

  private val version1 = AFTVersion(1, LocalDate.now())
  private val version2 = AFTVersion(2, LocalDate.now())
  private val versions = Seq(version1, version2)

  def service: AFTPartialService =
    new AFTPartialService(frontendAppConfig, schemeService, aftConnector, aftCacheConnector)

  "retrieveOptionAFTViewModel after overviewApiEnablement" must {
    "return overview api returns multiple returns in progress, multiple past returns and start link needs to be displayed" in {
      DateHelper.setDate(Some(LocalDate.of(2021,4,1)))
      when(aftConnector.getAftOverview(any())(any(), any()))
        .thenReturn(Future.successful(allTypesMultipleReturnsPresent))
      when(aftConnector.aftOverviewStartDate).thenReturn(LocalDate.of(2020, 4, 1))
      when(aftConnector.aftOverviewEndDate).thenReturn(LocalDate.of(2021, 6, 30))
      when(schemeService.retrieveSchemeDetails(any(), any())(any(), any()))
        .thenReturn(Future.successful(SchemeDetails("test-name", pstr, "Open")))

      whenReady(service.retrieveOptionAFTViewModel(srn, psaId)) {
        _ mustBe allTypesMultipleReturnsModel
      }
    }

    "return the correct model when return no returns are in progress" in {
      when(aftConnector.getAftOverview(any())(any(), any()))
        .thenReturn(Future.successful(noInProgress))
      when(aftConnector.aftOverviewStartDate).thenReturn(LocalDate.of(2020, 4, 1))
      when(aftConnector.aftOverviewEndDate).thenReturn(LocalDate.of(2021, 6, 30))

      whenReady(service.retrieveOptionAFTViewModel(srn, psaId)) {
        _ mustBe noInProgressModel
      }
    }

    "return the correct model when return one return is in progress but not locked" in {
      when(aftConnector.getAftOverview(any())(any(), any()))
        .thenReturn(Future.successful(oneInProgress))
      when(aftConnector.aftOverviewStartDate).thenReturn(LocalDate.of(2020, 4, 1))
      when(aftConnector.aftOverviewEndDate).thenReturn(LocalDate.of(2021, 6, 30))
      when(aftCacheConnector.lockedBy(any(), any())(any(), any()))
        .thenReturn(Future.successful(None))

      whenReady(service.retrieveOptionAFTViewModel(srn, psaId)) {
        _ mustBe oneInProgressModelNotLocked
      }
    }

    "return the correct model when one return is in progress and locked by another user" in {
      when(aftConnector.getAftOverview(any())(any(), any()))
        .thenReturn(Future.successful(oneInProgress))
      when(aftConnector.aftOverviewStartDate).thenReturn(LocalDate.of(2020, 4, 1))
      when(aftConnector.aftOverviewEndDate).thenReturn(LocalDate.of(2021, 6, 30))
      when(aftCacheConnector.lockedBy(any(), any())(any(), any()))
        .thenReturn(Future.successful(Some(name)))

      whenReady(service.retrieveOptionAFTViewModel(srn, psaId)) {
        _ mustBe oneInProgressModelLocked
      }
    }

    "return a model with start link and only 2 returns in progress" when {
      "a scheme has 3 compiles in progress but one has been zeroed out and all quarters have been initiated (ie no start link)" in {
        DateHelper.setDate(Some(LocalDate.of(2020, 12, 31)))
        when(aftConnector.getAftOverview(any())(any(), any()))
          .thenReturn(Future.successful(oneCompileZeroedOut))
        when(aftConnector.aftOverviewStartDate).thenReturn(LocalDate.of(2020, 4, 1))
        when(aftConnector.aftOverviewEndDate).thenReturn(LocalDate.of(2021, 12, 31))
        when(aftCacheConnector.lockedBy(any(), any())(any(), any()))
          .thenReturn(Future.successful(None))
        when(aftConnector.getIsAftNonZero(any(), Matchers.eq("2020-07-01"), any())(any(), any()))
          .thenReturn(Future.successful(false))
        when(aftConnector.getIsAftNonZero(any(), Matchers.eq("2020-04-01"), any())(any(), any()))
          .thenReturn(Future.successful(true))

        whenReady(service.retrieveOptionAFTViewModel(srn, psaId)) {
          _ mustBe oneCompileZeroedOutModel
        }
      }
    }

  }

  "retrieveOptionAFTViewModel before overviewApiEnablement" must {
    "return the correct model when return is locked by another credentials" in {
      DateHelper.setDate(Some(LocalDate.of(2020,4,1)))
      when(aftConnector.getListOfVersions(any(), any())(any(), any()))
        .thenReturn(Future.successful(Seq(version1)))
      when(aftCacheConnector.lockedBy(any(), any())(any(), any()))
        .thenReturn(Future.successful(Some(name)))

      whenReady(service.retrieveOptionAFTViewModel(srn, psaId)) {
        _ mustBe lockedAftModel
      }
    }

    "return the correct model when return is not locked but versions is empty" in {
      when(aftConnector.getListOfVersions(any(), any())(any(), any()))
        .thenReturn(Future.successful(Nil))
      when(aftCacheConnector.lockedBy(any(), any())(any(), any()))
        .thenReturn(Future.successful(None))

      whenReady(service.retrieveOptionAFTViewModel(srn, psaId)) {
        _ mustBe unlockedEmptyAftModel
      }
    }

    "return the correct model when return is locked but versions is empty" in {
      when(aftConnector.getListOfVersions(any(), any())(any(), any()))
        .thenReturn(Future.successful(Nil))
      when(aftCacheConnector.lockedBy(any(), any())(any(), any()))
        .thenReturn(Future.successful(Some(name)))

      whenReady(service.retrieveOptionAFTViewModel(srn, psaId)) {
        _ mustBe lockedAftModelWithNoVersion
      }
    }

    "return the correct model when return is in progress but not locked" in {
      when(aftConnector.getListOfVersions(any(), any())(any(), any()))
        .thenReturn(Future.successful(Seq(version1)))
      when(aftCacheConnector.lockedBy(any(), any())(any(), any()))
        .thenReturn(Future.successful(None))

      whenReady(service.retrieveOptionAFTViewModel(srn, psaId)) {
        _ mustBe inProgressUnlockedAftModel
      }
    }

  }

}

object AFTPartialServiceSpec {
  private val startDate = "2020-04-01"
  private val endDate = "2020-06-30"
  private val dateFormatterYMD: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
  private val formattedStartDate: String = LocalDate.parse(startDate, dateFormatterYMD).format(DateTimeFormatter.ofPattern("d MMMM"))
  private val formattedEndDate: String = LocalDate.parse(endDate, dateFormatterYMD).format(DateTimeFormatter.ofPattern("d MMMM yyyy"))
  private val srn = "srn"
  private val pstr = "pstr"
  private val psaId = "A0000000"
  private val name = "test-name"
  private val date = "2020-01-01"
  val minimalPsaName: Option[String] = Some("John Doe Doe")

  def lockedAftModel(implicit messages: Messages): Seq[AFTViewModel] = Seq(
    AFTViewModel(
      Some(msg"aftPartial.inProgress.forPeriod".withArgs(formattedStartDate, formattedEndDate)),
      Some(msg"aftPartial.status.lockedBy".withArgs(name)),
      Link(
        id = "aftSummaryPageLink",
        url = s"http://localhost:8206/manage-pension-scheme-accounting-for-tax/$srn/new-return/$startDate/1/summary",
        linkText = msg"aftPartial.view.link",
        hiddenText = Some(msg"aftPartial.view.hidden.forPeriod".withArgs(formattedStartDate, formattedEndDate)))
    )
  )

  def unlockedEmptyAftModel(implicit messages: Messages): Seq[AFTViewModel] = Seq(
    AFTViewModel(
      None,
      None,
      Link(
        id = "aftChargeTypePageLink",
        url = s"http://localhost:8206/manage-pension-scheme-accounting-for-tax/$srn/new-return/aft-login",
        linkText = msg"aftPartial.startLink.forPeriod".withArgs(formattedStartDate, formattedEndDate))
    )
  )
  def lockedAftModelWithNoVersion(implicit messages: Messages): Seq[AFTViewModel] = Seq(
    AFTViewModel(
      Some(msg"aftPartial.inProgress.forPeriod".withArgs(formattedStartDate, formattedEndDate)),
      Some(msg"aftPartial.status.lockedBy".withArgs(name)),
      Link(
        id = "aftSummaryPageLink",
        url = s"http://localhost:8206/manage-pension-scheme-accounting-for-tax/$srn/new-return/$startDate/summary",
        linkText = msg"aftPartial.view.link",
        hiddenText = Some(msg"aftPartial.view.hidden.forPeriod".withArgs(formattedStartDate, formattedEndDate)))
    )
  )
  def inProgressUnlockedAftModel(implicit messages: Messages): Seq[AFTViewModel] = Seq(
    AFTViewModel(
      Some(msg"aftPartial.inProgress.forPeriod".withArgs(formattedStartDate, formattedEndDate)),
      Some(msg"aftPartial.status.inProgress"),
      Link(
        id = "aftSummaryPageLink",
        url = s"http://localhost:8206/manage-pension-scheme-accounting-for-tax/$srn/new-return/$startDate/1/summary",
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

  val aftLoginUrl: String = "http://localhost:8206/manage-pension-scheme-accounting-for-tax/srn/new-return/aft-login"
  val amendUrl: String = "http://localhost:8206/manage-pension-scheme-accounting-for-tax/srn/previous-return/amend-select"
  val returnHistoryUrl: String = "http://localhost:8206/manage-pension-scheme-accounting-for-tax/srn/previous-return/2020-10-01/amend-previous"
  val aftSummaryUrl: String = "http://localhost:8206/manage-pension-scheme-accounting-for-tax/srn/new-return/2020-10-01/2/summary"
  val continueUrl: String = "http://localhost:8206/manage-pension-scheme-accounting-for-tax/srn/new-return/select-quarter-in-progress"

  def startModel(implicit messages: Messages): AFTViewModel = AFTViewModel(None, None,
    Link(id = "aftLoginLink", url = aftLoginUrl,
      linkText = msg"aftPartial.start.link"))

  def pastReturnsModel(implicit messages: Messages): AFTViewModel = AFTViewModel(None, None,
    Link(
      id = "aftAmendLink",
      url = amendUrl,
      linkText = msg"aftPartial.view.change.past"))

  def multipleInProgressModel(count: Int)(implicit messages: Messages): AFTViewModel = AFTViewModel(
    Some(msg"aftPartial.multipleInProgress.text"),
    Some(msg"aftPartial.multipleInProgress.count".withArgs(count)),
    Link(
      id = "aftContinueInProgressLink",
      url = continueUrl,
      linkText = msg"aftPartial.view.link",
      hiddenText = Some(msg"aftPartial.view.hidden"))
  )

  def oneInProgressModel(locked: Boolean)(implicit messages: Messages): AFTViewModel = AFTViewModel(
    Some(msg"aftPartial.inProgress.forPeriod".withArgs("1 October", "31 December 2020")),
    if (locked) {
      Some(msg"aftPartial.status.lockedBy".withArgs(name))
    }
    else {
      Some(msg"aftPartial.status.inProgress")
    },
    Link(id = "aftSummaryLink", url = aftSummaryUrl,
      linkText = msg"aftPartial.view.link",
      hiddenText = Some(msg"aftPartial.view.hidden.forPeriod".withArgs("1 October", "31 December 2020")))
  )

  val allTypesMultipleReturnsPresent = Seq(overviewApril20, overviewJuly20, overviewOctober20, overviewJan21)
  val noInProgress = Seq(overviewApril20, overviewJuly20)
  val oneInProgress = Seq(overviewApril20 , overviewOctober20)

  def allTypesMultipleReturnsModel(implicit messages: Messages) =
    Seq(multipleInProgressModel(2), startModel, pastReturnsModel)

  def noInProgressModel(implicit messages: Messages) =
    Seq(startModel, pastReturnsModel)

  def oneInProgressModelLocked(implicit messages: Messages) =
    Seq(oneInProgressModel(locked = true), startModel, pastReturnsModel)

  def oneInProgressModelNotLocked(implicit messages: Messages) =
    Seq(oneInProgressModel(locked = false), startModel, pastReturnsModel)

  def oneCompileZeroedOut(implicit messages: Messages) = Seq(overviewApril20.copy(numberOfVersions = 1, compiledVersionAvailable = true),
    overviewJuly20.copy(numberOfVersions = 1, compiledVersionAvailable = true),
    overviewOctober20.copy(numberOfVersions = 2, compiledVersionAvailable = true))

  def oneCompileZeroedOutModel(implicit messages: Messages) = Seq(multipleInProgressModel(2), startModel)

}