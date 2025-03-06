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
import forms.financialStatement.PaymentOrChargeTypeFormProvider
import models.financialStatement.PaymentOrChargeType.getPaymentOrChargeType
import models.financialStatement.{DisplayPaymentOrChargeType, PaymentOrChargeType, SchemeFSDetail}
import models.{ChargeDetailsFilter, DisplayHint, PaymentOverdue}
import play.api.data.Form
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import services.paymentsAndCharges.{PaymentsAndChargesService, PaymentsNavigationService}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import views.html.financialStatement.paymentsAndCharges.PaymentOrChargeTypeView

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class PaymentOrChargeTypeController @Inject()(override val messagesApi: MessagesApi,
                                              identify: IdentifierAction,
                                              allowAccess: AllowAccessActionProviderForIdentifierRequest,
                                              formProvider: PaymentOrChargeTypeFormProvider,
                                              val controllerComponents: MessagesControllerComponents,
                                              config: FrontendAppConfig,
                                              service: PaymentsAndChargesService,
                                              navService: PaymentsNavigationService,
                                              paymentOrChargeTypeView: PaymentOrChargeTypeView)
                                             (implicit ec: ExecutionContext) extends FrontendBaseController
  with I18nSupport {

  private def form(journeyType: ChargeDetailsFilter): Form[PaymentOrChargeType] = formProvider(journeyType)

  def onPageLoad(srn: String, journeyType: ChargeDetailsFilter): Action[AnyContent] = (identify andThen allowAccess(Some(srn))).async { implicit request =>
    service.getPaymentsForJourney(request.idOrException, srn, journeyType, request.isLoggedInAsPsa).flatMap { cache =>
      val paymentsOrCharges = getPaymentOrChargeTypes(cache.schemeFSDetail)

      Future.successful(Ok(paymentOrChargeTypeView(
        form(journeyType),
        s"paymentOrChargeType.$journeyType.title",
        PaymentOrChargeType.radios(form(journeyType), paymentsOrCharges),
        routes.PaymentOrChargeTypeController.onSubmit(srn, journeyType),
        config.schemeDashboardUrl(request).format(srn),
        cache.schemeDetails.schemeName
      )))
    }
  }

  def onSubmit(srn: String, journeyType: ChargeDetailsFilter): Action[AnyContent] = (identify andThen allowAccess(Some(srn))).async { implicit request =>
    service.getPaymentsForJourney(request.idOrException, srn, journeyType, request.isLoggedInAsPsa).flatMap { cache =>
      form(journeyType).bindFromRequest().fold(
        formWithErrors => {
          Future.successful(BadRequest(paymentOrChargeTypeView(
            formWithErrors,
            s"paymentOrChargeType.$journeyType.title",
            PaymentOrChargeType.radios(formWithErrors, getPaymentOrChargeTypes(cache.schemeFSDetail)),
            routes.PaymentOrChargeTypeController.onSubmit(srn, journeyType),
            config.schemeDashboardUrl(request).format(srn),
            cache.schemeDetails.schemeName
          )))
        },
        value => navService.navFromPaymentsTypePage(cache.schemeFSDetail, srn, value, journeyType)
      )
    }
  }

  private def getPaymentOrChargeTypes(paymentsOrCharges: Seq[SchemeFSDetail]): Seq[DisplayPaymentOrChargeType] =
    paymentsOrCharges.map(p => getPaymentOrChargeType(p.chargeType)).distinct.sortBy(_.toString).map { category =>

      val isOverdue: Boolean = paymentsOrCharges.filter(p => getPaymentOrChargeType(p.chargeType) == category).exists(service.isPaymentOverdue)
      val hint: Option[DisplayHint] = if (isOverdue) Some(PaymentOverdue) else None

      DisplayPaymentOrChargeType(category, hint)
    }
}
