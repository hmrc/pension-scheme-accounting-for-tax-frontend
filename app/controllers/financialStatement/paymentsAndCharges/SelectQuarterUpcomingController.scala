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

import config.FrontendAppConfig
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

class SelectQuarterUpcomingController @Inject()(config: FrontendAppConfig,
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

  private def form(quarters: Seq[Quarter])(implicit messages: Messages): Form[Quarter] =
    formProvider(messages("selectUpcomingChargesQuarter.error"), quarters)

  def onPageLoad(srn: String): Action[AnyContent] = identify.async { implicit request =>
    service.getPaymentsFromCache(request.idOrException, srn).flatMap { paymentsCache =>

      val upcomingPaymentsAndCharges: Seq[SchemeFS] = service.extractUpcomingCharges(paymentsCache.schemeFS)
      val quarters: Seq[Quarter] = getQuarters(upcomingPaymentsAndCharges)

        if (quarters.nonEmpty) {

          val json = Json.obj(
            "schemeName" -> paymentsCache.schemeDetails.schemeName,
            "form" -> form(quarters),
            "radios" -> Quarters.radios(form(quarters), getDisplayQuarters(upcomingPaymentsAndCharges),
              Seq("govuk-tag govuk-tag--red govuk-!-display-inline")),
            "returnUrl" -> config.schemeDashboardUrl(request).format(srn)
          )

          renderer.render(template = "financialStatement/paymentsAndCharges/selectUpcomingQuarter.njk", json).map(Ok(_))
        } else {
          Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad()))
        }

    }
  }

  def onSubmit(srn: String): Action[AnyContent] = identify.async { implicit request =>
    service.getPaymentsFromCache(request.idOrException, srn).flatMap { paymentsCache =>

      val upcomingPaymentsAndCharges: Seq[SchemeFS] = service.extractUpcomingCharges(paymentsCache.schemeFS)
      val quarters: Seq[Quarter] = getQuarters(upcomingPaymentsAndCharges)
        if (quarters.nonEmpty) {

          form(quarters)
            .bindFromRequest()
            .fold(
              formWithErrors => {

                  val json = Json.obj(
                    "schemeName" -> paymentsCache.schemeDetails.schemeName,
                    "form" -> formWithErrors,
                    "radios" -> Quarters.radios(formWithErrors, getDisplayQuarters(upcomingPaymentsAndCharges),
                      Seq("govuk-tag govuk-!-display-inline govuk-tag--red")),
                    "returnUrl" -> config.schemeDashboardUrl(request).format(srn)
                  )
                  renderer.render(template = "financialStatement/paymentsAndCharges/selectUpcomingQuarter.njk", json).map(BadRequest(_))

              },
              value =>
                Future.successful(Redirect(routes.PaymentsAndChargesUpcomingController.onPageLoad(srn, value.startDate)))
            )
        } else {
          Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad()))
        }
    }
  }

  private def getDisplayQuarters(payments: Seq[SchemeFS]): Seq[DisplayQuarter] = {
    implicit val localDateOrdering: Ordering[LocalDate] = _ compareTo _
    val quartersFound: Seq[LocalDate] = payments.map(_.periodStartDate).distinct.sorted
    quartersFound.map { startDate =>
      val hint: Option[DisplayHint] =
        if (payments.filter(_.periodStartDate == startDate).exists(service.isPaymentOverdue)) Some(PaymentOverdue) else None

      DisplayQuarter(Quarters.getQuarter(startDate), displayYear = true, None, hint)
    }
  }

  private val getQuarters: Seq[SchemeFS] => Seq[Quarter] =
    payments => payments.map(_.periodStartDate).distinct.map(Quarters.getQuarter)
}
