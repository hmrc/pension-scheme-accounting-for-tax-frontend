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
import forms.QuartersFormProvider
import models.LocalDateBinder._
import models.financialStatement.PaymentOrChargeType.{AccountingForTaxCharges, getPaymentOrChargeType}
import models.financialStatement.SchemeFSDetail
import models.{AFTQuarter, ChargeDetailsFilter, DisplayHint, DisplayQuarter, PaymentOverdue, Quarters}
import play.api.data.Form
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import services.financialOverview.scheme.PaymentsAndChargesService
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import views.html.financialOverview.scheme.SelectQuarterView

import java.time.LocalDate
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class SelectQuarterController @Inject()(config: FrontendAppConfig,
                                        override val messagesApi: MessagesApi,
                                        identify: IdentifierAction,
                                        allowAccess: AllowAccessActionProviderForIdentifierRequest,
                                        formProvider: QuartersFormProvider,
                                        val controllerComponents: MessagesControllerComponents,
                                        selectQuarterView: SelectQuarterView,
                                        service: PaymentsAndChargesService)
                                       (implicit ec: ExecutionContext)
  extends FrontendBaseController
    with I18nSupport {

  private def form(quarters: Seq[AFTQuarter], year: String)
                  (implicit messages: Messages): Form[AFTQuarter] =
    formProvider(messages(s"selectChargesQuarter.all.error", year), quarters)

  def onPageLoad(srn: String, year: String): Action[AnyContent] = (identify andThen allowAccess(Some(srn))).async { implicit request =>
    service.getPaymentsForJourney(request.idOrException, srn, ChargeDetailsFilter.All).flatMap { paymentsCache =>

      val quarters: Seq[AFTQuarter] = getQuarters(year, paymentsCache.schemeFSDetail)

      if (quarters.nonEmpty) {

        Future.successful(Ok(selectQuarterView(
          form = form(quarters, year),
          submitCall = routes.SelectQuarterController.onSubmit(srn, year),
          schemeName = paymentsCache.schemeDetails.schemeName,
          returnUrl = config.schemeDashboardUrl(request).format(srn),
          radios = Quarters.radios(form(quarters, year), getDisplayQuarters(year, paymentsCache.schemeFSDetail),
            Seq("govuk-tag govuk-tag--red govuk-!-display-inline"), areLabelsBold = false),
          Year = year
        )))
      } else {
        Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad))
      }

    }
  }

  def onSubmit(srn: String, year: String): Action[AnyContent] = (identify andThen allowAccess(Some(srn))).async { implicit request =>
    service.getPaymentsForJourney(request.idOrException, srn, ChargeDetailsFilter.All).flatMap { paymentsCache =>

      val quarters: Seq[AFTQuarter] = getQuarters(year, paymentsCache.schemeFSDetail)
      if (quarters.nonEmpty) {

        form(quarters, year).bindFromRequest().fold(
          formWithErrors => {

            Future.successful(BadRequest(selectQuarterView(
              form = formWithErrors,
              submitCall = routes.SelectQuarterController.onSubmit(srn, year),
              schemeName = paymentsCache.schemeDetails.schemeName,
              returnUrl = config.schemeDashboardUrl(request).format(srn),
              radios = Quarters.radios(formWithErrors, getDisplayQuarters(year, paymentsCache.schemeFSDetail),
                Seq("govuk-tag govuk-!-display-inline govuk-tag--red"), areLabelsBold = false),
              Year = year
            )))
          },
          value => {
            Future.successful(Redirect(routes.AllPaymentsAndChargesController.onPageLoad(srn, value.startDate, AccountingForTaxCharges)))
          }
        )
      } else {
        Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad))
      }
    }
  }

  private def getDisplayQuarters(year: String, payments: Seq[SchemeFSDetail]): Seq[DisplayQuarter] = {
    val quartersFound: Seq[LocalDate] = payments
      .filter(p => getPaymentOrChargeType(p.chargeType) == AccountingForTaxCharges)
      .filter(_.periodStartDate.exists(_.getYear == year.toInt))
      .flatMap(_.periodStartDate.toSeq).distinct
      .sortBy(_.getMonth)

    quartersFound.map { startDate =>
      val hint: Option[DisplayHint] =
        if (payments.filter(_.periodStartDate.contains(startDate)).exists(service.isPaymentOverdue)) Some(PaymentOverdue) else None

      DisplayQuarter(Quarters.getQuarter(startDate), displayYear = false, None, hint)

    }
  }

  private def getQuarters(year: String, payments: Seq[SchemeFSDetail]): Seq[AFTQuarter] =
    payments
      .filter(p => getPaymentOrChargeType(p.chargeType) == AccountingForTaxCharges)
      .filter(_.periodStartDate.exists(_.getYear == year.toInt))
      .flatMap{ paymentOrCharge =>
        paymentOrCharge.periodStartDate.toSeq.map(x => Quarters.getQuarter(x))
      }.distinct


}
