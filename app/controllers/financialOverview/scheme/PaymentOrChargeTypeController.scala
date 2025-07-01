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
import models.ChargeDetailsFilter.History
import models.financialStatement.PaymentOrChargeType.getPaymentOrChargeType
import models.financialStatement.{DisplayPaymentOrChargeType, PaymentOrChargeType, SchemeFSDetail}
import models.{ChargeDetailsFilter, DisplayHint, PaymentOverdue}
import play.api.data.Form
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import services.financialOverview.scheme.{PaymentsAndChargesService, PaymentsNavigationService}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
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

  def onPageLoad(srn: String, journeyType: ChargeDetailsFilter): Action[AnyContent] = (identify andThen allowAccess(Some(srn))).async { implicit request =>
    service.getPaymentsForJourney(request.idOrException, srn, journeyType, request.isLoggedInAsPsa).flatMap { cache =>

      val (title, radios) = if (journeyType == History) {
        val clearedPayments = cache.schemeFSDetail.filter(_.outstandingAmount <= 0)
        val paymentsOrCharges = getPaymentOrChargeTypes(clearedPayments, journeyType)
        (Messages("financial.overview.historyChargeType.title"), PaymentOrChargeType.radios(form(), paymentsOrCharges,
          Seq("govuk-tag govuk-tag--red govuk-!-display-inline"), areLabelsBold = false))
      } else {
        val paymentsOrCharges = getPaymentOrChargeTypes(cache.schemeFSDetail, journeyType)
        (Messages("paymentOrChargeType.all.title"), PaymentOrChargeType.radios(form(), paymentsOrCharges,
          Seq("govuk-tag govuk-tag--red govuk-!-display-inline"), areLabelsBold = false))
      }

      val loggedInAsPsa: Boolean = request.isLoggedInAsPsa

      Future.successful(Ok(paymentOrChargeTypeView(
          form = form(),
          title = title,
          submitCall = routes.PaymentOrChargeTypeController.onSubmit(srn, journeyType),
          schemeName = cache.schemeDetails.schemeName,
          returnUrl = Option(config.financialOverviewUrl).getOrElse("/financial-overview/%s").format(srn),
          radios = radios,
          journeyType = journeyType,
          returnDashboardUrl = if(loggedInAsPsa) {
            Option(config.managePensionsSchemeSummaryUrl).getOrElse("/pension-scheme-summary/%s").format(srn)
          } else {
            Option(config.managePensionsSchemePspUrl).getOrElse("/%s/dashboard/pension-scheme-details").format(srn)
          }
        )))
      }
    }

  def onSubmit(srn: String, journeyType: ChargeDetailsFilter): Action[AnyContent] = (identify andThen allowAccess(Some(srn))).async { implicit request =>
    service.getPaymentsForJourney(request.idOrException, srn, ChargeDetailsFilter.All, request.isLoggedInAsPsa).flatMap { cache =>
      form().bindFromRequest().fold(
        formWithErrors => {

          val (title, radios) = if (journeyType == History) {
            val clearedPayments = cache.schemeFSDetail.filter(_.outstandingAmount <= 0)
            val paymentsOrCharges = getPaymentOrChargeTypes(clearedPayments, journeyType)
            (Messages("financial.overview.historyChargeType.title"),
              PaymentOrChargeType.radios(formWithErrors, paymentsOrCharges))
          } else {
            val paymentsOrCharges = getPaymentOrChargeTypes(cache.schemeFSDetail, journeyType)
            (Messages("paymentOrChargeType.all.title"),
              PaymentOrChargeType.radios(formWithErrors, paymentsOrCharges))
          }
          val loggedInAsPsa: Boolean = request.isLoggedInAsPsa

          Future.successful(BadRequest(paymentOrChargeTypeView(
            form = formWithErrors,
            title = title,
            submitCall = routes.PaymentOrChargeTypeController.onSubmit(srn, journeyType),
            schemeName = cache.schemeDetails.schemeName,
            returnUrl = Option(config.financialOverviewUrl).getOrElse("/financial-overview/%s").format(srn),
            radios = radios,
            journeyType = journeyType,
            returnDashboardUrl = if(loggedInAsPsa) {
              Option(config.managePensionsSchemeSummaryUrl).getOrElse("/pension-scheme-summary/%s").format(srn)
            } else {
              Option(config.managePensionsSchemePspUrl).getOrElse("/%s/dashboard/pension-scheme-details").format(srn)
            }
          ))
        )
        },
        value => {
          journeyType match {
            case History => navService.navToSelectClearedChargesYear(srn, value)
            case _ => navService.navFromPaymentsTypePage(cache.schemeFSDetail, srn, value)
          }
        }
      )
    }
  }

  private def getPaymentOrChargeTypes(paymentsOrCharges: Seq[SchemeFSDetail], journeyType: ChargeDetailsFilter): Seq[DisplayPaymentOrChargeType] =
    paymentsOrCharges.map(p => getPaymentOrChargeType(p.chargeType)).distinct.sortBy(_.toString).map { category =>
      if (journeyType == History) {
        DisplayPaymentOrChargeType(category, None)
      } else {
        val isOverdue: Boolean = paymentsOrCharges.filter(p => getPaymentOrChargeType(p.chargeType) == category).exists(service.isPaymentOverdue)
        val hint: Option[DisplayHint] = if (isOverdue) Some(PaymentOverdue) else None

        DisplayPaymentOrChargeType(category, hint)
      }
    }
}
