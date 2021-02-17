/*
 * Copyright 2021 HM Revenue & Customs
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

import controllers.actions._
import forms.QuartersFormProvider
import models.LocalDateBinder._
import models.financialStatement.SchemeFS
import models.{DisplayHint, DisplayQuarter, PaymentOverdue, Quarter, Quarters}
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

class SelectQuarterUpcomingController @Inject()(
                                                  override val messagesApi: MessagesApi,
                                                  identify: IdentifierAction,
                                                  formProvider: QuartersFormProvider,
                                                  val controllerComponents: MessagesControllerComponents,
                                                  renderer: Renderer,
                                                  service: PaymentsAndChargesService)
                                               (implicit ec: ExecutionContext)
                                                  extends FrontendBaseController
                                                  with I18nSupport
                                                  with NunjucksSupport {

  private def form(quarters: Seq[Quarter], year: String)(implicit messages: Messages): Form[Quarter] =
    formProvider(messages("selectPenaltiesQuarter.error", year), quarters)

  def onPageLoad(srn: String, year: String): Action[AnyContent] = identify.async { implicit request =>
    service.getPaymentsFromCache(request.idOrException, srn).flatMap { paymentsCache =>

      val upcomingPaymentsAndCharges: Seq[SchemeFS] = service.extractUpcomingCharges[SchemeFS](paymentsCache.schemeFS, _.dueDate)
      val quarters: Seq[Quarter] = getQuarters(year, upcomingPaymentsAndCharges)

        if (quarters.nonEmpty) {

          val json = Json.obj(
            "year" -> year,
            "form" -> form(quarters, year),
            "radios" -> Quarters.radios(form(quarters, year), getDisplayQuarters(year, upcomingPaymentsAndCharges),
              Seq("govuk-tag govuk-tag--red govuk-!-display-inline")),
            "submitUrl" -> routes.SelectQuarterController.onSubmit(srn, year).url,
            "year" -> year
          )

          renderer.render(template = "financialStatement/paymentsAndCharges/selectUpcomingQuarter.njk", json).map(Ok(_))
        } else {
          Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad()))
        }

    }
  }

  def onSubmit(srn: String, year: String): Action[AnyContent] = identify.async { implicit request =>
    service.getPaymentsFromCache(request.psaIdOrException.id, srn).flatMap { paymentsCache =>

      val upcomingPaymentsAndCharges: Seq[SchemeFS] = service.extractUpcomingCharges[SchemeFS](paymentsCache.schemeFS, _.dueDate)
      val quarters: Seq[Quarter] = getQuarters(year, upcomingPaymentsAndCharges)
        if (quarters.nonEmpty) {

          form(quarters, year)
            .bindFromRequest()
            .fold(
              formWithErrors => {

                  val json = Json.obj(
                    "year" -> year,
                    "form" -> formWithErrors,
                    "radios" -> Quarters.radios(formWithErrors, getDisplayQuarters(year, upcomingPaymentsAndCharges),
                      Seq("govuk-tag govuk-!-display-inline govuk-tag--red")),
                    "submitUrl" -> routes.SelectQuarterController.onSubmit(srn, year).url,
                    "year" -> year
                  )
                  renderer.render(template = "financialStatement/paymentsAndCharges/selectUpcomingQuarter.njk", json).map(BadRequest(_))

              },
              value =>
                Future.successful(Redirect(routes.PaymentsAndChargesController.onPageLoad(srn, value.startDate)))
            )
        } else {
          Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad()))
        }
    }
  }

  private def getDisplayQuarters(year: String, payments: Seq[SchemeFS]): Seq[DisplayQuarter] = {
    val quartersFound: Seq[LocalDate] = payments.filter(_.periodStartDate.getYear == year.toInt).map(_.periodStartDate).distinct.sortBy(_.getMonth)
    quartersFound.map { startDate =>
      val hint: Option[DisplayHint] =
        if (payments.filter(_.periodStartDate == startDate).exists(service.isPaymentOverdue)) Some(PaymentOverdue) else None

      DisplayQuarter(Quarters.getQuarter(startDate), displayYear = true, None, hint)

    }
  }

  private def getQuarters(year: String, payments: Seq[SchemeFS]): Seq[Quarter] =
    payments.filter(_.periodStartDate.getYear == year.toInt).distinct
      .map(paymentOrCharge => Quarters.getQuarter(paymentOrCharge.periodStartDate))
}
