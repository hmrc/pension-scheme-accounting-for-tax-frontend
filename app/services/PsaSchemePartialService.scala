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
import models.financialStatement.PaymentOrChargeType.{AccountingForTaxCharges, getPaymentOrChargeType}

import javax.inject.Inject
import models.financialStatement.SchemeFS
import models.{AFTOverview, Draft, Quarters, SchemeDetails}
import play.api.i18n.Messages
import services.paymentsAndCharges.PaymentsAndChargesService
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.viewmodels._
import utils.DateHelper
import utils.DateHelper.{dateFormatterDMY, dateFormatterStartDate}
import viewmodels._

import scala.concurrent.{ExecutionContext, Future}

class PsaSchemePartialService @Inject()(
                                         appConfig: FrontendAppConfig,
                                         paymentsAndChargesService: PaymentsAndChargesService,
                                         aftConnector: AFTConnector,
                                         aftCacheConnector: UserAnswersCacheConnector
                                       )(implicit ec: ExecutionContext) {

  def aftCardModel(schemeDetails: SchemeDetails, srn: String)
                  (implicit hc: HeaderCarrier, messages: Messages): Future[Seq[CardViewModel]] =
    for {
      overview <- aftConnector.getAftOverview(schemeDetails.pstr)
      startLink <- getStartReturnLink(overview, srn, schemeDetails.pstr)
      (subHeadings, inProgressLink) <- getInProgressReturnsModel(overview, srn, schemeDetails.pstr)
    } yield Seq(CardViewModel(
      id = "aft-overview",
      heading = messages("aftPartial.head"),
      subHeadings = subHeadings,
      links = inProgressLink ++ startLink ++ getPastReturnsLink(overview, srn)
    ))

  /* Returns a start link if:
      1. Return has not been initiated for any of the quarters that are valid for starting a return OR
      2. Any of the returns in their first compile have been zeroed out due to deletion of all charges
   */

  private def getStartReturnLink(overview: Seq[AFTOverview], srn: String, pstr: String)
                                (implicit hc: HeaderCarrier): Future[Seq[Link]] = {

    val startLink: Link = Link(id = "aftLoginLink", url = appConfig.aftLoginUrl.format(srn),
      linkText = msg"aftPartial.start.link")

    val isReturnNotInitiatedForAnyQuarter: Boolean = {
      val aftValidYears = aftConnector.aftOverviewStartDate.getYear to aftConnector.aftOverviewEndDate.getYear
      aftValidYears.flatMap { year =>
        Quarters.availableQuarters(year)(appConfig).map { quarter =>
          !overview.map(_.periodStartDate).contains(Quarters.getQuarter(quarter, year).startDate)
        }
      }.contains(true)
    }

    if (isReturnNotInitiatedForAnyQuarter) {
      Future.successful(Seq(startLink))
    } else {
      retrieveZeroedOutReturns(overview, pstr).map {
        case zeroedReturns if zeroedReturns.nonEmpty => Seq(startLink) //if any returns in first compile are zeroed out, display start link
        case _ => Nil
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

  private def getPastReturnsLink(overview: Seq[AFTOverview], srn: String): Seq[Link] = {
    val pastReturns = overview.filter(!_.compiledVersionAvailable)

    if (pastReturns.nonEmpty) {
      Seq(Link(
        id = "aftAmendLink",
        url = appConfig.aftAmendUrl.format(srn),
        linkText = msg"aftPartial.view.change.past"))
    } else {
      Nil
    }
  }


  private def getInProgressReturnsModel(overview: Seq[AFTOverview],
                                        srn: String,
                                        pstr: String
                                       )(implicit hc: HeaderCarrier, messages: Messages): Future[(Seq[CardSubHeading], Seq[Link])] = {
    val inProgressReturns = overview.filter(_.compiledVersionAvailable)

    if (inProgressReturns.size == 1) {
      val startDate: LocalDate = inProgressReturns.head.periodStartDate
      val endDate: LocalDate = Quarters.getQuarter(startDate).endDate

      if (inProgressReturns.head.numberOfVersions == 1) {
        aftConnector.getIsAftNonZero(pstr, startDate.toString, "1").flatMap {
          case true => modelForSingleInProgressReturn(srn, startDate, endDate, inProgressReturns.head)
          case _ => Future.successful((Nil, Nil))
        }
      } else {
        modelForSingleInProgressReturn(srn, startDate, endDate, inProgressReturns.head)
      }

    } else if (inProgressReturns.nonEmpty) {
      modelForMultipleInProgressReturns(srn, pstr, inProgressReturns)
    } else {
      Future.successful((Nil, Nil))
    }
  }

  private def modelForSingleInProgressReturn(
                                              srn: String,
                                              startDate: LocalDate,
                                              endDate: LocalDate,
                                              overview: AFTOverview
                                            )(implicit hc: HeaderCarrier, messages: Messages): Future[(Seq[CardSubHeading], Seq[Link])] = {

    def returnTuple(subHeadingParam: String, linkText: Text): (Seq[CardSubHeading], Seq[Link]) = (
      Seq(CardSubHeading(
        subHeading = messages("aftPartial.inProgress.forPeriod", startDate.format(dateFormatterStartDate), endDate.format(dateFormatterDMY)),
        subHeadingClasses = "card-sub-heading",
        subHeadingParams = Seq(CardSubHeadingParam(
          subHeadingParam = subHeadingParam,
          subHeadingParamClasses = "font-small bold"
        )))),
      Seq(Link(
        id = "aftSummaryLink",
        url = appConfig.aftSummaryPageUrl.format(srn, startDate, Draft, overview.numberOfVersions),
        linkText = linkText,
        hiddenText = Some(msg"aftPartial.view.hidden.forPeriod".withArgs(startDate.format(dateFormatterStartDate), endDate.format(dateFormatterDMY)))
      ))
    )

    aftCacheConnector.lockDetail(srn, startDate.toString).map {
      case Some(lockDetail) => if (lockDetail.name.nonEmpty) {
        returnTuple(messages("aftPartial.status.lockDetail", lockDetail.name),
          msg"pspDashboardAftReturnsCard.inProgressReturns.link.single.locked")
      } else {
        returnTuple(messages("aftPartial.status.locked"),
          msg"pspDashboardAftReturnsCard.inProgressReturns.link.single.locked")
      }
      case _ => returnTuple(messages("aftPartial.status.inProgress"),
        msg"pspDashboardAftReturnsCard.inProgressReturns.link.single")
    }
  }

  private def modelForMultipleInProgressReturns(
                                                 srn: String,
                                                 pstr: String,
                                                 inProgressReturns: Seq[AFTOverview]
                                               )(implicit hc: HeaderCarrier,
                                                 messages: Messages): Future[(Seq[CardSubHeading], Seq[Link])] =
    retrieveZeroedOutReturns(inProgressReturns, pstr).map { zeroedReturns =>
      val countInProgress: Int = inProgressReturns.size - zeroedReturns.size
      if (countInProgress > 0) {
        (
          Seq(CardSubHeading(
            subHeading = messages("aftPartial.multipleInProgress.text"),
            subHeadingClasses = "card-sub-heading",
            subHeadingParams = Seq(CardSubHeadingParam(
              subHeadingParam = messages("aftPartial.multipleInProgress.count", countInProgress),
              subHeadingParamClasses = "font-small bold"
            )))),
          Seq(Link(
            id = "aftContinueInProgressLink",
            url = appConfig.aftContinueReturnUrl.format(srn),
            linkText = msg"pspDashboardAftReturnsCard.inProgressReturns.link",
            hiddenText = Some(msg"aftPartial.view.hidden")
          )))
      } else {
        (Nil, Nil)
      }
    }

  def upcomingAftChargesModel(schemeFs: Seq[SchemeFS], srn: String)
                             (implicit messages: Messages): Seq[CardViewModel] = {
    val upcomingCharges: Seq[SchemeFS] =
      paymentsAndChargesService.extractUpcomingCharges(schemeFs)

    val pastCharges: Seq[SchemeFS] = schemeFs.filter(_.periodEndDate.isBefore(DateHelper.today))

    if (upcomingCharges == Seq.empty && pastCharges == Seq.empty) {
      Nil
    } else {
      Seq(CardViewModel(
        id = "upcoming-aft-charges",
        heading = messages("pspDashboardUpcomingAftChargesCard.h2"),
        subHeadings = upcomingChargesSubHeadings(upcomingCharges),
        links = viewUpcomingLink(upcomingCharges, srn) ++ viewPastPaymentsAndChargesLink(pastCharges, srn)
      ))
    }
  }

  private def upcomingChargesSubHeadings(upcomingCharges: Seq[SchemeFS])(implicit messages: Messages): Seq[CardSubHeading] =
    if (upcomingCharges != Seq.empty) {
      val amount = s"${FormatHelper.formatCurrencyAmountAsString(upcomingCharges.map(_.amountDue).sum)}"

      val upcomingChargesSubHeading: String =
        if (upcomingCharges.map(_.dueDate).distinct.size == 1) {
          val dueDate = upcomingCharges.map(_.dueDate).distinct.flatten.head.format(fullDatePattern)
          messages("pspDashboardUpcomingAftChargesCard.span.singleDueDate", dueDate)
        } else {
          messages("pspDashboardUpcomingAftChargesCard.span.multipleDueDate")
        }

      Seq(CardSubHeading(
        subHeading = upcomingChargesSubHeading,
        subHeadingClasses = "card-sub-heading",
        subHeadingParams = Seq(CardSubHeadingParam(
          subHeadingParam = amount,
          subHeadingParamClasses = "font-large bold"
        ))
      ))
    } else {
      Nil
    }

  private def viewUpcomingLink(upcomingCharges: Seq[SchemeFS], srn: String): Seq[Link] =
    if (upcomingCharges != Seq.empty) {
      val nonAftUpcomingCharges: Seq[SchemeFS] = upcomingCharges.filter(p => getPaymentOrChargeType(p.chargeType) != AccountingForTaxCharges)

        val linkText: Text = if (upcomingCharges.map(_.dueDate).distinct.size == 1 && nonAftUpcomingCharges.isEmpty) {
          msg"pspDashboardUpcomingAftChargesCard.link.paymentsAndChargesForPeriod.single".withArgs(
            upcomingCharges.map(_.periodStartDate).distinct.head.format(smallDatePattern),
            upcomingCharges.map(_.periodEndDate).distinct.head.format(smallDatePattern))
        } else {
          msg"pspDashboardUpcomingAftChargesCard.link.paymentsAndChargesForPeriod.multiple"
        }

      Seq(Link("upcoming-payments-and-charges", appConfig.upcomingChargesUrl.format(srn), linkText, None))

    } else {
      Nil
    }

  private def viewPastPaymentsAndChargesLink(pastCharges: Seq[SchemeFS], srn: String): Seq[Link] =
    if (pastCharges == Seq.empty) {
      Nil
    } else {
      Seq(Link(
        id = "past-payments-and-charges",
        url = appConfig.paymentsAndChargesUrl.format(srn),
        linkText = msg"pspDashboardUpcomingAftChargesCard.link.pastPaymentsAndCharges",
        hiddenText = None
      ))
    }

  def overdueAftChargesModel(schemeFs: Seq[SchemeFS], srn: String)
                            (implicit messages: Messages): Seq[CardViewModel] = {
    val overdueCharges: Seq[SchemeFS] = paymentsAndChargesService.getOverdueCharges(schemeFs)
    val totalOverdue: BigDecimal = overdueCharges.map(_.amountDue).sum
    val totalInterestAccruing: BigDecimal = overdueCharges.map(_.accruedInterestTotal).sum

    val subHeadingTotalOverDue: Seq[CardSubHeading] = Seq(CardSubHeading(
      subHeading = messages("pspDashboardOverdueAftChargesCard.total.span"),
      subHeadingClasses = "card-sub-heading",
      subHeadingParams = Seq(CardSubHeadingParam(
        subHeadingParam = s"${FormatHelper.formatCurrencyAmountAsString(totalOverdue)}",
        subHeadingParamClasses = "font-large bold"
      ))
    ))

    val subHeadingInterestAccruing: Seq[CardSubHeading] = Seq(CardSubHeading(
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
    ))

    if (overdueCharges.nonEmpty) {
      Seq(CardViewModel(
        id = "aft-overdue-charges",
        heading = messages("pspDashboardOverdueAftChargesCard.h2"),
        subHeadings = subHeadingTotalOverDue ++ subHeadingInterestAccruing,
        links = viewOverdueLink(overdueCharges, srn)
      ))
    } else {
      Nil
    }
  }

  private def viewOverdueLink(schemeFs: Seq[SchemeFS], srn: String): Seq[Link] = {
    val nonAftOverdueCharges: Seq[SchemeFS] = schemeFs.filter(p => getPaymentOrChargeType(p.chargeType) != AccountingForTaxCharges)
      val linkText = if (schemeFs.map(_.periodStartDate).distinct.size == 1 && nonAftOverdueCharges.isEmpty) {
        msg"pspDashboardOverdueAftChargesCard.viewOverduePayments.link.singlePeriod"
          .withArgs(
            schemeFs.map(_.periodStartDate).distinct.head.format(smallDatePattern),
            schemeFs.map(_.periodEndDate).distinct.head.format(smallDatePattern))
      } else {
        msg"pspDashboardOverdueAftChargesCard.viewOverduePayments.link.multiplePeriods"
      }

    Seq(Link("overdue-payments-and-charges", appConfig.overdueChargesUrl.format(srn), linkText, None))
  }

  val fullDatePattern: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy")
  val smallDatePattern: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM")
}
