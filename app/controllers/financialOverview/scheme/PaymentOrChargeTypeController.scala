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
import forms.financialStatement.PaymentOrChargeTypeFormProvider
import models.financialStatement.PaymentOrChargeType.getPaymentOrChargeType
import models.financialStatement.{DisplayPaymentOrChargeType, PaymentOrChargeType, SchemeFSDetail}
import models.{ChargeDetailsFilter, DisplayHint, PaymentOverdue}
import play.api.data.Form
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import services.financialOverview.scheme.{PaymentsAndChargesService, PaymentsNavigationService}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import utils.TwirlMigration
import views.html.financialOverview.scheme.PaymentOrChargeTypeView

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class PaymentOrChargeTypeController @Inject()(override val messagesApi: MessagesApi,
                                              identify: IdentifierAction,
                                              allowAccess: AllowAccessActionProviderForIdentifierRequest,
                                              formProvider: PaymentOrChargeTypeFormProvider,
                                              val controllerComponents: MessagesControllerComponents,
                                              config: FrontendAppConfig,
                                              service: PaymentsAndChargesService,
                                              paymentOrChargeTypeView: PaymentOrChargeTypeView,
                                              navService: PaymentsNavigationService)
                                             (implicit ec: ExecutionContext) extends FrontendBaseController
  with I18nSupport {

  private def form(): Form[PaymentOrChargeType] = formProvider()

  def onPageLoad(srn: String): Action[AnyContent] = (identify andThen allowAccess(Some(srn))).async { implicit request =>
    service.getPaymentsForJourney(request.idOrException, srn, ChargeDetailsFilter.All).flatMap { cache =>
        val paymentsOrCharges = getPaymentOrChargeTypes(cache.schemeFSDetail)

        val messages = request2Messages

        Future.successful(Ok(paymentOrChargeTypeView(
          form = form(),
          titleMessage = messages(s"paymentOrChargeType.all.title"),
          submitCall = routes.PaymentOrChargeTypeController.onSubmit(srn),
          schemeName = cache.schemeDetails.schemeName,
          returnUrl = config.schemeDashboardUrl(request).format(srn),
          radios = TwirlMigration.toTwirlRadiosWithHintText(PaymentOrChargeType.radios(form(), paymentsOrCharges,
            Seq("govuk-tag govuk-tag--red govuk-!-display-inline"), areLabelsBold = false))
        )))
      }
    }

  def onSubmit(srn: String): Action[AnyContent] = (identify andThen allowAccess(Some(srn))).async { implicit request =>
    service.getPaymentsForJourney(request.idOrException, srn, ChargeDetailsFilter.All).flatMap { cache =>
      form().bindFromRequest().fold(
        formWithErrors => {

          Future.successful(BadRequest(paymentOrChargeTypeView(
            form = formWithErrors,
            titleMessage = s"paymentOrChargeType.all.title",
            submitCall = routes.PaymentOrChargeTypeController.onSubmit(srn),
            schemeName = cache.schemeDetails.schemeName,
            returnUrl = config.schemeDashboardUrl(request).format(srn),
            radios = TwirlMigration.toTwirlRadiosWithHintText(PaymentOrChargeType.radios(formWithErrors, getPaymentOrChargeTypes(cache.schemeFSDetail))
              )
            )
          )
        )
        },
        value => navService.navFromPaymentsTypePage(cache.schemeFSDetail, srn, value)
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
