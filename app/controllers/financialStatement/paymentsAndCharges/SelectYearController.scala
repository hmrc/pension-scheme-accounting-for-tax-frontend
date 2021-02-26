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
import models.{FSYears, Year, DisplayYear, PaymentOverdue}
import play.api.data.Form
import play.api.i18n.{MessagesApi, I18nSupport}
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
                                     allowAccess: AllowAccessActionProviderForIdentifierRequest,
                                     formProvider: YearsFormProvider,
                                     val controllerComponents: MessagesControllerComponents,
                                     renderer: Renderer,
                                     config: FrontendAppConfig,
                                     service: PaymentsAndChargesService)
                                    (implicit ec: ExecutionContext) extends FrontendBaseController
  with I18nSupport
  with NunjucksSupport {

  private def form(implicit config: FrontendAppConfig): Form[Year] = formProvider("selectChargesYear.error")

  def onPageLoad(srn: String): Action[AnyContent] = (identify andThen allowAccess()).async { implicit request =>
    service.getPaymentsFromCache(request.idOrException, srn).flatMap { paymentsCache =>

      val years = getYears(paymentsCache.schemeFS)
      val json = Json.obj(
        "schemeName" -> paymentsCache.schemeDetails.schemeName,
        "form" -> form(config),
        "radios" -> FSYears.radios(form(config), years),
        "returnUrl" -> config.schemeDashboardUrl(request).format(srn)
      )

      renderer.render(template = "financialStatement/paymentsAndCharges/selectYear.njk", json).map(Ok(_))
    }
  }

  def onSubmit(srn: String): Action[AnyContent] = identify.async { implicit request =>
    service.getPaymentsFromCache(request.idOrException, srn).flatMap { paymentsCache =>

      form(config).bindFromRequest().fold(
        formWithErrors => {

          val json = Json.obj(
            "schemeName" -> paymentsCache.schemeDetails.schemeName,
            "form" -> formWithErrors,
            "radios" -> FSYears.radios(formWithErrors, getYears(paymentsCache.schemeFS)),
            "returnUrl" -> config.schemeDashboardUrl(request).format(srn)
          )
          renderer.render(template = "financialStatement/paymentsAndCharges/selectYear.njk", json).map(BadRequest(_))
        },
        value => {
          val quartersSeq = paymentsCache.schemeFS.filter(_.periodStartDate.getYear == value.year).map(_.periodStartDate).distinct
          if (quartersSeq.size == 1) {
            Future.successful(Redirect(routes.PaymentsAndChargesController.onPageLoad(srn, quartersSeq.head.toString)))
          } else {
            Future.successful(Redirect(routes.SelectQuarterController.onPageLoad(srn, value.getYear.toString)))
          }
        }
      )
    }
  }

  def getYears(payments: Seq[SchemeFS]): Seq[DisplayYear] =
    payments.map(_.periodStartDate.getYear).distinct.sorted.reverse.map { year =>
      val hint = if (payments.filter(_.periodStartDate.getYear == year).exists(service.isPaymentOverdue)) Some(PaymentOverdue) else None
      DisplayYear(year, hint)
    }

}
