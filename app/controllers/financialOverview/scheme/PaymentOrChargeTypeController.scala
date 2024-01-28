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
import connectors.EventReportingConnector
import controllers.actions._
import forms.financialStatement.PaymentOrChargeTypeFormProvider
import models.financialStatement.PaymentOrChargeType.{EventReportingCharges, getPaymentOrChargeType}
import models.financialStatement.{DisplayPaymentOrChargeType, PaymentOrChargeType, SchemeFSDetail}
import models.{ChargeDetailsFilter, DisplayHint, PaymentOverdue}
import play.api.data.Form
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import renderer.Renderer
import services.financialOverview.scheme.{PaymentsAndChargesService, PaymentsNavigationService}
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
                                              eventReportingConnector: EventReportingConnector,
                                              navService: PaymentsNavigationService)
                                             (implicit ec: ExecutionContext) extends FrontendBaseController
  with I18nSupport
  with NunjucksSupport {

  private def form(): Form[PaymentOrChargeType] = formProvider()

  def onPageLoad(srn: String,  pstr: String): Action[AnyContent] = (identify andThen allowAccess()).async { implicit request =>
    service.getPaymentsForJourney(request.idOrException, srn, ChargeDetailsFilter.All).flatMap { cache =>
      eventReportingConnector.getFeatureToggle("event-reporting").flatMap { toggleDetail =>
        val paymentsOrCharges = filterPaymentOrChargeTypesByFeatureToggle(getPaymentOrChargeTypes(cache.schemeFSDetail),toggleDetail.isEnabled)
        val json = Json.obj(
          "titleMessage" -> s"paymentOrChargeType.all.title",
          "form" -> form(),
          "radios" -> PaymentOrChargeType.radios(form(), paymentsOrCharges,
            Seq("govuk-tag govuk-tag--red govuk-!-display-inline"), areLabelsBold = false),
          "schemeName" -> cache.schemeDetails.schemeName,
          "returnUrl" -> config.schemeDashboardUrl(request).format(srn)
        )

        renderer.render(template = "financialOverview/scheme/paymentOrChargeType.njk", json).map(Ok(_))
      }
    }
  }

  def onSubmit(srn: String,  pstr: String): Action[AnyContent] = identify.async { implicit request =>
    service.getPaymentsForJourney(request.idOrException, srn, ChargeDetailsFilter.All).flatMap { cache =>
      form().bindFromRequest().fold(
        formWithErrors => {
          val json = Json.obj(
            "titleMessage" -> s"paymentOrChargeType.all.title",
            "form" -> formWithErrors,
            "radios" -> PaymentOrChargeType.radios(formWithErrors, getPaymentOrChargeTypes(cache.schemeFSDetail)),
            "schemeName" -> cache.schemeDetails.schemeName,
            "returnUrl" -> config.schemeDashboardUrl(request).format(srn)
          )
          renderer.render(template = "financialOverview/scheme/paymentOrChargeType.njk", json).map(BadRequest(_))
        },
        value => navService.navFromPaymentsTypePage(cache.schemeFSDetail, srn, pstr, value)
      )
    }
  }

  private def getPaymentOrChargeTypes(paymentsOrCharges: Seq[SchemeFSDetail]): Seq[DisplayPaymentOrChargeType] =
    paymentsOrCharges.map(p => getPaymentOrChargeType(p.chargeType)).distinct.sortBy(_.toString).map { category =>

      val isOverdue: Boolean = paymentsOrCharges.filter(p => getPaymentOrChargeType(p.chargeType) == category).exists(service.isPaymentOverdue)
      val hint: Option[DisplayHint] = if (isOverdue) Some(PaymentOverdue) else None

      DisplayPaymentOrChargeType(category, hint)
    }

  private def filterPaymentOrChargeTypesByFeatureToggle(seqDisplayPaymentOrChargeType: Seq[DisplayPaymentOrChargeType],
                                                        isEnabled: Boolean): Seq[DisplayPaymentOrChargeType] = {
    if (isEnabled) seqDisplayPaymentOrChargeType else seqDisplayPaymentOrChargeType.filter(category => {category.chargeType != EventReportingCharges})
  }
}
