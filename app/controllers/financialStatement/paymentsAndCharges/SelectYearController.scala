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
import forms.YearsFormProvider
import models.financialStatement.SchemeFS
import models.requests.IdentifierRequest
import models.{DisplayYear, FSYears, PaymentOverdue, Year}
import play.api.data.Form
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.Json
import play.api.mvc._
import renderer.Renderer
import services.paymentsAndCharges.PaymentsAndChargesService
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.NunjucksSupport

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class SelectYearController @Inject()(override val messagesApi: MessagesApi,
                                           identify: IdentifierAction,
                                           formProvider: YearsFormProvider,
                                           val controllerComponents: MessagesControllerComponents,
                                           renderer: Renderer,
                                           config: FrontendAppConfig,
                                           service: PaymentsAndChargesService)
                                          (implicit ec: ExecutionContext) extends FrontendBaseController
                                                                      with I18nSupport
                                                                      with NunjucksSupport {

  private def form(errorKey: String)(implicit config: FrontendAppConfig): Form[Year] = formProvider(errorKey)

  def onPageLoad(srn: String): Action[AnyContent] = identify.async { implicit request =>
    renderView(srn)
  }

  def onPageLoadOverDue(srn: String): Action[AnyContent] = identify.async { implicit request =>
    renderView(srn, forOverdueCharges = true)
  }

  def renderView(srn: String, forOverdueCharges: Boolean = false)
                (implicit request: IdentifierRequest[AnyContent]): Future[Result] = {
    service.getPaymentsFromCache(request.idOrException, srn).flatMap { paymentsCache =>

      val (payments, title, errorKey): (Seq[SchemeFS], String, String) =
        if(!forOverdueCharges) {
          (payments, "selectChargesYear.title", "selectChargesYear.error")
        } else {
          (service.getOverdueCharges(paymentsCache.schemeFS), "selectOverdueChargesYear.title", "selectOverdueChargesYear.error")
        }

      val years = getYears(payments)
      val json = Json.obj(
        "title" -> title,
        "schemeName" -> paymentsCache.schemeDetails.schemeName,
        "form" -> form(errorKey)(config),
        "radios" -> FSYears.radios(form(errorKey)(config), years),
        "returnUrl" -> config.schemeDashboardUrl(request).format(srn)
      )

      renderer.render(template = "financialStatement/paymentsAndCharges/selectYear.njk", json).map(Ok(_))
    }
  }

  def onSubmit(srn: String): Action[AnyContent] = identify.async { implicit request =>
    onSubmitGeneric(srn)
  }

  def onSubmitOverdue(srn: String): Action[AnyContent] = identify.async { implicit request =>
    onSubmitGeneric(srn, forOverdueCharges = true)
  }

  def onSubmitGeneric(srn: String, forOverdueCharges: Boolean = false)
                   (implicit request: IdentifierRequest[AnyContent]): Future[Result] =
    service.getPaymentsFromCache(request.idOrException, srn).flatMap { paymentsCache =>

    val (payments, title, errorKey, redirectUrl): (Seq[SchemeFS], String, String, Int => Call) =
      if(!forOverdueCharges) {
        (payments,
          "selectChargesYear.title",
          "selectChargesYear.error",
          (year: Int) => routes.SelectQuarterController.onPageLoad(srn, year.toString))
      } else {
        (service.getOverdueCharges(paymentsCache.schemeFS),
          "selectOverdueChargesYear.title",
          "selectOverdueChargesYear.error",
          (year: Int) => routes.SelectQuarterController.onPageLoadOverdue(srn, year.toString))
      }

    form(errorKey)(config).bindFromRequest().fold(
      formWithErrors => {

          val json = Json.obj(
            "title" -> title,
            "schemeName" -> paymentsCache.schemeDetails.schemeName,
            "form" -> formWithErrors,
            "radios" -> FSYears.radios(formWithErrors, getYears(payments)),
            "returnUrl" -> config.schemeDashboardUrl(request).format(srn)
          )
          renderer.render(template = "financialStatement/paymentsAndCharges/selectYear.njk", json).map(BadRequest(_))
        },
      value => Future.successful(Redirect(redirectUrl(value.getYear)))
    )
  }

  def getYears(payments: Seq[SchemeFS]): Seq[DisplayYear] =
    payments.map(_.periodStartDate.getYear).distinct.sorted.reverse.map { year =>
      val hint = if (payments.filter(_.periodStartDate.getYear == year).exists(service.isPaymentOverdue)) Some(PaymentOverdue) else None
      DisplayYear(year, hint)
    }

}
