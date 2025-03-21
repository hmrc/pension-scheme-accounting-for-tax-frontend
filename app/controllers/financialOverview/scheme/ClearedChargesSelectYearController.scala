/*
 * Copyright 2025 HM Revenue & Customs
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
import controllers.actions.{AllowAccessActionProviderForIdentifierRequest, IdentifierAction}
import forms.YearsFormProvider
import models.financialStatement.PaymentOrChargeType.getPaymentOrChargeType
import models.financialStatement.{PaymentOrChargeType, SchemeFSDetail}
import models.{ChargeDetailsFilter, DisplayYear, Enumerable, FSYears, Year}
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import services.financialOverview.scheme.PaymentsAndChargesService
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import utils.DateHelper
import views.html.financialOverview.scheme.ClearedChargesSelectYearView

import java.time.LocalDate
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class ClearedChargesSelectYearController @Inject()(override val messagesApi: MessagesApi,
                                                   identify: IdentifierAction,
                                                   allowAccess: AllowAccessActionProviderForIdentifierRequest,
                                                   formProvider: YearsFormProvider,
                                                   val controllerComponents: MessagesControllerComponents,
                                                   config: FrontendAppConfig,
                                                   service: PaymentsAndChargesService,
                                                   selectYearView: ClearedChargesSelectYearView
                                                  )(implicit ec: ExecutionContext)
  extends FrontendBaseController
    with I18nSupport {

  def onPageLoad(srn: String, paymentOrChargeType: PaymentOrChargeType): Action[AnyContent] =
    (identify andThen allowAccess(Some(srn))).async { implicit request =>
      service.getPaymentsForJourney(request.idOrException, srn, ChargeDetailsFilter.All, request.isLoggedInAsPsa).flatMap { paymentsCache =>
        val typeParam: String = service.getTypeParam(paymentOrChargeType)
        val clearedPayments = paymentsCache.schemeFSDetail.filter(_.outstandingAmount <= 0)
        val years = getYears(clearedPayments, paymentOrChargeType)
        val loggedInAsPsa: Boolean = request.isLoggedInAsPsa
        implicit val ev: Enumerable[Year] = FSYears.enumerable(years.map(_.year))

        val form = formProvider(Messages(s"selectChargesTaxYear.all.error", typeParam))(implicitly)
        Future.successful(Ok(selectYearView(
          form = form,
          title = Messages("schemeFinancial.clearedPaymentsAndCharges"),
          submitCall = routes.ClearedChargesSelectYearController.onSubmit(srn, paymentOrChargeType),
          schemeName = paymentsCache.schemeDetails.schemeName,
          returnUrl = Option(config.financialOverviewUrl).getOrElse("/financial-overview/%s").format(srn),
          returnDashboardUrl = if(loggedInAsPsa) {
            Option(config.managePensionsSchemeSummaryUrl).getOrElse("/pension-scheme-summary/%s").format(srn)
          } else {
            Option(config.managePensionsSchemePspUrl).getOrElse("/%s/dashboard/pension-scheme-details").format(srn)
          },
          radios = FSYears.radios(form, years, isYearRangeFormat = true)
        )))
      }
    }

  def onSubmit(srn: String, paymentOrChargeType: PaymentOrChargeType): Action[AnyContent] =
    (identify andThen allowAccess(Some(srn))).async { implicit request =>
      service.getPaymentsForJourney(request.idOrException, srn, ChargeDetailsFilter.All, request.isLoggedInAsPsa).flatMap { paymentsCache =>
        val typeParam: String = service.getTypeParam(paymentOrChargeType)
        val clearedPayments = paymentsCache.schemeFSDetail.filter(_.outstandingAmount <= 0)
        val years = getYears(clearedPayments, paymentOrChargeType)
        val loggedInAsPsa: Boolean = request.isLoggedInAsPsa
        implicit val ev: Enumerable[Year] = FSYears.enumerable(years.map(_.year))

        formProvider(Messages(s"selectChargesTaxYear.all.error", typeParam))(implicitly)
          .bindFromRequest()
          .fold(
            formWithErrors => {

              Future.successful(BadRequest(selectYearView(
                form = formWithErrors,
                title = Messages("schemeFinancial.clearedPaymentsAndCharges"),
                submitCall = routes.ClearedChargesSelectYearController.onSubmit(srn, paymentOrChargeType),
                schemeName = paymentsCache.schemeDetails.schemeName,
                returnUrl = Option(config.financialOverviewUrl).getOrElse("/financial-overview/%s").format(srn),
                radios = FSYears.radios(formWithErrors, years, isYearRangeFormat = true),
                returnDashboardUrl = if(loggedInAsPsa) {
                  Option(config.managePensionsSchemeSummaryUrl).getOrElse("/pension-scheme-summary/%s").format(srn)
                } else {
                  Option(config.managePensionsSchemePspUrl).getOrElse("/%s/dashboard/pension-scheme-details").format(srn)
                }
              )))
            },
            value =>
              Future.successful(Redirect(routes.ClearedPaymentsAndChargesController.onPageLoad(srn, value.year.toString, paymentOrChargeType)))
          )
      }
    }

  private def getYears(payments: Seq[SchemeFSDetail], paymentOrChargeType: PaymentOrChargeType): Seq[DisplayYear] = {
    val earliestAllowedYear = LocalDate.now.getYear - 7

    val years = payments.filter(p => getPaymentOrChargeType(p.chargeType) == paymentOrChargeType)
      .filter(_.periodEndDate.nonEmpty)
      .filter(date => DateHelper.getTaxYear(date.periodEndDate.get) >= earliestAllowedYear)
      .map(schemeFsDetails => DateHelper.getTaxYear(schemeFsDetails.periodEndDate.get))
      .distinct
      .sorted
      .reverse

    years.map { year => {
      DisplayYear(year, None)
    }}
  }
}
