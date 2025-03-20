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
import forms.YearsFormProvider
import models.financialStatement.PaymentOrChargeType.{AccountingForTaxCharges, ExcessReliefPaidCharges, InterestOnExcessRelief, getPaymentOrChargeType}
import models.financialStatement.{PaymentOrChargeType, SchemeFSDetail}
import models.{ChargeDetailsFilter, DisplayYear, Enumerable, FSYears, PaymentOverdue, Year}
import play.api.data.Form
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.mvc._
import services.financialOverview.scheme.{PaymentsAndChargesService, PaymentsNavigationService}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import views.html.financialOverview.scheme.SelectYearView

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class SelectYearController @Inject()(override val messagesApi: MessagesApi,
                                     identify: IdentifierAction,
                                     allowAccess: AllowAccessActionProviderForIdentifierRequest,
                                     formProvider: YearsFormProvider,
                                     val controllerComponents: MessagesControllerComponents,
                                     config: FrontendAppConfig,
                                     service: PaymentsAndChargesService,
                                     selectYearView: SelectYearView,
                                     navService: PaymentsNavigationService
                                    )(implicit ec: ExecutionContext)
    extends FrontendBaseController
    with I18nSupport {

  private def form(paymentOrChargeType: PaymentOrChargeType, typeParam: String)(implicit messages: Messages, ev: Enumerable[Year]): Form[Year] = {
    val errorMessage = if (isTaxYearFormat(paymentOrChargeType)) {
      messages(s"selectChargesTaxYear.all.error", typeParam)
    } else {
      messages(s"selectChargesYear.all.error", typeParam)
    }
    formProvider(errorMessage)(implicitly)
  }

  def onPageLoad(srn: String, paymentOrChargeType: PaymentOrChargeType): Action[AnyContent] =
    (identify andThen allowAccess(Some(srn))).async { implicit request =>
      service.getPaymentsForJourney(request.idOrException, srn, ChargeDetailsFilter.All, request.isLoggedInAsPsa).flatMap { paymentsCache =>
        val typeParam: String = service.getTypeParam(paymentOrChargeType)
        val years = getYears(paymentsCache.schemeFSDetail, paymentOrChargeType)
        implicit val ev: Enumerable[Year] = FSYears.enumerable(years.map(_.year))
        val loggedInAsPsa: Boolean = request.isLoggedInAsPsa

        Future.successful(Ok(selectYearView(
          form = form(paymentOrChargeType, typeParam),
          titleMessage = getTitle(typeParam, paymentOrChargeType),
          penaltyType = typeParam,
          submitCall = routes.SelectYearController.onSubmit(srn, paymentOrChargeType),
          schemeName = paymentsCache.schemeDetails.schemeName,
          returnUrl = Option(config.financialOverviewUrl).getOrElse("/financial-overview/%s").format(srn),
          returnDashboardUrl = if(loggedInAsPsa) {
            Option(config.managePensionsSchemeSummaryUrl).getOrElse("/pension-scheme-summary/%s").format(srn)
          } else {
            Option(config.managePensionsSchemePspUrl).getOrElse("/%s/dashboard/pension-scheme-details").format(srn)
          },
          radios = FSYears.radios(form(paymentOrChargeType, typeParam), years, isTaxYearFormat(paymentOrChargeType))
        )))
      }
    }

  def onSubmit(srn: String, paymentOrChargeType: PaymentOrChargeType): Action[AnyContent] =
    (identify andThen allowAccess(Some(srn))).async { implicit request =>
      service.getPaymentsForJourney(request.idOrException, srn, ChargeDetailsFilter.All, request.isLoggedInAsPsa).flatMap { paymentsCache =>
        val typeParam: String = service.getTypeParam(paymentOrChargeType)
        val years = getYears(paymentsCache.schemeFSDetail, paymentOrChargeType)
        implicit val ev: Enumerable[Year] = FSYears.enumerable(years.map(_.year))
        val loggedInAsPsa: Boolean = request.isLoggedInAsPsa

        form(paymentOrChargeType, typeParam)
          .bindFromRequest()
          .fold(
            formWithErrors => {

              Future.successful(BadRequest(selectYearView(
                form = formWithErrors,
                titleMessage = getTitle(typeParam, paymentOrChargeType),
                penaltyType = typeParam,
                submitCall = routes.SelectYearController.onSubmit(srn, paymentOrChargeType),
                schemeName = paymentsCache.schemeDetails.schemeName,
                returnUrl = Option(config.financialOverviewUrl).getOrElse("/financial-overview/%s").format(srn),
                returnDashboardUrl = if(loggedInAsPsa) {
                  Option(config.managePensionsSchemeSummaryUrl).getOrElse("/pension-scheme-summary/%s").format(srn)
                } else {
                  Option(config.managePensionsSchemePspUrl).getOrElse("/%s/dashboard/pension-scheme-details").format(srn)
                },
                radios = FSYears.radios(formWithErrors, years, isTaxYearFormat(paymentOrChargeType))
              )))
            },
          value =>
            if (paymentOrChargeType == AccountingForTaxCharges) {
              navService.navFromAFTYearsPage(paymentsCache.schemeFSDetail, value.year, srn)
            } else {
              Future.successful(Redirect(routes.AllPaymentsAndChargesController.onPageLoad(srn, value.year.toString, paymentOrChargeType)))
          }
        )
    }
  }

  def getTitle(typeParam: String, paymentOrChargeType: PaymentOrChargeType)(implicit messages: Messages): String =
    if (isTaxYearFormat(paymentOrChargeType)) {
      messages(s"selectChargesTaxYear.all.title", typeParam)
    } else {
      messages(s"selectChargesYear.all.title", typeParam)
    }

  def getYears(payments: Seq[SchemeFSDetail], paymentOrChargeType: PaymentOrChargeType): Seq[DisplayYear] =
    payments
      .filter(p => getPaymentOrChargeType(p.chargeType) == paymentOrChargeType)
      .filter(_.periodEndDate.nonEmpty)
      .map(_.periodEndDate.get.getYear)
      .distinct
      .sorted
      .reverse
      .map { year =>
        val hint = if (payments.filter(_.periodEndDate.exists(_.getYear == year)).exists(service.isPaymentOverdue)) Some(PaymentOverdue) else None
        DisplayYear(year, hint)
      }

  val isTaxYearFormat: PaymentOrChargeType => Boolean = ct => ct == InterestOnExcessRelief || ct == ExcessReliefPaidCharges

}
