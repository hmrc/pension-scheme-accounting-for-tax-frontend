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

package controllers.financialStatement.paymentsAndCharges

import config.FrontendAppConfig
import controllers.actions._
import models.ChargeDetailsFilter.{All, Upcoming}
import models.financialStatement.PaymentOrChargeType.{AccountingForTaxCharges, ExcessReliefPaidCharges, InterestOnExcessRelief, getPaymentOrChargeType}
import models.financialStatement.{PaymentOrChargeType, SchemeFSDetail}
import models.{ChargeDetailsFilter, Quarters}
import play.api.Logger
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.mvc._
import services.paymentsAndCharges.PaymentsAndChargesService
import uk.gov.hmrc.govukfrontend.views.viewmodels.table.Table
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import utils.DateHelper
import utils.DateHelper.{dateFormatterDMY, dateFormatterStartDate}

import java.time.LocalDate
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import views.html.financialStatement.paymentsAndCharges.PaymentsAndChargesView

class PaymentsAndChargesController @Inject()(
                                              override val messagesApi: MessagesApi,
                                              identify: IdentifierAction,
                                              allowAccess: AllowAccessActionProviderForIdentifierRequest,
                                              val controllerComponents: MessagesControllerComponents,
                                              config: FrontendAppConfig,
                                              paymentsAndChargesService: PaymentsAndChargesService,
                                              paymentsAndChargesView: PaymentsAndChargesView
                                            )(implicit ec: ExecutionContext)
  extends FrontendBaseController
    with I18nSupport {

  private val logger = Logger(classOf[PaymentsAndChargesController])

  def onPageLoad(srn: String, period: String, paymentOrChargeType: PaymentOrChargeType, journeyType: ChargeDetailsFilter): Action[AnyContent] =
    (identify andThen allowAccess(Some(srn))).async { implicit request =>
      paymentsAndChargesService.getPaymentsForJourney(request.idOrException, srn, journeyType, request.isLoggedInAsPsa).flatMap { paymentsCache =>
        val (title, filteredPayments): (String, Seq[SchemeFSDetail]) =
          getTitleAndFilteredPayments(paymentsCache.schemeFSDetail, period, paymentOrChargeType, journeyType)
        if (filteredPayments.nonEmpty) {
          val tableOfPaymentsAndCharges = {
            val table = paymentsAndChargesService.getPaymentsAndCharges(srn, filteredPayments, journeyType, paymentOrChargeType)
            if (journeyType == Upcoming) removePaymentStatusColumn(table) else table
          }
          Future.successful(Ok(paymentsAndChargesView(
            title,
            tableOfPaymentsAndCharges,
            config.schemeDashboardUrl(request).format(srn),
            paymentsCache.schemeDetails.schemeName
          )))
        } else {
          logger.warn(s"No Scheme Payments and Charges returned for the selected period $period")
          Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad))
        }

      }
    }

  val isTaxYearFormat: PaymentOrChargeType => Boolean = ct => ct == InterestOnExcessRelief || ct == ExcessReliefPaidCharges

  private def getTitleAndFilteredPayments(payments: Seq[SchemeFSDetail], period: String,
                                          paymentOrChargeType: PaymentOrChargeType, journeyType: ChargeDetailsFilter)
                                         (implicit messages: Messages): (String, Seq[SchemeFSDetail]) =
    if (paymentOrChargeType == AccountingForTaxCharges) {

      val startDate: LocalDate = LocalDate.parse(period)
      (messages(s"paymentsAndCharges.$journeyType.aft.title",
        startDate.format(dateFormatterStartDate),
        Quarters.getQuarter(startDate).endDate.format(dateFormatterDMY)),
        payments.filter(p => getPaymentOrChargeType(p.chargeType) == AccountingForTaxCharges)
          .filter(_.periodStartDate.contains(startDate)))

    } else {

      val typeParam: String = messages(s"paymentOrChargeType.${paymentOrChargeType.toString}")
      val messageParam: String = if (journeyType == All) typeParam else typeParam.toLowerCase
      val filteredPayments = payments.filter(p => getPaymentOrChargeType(p.chargeType) == paymentOrChargeType)
        .filter(_.periodEndDate.exists(_.getYear == period.toInt))

      val title = if (isTaxYearFormat(paymentOrChargeType) && filteredPayments.nonEmpty) {
        messages(s"paymentsAndCharges.$journeyType.excessCharges.title", messageParam,
          DateHelper.formatDateDMY(filteredPayments.head.periodStartDate),
          DateHelper.formatDateDMY(filteredPayments.head.periodEndDate)
        )
      } else {
        messages(s"paymentsAndCharges.$journeyType.nonAft.title", messageParam, period)
      }
      (title, filteredPayments)
    }

  private val removePaymentStatusColumn: Table => Table = table => {
    val header = table.head.map(headCells => headCells.dropRight(1))
    Table(
      caption = table.caption,
      captionClasses = table.captionClasses,
      firstCellIsHeader = table.firstCellIsHeader,
      head = header,
      rows = table.rows.map(p => p.take(p.size - 1)),
      classes = table.classes,
      attributes = table.attributes
    )
  }
}
