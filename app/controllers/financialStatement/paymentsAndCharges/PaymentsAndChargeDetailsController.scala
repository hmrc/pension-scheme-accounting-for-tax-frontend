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
import helpers.FormatHelper
import models.ChargeDetailsFilter
import models.LocalDateBinder._
import models.financialStatement.PaymentOrChargeType.{AccountingForTaxCharges, getPaymentOrChargeType}
import models.financialStatement.SchemeFSChargeType.{PSS_AFT_RETURN, PSS_AFT_RETURN_INTEREST, PSS_OTC_AFT_RETURN}
import models.financialStatement.{PaymentOrChargeType, SchemeFSDetail}
import models.requests.IdentifierRequest
import play.api.Logger
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.mvc._
import services.paymentsAndCharges.PaymentsAndChargesService
import uk.gov.hmrc.govukfrontend.views.viewmodels.content.HtmlContent
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import utils.DateHelper.{dateFormatterDMY, formatDateDMY, formatStartDate}
import views.html.financialStatement.paymentsAndCharges.PaymentsAndChargeDetailsView

import java.time.LocalDate
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class PaymentsAndChargeDetailsController @Inject()(
                                                    override val messagesApi: MessagesApi,
                                                    identify: IdentifierAction,
                                                    allowAccess: AllowAccessActionProviderForIdentifierRequest,
                                                    val controllerComponents: MessagesControllerComponents,
                                                    config: FrontendAppConfig,
                                                    paymentsAndChargesService: PaymentsAndChargesService,
                                                    paymentsAndChargeDetailsView: PaymentsAndChargeDetailsView
                                                  )(implicit ec: ExecutionContext)
  extends FrontendBaseController
    with I18nSupport {

  private val logger = Logger(classOf[PaymentsAndChargeDetailsController])

  def onPageLoad(srn: String, period: String, index: String,
                 paymentOrChargeType: PaymentOrChargeType, journeyType: ChargeDetailsFilter): Action[AnyContent] =
    (identify andThen allowAccess(Some(srn))).async {
    implicit request =>
      paymentsAndChargesService.getPaymentsForJourney(request.idOrException, srn, journeyType, request.isLoggedInAsPsa).flatMap { paymentsCache =>

        val schemeFSDetail: Seq[SchemeFSDetail] = getFilteredPayments(paymentsCache.schemeFSDetail, period, paymentOrChargeType)
        buildPage(schemeFSDetail, period, index, paymentsCache.schemeDetails.schemeName, srn, paymentOrChargeType, journeyType)
      }
  }

  private def buildPage(
                         filteredCharges: Seq[SchemeFSDetail],
                         period: String,
                         index: String,
                         schemeName: String,
                         srn: String,
                         paymentOrChargeType: PaymentOrChargeType,
                         journeyType: ChargeDetailsFilter
                       )(
                         implicit request: IdentifierRequest[AnyContent]
                       ): Future[Result] = {

    val chargeRefs: Seq[String] = filteredCharges.map(_.chargeReference)
    val interestUrl = controllers.financialStatement.paymentsAndCharges.routes.PaymentsAndChargesInterestController
        .onPageLoad(srn, period, index, paymentOrChargeType, journeyType).url
    if (chargeRefs.size > index.toInt) {
      filteredCharges.find(_.chargeReference == chargeRefs(index.toInt)) match {
        case Some(schemeFs) =>
          val returnUrl = config.schemeDashboardUrl(request.psaId, request.pspId).replace("%s", srn)
          Future.successful(Ok(paymentsAndChargeDetailsView(
            chargeType = schemeFs.chargeType.toString,
            isPaymentOverdue = isPaymentOverdue(schemeFs),
            tableHeader = tableHeader(schemeFs),
            chargeReferenceTextMessage = chargeReferenceTextMessage(schemeFs),
            chargeDetailsList = paymentsAndChargesService.getChargeDetailsForSelectedCharge(schemeFs),
            interest = schemeFs.accruedInterestTotal,
            insetText = getHtmlInsetText(schemeFs, interestUrl),
            hintText = optHintText(schemeFs),
            returnHistoryURL = returnHistoryUrl(srn, period, paymentOrChargeType),
            returnUrl = returnUrl,
            schemeName
          )))
        case _ =>
          logger.warn(
            s"No Payments and Charge details found for the " +
              s"selected charge reference ${chargeRefs(index.toInt)}"
          )
          Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad))
      }
    } else {
      logger.warn(
        s"[paymentsAndCharges.PaymentsAndChargeDetailsController][IndexOutOfBoundsException]:" +
          s"index $period/$index of attempted"
      )
      Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad))
    }
  }

  private def getHtmlInsetText(schemeFSDetail: SchemeFSDetail, interestUrl: String)(implicit messages: Messages): HtmlContent = {
    (schemeFSDetail.dueDate, schemeFSDetail.accruedInterestTotal > 0, schemeFSDetail.amountDue > 0) match {
      case (Some(_), true, true) =>
        HtmlContent(
          s"<h2 class=govuk-heading-s>${messages("paymentsAndCharges.chargeDetails.interestAccruing")}</h2>" +
            s"<p class=govuk-body>${messages("paymentsAndCharges.chargeDetails.amount.not.paid.by.dueDate")}" +
            s" <span><a id='breakdown' class=govuk-link href=$interestUrl>" +
            s"${messages("paymentsAndCharges.chargeDetails.interest.paid")}</a></span></p>"
        )
      case (Some(date), true, false) =>
        HtmlContent(
          s"<h2 class=govuk-heading-s>${messages("paymentsAndCharges.chargeDetails.interestAccrued")}</h2>" +
            s"<p class=govuk-body>${messages("paymentsAndCharges.chargeDetails.amount.paid.after.dueDate", date.format(dateFormatterDMY))}</p>"
        )
      case _ =>
        HtmlContent("")
    }
  }

  private def chargeReferenceTextMessage(schemeFSDetail: SchemeFSDetail)(implicit messages: Messages): String =
    if (schemeFSDetail.totalAmount < 0) {
      messages("paymentsAndCharges.credit.information",
        s"${FormatHelper.formatCurrencyAmountAsString(schemeFSDetail.totalAmount.abs)}")
    } else {
      messages("paymentsAndCharges.chargeDetails.chargeReference", schemeFSDetail.chargeReference)
    }

  private def optHintText(schemeFSDetail: SchemeFSDetail)(implicit messages: Messages): String =
    if (schemeFSDetail.chargeType == PSS_AFT_RETURN_INTEREST && schemeFSDetail.amountDue == BigDecimal(0.00)) {
      messages("paymentsAndCharges.interest.hint")
    } else {
      ""
    }

  private def returnHistoryUrl(srn: String, period: String, paymentOrChargeType: PaymentOrChargeType): String =
    if(paymentOrChargeType == AccountingForTaxCharges) {
      controllers.amend.routes.ReturnHistoryController.onPageLoad(srn, LocalDate.parse(period)).url
    } else {
      ""
    }

  private def isPaymentOverdue(schemeFSDetail: SchemeFSDetail): Boolean =
    (schemeFSDetail.amountDue > 0 && schemeFSDetail.accruedInterestTotal > 0
      && (schemeFSDetail.chargeType == PSS_AFT_RETURN || schemeFSDetail.chargeType == PSS_OTC_AFT_RETURN))

  private def tableHeader(schemeFSDetail: SchemeFSDetail)(implicit messages: Messages): String =
    messages(
      "paymentsAndCharges.caption",
      formatStartDate(schemeFSDetail.periodStartDate),
      formatDateDMY(schemeFSDetail.periodEndDate)
    )

  private def getFilteredPayments(payments: Seq[SchemeFSDetail], period: String, paymentOrChargeType: PaymentOrChargeType): Seq[SchemeFSDetail] =
    if(paymentOrChargeType == AccountingForTaxCharges) {
      val startDate: LocalDate = LocalDate.parse(period)
      payments.filter(p => getPaymentOrChargeType(p.chargeType) == AccountingForTaxCharges).filter(_.periodStartDate.contains(startDate))
    } else {
        payments.filter(p => getPaymentOrChargeType(p.chargeType) == paymentOrChargeType).filter(_.periodEndDate.exists(_.getYear == period.toInt))
    }

}
