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
import forms.financialStatement.PaymentOrChargeTypeFormProvider
import models.financialStatement.PaymentOrChargeType.getPaymentOrChargeType
import models.financialStatement.{DisplayPaymentOrChargeType, PaymentOrChargeType, SchemeFS}
import models.{ChargeDetailsFilter, DisplayHint, PaymentOverdue}
import play.api.data.Form
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import renderer.Renderer
import services.paymentsAndCharges.{PaymentsAndChargesService, PaymentsNavigationService}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.NunjucksSupport

import javax.inject.Inject
import scala.concurrent.ExecutionContext

class PaymentOrChargeTypeController @Inject()(override val messagesApi: MessagesApi,
                                              identify: IdentifierAction,
                                              allowAccess: AllowAccessActionProviderForIdentifierRequest,
                                              formProvider: PaymentOrChargeTypeFormProvider,
                                              val controllerComponents: MessagesControllerComponents,
                                              renderer: Renderer,
                                              config: FrontendAppConfig,
                                              service: PaymentsAndChargesService,
                                              navService: PaymentsNavigationService)
                                             (implicit ec: ExecutionContext) extends FrontendBaseController
  with I18nSupport
  with NunjucksSupport {

  private def form(journeyType: ChargeDetailsFilter): Form[PaymentOrChargeType] = formProvider(journeyType)

  def onPageLoad(srn: String, journeyType: ChargeDetailsFilter): Action[AnyContent] = (identify andThen allowAccess()).async { implicit request =>
    service.getPaymentsForJourney(request.idOrException, srn, journeyType).flatMap { cache =>
      val paymentsOrCharges = getPaymentOrChargeTypes(cache.schemeFS)
      val json = Json.obj(
        "titleMessage" -> s"paymentOrChargeType.$journeyType.title",
        "form" -> form(journeyType),
        "radios" -> PaymentOrChargeType.radios(form(journeyType), paymentsOrCharges),
        "schemeName" -> cache.schemeDetails.schemeName,
        "returnUrl" -> config.schemeDashboardUrl(request).format(srn)
      )

      renderer.render(template = "financialStatement/paymentsAndCharges/paymentOrChargeType.njk", json).map(Ok(_))
    }
  }

  def onSubmit(srn: String, journeyType: ChargeDetailsFilter): Action[AnyContent] = identify.async { implicit request =>
    service.getPaymentsForJourney(request.psaIdOrException.id, srn, journeyType).flatMap { cache =>
      form(journeyType).bindFromRequest().fold(
        formWithErrors => {
          val json = Json.obj(
            "titleMessage" -> s"paymentOrChargeType.$journeyType.title",
            "form" -> formWithErrors,
            "radios" -> PaymentOrChargeType.radios(formWithErrors, getPaymentOrChargeTypes(cache.schemeFS)),
            "schemeName" -> cache.schemeDetails.schemeName,
            "returnUrl" -> config.schemeDashboardUrl(request).format(srn)
          )
          renderer.render(template = "financialStatement/paymentsAndCharges/paymentOrChargeType.njk", json).map(BadRequest(_))
        },
        value => navService.navFromPaymentsTypePage(cache.schemeFS, srn, value, journeyType)
      )
    }
  }

  private def getPaymentOrChargeTypes(paymentsOrCharges: Seq[SchemeFS]): Seq[DisplayPaymentOrChargeType] =
    paymentsOrCharges.map(p => getPaymentOrChargeType(p.chargeType)).distinct.sortBy(_.toString).map { category =>

      val isOverdue: Boolean = paymentsOrCharges.filter(p => getPaymentOrChargeType(p.chargeType) == category).exists(service.isPaymentOverdue)
      val hint: Option[DisplayHint] = if (isOverdue) Some(PaymentOverdue) else None

      DisplayPaymentOrChargeType(category, hint)
    }
}
