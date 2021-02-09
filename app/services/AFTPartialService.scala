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

import config.FrontendAppConfig
import connectors.AFTConnector
import connectors.cache.UserAnswersCacheConnector
import dateOrdering.orderingLocalDate
import helpers.FormatHelper
import models.financialStatement.SchemeFS
import javax.inject.Inject
import models.{AFTOverview, Quarters, Draft, SchemeDetails, LockDetail}
import play.api.i18n.Messages
import play.api.libs.json.{Json, JsObject}
import services.paymentsAndCharges.PaymentsAndChargesService
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.viewmodels._
import utils.DateHelper
import utils.DateHelper.{dateFormatterStartDate, dateFormatterDMY}
import viewmodels.{Link, AFTViewModel, PspDashboardAftViewModel}

import scala.concurrent.{Future, ExecutionContext}

class AFTPartialService @Inject()(
                                   appConfig: FrontendAppConfig,
                                   schemeService: SchemeService,
                                   paymentsAndChargesService: PaymentsAndChargesService,
                                   aftConnector: AFTConnector,
                                   aftCacheConnector: UserAnswersCacheConnector
                                 )(implicit ec: ExecutionContext) {

  def retrievePspDashboardAftReturnsModel(
                                           srn: String,
                                           pspId: String,
                                           schemeIdType: String,
                                           authorisingPsaId: String
                                         )(
                                           implicit
                                           hc: HeaderCarrier,
                                           messages: Messages
                                         ): Future[PspDashboardAftViewModel] = {
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

        PspDashboardAftViewModel(
          subHeadings = subHeading,
          links = links
        )
      }
    }
  }

  // scalastyle:off method.length
  def retrievePspDashboardUpcomingAftChargesModel(schemeFs: Seq[SchemeFS], srn: String)
                                                 (implicit messages: Messages): PspDashboardAftViewModel = {

    val upcomingCharges: Seq[SchemeFS] =
      paymentsAndChargesService.getUpcomingCharges(schemeFs)

    val pastCharges: Seq[SchemeFS] = schemeFs
      .filter(_.periodEndDate.isBefore(DateHelper.today))

    val total = upcomingCharges.map(_.amountDue).sum

    val span =
      if (upcomingCharges.map(_.dueDate).distinct.size == 1) {
        msg"pspDashboardUpcomingAftChargesCard.span.singleDueDate"
          .withArgs(upcomingCharges.map(_.dueDate).distinct
            .flatten
            .head
            .format(DateTimeFormatter.ofPattern("d MMMM yyyy")))
      } else {
        msg"pspDashboardUpcomingAftChargesCard.span.multipleDueDate"
      }

    val subHeading = Json.obj(
      "total" -> s"${FormatHelper.formatCurrencyAmountAsString(total)}",
      "span" -> span
    )


    val viewUpcomingLink: Option[Link] = {
      if (upcomingCharges == Seq.empty) {
        None
      } else {
        val upcomingLinkText =
          if (upcomingCharges.map(_.periodStartDate).distinct.size == 1) {
            msg"pspDashboardUpcomingAftChargesCard.link.paymentsAndChargesForPeriod.single"
              .withArgs(
                upcomingCharges.map(_.periodStartDate)
                  .distinct
                  .head
                  .format(DateTimeFormatter.ofPattern("d MMMM")),
                upcomingCharges.map(_.periodEndDate)
                  .distinct
                  .head
                  .format(DateTimeFormatter.ofPattern("d MMMM"))
              )
          } else {
            msg"pspDashboardUpcomingAftChargesCard.link.paymentsAndChargesForPeriod.multiple"
          }

        val startDate: LocalDate =
          upcomingCharges.sortBy(_.periodStartDate).map(_.periodStartDate).distinct.head
        Some(Link(
          id = "upcoming-payments-and-charges",
          url = appConfig.paymentsAndChargesUpcomingUrl.format(srn, startDate),
          linkText = upcomingLinkText,
          hiddenText = None
        ))
      }
    }

    val viewPastPaymentsAndChargesLink: Option[Link] =
      if (pastCharges == Seq.empty) {
        None
      } else {
        Some(Link(
          id = "past-payments-and-charges",
          url = appConfig.paymentsAndChargesUrl.format(srn, "2020"),
          linkText = msg"pspDashboardUpcomingAftChargesCard.link.pastPaymentsAndCharges",
          hiddenText = None
        ))
      }


    val links = Seq(viewUpcomingLink, viewPastPaymentsAndChargesLink).flatten

    PspDashboardAftViewModel(
      subHeadings = Seq(subHeading),
      links = links
    )
  }

  // scalastyle:off method.length
  def retrievePspDashboardOverdueAftChargesModel(schemeFs: Seq[SchemeFS], srn: String)
                                                (implicit messages: Messages): PspDashboardAftViewModel = {

    val totalOverdue: BigDecimal =
      schemeFs.map(_.amountDue).sum

    val totalInterestAccruing: BigDecimal =
      schemeFs.map(_.accruedInterestTotal).sum

    val subHeadingTotal = Json.obj(
      "total" -> s"${FormatHelper.formatCurrencyAmountAsString(totalOverdue)}",
      "span" -> msg"pspDashboardOverdueAftChargesCard.total.span"
    )

    val subHeadingInterestAccruing = Json.obj(
      "total" -> s"${FormatHelper.formatCurrencyAmountAsString(totalInterestAccruing)}",
      "span" -> msg"pspDashboardOverdueAftChargesCard.interestAccruing.span"
    )


    val viewOverdueLink: Option[Link] = {
      if (schemeFs == Seq.empty) {
        None
      } else {
        val overdueLinkText =
          if (schemeFs.map(_.periodStartDate).distinct.size == 1) {
            msg"pspDashboardOverdueAftChargesCard.viewOverduePayments.link.singlePeriod"
              .withArgs(
                schemeFs.map(_.periodStartDate)
                  .distinct
                  .head
                  .format(DateTimeFormatter.ofPattern("d MMMM")),
                schemeFs.map(_.periodEndDate)
                  .distinct
                  .head
                  .format(DateTimeFormatter.ofPattern("d MMMM"))
              )
          } else {
            msg"pspDashboardOverdueAftChargesCard.viewOverduePayments.link.multiplePeriods"
          }

        val startDate: LocalDate =
          schemeFs.sortBy(_.periodStartDate).map(_.periodStartDate).distinct.head
        Some(Link(
          id = "overdue-payments-and-charges",
          url = appConfig.paymentsAndChargesOverdueUrl.format(srn, startDate),
          linkText = overdueLinkText,
          hiddenText = None
        ))
      }
    }

    PspDashboardAftViewModel(
      subHeadings = Seq(subHeadingTotal, subHeadingInterestAccruing),
      links = Seq(viewOverdueLink).flatten
    )
  }

  private def optionSubHeading(
                                inProgressReturns: Seq[AFTOverview],
                                schemeDetails: SchemeDetails,
                                srn: String,
                                authorisingPsaId: String
                              )(
                                implicit hc: HeaderCarrier,
                                messages: Messages
                              ): Future[Seq[JsObject]] = {
    if (inProgressReturns.size == 1) {
      val startDate = inProgressReturns.head.periodStartDate.toString

      aftCacheConnector.lockDetail(srn, startDate) flatMap {
        optLockDetail =>
          if (inProgressReturns.head.numberOfVersions == 1) {
            aftConnector.getIsAftNonZero(
              pstr = schemeDetails.pstr,
              startDate = startDate,
              aftVersion = "1"
            ) flatMap {
              case true =>
                Future.successful(Seq(
                  singleReturnSubHeading(inProgressReturns, optLockDetail, authorisingPsaId)
                ))
              case _ =>
                Future.successful(Seq.empty)
            }
          } else {
            Future.successful(Seq(
              singleReturnSubHeading(inProgressReturns, optLockDetail, authorisingPsaId)
            ))
          }
      }
    } else if (inProgressReturns.size > 1) {
      Future.successful(Seq(multipleReturnSubHeading(inProgressReturns)))
    } else {
      Future.successful(Seq.empty)
    }
  }

  private def multipleReturnSubHeading(inProgressReturns: Seq[AFTOverview])
                                      (implicit messages: Messages): JsObject =
    Json.obj(
      "h3" -> msg"pspDashboardAftReturnsCard.h3.multiple".withArgs(inProgressReturns.size.toString).resolve,
      "span" -> msg"pspDashboardAftReturnsCard.span.multiple".resolve
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
          msg"pspDashboardAftReturnsCard.h3.single.lockedBy".withArgs(lockDetail.get.name).resolve
        } else {
          msg"pspDashboardAftReturnsCard.h3.single.locked".resolve
        }
      } else {
        msg"pspDashboardAftReturnsCard.h3.single".resolve
      }

    Json.obj(
      "h3" -> h3,
      "span" -> msg"pspDashboardAftReturnsCard.span.single".withArgs(startDateStr, endDate)
    )
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

  private def getPastReturnsModelOpt(overview: Seq[AFTOverview], srn: String): Option[AFTViewModel] = {
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

  private def pspAftDashboardGetInProgressReturnsModel(
                                                        overview: Seq[AFTOverview],
                                                        srn: String,
                                                        pstr: String
                                                      )(
                                                        implicit
                                                        hc: HeaderCarrier
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
                                                         implicit hc: HeaderCarrier
                                                       ): Future[Option[Link]] = {
    aftCacheConnector.lockDetail(srn, startDate.toString).map {
      case Some(_) =>
        Some(Link(
          id = "aftSummaryLink",
          url = appConfig.aftSummaryPageUrl.format(srn, startDate, Draft, overview.numberOfVersions),
          linkText = msg"pspDashboardAftReturnsCard.inProgressReturns.link.single.locked",
          hiddenText = Some(msg"aftPartial.view.hidden.forPeriod".withArgs(
            startDate.format(dateFormatterStartDate),
            endDate.format(dateFormatterDMY)
          ))
        ))
      case _ =>
        Some(Link(
          id = "aftSummaryLink",
          url = appConfig.aftSummaryPageUrl.format(srn, startDate, Draft, overview.numberOfVersions),
          linkText = msg"pspDashboardAftReturnsCard.inProgressReturns.link.single",
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
                                                           implicit hc: HeaderCarrier
                                                         ): Future[Option[Link]] = {
    retrieveZeroedOutReturns(inProgressReturns, pstr).map { zeroedReturns =>

      val countInProgress: Int = inProgressReturns.size - zeroedReturns.size

      if (countInProgress > 0) {

        Some(Link(
          id = "aftContinueInProgressLink",
          url = appConfig.aftContinueReturnUrl.format(srn),
          linkText = msg"pspDashboardAftReturnsCard.inProgressReturns.link",
          hiddenText = Some(msg"aftPartial.view.hidden")
        ))
      } else {
        None
      }
    }
  }
}
