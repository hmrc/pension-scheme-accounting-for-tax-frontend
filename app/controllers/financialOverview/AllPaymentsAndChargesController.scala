/*
 * Copyright 2022 HM Revenue & Customs
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

package controllers.financialOverview

import config.FrontendAppConfig
import controllers.actions._
import models.financialStatement.PaymentOrChargeType.{AccountingForTaxCharges, ExcessReliefPaidCharges, InterestOnExcessRelief, getPaymentOrChargeType}
import models.financialStatement.{PaymentOrChargeType, SchemeFS}
import models.{ChargeDetailsFilter, Quarters}
import play.api.Logger
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.libs.json.Json
import play.api.mvc._
import renderer.Renderer
import services.financialOverview.AllPaymentsAndChargesService
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.NunjucksSupport
import utils.DateHelper.{dateFormatterDMY, dateFormatterStartDate}
import viewmodels.Table

import java.time.LocalDate
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class AllPaymentsAndChargesController @Inject()(
                                              override val messagesApi: MessagesApi,
                                              identify: IdentifierAction,
                                              allowAccess: AllowAccessActionProviderForIdentifierRequest,
                                              val controllerComponents: MessagesControllerComponents,
                                              config: FrontendAppConfig,
                                              paymentsAndChargesService: AllPaymentsAndChargesService,
                                              renderer: Renderer
                                            )(implicit ec: ExecutionContext)
  extends FrontendBaseController
    with I18nSupport
    with NunjucksSupport {

  private val logger = Logger(classOf[PaymentsAndChargesController])

  def onPageLoad(srn: String, period: String, paymentOrChargeType: PaymentOrChargeType): Action[AnyContent] =
    (identify andThen allowAccess()).async { implicit request =>
      paymentsAndChargesService.getPaymentsForJourney(request.idOrException, srn, ChargeDetailsFilter.All).flatMap { paymentsCache =>

              val (title, filteredPayments): (String, Seq[SchemeFS]) =
                getTitleAndFilteredPayments(paymentsCache.schemeFS, period, paymentOrChargeType)

              if (filteredPayments.nonEmpty) {

                val tableOfPaymentsAndCharges: Table =
                  paymentsAndChargesService.getPaymentsAndCharges(srn, filteredPayments, ChargeDetailsFilter.All, paymentOrChargeType)


                val json = Json.obj(
                  fields =
                    "titleMessage" -> title,
                    "paymentAndChargesTable" -> tableOfPaymentsAndCharges,
                    "schemeName" -> paymentsCache.schemeDetails.schemeName,
                    "returnUrl" -> config.schemeDashboardUrl(request).format(srn)
                )
                renderer.render(template = "financialStatement/paymentsAndCharges/paymentsAndCharges.njk", json).map(Ok(_))

              } else {
                logger.warn(s"No Scheme Payments and Charges returned for the selected period $period")
                Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad))
              }

      }
  }

  val isTaxYearFormat: PaymentOrChargeType => Boolean = ct => ct == InterestOnExcessRelief || ct == ExcessReliefPaidCharges

  private def getTitleAndFilteredPayments(payments: Seq[SchemeFS], period: String, paymentOrChargeType: PaymentOrChargeType)
                                         (implicit messages: Messages): (String, Seq[SchemeFS]) =
    if(paymentOrChargeType == AccountingForTaxCharges) {

      val startDate: LocalDate = LocalDate.parse(period)
      (messages(s"paymentsAndCharges.all.aft.title",
        startDate.format(dateFormatterStartDate),
        Quarters.getQuarter(startDate).endDate.format(dateFormatterDMY)),
      payments.filter(p => getPaymentOrChargeType(p.chargeType) == AccountingForTaxCharges).filter(_.periodStartDate == startDate))

    } else {

      val typeParam: String = messages(s"paymentOrChargeType.${paymentOrChargeType.toString}")
      val filteredPayments = payments.filter(p => getPaymentOrChargeType(p.chargeType) == paymentOrChargeType).filter(_.periodEndDate.getYear == period.toInt)

      val title = if(isTaxYearFormat(paymentOrChargeType) && filteredPayments.nonEmpty) {
        messages(s"paymentsAndCharges.all.excessCharges.title", typeParam,
          filteredPayments.head.periodStartDate.format(dateFormatterDMY),
          filteredPayments.head.periodEndDate.format(dateFormatterDMY)
        )
      } else {
        messages(s"paymentsAndCharges.all.nonAft.title", typeParam, period)
      }
        (title, filteredPayments)
    }

}
