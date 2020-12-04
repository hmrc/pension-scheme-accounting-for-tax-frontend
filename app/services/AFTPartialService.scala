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
import models.{AFTOverview, Draft, LockDetail, Quarters, SchemeDetails}
import play.api.i18n.Messages
import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.viewmodels._
import utils.DateHelper.{dateFormatterDMY, dateFormatterStartDate}
import viewmodels.{AFTViewModel, Link, PspDashboardAftReturnsViewModel}

import scala.concurrent.{ExecutionContext, Future}

class AFTPartialService @Inject()(
                                   appConfig: FrontendAppConfig,
                                   schemeService: SchemeService,
                                   aftConnector: AFTConnector,
                                   aftCacheConnector: UserAnswersCacheConnector
                                 )(implicit ec: ExecutionContext) {

  def retrieveOptionAFTViewModel(srn: String, psaId: String, schemeIdType: String)
                                (implicit hc: HeaderCarrier, messages: Messages): Future[Seq[AFTViewModel]] =
    schemeService.retrieveSchemeDetails(
      psaId = psaId,
      srn = srn,
      schemeIdType = schemeIdType
    ) flatMap { schemeDetails =>
      for {
        overview <- aftConnector.getAftOverview(schemeDetails.pstr)
        inProgressReturnsOpt <- getInProgressReturnsModel(overview, srn, schemeDetails.pstr)
        startReturnsOpt <- getStartReturnsModel(overview, srn, schemeDetails.pstr)
      } yield {
        Seq(inProgressReturnsOpt, startReturnsOpt, getPastReturnsModelOpt(overview, srn)).flatten
      }
    }

  def retrievePspDashboardAftReturnsModel(
                                           srn: String,
                                           pspId: String,
                                           schemeIdType: String,
                                           authorisingPsaId: String
                                         )(
                                           implicit
                                           hc: HeaderCarrier,
                                           messages: Messages
                                         ): Future[PspDashboardAftReturnsViewModel] = {
    schemeService.retrieveSchemeDetails(
      psaId = pspId,
      srn = srn,
      schemeIdType = schemeIdType
    ) flatMap { schemeDetails =>
      for {
        overview <- aftConnector.getAftOverview(schemeDetails.pstr)
        inProgressReturnsLinkOpt <- pspAftDashboardGetInProgressReturnsModel(
          overview = overview,
          srn = srn,
          pstr = schemeDetails.pstr
        )
        inProgressReturns = overview.filter(_.compiledVersionAvailable)
        subHeading <- optionSubHeading(inProgressReturns, schemeDetails, srn, authorisingPsaId)
      } yield {

        val links: Seq[Link] =
          Seq(
            inProgressReturnsLinkOpt,
            Option(Link(
              id = "aftLoginLink",
              url = appConfig.aftLoginUrl.format(srn),
              linkText = msg"aftPartial.start.link"
            )),
            getPastReturnsModelOpt(overview, srn).map(_.link)
          ).flatten

        PspDashboardAftReturnsViewModel(
          subHeading = subHeading,
          links = links
        )
      }
    }
  }

  private def optionSubHeading(
                                inProgressReturns: Seq[AFTOverview],
                                schemeDetails: SchemeDetails,
                                srn: String,
                                authorisingPsaId: String
                              )(
                                implicit hc: HeaderCarrier,
                                messages: Messages
                              ): Future[Option[JsObject]] = {
    val startDate = inProgressReturns.head.periodStartDate.toString

    if (inProgressReturns.size == 1) {
      aftCacheConnector.lockDetail(srn, startDate) flatMap {
        optLockDetail =>
          if (inProgressReturns.head.numberOfVersions == 1) {
            aftConnector.getIsAftNonZero(
              pstr = schemeDetails.pstr,
              startDate = startDate,
              aftVersion = "1"
            ) flatMap {
              case true =>
                Future.successful(Some(
                  singleReturnSubHeading(inProgressReturns, optLockDetail, authorisingPsaId)
                ))
              case _ =>
                Future.successful(None)
            }
          } else {
            Future.successful(Some(
              singleReturnSubHeading(inProgressReturns, optLockDetail, authorisingPsaId)
            ))
          }
      }
    } else if (inProgressReturns.size > 1) {
      Future.successful(Some(multipleReturnSubHeading(inProgressReturns)))
    } else {
      Future.successful(None)
    }
  }

  private def multipleReturnSubHeading(inProgressReturns: Seq[AFTOverview])
                                      (implicit messages: Messages): JsObject =
    Json.obj(
      "h3" -> msg"pspDashboardAftReturnsPartial.h3.multiple".withArgs(inProgressReturns.size.toString).resolve,
      "span" -> msg"pspDashboardAftReturnsPartial.span.multiple".resolve
    )

  private def singleReturnSubHeading(
                                      inProgressReturns: Seq[AFTOverview],
                                      lockDetail: Option[LockDetail],
                                      authorisingPsaId: String
                                    )(
                                      implicit messages: Messages
                                    ): JsObject = {
    val startDate: LocalDate = inProgressReturns.head.periodStartDate
    val startDateStr: String = startDate.format(DateTimeFormatter.ofPattern("d MMMM"))
    val endDate: String =
      Quarters
        .getQuarter(startDate)
        .endDate
        .format(DateTimeFormatter.ofPattern("d MMMM yyyy"))

    val h3: String =
      if (lockDetail.nonEmpty) {
        if (lockDetail.get.psaOrPspId == authorisingPsaId) {
          msg"pspDashboardAftReturnsPartial.h3.single.lockedBy".withArgs(lockDetail.get.name).resolve
        } else {
          msg"pspDashboardAftReturnsPartial.h3.single.locked".resolve
        }
      } else {
        msg"pspDashboardAftReturnsPartial.h3.single".resolve
      }

    Json.obj(
      "h3" -> h3,
      "span" -> msg"pspDashboardAftReturnsPartial.span.single".withArgs(startDateStr, endDate)
    )
  }

  /* Returns a start link if:
      1. Return has not been initiated for any of the quarters that are valid for starting a return OR
      2. Any of the returns in their first compile have been zeroed out due to deletion of all charges
   */

  private def getStartReturnsModel(overview: Seq[AFTOverview], srn: String, pstr: String)
                                  (implicit hc: HeaderCarrier, messages: Messages): Future[Option[AFTViewModel]] = {

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
  private def retrieveZeroedOutReturns(overview: Seq[AFTOverview], pstr: String)
                                      (implicit hc: HeaderCarrier): Future[Seq[AFTOverview]] = {
    val firstCompileReturns = overview.filter(_.compiledVersionAvailable).filter(_.numberOfVersions == 1)

    Future.sequence(firstCompileReturns.map(aftReturn =>
      aftConnector.getIsAftNonZero(pstr, aftReturn.periodStartDate.toString, "1"))).map {
      isNonZero => (firstCompileReturns zip isNonZero).filter(!_._2).map(_._1)
    }
  }

  private def getPastReturnsModelOpt(overview: Seq[AFTOverview], srn: String)
                                    (implicit hc: HeaderCarrier, messages: Messages): Option[AFTViewModel] = {
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


  private def getInProgressReturnsModel(
                                         overview: Seq[AFTOverview],
                                         srn: String,
                                         pstr: String,
                                         linkText: Text = msg"aftPartial.view.link"
                                       )(
                                         implicit
                                         hc: HeaderCarrier,
                                         messages: Messages
                                       ): Future[Option[AFTViewModel]] = {
    val inProgressReturns = overview.filter(_.compiledVersionAvailable)

    if (inProgressReturns.size == 1) {
      val startDate: LocalDate = inProgressReturns.head.periodStartDate
      val endDate: LocalDate = Quarters.getQuarter(startDate).endDate

      if (inProgressReturns.head.numberOfVersions == 1) {
        aftConnector.getIsAftNonZero(pstr, startDate.toString, "1").flatMap {
          case true => modelForSingleInProgressReturn(srn, startDate, endDate, inProgressReturns.head, linkText)
          case _ => Future.successful(None)
        }
      } else {
        modelForSingleInProgressReturn(srn, startDate, endDate, inProgressReturns.head, linkText)
      }

    } else if (inProgressReturns.nonEmpty) {
      modelForMultipleInProgressReturns(srn, pstr, inProgressReturns, linkText)
    } else {
      Future.successful(None)
    }
  }

  private def modelForSingleInProgressReturn(
                                              srn: String,
                                              startDate: LocalDate,
                                              endDate: LocalDate,
                                              overview: AFTOverview,
                                              linkText: Text
                                            )(implicit hc: HeaderCarrier, messages: Messages): Future[Option[AFTViewModel]] = {
    aftCacheConnector.lockDetail(srn, startDate.toString).map {
      case Some(lockDetail) => Some(AFTViewModel(
        Some(msg"aftPartial.inProgress.forPeriod".withArgs(startDate.format(dateFormatterStartDate), endDate.format(dateFormatterDMY))),
        if (lockDetail.name.nonEmpty) {
          Some(msg"aftPartial.status.lockDetail".withArgs(lockDetail.name))
        }
        else {
          Some(msg"aftPartial.status.locked")
        },
        Link(id = "aftSummaryLink",
          url = appConfig.aftSummaryPageUrl.format(srn, startDate, Draft, overview.numberOfVersions),
          linkText = linkText,
          hiddenText = Some(msg"aftPartial.view.hidden.forPeriod".withArgs(startDate.format(dateFormatterStartDate), endDate.format(dateFormatterDMY)))
        )
      ))
      case _ => Some(AFTViewModel(
        Some(msg"aftPartial.inProgress.forPeriod".withArgs(startDate.format(dateFormatterStartDate), endDate.format(dateFormatterDMY))),
        Some(msg"aftPartial.status.inProgress"),
        Link(
          id = "aftSummaryLink",
          url = appConfig.aftSummaryPageUrl.format(srn, startDate, Draft, overview.numberOfVersions),
          linkText = linkText,
          hiddenText = Some(msg"aftPartial.view.hidden.forPeriod".withArgs(startDate.format(dateFormatterStartDate), endDate.format(dateFormatterDMY)))
        )
      ))
    }
  }

  private def modelForMultipleInProgressReturns(
                                                 srn: String,
                                                 pstr: String,
                                                 inProgressReturns: Seq[AFTOverview],
                                                 linkText: Text
                                               )(
                                                 implicit hc: HeaderCarrier,
                                                 messages: Messages
                                               ): Future[Option[AFTViewModel]] = {

    retrieveZeroedOutReturns(inProgressReturns, pstr).map { zeroedReturns =>

      val countInProgress: Int = inProgressReturns.size - zeroedReturns.size

      if (countInProgress > 0) {
        Some(AFTViewModel(
          Some(msg"aftPartial.multipleInProgress.text"),
          Some(msg"aftPartial.multipleInProgress.count".withArgs(countInProgress)),
          Link(
            id = "aftContinueInProgressLink",
            url = appConfig.aftContinueReturnUrl.format(srn),
            linkText = linkText,
            hiddenText = Some(msg"aftPartial.view.hidden")
          )
        ))
      } else {
        None
      }
    }
  }

  private def pspAftDashboardGetInProgressReturnsModel(
                                                        overview: Seq[AFTOverview],
                                                        srn: String,
                                                        pstr: String
                                                      )(
                                                        implicit
                                                        hc: HeaderCarrier,
                                                        messages: Messages
                                                      ): Future[Option[Link]] = {
    val inProgressReturns = overview.filter(_.compiledVersionAvailable)

    if (inProgressReturns.size == 1) {
      val startDate: LocalDate = inProgressReturns.head.periodStartDate
      val endDate: LocalDate = Quarters.getQuarter(startDate).endDate

      if (inProgressReturns.head.numberOfVersions == 1) {
        aftConnector.getIsAftNonZero(pstr, startDate.toString, "1").flatMap {
          case true => pspAftDashboardSingleInProgressReturnLink(srn, startDate, endDate, inProgressReturns.head)
          case _ => Future.successful(None)
        }
      } else {
        pspAftDashboardSingleInProgressReturnLink(srn, startDate, endDate, inProgressReturns.head)
      }

    } else if (inProgressReturns.nonEmpty) {
      pspAftDashboardMultipleInProgressReturnLink(srn, pstr, inProgressReturns)
    } else {
      Future.successful(None)
    }
  }

  private def pspAftDashboardSingleInProgressReturnLink(
                                                         srn: String,
                                                         startDate: LocalDate,
                                                         endDate: LocalDate,
                                                         overview: AFTOverview
                                                       )(
                                                         implicit hc: HeaderCarrier,
                                                         messages: Messages
                                                       ): Future[Option[Link]] = {
    aftCacheConnector.lockDetail(srn, startDate.toString).map {
      case Some(_) =>
        Some(Link(
          id = "aftSummaryLink",
          url = appConfig.aftSummaryPageUrl.format(srn, startDate, Draft, overview.numberOfVersions),
          linkText = msg"pspDashboardAftReturnsPartial.inProgressReturns.link.single.locked",
          hiddenText = Some(msg"aftPartial.view.hidden.forPeriod".withArgs(
            startDate.format(dateFormatterStartDate),
            endDate.format(dateFormatterDMY)
          ))
        ))
      case _ =>
        Some(Link(
          id = "aftSummaryLink",
          url = appConfig.aftSummaryPageUrl.format(srn, startDate, Draft, overview.numberOfVersions),
          linkText = msg"pspDashboardAftReturnsPartial.inProgressReturns.link.single",
          hiddenText = Some(msg"aftPartial.view.hidden.forPeriod".withArgs(
            startDate.format(dateFormatterStartDate),
            endDate.format(dateFormatterDMY)
          ))
        ))
    }
  }

  private def pspAftDashboardMultipleInProgressReturnLink(
                                                           srn: String,
                                                           pstr: String,
                                                           inProgressReturns: Seq[AFTOverview]
                                                         )(
                                                           implicit hc: HeaderCarrier,
                                                           messages: Messages
                                                         ): Future[Option[Link]] = {
    retrieveZeroedOutReturns(inProgressReturns, pstr).map { zeroedReturns =>

      val countInProgress: Int = inProgressReturns.size - zeroedReturns.size

      if (countInProgress > 0) {

        Some(Link(
          id = "aftContinueInProgressLink",
          url = appConfig.aftContinueReturnUrl.format(srn),
          linkText = msg"pspDashboardAftReturnsPartial.inProgressReturns.link",
          hiddenText = Some(msg"aftPartial.view.hidden")
        ))
      } else {
        None
      }
    }
  }
}
