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

package controllers.financialOverview.scheme

import config.FrontendAppConfig
import controllers.actions._
import helpers.FormatHelper
import models.financialStatement.PaymentOrChargeType.{AccountingForTaxCharges, ExcessReliefPaidCharges, InterestOnExcessRelief, getPaymentOrChargeType}
import models.financialStatement.{PaymentOrChargeType, SchemeFSDetail}
import models.{ChargeDetailsFilter, Quarters}
import play.api.Logger
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.mvc._
import services.financialOverview.scheme.PaymentsAndChargesService
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import utils.DateHelper
import utils.DateHelper.{dateFormatterDMY, dateFormatterStartDate}
import views.html.financialOverview.scheme.PaymentsAndChargesNewView

import java.time.LocalDate
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class AllPaymentsAndChargesController @Inject()(
                                                 override val messagesApi: MessagesApi,
                                                 identify: IdentifierAction,
                                                 allowAccess: AllowAccessActionProviderForIdentifierRequest,
                                                 val controllerComponents: MessagesControllerComponents,
                                                 config: FrontendAppConfig,
                                                 view: PaymentsAndChargesNewView,
                                                 paymentsAndChargesService: PaymentsAndChargesService
                                               )(implicit ec: ExecutionContext)
  extends FrontendBaseController
    with I18nSupport {

  private val logger = Logger(classOf[AllPaymentsAndChargesController])

  def onPageLoad(srn: String, period: String, paymentOrChargeType: PaymentOrChargeType, journeyType: ChargeDetailsFilter): Action[AnyContent] =
    (identify andThen allowAccess(Some(srn))).async { implicit request =>
      paymentsAndChargesService.getPaymentsForJourney(request.idOrException, srn, journeyType, request.isLoggedInAsPsa).flatMap { paymentsCache =>
        val (title, filteredPayments): (String, Seq[SchemeFSDetail]) =
          getTitleAndFilteredPayments(paymentsCache.schemeFSDetail, period, paymentOrChargeType)

        val loggedInAsPsa: Boolean = request.isLoggedInAsPsa
        val dueCharges: Seq[SchemeFSDetail] = paymentsAndChargesService.getDueCharges(filteredPayments)
        val totalDueCharges: BigDecimal = dueCharges.map(_.amountDue).sum
        val interestCharges: Seq[SchemeFSDetail] = paymentsAndChargesService.getInterestCharges(filteredPayments)
        val totalInterestCharges: BigDecimal = interestCharges.map(_.accruedInterestTotal).sum
        val totalCharges: BigDecimal = totalDueCharges + totalInterestCharges

        if (filteredPayments.nonEmpty) {
          val tableOfPaymentsAndCharges = paymentsAndChargesService.getPaymentsAndCharges(srn, filteredPayments, journeyType, config)

          val messages = request2Messages

          Future.successful(Ok(view(
            journeyType = journeyType.toString,
            schemeName = paymentsCache.schemeDetails.schemeName,
            titleMessage = title,
            pstr = paymentsCache.schemeDetails.pstr,
            reflectChargeText = messages(s"paymentsAndCharges.reflect.charge.text"),
            totalOverdue = s"${FormatHelper.formatCurrencyAmountAsString(totalDueCharges)}",
            totalInterestAccruing = s"${FormatHelper.formatCurrencyAmountAsString(totalInterestCharges)}",
            totalUpcoming = s"${FormatHelper.formatCurrencyAmountAsString(totalCharges)}",
            totalDue = s"${FormatHelper.formatCurrencyAmountAsString(totalCharges)}",
            penaltiesTable = tableOfPaymentsAndCharges,
            paymentAndChargesTable = tableOfPaymentsAndCharges,
            returnUrl = Option(config.financialOverviewUrl).getOrElse("/financial-overview/%s").format(srn),
            returnDashboardUrl = if(loggedInAsPsa) {
              Option(config.managePensionsSchemeSummaryUrl).getOrElse("/pension-scheme-summary/%s").format(srn)
            } else {
              Option(config.managePensionsSchemePspUrl).getOrElse("/%s/dashboard/pension-scheme-details").format(srn)
            }
          )))
        } else {
          logger.warn(s"No Scheme Payments and Charges returned for the selected period $period")
          Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad))
        }
      }
    }

  private val isTaxYearFormat: PaymentOrChargeType => Boolean = ct => ct == InterestOnExcessRelief || ct == ExcessReliefPaidCharges

  private def getTitleAndFilteredPayments(payments: Seq[SchemeFSDetail], period: String, paymentOrChargeType: PaymentOrChargeType)
                                         (implicit messages: Messages): (String, Seq[SchemeFSDetail]) =
    if (paymentOrChargeType == AccountingForTaxCharges) {
      val startDate: LocalDate = LocalDate.parse(period)
      (messages(s"paymentsAndCharges.all.aft.title",
        startDate.format(dateFormatterStartDate),
        Quarters.getQuarter(startDate).endDate.format(dateFormatterDMY)),
        payments.filter(p => getPaymentOrChargeType(p.chargeType) == AccountingForTaxCharges).filter(_.periodStartDate.contains(startDate)))
    } else {
      val typeParam: String = messages(s"paymentOrChargeType.${paymentOrChargeType.toString}")
      val filteredPayments = payments.filter(p => getPaymentOrChargeType(p.chargeType) == paymentOrChargeType)
        .filter(_.periodEndDate.exists(_.getYear == period.toInt))
      val title = if (isTaxYearFormat(paymentOrChargeType) && filteredPayments.nonEmpty) {
        messages(s"paymentsAndCharges.all.excessCharges.title", typeParam,
          DateHelper.formatDateDMY(filteredPayments.head.periodStartDate),
          DateHelper.formatDateDMY(filteredPayments.head.periodEndDate)
        )
      } else {
        messages(s"paymentsAndCharges.all.nonAft.title", typeParam, period)
      }
      (title, filteredPayments)
    }
}
