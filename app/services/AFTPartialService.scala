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

import config.FrontendAppConfig
import connectors.AFTConnector
import connectors.cache.UserAnswersCacheConnector
import javax.inject.Inject
import models.{AFTOverview, AFTVersion, Draft, Quarters}
import play.api.i18n.Messages
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.viewmodels._
import utils.DateHelper
import utils.DateHelper.{dateFormatterDMY, dateFormatterStartDate}
import viewmodels.{AFTViewModel, Link}

import scala.concurrent.{ExecutionContext, Future}

class AFTPartialService @Inject()(appConfig: FrontendAppConfig,
                                  schemeService: SchemeService,
                                 aftConnector: AFTConnector,
                                 aftCacheConnector: UserAnswersCacheConnector
                                )(implicit ec: ExecutionContext) {

  def retrieveOptionAFTViewModel(srn: String, psaId: String)(implicit hc: HeaderCarrier, messages: Messages): Future[Seq[AFTViewModel]] = {
    schemeService.retrieveSchemeDetails(psaId, srn).flatMap { schemeDetails =>
      
      if (isOverviewApiDisabled) { //TODO This case to be deleted after 1 July 2020 and only the else section for this if to remain
        for {
          versions <- aftConnector.getListOfVersions(schemeDetails.pstr, appConfig.earliestStartDate)
          optLockedBy <- aftCacheConnector.lockedBy(srn, appConfig.earliestStartDate)
        } yield {
          createAFTViewModel(versions, optLockedBy, srn, appConfig.earliestStartDate, appConfig.earliestEndDate)
        }
      } else {
        createAFTOverviewModel(schemeDetails.pstr, srn)
      }
    }
  }

  private def isOverviewApiDisabled: Boolean =
    LocalDate.parse(appConfig.overviewApiEnablementDate).isAfter(DateHelper.today)


  private def createAFTOverviewModel(pstrId: String, srn: String)(
    implicit hc: HeaderCarrier, messages: Messages): Future[Seq[AFTViewModel]] = {
    for {
      overview <- aftConnector.getAftOverview(pstrId)
      inProgressReturnsOpt <- getInProgressReturnsModel(overview, srn, pstrId)
      startReturnsOpt <- getStartReturnsModel(overview, srn, pstrId)
    } yield {
      Seq(inProgressReturnsOpt, startReturnsOpt, getPastReturnsModel(overview, srn)).flatten
    }
  }


  /* Returns a start link if:
      1. Return has not been initiated for any of the quarters that are valid for starting a return OR
      2. Any of the returns in their first compile have been zeroed out due to deletion of all charges
   */

  private def getStartReturnsModel(overview: Seq[AFTOverview], srn: String, pstr: String
                                  )(implicit hc: HeaderCarrier, messages: Messages): Future[Option[AFTViewModel]] = {

    val startLink: Option[AFTViewModel] = Some(AFTViewModel(None, None,
      Link(id = "aftLoginLink", url = appConfig.aftLoginUrl.format(srn),
        linkText = msg"aftPartial.start.link")))

    val isReturnNotInitiatedForAnyQuarter: Boolean = {
      val aftValidYears = aftConnector.aftOverviewStartDate.getYear to aftConnector.aftOverviewEndDate.getYear
      aftValidYears.flatMap { year =>
        Quarters.availableQuarters(year)(appConfig).map { quarter =>
          !overview.map(_.periodStartDate).contains(Quarters.getQuarter(quarter, year).startDate)
        }
      }.contains(true)
    }

    if (isReturnNotInitiatedForAnyQuarter) {
      Future.successful(startLink)
    } else {

      retrieveZeroedOutReturns(overview, pstr).map {
        case zeroedReturns if zeroedReturns.nonEmpty => startLink //if any returns in first compile are zeroed out, display start link
        case _ => None
      }
    }
  }

  /* Returns a seq of the aftReturns in their first compile have been zeroed out due to deletion of all charges
  */
  private def retrieveZeroedOutReturns(overview: Seq[AFTOverview], pstr: String
                                      )(implicit hc: HeaderCarrier): Future[Seq[AFTOverview]] = {
    val firstCompileReturns = overview.filter(_.compiledVersionAvailable).filter(_.numberOfVersions == 1)

    Future.sequence(firstCompileReturns.map(aftReturn =>
      aftConnector.getIsAftNonZero(pstr, aftReturn.periodStartDate.toString, "1"))).map {
      isNonZero => (firstCompileReturns zip isNonZero).filter(!_._2).map(_._1)
    }
  }

  private def getPastReturnsModel(overview: Seq[AFTOverview], srn: String)(implicit hc: HeaderCarrier, messages: Messages): Option[AFTViewModel] = {
    val pastReturns = overview.filter(!_.compiledVersionAvailable)

    if (pastReturns.nonEmpty) {
      Some(AFTViewModel(
        None,
        None,
        Link(
          id = "aftAmendLink",
          url = appConfig.aftAmendUrl.format(srn),
          linkText = msg"aftPartial.view.change.past")
      ))
    } else {
      None
    }
  }


  private def getInProgressReturnsModel(overview: Seq[AFTOverview], srn: String, pstr: String)
                                       (implicit hc: HeaderCarrier, messages: Messages): Future[Option[AFTViewModel]] = {
    val inProgressReturns = overview.filter(_.compiledVersionAvailable)

    if(inProgressReturns.size == 1){
      val startDate: LocalDate = inProgressReturns.head.periodStartDate
      val endDate: LocalDate = Quarters.getQuarter(startDate).endDate

      if(inProgressReturns.head.numberOfVersions == 1) {
        aftConnector.getIsAftNonZero(pstr, startDate.toString, "1").flatMap {
          case true => modelForSingleInProgressReturn(srn, startDate, endDate, inProgressReturns.head)
          case _ => Future.successful(None)
        }
      } else {
        modelForSingleInProgressReturn(srn, startDate, endDate, inProgressReturns.head)
      }

    } else if(inProgressReturns.nonEmpty) {
      modelForMultipleInProgressReturns(srn, pstr, inProgressReturns)
    }
    else {
      Future.successful(None)
    }
  }

  private def modelForSingleInProgressReturn(srn: String, startDate: LocalDate, endDate: LocalDate, overview: AFTOverview
                                            )(implicit  hc: HeaderCarrier, messages: Messages): Future[Option[AFTViewModel]] = {
    aftCacheConnector.lockedBy(srn, startDate.toString).map {
      case Some(lockedBy) => Some(AFTViewModel(
        Some(msg"aftPartial.inProgress.forPeriod".withArgs(startDate.format(dateFormatterStartDate), endDate.format(dateFormatterDMY))),
        if (lockedBy.nonEmpty) {
          Some(msg"aftPartial.status.lockedBy".withArgs(lockedBy))
        }
        else {
          Some(msg"aftPartial.status.locked")
        },
        Link(id = "aftSummaryLink", url = appConfig.aftSummaryPageUrl.format(srn, startDate, Draft, overview.numberOfVersions),
          linkText = msg"aftPartial.view.link",
          hiddenText = Some(msg"aftPartial.view.hidden.forPeriod".withArgs(startDate.format(dateFormatterStartDate), endDate.format(dateFormatterDMY)))
        )
      ))
      case _ => Some(AFTViewModel(
        Some(msg"aftPartial.inProgress.forPeriod".withArgs(startDate.format(dateFormatterStartDate), endDate.format(dateFormatterDMY))),
        Some(msg"aftPartial.status.inProgress"),
        Link(
          id = "aftSummaryLink",
          url = appConfig.aftSummaryPageUrl.format(srn, startDate, Draft, overview.numberOfVersions),
          linkText = msg"aftPartial.view.link",
          hiddenText = Some(msg"aftPartial.view.hidden.forPeriod".withArgs(startDate.format(dateFormatterStartDate), endDate.format(dateFormatterDMY)))
      )))
    }
  }

  private def modelForMultipleInProgressReturns(srn: String, pstr: String, inProgressReturns: Seq[AFTOverview]
                                               )(implicit hc: HeaderCarrier, messages: Messages): Future[Option[AFTViewModel]] = {

    retrieveZeroedOutReturns(inProgressReturns, pstr).map { zeroedReturns =>

      val countInProgress:Int = inProgressReturns.size - zeroedReturns.size

      if(countInProgress > 0) {
        Some(AFTViewModel(
          Some(msg"aftPartial.multipleInProgress.text"),
          Some(msg"aftPartial.multipleInProgress.count".withArgs(countInProgress)),
          Link(
            id = "aftContinueInProgressLink",
            url = appConfig.aftContinueReturnUrl.format(srn),
            linkText = msg"aftPartial.view.link",
            hiddenText = Some(msg"aftPartial.view.hidden")
          )
        ))
      } else {
        None
      }
    }
  }

  private def createAFTViewModel(versions: Seq[AFTVersion], optLockedBy: Option[String],
                                 srn: String, startDate: String, endDate: String)(implicit messages: Messages): Seq[AFTViewModel] = {
    val dateFormatterYMD: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    val formattedStartDate: String = LocalDate.parse(startDate, dateFormatterYMD).format(dateFormatterStartDate)
    val formattedEndDate: String = LocalDate.parse(endDate, dateFormatterYMD).format(dateFormatterDMY)
    optLockedBy match {
      case None if versions.isEmpty =>
        Seq(AFTViewModel(None, None,
          Link(id = "aftChargeTypePageLink", url = appConfig.aftLoginUrl.format(srn),
            linkText = msg"aftPartial.startLink.forPeriod".withArgs(formattedStartDate, formattedEndDate)))
        )
      case Some(name) if versions.isEmpty =>
        Seq(AFTViewModel(
          Some(msg"aftPartial.inProgress.forPeriod".withArgs(formattedStartDate, formattedEndDate)),
          if (name.nonEmpty) {
            Some(msg"aftPartial.status.lockedBy".withArgs(name))
          }
          else {
            Some(msg"aftPartial.status.locked")
          },
          Link(id = "aftSummaryPageLink", url = appConfig.aftSummaryPageUrl.format(srn, startDate, Draft, 1),
            linkText = msg"aftPartial.view.link",
            hiddenText = Some(msg"aftPartial.view.hidden.forPeriod".withArgs(formattedStartDate, formattedEndDate))
          )
        )
        )
      case Some(name) =>
        Seq(AFTViewModel(
          Some(msg"aftPartial.inProgress.forPeriod".withArgs(formattedStartDate, formattedEndDate)),
          if (name.nonEmpty) {
            Some(msg"aftPartial.status.lockedBy".withArgs(name))
          }
          else {
            Some(msg"aftPartial.status.locked")
          },
          Link(id = "aftSummaryPageLink",
            url = appConfig.aftSummaryPageUrl.format(srn, startDate, Draft, versions.head.reportVersion),
            linkText = msg"aftPartial.view.link",
            hiddenText = Some(msg"aftPartial.view.hidden.forPeriod".withArgs(formattedStartDate, formattedEndDate)))
        )
        )

      case _ =>
        Seq(AFTViewModel(
          Some(msg"aftPartial.inProgress.forPeriod".withArgs(formattedStartDate, formattedEndDate)),
          Some(msg"aftPartial.status.inProgress"),
          Link(
            id = "aftSummaryPageLink",
            url = appConfig.aftSummaryPageUrl.format(srn, startDate, Draft, versions.head.reportVersion),
            linkText = msg"aftPartial.view.link",
            hiddenText = Some(msg"aftPartial.view.hidden.forPeriod".withArgs(formattedStartDate, formattedEndDate)))
        )
        )
    }
  }
}
