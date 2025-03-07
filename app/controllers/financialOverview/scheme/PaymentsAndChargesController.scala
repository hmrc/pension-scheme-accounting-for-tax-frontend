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
import models.ChargeDetailsFilter
import models.ChargeDetailsFilter.Upcoming
import models.financialStatement.SchemeFSDetail
import play.api.Logger
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc._
import services.financialOverview.scheme.PaymentsAndChargesService
import uk.gov.hmrc.govukfrontend.views.Aliases.Table
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import views.html.financialOverview.scheme.{PaymentsAndChargesNewView, PaymentsAndChargesView}

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class PaymentsAndChargesController @Inject()(
                                              override val messagesApi: MessagesApi,
                                              identify: IdentifierAction,
                                              allowAccess: AllowAccessActionProviderForIdentifierRequest,
                                              val controllerComponents: MessagesControllerComponents,
                                              config: FrontendAppConfig,
                                              paymentsAndChargesService: PaymentsAndChargesService,
                                              view: PaymentsAndChargesView,
                                              newView: PaymentsAndChargesNewView
                                            )(implicit ec: ExecutionContext)
  extends FrontendBaseController
    with I18nSupport {
  private val logger = Logger(classOf[PaymentsAndChargesController])

  def onPageLoad(srn: String, journeyType: ChargeDetailsFilter): Action[AnyContent] =
    (identify andThen allowAccess(Some(srn))).async {
      implicit request =>
        paymentsAndChargesService.getPaymentsForJourney(request.idOrException, srn, journeyType, request.isLoggedInAsPsa).flatMap {
          paymentsCache =>
            val overdueCharges: Seq[SchemeFSDetail] = paymentsAndChargesService.getOverdueCharges(paymentsCache.schemeFSDetail)
            val interestCharges: Seq[SchemeFSDetail] = paymentsAndChargesService.getInterestCharges(paymentsCache.schemeFSDetail)
            val totalOverdue: BigDecimal = overdueCharges.map(_.amountDue).sum
            val totalInterestAccruing: BigDecimal = interestCharges.map(_.accruedInterestTotal).sum
            val upcomingCharges: Seq[SchemeFSDetail] = paymentsAndChargesService.extractUpcomingCharges(paymentsCache.schemeFSDetail)
            val totalUpcoming: BigDecimal = upcomingCharges.map(_.amountDue).sum

            logger.warn(s"${srn} PaymentsAndChargesController.onPageLoad totalUpcoming: ${totalUpcoming}")

            if (paymentsCache.schemeFSDetail.nonEmpty) {

              val reflectChargeTextMsgKey: String = if (config.podsNewFinancialCredits) {
                s"financialPaymentsAndCharges.$journeyType.reflect.charge.text.new"
              } else {
                s"financialPaymentsAndCharges.$journeyType.reflect.charge.text"
              }

              val table: Table = paymentsAndChargesService.getPaymentsAndCharges(srn, paymentsCache.schemeFSDetail, journeyType, config)
              val tableOfPaymentsAndCharges = if (journeyType == Upcoming) removePaymentStatusColumn(table) else table

              val messages = request2Messages

              val paymentsAndChargesTemplate = if (config.podsNewFinancialCredits) {
                newView(
                  titleMessage = messages(getTitleMessage(journeyType)), journeyType = journeyType,
                  schemeName = paymentsCache.schemeDetails.schemeName,
                  pstr = "",
                  reflectChargeText = messages(reflectChargeTextMsgKey),
                  totalOverdue = s"${FormatHelper.formatCurrencyAmountAsString(totalOverdue)}",
                  totalInterestAccruing = s"${FormatHelper.formatCurrencyAmountAsString(totalInterestAccruing)}",
                  totalUpcoming = s"${FormatHelper.formatCurrencyAmountAsString(totalUpcoming)}",
                  totalDue = s"${FormatHelper.formatCurrencyAmountAsString(totalUpcoming)}",
                  penaltiesTable = tableOfPaymentsAndCharges,
                  paymentAndChargesTable = tableOfPaymentsAndCharges,
                  returnUrl = config.schemeDashboardUrl(request).format(srn)
                )
              } else {
                view(
                  titleMessage = messages(getTitleMessage(journeyType)), journeyType = journeyType,
                  schemeName = paymentsCache.schemeDetails.schemeName,
                  pstr = "",
                  reflectChargeText = messages(reflectChargeTextMsgKey),
                  totalDue = s"${FormatHelper.formatCurrencyAmountAsString(totalUpcoming)}",
                  totalInterestAccruing = s"${FormatHelper.formatCurrencyAmountAsString(totalInterestAccruing)}",
                  totalUpcoming = s"${FormatHelper.formatCurrencyAmountAsString(totalUpcoming)}",
                  penaltiesTable = tableOfPaymentsAndCharges,
                  paymentAndChargesTable = tableOfPaymentsAndCharges,
                  returnUrl = config.schemeDashboardUrl(request).format(srn)
                )
              }

              Future.successful(Ok(paymentsAndChargesTemplate))
            } else {
              logger.warn(s"Empty payments cache for journey type: ${journeyType}")
              Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad))
            }
        }
    }

  private def getTitleMessage(journeyType: ChargeDetailsFilter): String = {
    if (config.podsNewFinancialCredits) {
      s"schemeFinancial.overview.$journeyType.title.v2"
    } else {
      s"schemeFinancial.overview.$journeyType.title"
    }
  }

  private val removePaymentStatusColumn: Table => Table = table => {
    Table(caption = table.caption,
      captionClasses = table.captionClasses,
      firstCellIsHeader = table.firstCellIsHeader,
      head = Some(table.head.getOrElse(Seq()).take(table.head.size - 1)),
      rows = table.rows.map(p => p.take(p.size - 1)),
      classes = table.classes,
      attributes = table.attributes
    )
  }
}
