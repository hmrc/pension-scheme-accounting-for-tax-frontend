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
import helpers.FormatHelper
import models.financialStatement.{PsaFSDetail, SchemeFSDetail}
import play.api.i18n.Messages
import play.api.libs.json.{JsObject, Json}
import services.paymentsAndCharges.PaymentsAndChargesService
import uk.gov.hmrc.govukfrontend.views.Aliases.Text
import uk.gov.hmrc.govukfrontend.views.viewmodels.content.HtmlContent
import uk.gov.hmrc.govukfrontend.views.viewmodels.table
import uk.gov.hmrc.govukfrontend.views.viewmodels.table.{HeadCell, TableRow}
import utils.DateHelper
import utils.DateHelper.formatDateDMY
import viewmodels._

import java.time.format.DateTimeFormatter
import javax.inject.Inject

class AFTPartialService @Inject()(
                                   appConfig: FrontendAppConfig,
                                   paymentsAndChargesService: PaymentsAndChargesService
                                 ) {


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
      HeadCell(
        HtmlContent(
          s"<span class='govuk-visually-hidden'>${messages("refunds.aft.creditDetails")}</span>"
        )),
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
