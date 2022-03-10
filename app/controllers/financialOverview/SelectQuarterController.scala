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
import forms.QuartersFormProvider
import models.LocalDateBinder._
import models.financialStatement.PaymentOrChargeType.{AccountingForTaxCharges, getPaymentOrChargeType}
import models.financialStatement.SchemeFS
import models.{AFTQuarter, ChargeDetailsFilter, DisplayHint, DisplayQuarter, PaymentOverdue, Quarters}
import play.api.data.Form
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import renderer.Renderer
import services.financialOverview.PaymentsAndChargesService
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.NunjucksSupport

import java.time.LocalDate
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class SelectQuarterController @Inject()(config: FrontendAppConfig,
                                                  override val messagesApi: MessagesApi,
                                                  identify: IdentifierAction,
                                                  allowAccess: AllowAccessActionProviderForIdentifierRequest,
                                                  formProvider: QuartersFormProvider,
                                                  val controllerComponents: MessagesControllerComponents,
                                                  renderer: Renderer,
                                                  service: PaymentsAndChargesService)
                                                  (implicit ec: ExecutionContext)
                                                  extends FrontendBaseController
                                                  with I18nSupport
                                                  with NunjucksSupport {

  private def form(quarters: Seq[AFTQuarter], year: String)
                  (implicit messages: Messages): Form[AFTQuarter] =
    formProvider(messages(s"selectChargesQuarter.all.error", year), quarters)

  def onPageLoad(srn: String,  pstr: String, year: String): Action[AnyContent] = (identify andThen allowAccess()).async { implicit request =>
    service.getPaymentsForJourney(request.idOrException, srn, ChargeDetailsFilter.All).flatMap { paymentsCache =>

      val quarters: Seq[AFTQuarter] = getQuarters(year, paymentsCache.schemeFS)

        if (quarters.nonEmpty) {

          val json = Json.obj(
            "titleMessage" -> s"selectChargesQuarter.all.title",
            "schemeName" -> paymentsCache.schemeDetails.schemeName,
            "year" -> year,
            "form" -> form(quarters, year),
            "radios" -> Quarters.radios(form(quarters, year), getDisplayQuarters(year, paymentsCache.schemeFS),
              Seq("govuk-tag govuk-tag--red govuk-!-display-inline"), areLabelsBold = false),
            "returnUrl" -> config.schemeDashboardUrl(request).format(srn)
          )

          renderer.render(template = "financialOverview/selectQuarter.njk", json).map(Ok(_))
        } else {
          Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad))
        }

    }
  }

  def onSubmit(srn: String,  pstr: String, year: String): Action[AnyContent] = identify.async { implicit request =>
    service.getPaymentsForJourney(request.idOrException, srn, ChargeDetailsFilter.All).flatMap { paymentsCache =>

      val quarters: Seq[AFTQuarter] = getQuarters(year, paymentsCache.schemeFS)
        if (quarters.nonEmpty) {

          form(quarters, year).bindFromRequest().fold(
              formWithErrors => {

                  val json = Json.obj(
                    "titleMessage" -> s"selectChargesQuarter.all.title",
                    "schemeName" -> paymentsCache.schemeDetails.schemeName,
                    "year" -> year,
                    "form" -> formWithErrors,
                    "radios" -> Quarters.radios(formWithErrors, getDisplayQuarters(year, paymentsCache.schemeFS),
                      Seq("govuk-tag govuk-!-display-inline govuk-tag--red"), areLabelsBold = false),
                    "returnUrl" -> config.schemeDashboardUrl(request).format(srn)
                  )
                  renderer.render(template = "financialOverview/selectQuarter.njk", json).map(BadRequest(_))

              },
              value => {
                Future.successful(Redirect(routes.AllPaymentsAndChargesController.onPageLoad(srn, pstr, value.startDate, AccountingForTaxCharges)))
              }
            )
        } else {
          Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad))
        }
    }
  }

  private def getDisplayQuarters(year: String, payments: Seq[SchemeFS]): Seq[DisplayQuarter] = {

    val quartersFound: Seq[LocalDate] = payments
      .filter(p => getPaymentOrChargeType(p.chargeType) == AccountingForTaxCharges)
      .filter(_.periodStartDate.getYear == year.toInt).map(_.periodStartDate).distinct
      .sortBy(_.getMonth)

    quartersFound.map { startDate =>
      val hint: Option[DisplayHint] =
        if (payments.filter(_.periodStartDate == startDate).exists(service.isPaymentOverdue)) Some(PaymentOverdue) else None

      DisplayQuarter(Quarters.getQuarter(startDate), displayYear = false, None, hint)

    }
  }

  private def getQuarters(year: String, payments: Seq[SchemeFS]): Seq[AFTQuarter] =
    payments
      .filter(p => getPaymentOrChargeType(p.chargeType) == AccountingForTaxCharges)
      .filter(_.periodStartDate.getYear == year.toInt)
      .map(paymentOrCharge => Quarters.getQuarter(paymentOrCharge.periodStartDate)).distinct
}
