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

import config.FrontendAppConfig
import connectors.AFTConnector
import connectors.cache.UserAnswersCacheConnector
import helpers.FormatHelper
import models.financialStatement.PaymentOrChargeType.{AccountingForTaxCharges, getPaymentOrChargeType}
import models.financialStatement.SchemeFSDetail.{endDate, startDate}
import models.financialStatement.{PsaFSDetail, SchemeFSDetail}
import models.{AFTOverviewOnPODS, Draft, LockDetail, Quarters}
import play.api.i18n.Messages
import play.api.libs.json.{JsObject, Json}
import services.paymentsAndCharges.PaymentsAndChargesService
import uk.gov.hmrc.govukfrontend.views.Aliases.Text
import uk.gov.hmrc.govukfrontend.views.viewmodels.content.{Content, HtmlContent}
import uk.gov.hmrc.govukfrontend.views.viewmodels.table
import uk.gov.hmrc.govukfrontend.views.viewmodels.table.{HeadCell, TableRow}
import uk.gov.hmrc.http.HeaderCarrier
import utils.DateHelper
import utils.DateHelper.{dateFormatterDMY, dateFormatterStartDate, formatDateDMY}
import viewmodels._

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class AFTPartialService @Inject()(
                                   appConfig: FrontendAppConfig,
                                   paymentsAndChargesService: PaymentsAndChargesService,
                                   aftConnector: AFTConnector,
                                   aftCacheConnector: UserAnswersCacheConnector
                                 )(implicit ec: ExecutionContext) {

  def retrievePspDashboardAftReturnsModel(srn: String, pstr: String, authorisingPsaId: String)
                                         (implicit hc: HeaderCarrier, messages: Messages): Future[DashboardAftViewModel] =
    for {
      overview <- aftConnector.getAftOverview(pstr)
      reportsOnPods = overview.filter(_.versionDetails.isDefined).map(_.toPodsReport)
      inProgressReturnsLinkOpt <- pspAftDashboardGetInProgressReturnsModel(reportsOnPods, srn, pstr)
      inProgressReturns = reportsOnPods.filter(_.compiledVersionAvailable)
      subHeading <- optionSubHeading(inProgressReturns, pstr, srn, authorisingPsaId)
    } yield {

      val links: Seq[Link] = Seq(
        inProgressReturnsLinkOpt,
        Option(Link("aftLoginLink", appConfig.aftLoginUrl.format(srn), Text(Messages("aftPartial.start.link")))),
        getPastReturnsModelOpt(reportsOnPods, srn).map(_.link)
      ).flatten

      DashboardAftViewModel(subHeading, links)
    }


  // scalastyle:off method.length
  def retrievePspDashboardUpcomingAftChargesModel(schemeFs: Seq[SchemeFSDetail], srn: String)
                                                 (implicit messages: Messages): DashboardAftViewModel = {

    val upcomingCharges: Seq[SchemeFSDetail] =
      paymentsAndChargesService.extractUpcomingCharges(schemeFs)

    val pastCharges: Seq[SchemeFSDetail] = schemeFs.filter(_.periodEndDate.exists(_.isBefore(DateHelper.today)))

    val total = upcomingCharges.map(_.amountDue).sum

    val span =
      if (upcomingCharges.map(_.dueDate).distinct.size == 1) {
        Messages("pspDashboardUpcomingAftChargesCard.span.singleDueDate",
          upcomingCharges.map(_.dueDate).distinct.flatten.head.format(DateTimeFormatter.ofPattern("d MMMM yyyy")))
      } else {
        Messages("pspDashboardUpcomingAftChargesCard.span.multipleDueDate")
      }

    val subHeading = Json.obj(
      "total" -> s"${FormatHelper.formatCurrencyAmountAsString(total)}",
      "span" -> span
    )


    val viewUpcomingLink: Option[Link] = {
      if (upcomingCharges == Seq.empty) {
        None
      } else {
        val nonAftUpcomingCharges: Seq[SchemeFSDetail] = upcomingCharges.filter(p => getPaymentOrChargeType(p.chargeType) != AccountingForTaxCharges)
        val linkText: Content = if (upcomingCharges.map(_.dueDate).distinct.size == 1 && nonAftUpcomingCharges.isEmpty) {
          Text(Messages("pspDashboardUpcomingAftChargesCard.link.paymentsAndChargesForPeriod.single",
            Seq(
              startDate(upcomingCharges).format(DateTimeFormatter.ofPattern("d MMMM")),
              endDate(upcomingCharges).format(DateTimeFormatter.ofPattern("d MMMM"))
            )))
        } else {
          Text(Messages("pspDashboardUpcomingAftChargesCard.link.paymentsAndChargesForPeriod.multiple"))
        }
        Some(Link("upcoming-payments-and-charges", appConfig.upcomingChargesUrl.format(srn), linkText, None))
      }
    }

    val viewPastPaymentsAndChargesLink: Option[Link] =
      if (pastCharges == Seq.empty) {
        None
      } else {
        Some(Link(
          id = "past-payments-and-charges",
          url = appConfig.paymentsAndChargesUrl.format(srn),
          linkText = Text(Messages("pspDashboardUpcomingAftChargesCard.link.allPaymentsAndCharges")),
          hiddenText = None
        ))
      }


    val links = Seq(viewUpcomingLink, viewPastPaymentsAndChargesLink).flatten

    DashboardAftViewModel(
      subHeadings = Seq(subHeading),
      links = links
    )
  }

  // scalastyle:off method.length
  def retrievePspDashboardOverdueAftChargesModel(schemeFs: Seq[SchemeFSDetail], srn: String)
                                                (implicit messages: Messages): DashboardAftViewModel = {

    val totalOverdue: BigDecimal = schemeFs.map(_.amountDue).sum
    val totalInterestAccruing: BigDecimal = schemeFs.map(_.accruedInterestTotal).sum

    val subHeadingTotal = Json.obj(

      "total" -> s"${FormatHelper.formatCurrencyAmountAsString(totalOverdue)}",
      "span" -> Messages("pspDashboardOverdueAftChargesCard.total.span")
    )

    val subHeadingInterestAccruing = Json.obj(
      "total" -> s"${FormatHelper.formatCurrencyAmountAsString(totalInterestAccruing)}",
      "span" -> Messages("pspDashboardOverdueAftChargesCard.interestAccruing.span")
    )


    val viewOverdueLink: Option[Link] = {
      if (schemeFs == Seq.empty) {
        None
      } else {
        val nonAftOverdueCharges: Seq[SchemeFSDetail] = schemeFs.filter(p => getPaymentOrChargeType(p.chargeType) != AccountingForTaxCharges)
        val linkText: Text = if (schemeFs.filter(_.periodStartDate.nonEmpty).map(_.periodStartDate).distinct.size == 1 && nonAftOverdueCharges.isEmpty) {
          Text(Messages("pspDashboardOverdueAftChargesCard.viewOverduePayments.link.singlePeriod"
            ,Seq(
              startDate(schemeFs).format(DateTimeFormatter.ofPattern("d MMMM")),
              endDate(schemeFs).format(DateTimeFormatter.ofPattern("d MMMM"))
            )))
        } else {
          Text(Messages("pspDashboardOverdueAftChargesCard.viewOverduePayments.link.multiplePeriods"))
        }
        Some(Link("overdue-payments-and-charges", appConfig.overdueChargesUrl.format(srn), linkText, None))
      }
    }

    DashboardAftViewModel(
      subHeadings = Seq(subHeadingTotal, subHeadingInterestAccruing),
      links = Seq(viewOverdueLink).flatten
    )
  }

  def retrievePspDashboardPaymentsAndChargesModel(schemeFsDetail: Seq[SchemeFSDetail], srn: String, pstr: String)
                                                 (implicit messages: Messages): Seq[CardViewModel] = {
    val interestCharges: Seq[SchemeFSDetail] = paymentsAndChargesService.getInterestCharges(schemeFsDetail)
    val overdueChargesAbs: Seq[SchemeFSDetail] = paymentsAndChargesService.getOverdueCharges(schemeFsDetail.filter(_.amountDue > BigDecimal(0.00)))
    val upcomingChargesAbs: Seq[SchemeFSDetail] = paymentsAndChargesService.extractUpcomingCharges(schemeFsDetail.filter(_.amountDue > BigDecimal(0.00)))
    val totalOverdue: BigDecimal = overdueChargesAbs.map(_.amountDue).sum
    val totalInterestAccruing: BigDecimal = interestCharges.map(_.accruedInterestTotal).sum
    val totalUpcomingCharges: BigDecimal = upcomingChargesAbs.map(_.amountDue).sum
    val totalOutstandingPayments: BigDecimal = totalUpcomingCharges + totalOverdue + totalInterestAccruing
    val isChargePresent: Boolean = schemeFsDetail.nonEmpty
    val subHeadingTotalOutstanding: Seq[CardSubHeading] = Seq(CardSubHeading(
      subHeading = messages("pspDashboardOverdueAftChargesCard.outstanding.span"),
      subHeadingClasses = "card-sub-heading",
      subHeadingParams = Seq(CardSubHeadingParam(
        subHeadingParam = s"${FormatHelper.formatCurrencyAmountAsString(totalOutstandingPayments)}",
        subHeadingParamClasses = "font-large bold"
      ))
    ))

    val subHeadingPaymentsOverdue: Seq[CardSubHeading] = if (totalOverdue > 0) {
      Seq(CardSubHeading(
        subHeading = "",
        subHeadingClasses = "govuk-tag govuk-tag--red",
        subHeadingParams = Seq(CardSubHeadingParam(
          subHeadingParam = messages("pspDashboardOverdueAftChargesCard.overdue.span"),
          subHeadingParamClasses = "govuk-tag govuk-tag--red"
        ))

      ))
    }
    else {
      Nil
    }

    if (isChargePresent) {
      Seq(CardViewModel(
        id = "aft-overdue-charges",
        heading = messages("pspDashboardOverdueAndUpcomingAftChargesCard.h2"),
        subHeadings = subHeadingTotalOutstanding ++ subHeadingPaymentsOverdue,
        links = viewFinancialOverviewLink(srn) ++ viewAllPaymentsAndChargesLink(srn, pstr)
      ))
    }
    else {
      Nil
    }
  }

  private def viewFinancialOverviewLink(srn: String)(implicit messages: Messages): Seq[Link] =
      Seq(Link(
        id = "view-your-financial-overview",
        url = appConfig.financialOverviewUrl.format(srn),
        linkText = Text(Messages("pspDashboardUpcomingAftChargesCard.link.financialOverview")),
        hiddenText = None
      ))


  private def viewAllPaymentsAndChargesLink(srn: String, pstr: String)(implicit messages: Messages): Seq[Link] =
      Seq(Link(
        id = "past-payments-and-charges",
        url = appConfig.financialPaymentsAndChargesUrl.format(srn),
        linkText = Text(Messages("pspDashboardUpcomingAftChargesCard.link.allPaymentsAndCharges")),
        hiddenText = None
      ))


  private def optionSubHeading(
                                inProgressReturns: Seq[AFTOverviewOnPODS],
                                schemePstr: String,
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
            aftConnector.getIsAftNonZero(schemePstr, startDate, aftVersion = "1") flatMap {
              case true =>
                Future.successful(Seq(singleReturnSubHeading(inProgressReturns, optLockDetail, authorisingPsaId)))
              case _ =>
                Future.successful(Seq.empty)
            }
          } else {
            Future.successful(Seq(singleReturnSubHeading(inProgressReturns, optLockDetail, authorisingPsaId)))
          }
      }
    } else if (inProgressReturns.size > 1) {
      Future.successful(Seq(multipleReturnSubHeading(inProgressReturns)))
    } else {
      Future.successful(Seq.empty)
    }
  }

  private def multipleReturnSubHeading(inProgressReturns: Seq[AFTOverviewOnPODS])
                                      (implicit messages: Messages): JsObject =
    Json.obj(
      "h3" -> Messages("pspDashboardAftReturnsCard.h3.multiple", inProgressReturns.size.toString),
      "span" -> Messages("pspDashboardAftReturnsCard.span.multiple")
    )

  private def singleReturnSubHeading(inProgressReturns: Seq[AFTOverviewOnPODS], lockDetail: Option[LockDetail], authorisingPsaId: String)
                                    (implicit messages: Messages): JsObject = {

    val startDate: LocalDate = inProgressReturns.head.periodStartDate
    val startDateStr: String = startDate.format(DateTimeFormatter.ofPattern("d MMMM"))
    val endDate: String = Quarters.getQuarter(startDate).endDate.format(DateTimeFormatter.ofPattern("d MMMM yyyy"))

    val h3: String =
      if (lockDetail.nonEmpty) {
        if (lockDetail.get.psaOrPspId == authorisingPsaId) {
          Messages("pspDashboardAftReturnsCard.h3.single.lockedBy", lockDetail.get.name)
        } else {
          Messages("pspDashboardAftReturnsCard.h3.single.locked")
        }
      } else {
        Messages("pspDashboardAftReturnsCard.h3.single")
      }

    Json.obj(
      "h3" -> h3,
      "span" -> Messages("pspDashboardAftReturnsCard.span.single", startDateStr, endDate)
    )
  }


  /* Returns a seq of the aftReturns in their first compile have been zeroed out due to deletion of all charges
  */
  private def retrieveZeroedOutReturns(overview: Seq[AFTOverviewOnPODS], pstr: String)
                                      (implicit hc: HeaderCarrier): Future[Seq[AFTOverviewOnPODS]] = {
    val firstCompileReturns = overview.filter(_.compiledVersionAvailable).filter(_.numberOfVersions == 1)

    Future.sequence(firstCompileReturns.map(aftReturn =>
      aftConnector.getIsAftNonZero(pstr, aftReturn.periodStartDate.toString, "1"))).map {
      isNonZero => (firstCompileReturns zip isNonZero).filter(!_._2).map(_._1)
    }
  }

  private def getPastReturnsModelOpt(overview: Seq[AFTOverviewOnPODS], srn: String)(implicit messages: Messages): Option[AFTViewModel] = {
    val pastReturns = overview.filter(!_.compiledVersionAvailable)

    if (pastReturns.nonEmpty) {
      Some(AFTViewModel(None, None, Link("aftAmendLink", appConfig.aftAmendUrl.format(srn), Text(Messages("aftPartial.view.change.past")))))
    } else {
      None
    }
  }

  private def pspAftDashboardGetInProgressReturnsModel(
                                                        overview: Seq[AFTOverviewOnPODS],
                                                        srn: String,
                                                        pstr: String
                                                      )(implicit hc: HeaderCarrier,messages: Messages
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
                                                         overview: AFTOverviewOnPODS
                                                       )(
                                                         implicit hc: HeaderCarrier, messages: Messages
                                                       ): Future[Option[Link]] = {
    aftCacheConnector.lockDetail(srn, startDate.toString).map {
      case Some(_) =>
        Some(Link(
          id = "aftSummaryLink",
          url = appConfig.aftSummaryPageUrl.format(srn, startDate, Draft, overview.numberOfVersions),
          linkText = Text(Messages("pspDashboardAftReturnsCard.inProgressReturns.link.single.locked")),
          hiddenText = Some(Text(Messages("aftPartial.view.hidden.forPeriod", Seq(
            startDate.format(dateFormatterStartDate),
            endDate.format(dateFormatterDMY)
          ))))
        ))
      case _ =>
        Some(Link(
          id = "aftSummaryLink",
          url = appConfig.aftSummaryPageUrl.format(srn, startDate, Draft, overview.numberOfVersions),
          linkText = Text(Messages("pspDashboardAftReturnsCard.inProgressReturns.link.single")),
          hiddenText = Some(Text(Messages("aftPartial.view.hidden.forPeriod", Seq(
            startDate.format(dateFormatterStartDate),
            endDate.format(dateFormatterDMY)
          ))))
        ))
    }
  }

  private def pspAftDashboardMultipleInProgressReturnLink(
                                                           srn: String,
                                                           pstr: String,
                                                           inProgressReturns: Seq[AFTOverviewOnPODS]
                                                         )(
                                                           implicit hc: HeaderCarrier, messages: Messages
                                                         ): Future[Option[Link]] = {
    retrieveZeroedOutReturns(inProgressReturns, pstr).map { zeroedReturns =>

      val countInProgress: Int = inProgressReturns.size - zeroedReturns.size

      if (countInProgress > 0) {

        Some(Link(
          id = "aftContinueInProgressLink",
          url = appConfig.aftContinueReturnUrl.format(srn),
          linkText = Text(Messages("pspDashboardAftReturnsCard.inProgressReturns.link")),
          hiddenText = Some(Text(Messages("aftPartial.view.hidden")))
        ))
      } else {
        None
      }
    }
  }

  def penaltiesAndCharges(psaFSDetail: Seq[PsaFSDetail])
                         (implicit messages: Messages): Seq[CardViewModel] = {
    val overdueCharges: Seq[PsaFSDetail] = psaFSDetail.filter(charge => charge.dueDate.exists(_.isBefore(DateHelper.today)))
    val upcomingCharges: Seq[PsaFSDetail] = psaFSDetail.filter(_.dueDate.exists(!_.isBefore(DateHelper.today)))
    val totalOverdue: BigDecimal = overdueCharges.map(_.amountDue).sum
    val totalInterestAccruing: BigDecimal = overdueCharges.map(_.accruedInterestTotal).sum
    val totalUpcomingCharges: BigDecimal = upcomingCharges.map(_.amountDue).sum
    val totalOutstandingPayments: BigDecimal = totalUpcomingCharges + totalOverdue + totalInterestAccruing
    val isChargesPresent: Boolean = psaFSDetail.nonEmpty
    val subHeadingTotalOutstanding: Seq[CardSubHeading] = Seq(CardSubHeading(
      subHeading = messages("pspDashboardOverdueAftChargesCard.outstanding.span"),
      subHeadingClasses = "card-sub-heading",
      subHeadingParams = Seq(CardSubHeadingParam(
        subHeadingParam = s"${FormatHelper.formatCurrencyAmountAsString(totalOutstandingPayments)}",
        subHeadingParamClasses = "font-large bold"
      ))
    ))
    val subHeadingPenaltiesOverdue: Seq[CardSubHeading] = if (totalOverdue > 0) {
      Seq(CardSubHeading(
        subHeading = "",
        subHeadingClasses = "govuk-tag govuk-tag--red",
        subHeadingParams = Seq(CardSubHeadingParam(
          subHeadingParam = messages("pspDashboardOverdueAftChargesCard.overdue.span"),
          subHeadingParamClasses = "govuk-tag govuk-tag--red"
        ))

      ))
    }
    else {
      Nil
    }

    if(isChargesPresent) {
      Seq(CardViewModel(
        id = "aft-overdue-charges",
        heading = messages("psaPenaltiesCard.h2"),
        subHeadings = subHeadingTotalOutstanding ++ subHeadingPenaltiesOverdue,
        links = viewFinancialOverviewLink() ++ viewAllPenaltiesAndChargesLink()
      ))
    }
    else {
      Nil
    }
  }

  private def viewFinancialOverviewLink()(implicit messages: Messages): Seq[Link] =
    Seq(Link(
      id = "view-your-financial-overview",
      url = appConfig.psafinancialOverviewUrl,
      linkText = Text(Messages("pspDashboardUpcomingAftChargesCard.link.financialOverview")),
      hiddenText = None
    ))

  private def viewAllPenaltiesAndChargesLink()(implicit messages: Messages): Seq[Link] =
    Seq(Link(
      id = "past-penalties-id",
      url = appConfig.viewAllPenaltiesForFinancialOverviewUrl,
      linkText = Text(Messages("psa.financial.overview.pastPenalties.link")),
      hiddenText = None
    ))

  def retrievePsaPenaltiesCardModel(psaFs: Seq[PsaFSDetail])
                                   (implicit messages: Messages): DashboardAftViewModel = {

    val upcomingCharges: Seq[PsaFSDetail] =
      psaFs.filter(_.dueDate.exists(!_.isBefore(DateHelper.today)))

    val subHeadingPaymentDue = {

      val totalUpcoming = upcomingCharges.map(_.amountDue).sum

      val span: String = if (upcomingCharges.map(_.dueDate).distinct.size == 1) {
        Messages("pspDashboardUpcomingAftChargesCard.span.singleDueDate",Seq(
          upcomingCharges.map(_.dueDate).distinct.flatten.head.format(DateTimeFormatter.ofPattern("d MMMM yyyy"))))
      } else {
        Messages("pspDashboardUpcomingAftChargesCard.span.multipleDueDate")
      }

      Json.obj(
        "total" -> s"${FormatHelper.formatCurrencyAmountAsString(totalUpcoming)}",
        "span" -> span
      )
    }

    val subHeadingTotalOverduePayments: JsObject = {
      val pastDueDateCharges: Seq[PsaFSDetail] =
        psaFs.filter(charge => charge.dueDate.exists(_.isBefore(DateHelper.today)))
      val totalOverdue: BigDecimal = pastDueDateCharges.map(_.amountDue).sum
      Json.obj(
        "total" -> s"${FormatHelper.formatCurrencyAmountAsString(totalOverdue)}",
        "span" -> Messages("pspDashboardOverdueAftChargesCard.total.span")
      )
    }

    val outstandingLink: Seq[Link] = if (psaFs.exists(_.amountDue > BigDecimal(0.00))) {
      Seq(Link("outstanding-penalties-id", appConfig.viewUpcomingPenaltiesUrl, Text(Messages("psaPenaltiesCard.paymentsDue.linkText")), None))
    } else {
      Nil
    }

    DashboardAftViewModel(
      subHeadings = Seq(subHeadingPaymentDue, subHeadingTotalOverduePayments),
      links = outstandingLink :+ Link("past-penalties-id", appConfig.viewPenaltiesUrl, Text(Messages("psaPenaltiesCard.viewPastPenalties")), None))
  }

  def retrievePsaChargesAmount(psaFs: Seq[PsaFSDetail]): (String, String, String) = {

    val upcomingCharges: Seq[PsaFSDetail] =
      psaFs.filter(_.dueDate.exists(!_.isBefore(DateHelper.today)))

    val overdueCharges: Seq[PsaFSDetail] =
      psaFs.filter(charge => charge.dueDate.exists(_.isBefore(DateHelper.today)))

    val totalUpcomingCharge = upcomingCharges.map(_.amountDue).sum
    val totalOverdueCharge: BigDecimal = overdueCharges.map(_.amountDue).sum
    val totalInterestAccruing: BigDecimal = overdueCharges.map(_.accruedInterestTotal).sum

    val totalUpcomingChargeFormatted = s"${FormatHelper.formatCurrencyAmountAsString(totalUpcomingCharge)}"
    val totalOverdueChargeFormatted = s"${FormatHelper.formatCurrencyAmountAsString(totalOverdueCharge)}"
    val totalInterestAccruingFormatted = s"${FormatHelper.formatCurrencyAmountAsString(totalInterestAccruing)}"


    (totalUpcomingChargeFormatted, totalOverdueChargeFormatted, totalInterestAccruingFormatted)
  }

  def retrievePaidPenaltiesAndCharges(psaFs: Seq[PsaFSDetail]): Seq[PsaFSDetail] = {
    psaFs.filter(_.outstandingAmount <= 0)
  }

  def getCreditBalanceAmount(psaFs: Seq[PsaFSDetail]): BigDecimal = {
    val sumAmountOverdue = psaFs.filter(_.dueDate.nonEmpty).map(_.amountDue).sum
    val creditBalanceAmt = if (sumAmountOverdue >= 0) {
      BigDecimal(0.00)
    } else {
      sumAmountOverdue.abs
    }
    creditBalanceAmt
  }

  def getLatestCreditsDetails(latestCredits: Seq[PsaFSDetail]
                                            )(implicit messages: Messages): table.Table = {

    val head: Seq[HeadCell] = Seq(
      HeadCell(Text("")),
      HeadCell(Text(Messages("refunds.aft.date"))),
      HeadCell(Text(Messages("refunds.aft.credit.value"))))

    val rows = latestCredits.map { psaFSDetail =>
        Seq(
          TableRow(getCreditsLabel(psaFSDetail), classes = "govuk-!-width-one-half"),
          TableRow(Text(formatDateDMY(psaFSDetail.dueDate.get)), classes = "govuk-!-width-one-quarter"),
          TableRow(Text(s"${FormatHelper.formatCurrencyAmountAsString(psaFSDetail.amountDue.abs)}"), classes = "govuk-!-width-one-quarter"))
    }

    uk.gov.hmrc.govukfrontend.views.viewmodels.table.Table(head = Some(head), rows = rows , attributes = Map("role" -> "table"))
  }

  private def getCreditsLabel(psaFSDetail: PsaFSDetail): HtmlContent = {
    HtmlContent(
      s"${psaFSDetail.chargeType.toString}</br>" +
        formatDateDMY(psaFSDetail.periodStartDate) + " to " + formatDateDMY(psaFSDetail.periodEndDate)
    )
  }
}
