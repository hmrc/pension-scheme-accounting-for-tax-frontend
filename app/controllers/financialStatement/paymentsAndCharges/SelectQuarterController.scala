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
import controllers.financialStatement.paymentsAndCharges.routes.PaymentsAndChargesController
import forms.QuartersFormProvider
import models.LocalDateBinder._
import models.financialStatement.PaymentOrChargeType.{AccountingForTaxCharges, getPaymentOrChargeType}
import models.financialStatement.SchemeFSDetail
import models.{AFTQuarter, ChargeDetailsFilter, DisplayHint, DisplayQuarter, PaymentOverdue, Quarters}
import play.api.data.Form
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import renderer.Renderer
import services.paymentsAndCharges.PaymentsAndChargesService
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

  private def form(quarters: Seq[AFTQuarter], year: String, journeyType: ChargeDetailsFilter)
                  (implicit messages: Messages): Form[AFTQuarter] =
    formProvider(messages(s"selectChargesQuarter.$journeyType.error", year), quarters)

  def onPageLoad(srn: String, year: String, journeyType: ChargeDetailsFilter): Action[AnyContent] = (identify andThen allowAccess()).async { implicit request =>
    service.getPaymentsForJourney(request.idOrException, srn, journeyType).flatMap { paymentsCache =>

      val quarters: Seq[AFTQuarter] = getQuarters(year, paymentsCache.schemeFSDetail)

        if (quarters.nonEmpty) {

          val json = Json.obj(
            "titleMessage" -> s"selectChargesQuarter.$journeyType.title",
            "schemeName" -> paymentsCache.schemeDetails.schemeName,
            "year" -> year,
            "form" -> form(quarters, year, journeyType),
            "radios" -> Quarters.radios(form(quarters, year, journeyType), getDisplayQuarters(year, paymentsCache.schemeFSDetail),
              Seq("govuk-tag govuk-tag--red govuk-!-display-inline"), areLabelsBold = false),
            "returnUrl" -> config.schemeDashboardUrl(request).format(srn)
          )

          renderer.render(template = "financialStatement/paymentsAndCharges/selectQuarter.njk", json).map(Ok(_))
        } else {
          Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad))
        }

    }
  }

  def onSubmit(srn: String, year: String, journeyType: ChargeDetailsFilter): Action[AnyContent] = identify.async { implicit request =>
    service.getPaymentsForJourney(request.idOrException, srn, journeyType).flatMap { paymentsCache =>

      val quarters: Seq[AFTQuarter] = getQuarters(year, paymentsCache.schemeFSDetail)
        if (quarters.nonEmpty) {

          form(quarters, year, journeyType).bindFromRequest().fold(
              formWithErrors => {

                  val json = Json.obj(
                    "titleMessage" -> s"selectChargesQuarter.$journeyType.title",
                    "schemeName" -> paymentsCache.schemeDetails.schemeName,
                    "year" -> year,
                    "form" -> formWithErrors,
                    "radios" -> Quarters.radios(formWithErrors, getDisplayQuarters(year, paymentsCache.schemeFSDetail),
                      Seq("govuk-tag govuk-!-display-inline govuk-tag--red"), areLabelsBold = false),
                    "returnUrl" -> config.schemeDashboardUrl(request).format(srn)
                  )
                  renderer.render(template = "financialStatement/paymentsAndCharges/selectQuarter.njk", json).map(BadRequest(_))

              },
              value => {
                Future.successful(Redirect(PaymentsAndChargesController.onPageLoad(srn, value.startDate, AccountingForTaxCharges, journeyType)))
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
      .filter(_.periodStartDate.nonEmpty)
      .filter(_.periodStartDate.exists(_.getYear == year.toInt)).map(_.periodStartDate.get).distinct
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
      .filter(_.periodStartDate.nonEmpty)
      .filter(_.periodStartDate.exists(_.getYear == year.toInt))
      .map(paymentOrCharge => Quarters.getQuarter(paymentOrCharge.periodStartDate.get)).distinct
}
