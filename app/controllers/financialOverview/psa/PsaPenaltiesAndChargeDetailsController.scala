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

package controllers.financialOverview.psa

import config.FrontendAppConfig
import controllers.actions._
import controllers.financialOverview.psa.routes.AllPenaltiesAndChargesController
import helpers.FormatHelper
import models.ChargeDetailsFilter
import models.ChargeDetailsFilter.All
import models.financialStatement.PenaltyType.{AccountingForTaxPenalties, EventReportingCharges, getPenaltyType}
import models.financialStatement.PsaFSChargeType.{CONTRACT_SETTLEMENT, CONTRACT_SETTLEMENT_INTEREST, INTEREST_ON_CONTRACT_SETTLEMENT}
import models.financialStatement.{PenaltyType, PsaFSChargeType, PsaFSDetail}
import models.requests.IdentifierRequest
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.libs.json.{JsObject, Json}
import play.api.mvc._
import services.SchemeService
import services.financialOverview.psa.PsaPenaltiesAndChargesService
import uk.gov.hmrc.govukfrontend.views.viewmodels.content.HtmlContent
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import utils.DateHelper.{dateFormatterDMY, formatDateDMY, formatStartDate}
import views.html.financialOverview.psa.{PsaChargeDetailsNewView, PsaChargeDetailsView}

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class PsaPenaltiesAndChargeDetailsController @Inject()(identify: IdentifierAction,
                                                       allowAccess: AllowAccessActionProviderForIdentifierRequest,
                                                       override val messagesApi: MessagesApi,
                                                       val controllerComponents: MessagesControllerComponents,
                                                       psaPenaltiesAndChargesService: PsaPenaltiesAndChargesService,
                                                       config: FrontendAppConfig,
                                                       schemeService: SchemeService,
                                                       view: PsaChargeDetailsView,
                                                       newView: PsaChargeDetailsNewView,
                                                      )(implicit ec: ExecutionContext)
  extends FrontendBaseController
    with I18nSupport {

  def onPageLoad(identifier: String,
                 index: String,
                 journeyType: ChargeDetailsFilter): Action[AnyContent] =
    (identify andThen allowAccess()).async {
      implicit request =>
        psaPenaltiesAndChargesService.getPenaltiesForJourney(request.idOrException, journeyType).flatMap { penaltiesCache =>

          val penaltyOpt: Option[PsaFSDetail] = penaltiesCache.penalties.find(_.index.toString == index)

          if(penaltyOpt.nonEmpty) {
            schemeService.retrieveSchemeDetails(request.idOrException, identifier, "pstr") flatMap {
              schemeDetails =>

                val penalty = penaltyOpt.head
                val isOverdue = psaPenaltiesAndChargesService.isPaymentOverdue(penalty)
                val paymentDueAmount = FormatHelper.formatCurrencyAmountAsString(penalty.amountDue)
                val paymentDueDate = psaPenaltiesAndChargesService.getPaymentDueDate(penalty)
                val chargeReference = penalty.chargeReference
                val penaltyAmount = penalty.totalAmount
                val htmlInsetText = setInsetTextNew(penalty, routes.PsaPaymentsAndChargesInterestController.onPageLoad(penalty.pstr, penalty.index.toString, journeyType).url,
                  routes.PsaPenaltiesAndChargeDetailsController.onPageLoad(penalty.pstr, penalty.index.toString, All).url)
                val returnUrl = getReturnUrl(penalty, getPenaltyType(penalty.chargeType), journeyType)
                val isInterestPresent = penalty.accruedInterestTotal > 0 || penalty.chargeType == PsaFSChargeType.CONTRACT_SETTLEMENT_INTEREST
                val chargeHeaderDetails = psaPenaltiesAndChargesService.chargeHeaderDetailsRows(penalty)
                val chargeAmountDetails = psaPenaltiesAndChargesService.chargeAmountDetailsRows(penalty)
                val returnUrlText = getReturnUrlText(penalty, getPenaltyType(penalty.chargeType), journeyType)

                val templateToRender = if (config.podsNewFinancialCredits) {
                  newView(
                    psaName = penaltiesCache.psaName,
                    schemeAssociated = true,
                    schemeName = schemeDetails.schemeName,
                    heading = penalty.chargeType.toString,
                    isOverdue = isOverdue,
                    paymentDueAmount = paymentDueAmount,
                    paymentDueDate = paymentDueDate,
                    chargeReference = chargeReference,
                    penaltyAmount = penaltyAmount,
                    htmlInsetText = HtmlContent(htmlInsetText.value),
                    returnUrl = returnUrl,
                    isInterestPresent = isInterestPresent,
                    chargeHeaderDetails = chargeHeaderDetails,
                    chargeAmountDetails = chargeAmountDetails,
                    returnUrlText = returnUrlText.toString()
                  )
                } else {
                  view(
                    psaName = penaltiesCache.psaName,
                    schemeAssociated = true,
                    schemeName = schemeDetails.schemeName,
                    heading = penalty.chargeType.toString,
                    isOverdue = isOverdue,
                    period = psaPenaltiesAndChargesService.setPeriod(penalty.chargeType, penalty.periodStartDate, penalty.periodEndDate),
                    chargeReference = chargeReference,
                    penaltyAmount = penaltyAmount,
                    htmlInsetText = HtmlContent(htmlInsetText.value),
                    returnUrl = returnUrl,
                    isInterestPresent = isInterestPresent,
                    list = psaPenaltiesAndChargesService.chargeDetailsRows(penalty, journeyType),
                    returnUrlText = returnUrlText.toString()
                  )
                }

                Future.successful(Ok(templateToRender))
            }
          } else {
            Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad))
          }
        }
    }

  private def setInsetText(psaFS: PsaFSDetail, interestUrl: String, originalChargeUrl: String)
                          (implicit messages: Messages): HtmlContent = {
    if (psaFS.chargeType == CONTRACT_SETTLEMENT && psaFS.accruedInterestTotal > 0) {
      setInsetTextForContractCharge(psaFS, interestUrl, messages)
    } else if (psaFS.chargeType == CONTRACT_SETTLEMENT_INTEREST) {
      setInsetTextForContractInterest(psaFS, originalChargeUrl, messages)
    }
    else {
      HtmlContent("")
    }
  }

  private def setInsetTextNew(psaFS: PsaFSDetail, interestUrl: String, originalChargeUrl: String)
                             (implicit messages: Messages): HtmlContent = {
    if (psaFS.chargeType == CONTRACT_SETTLEMENT && psaFS.accruedInterestTotal > 0) {
      setInsetTextForContractChargeNew(psaFS, interestUrl, messages)
    } else if (psaFS.chargeType == CONTRACT_SETTLEMENT_INTEREST) {
      setInsetTextForContractInterest(psaFS, originalChargeUrl, messages)
    }
    else {
      HtmlContent("")
    }
  }

  private def setInsetTextForContractInterest(psaFS: PsaFSDetail, originalChargeUrl: String, messages: Messages) = {
    HtmlContent(
      s"<p class=govuk-body>${messages("psa.financial.overview.interest.late.payment.text", psaFS.chargeType.toString.toLowerCase())}</p>" +
        s"<p class=govuk-body><a id='breakdown' class=govuk-link href=$originalChargeUrl>" +
        s"${messages("financialPaymentsAndCharges.interest.chargeReference.linkText")}</a></p>"
    )
  }

  private def setInsetTextForContractCharge(psaFS: PsaFSDetail, interestUrl: String, messages: Messages) = {
    psaFS.dueDate match {
      case Some(dueDateValue) =>
        HtmlContent(
          s"<h2 class=govuk-heading-s>${messages("paymentsAndCharges.chargeDetails.interestAccruing")}</h2>" +
            s"<p class=govuk-body>${messages("financialPaymentsAndCharges.chargeDetails.amount.not.paid.by.dueDate.line1")}" +
            s" <span class=govuk-!-font-weight-bold>${
              messages("financialPaymentsAndCharges.chargeDetails.amount.not.paid.by.dueDate.line2",
                psaFS.accruedInterestTotal)
            }</span>" +
            s" <span>${messages("financialPaymentsAndCharges.chargeDetails.amount.not.paid.by.dueDate.line3", dueDateValue.format(dateFormatterDMY))}<span>" +
            s"<p class=govuk-body><span><a id='breakdown' class=govuk-link href=$interestUrl>" +
            s" ${messages("paymentsAndCharges.chargeDetails.interest.paid")}</a></span></p>"
        )
      case _ =>
        HtmlContent("")
    }
  }

  private def setInsetTextForContractChargeNew(psaFS: PsaFSDetail, interestUrl: String, messages: Messages) = {
    psaFS.dueDate match {
      case Some(dueDateValue) =>
        HtmlContent(
          s"<p class=govuk-body>${messages("financialPaymentsAndCharges.chargeDetails.amount.not.paid.by.dueDate.line1")}" +
            s" <span class=govuk-!-font-weight-regular>${
              messages("financialPaymentsAndCharges.chargeDetails.amount.not.paid.by.dueDate.line2",
                psaFS.accruedInterestTotal)
            }</span>" +
            s" <span>${messages("financialPaymentsAndCharges.chargeDetails.amount.not.paid.by.dueDate.line3", dueDateValue.format(dateFormatterDMY))}<span>" +
            s"<p class=govuk-body><span><a id='breakdown' class=govuk-link href=$interestUrl>" +
            s" ${messages("paymentsAndCharges.chargeDetails.interest.paid")}</a></span></p>"
        )
      case _ =>
        HtmlContent("")
    }
  }

  def commonJsonNewV2(psaFSDetail: PsaFSDetail,
                              journeyType: ChargeDetailsFilter
                             )(implicit request: IdentifierRequest[AnyContent]): JsObject = {
    val interestUrl = routes.PsaPaymentsAndChargesInterestController.onPageLoad(psaFSDetail.pstr, psaFSDetail.index.toString, journeyType).url
    val isInterestPresent: Boolean = psaFSDetail.accruedInterestTotal > 0 || psaFSDetail.chargeType == PsaFSChargeType.CONTRACT_SETTLEMENT_INTEREST
    val detailsChargeType = psaFSDetail.chargeType
    val detailsChargeTypeHeading = if (detailsChargeType == PsaFSChargeType.CONTRACT_SETTLEMENT_INTEREST) INTEREST_ON_CONTRACT_SETTLEMENT else detailsChargeType
    val penaltyType = getPenaltyType(detailsChargeType)

    val originalChargeUrl = psaFSDetail.psaSourceChargeInfo match {
      case Some(sourceChargeRef) =>
        routes.PsaPenaltiesAndChargeDetailsController.onPageLoad(psaFSDetail.pstr, sourceChargeRef.index.toString, All).url
      case _ => ""
    }

    Json.obj(
      "heading" -> detailsChargeTypeHeading.toString,
      "isOverdue" -> psaPenaltiesAndChargesService.isPaymentOverdue(psaFSDetail),
      "paymentDueAmount" -> FormatHelper.formatCurrencyAmountAsString(psaFSDetail.amountDue),
      "paymentDueDate" -> psaPenaltiesAndChargesService.getPaymentDueDate(psaFSDetail),
      "chargeReference" -> psaFSDetail.chargeReference,
      "penaltyAmount" -> psaFSDetail.totalAmount,
      "htmlInsetText" -> setInsetTextNew(psaFSDetail, interestUrl, originalChargeUrl).toString,
      "returnUrl" -> getReturnUrl(psaFSDetail, penaltyType, journeyType),
      "isInterestPresent" -> isInterestPresent,
      "chargeHeaderDetails" -> psaPenaltiesAndChargesService.chargeHeaderDetailsRows(psaFSDetail),
      "chargeAmountDetails" -> psaPenaltiesAndChargesService.chargeAmountDetailsRows(psaFSDetail)
    ) ++ getReturnUrlText(psaFSDetail, penaltyType, journeyType)
  }

  def commonJson(psaFSDetail: PsaFSDetail,
                         journeyType: ChargeDetailsFilter
                        )(implicit request: IdentifierRequest[AnyContent]): JsObject = {
    val period = psaPenaltiesAndChargesService.setPeriod(psaFSDetail.chargeType, psaFSDetail.periodStartDate, psaFSDetail.periodEndDate)
    val interestUrl = routes.PsaPaymentsAndChargesInterestController.onPageLoad(psaFSDetail.pstr, psaFSDetail.index.toString, journeyType).url
    val isInterestPresent: Boolean = psaFSDetail.accruedInterestTotal > 0 || psaFSDetail.chargeType == PsaFSChargeType.CONTRACT_SETTLEMENT_INTEREST
    val detailsChargeType = psaFSDetail.chargeType
    val detailsChargeTypeHeading = if (detailsChargeType == PsaFSChargeType.CONTRACT_SETTLEMENT_INTEREST) INTEREST_ON_CONTRACT_SETTLEMENT else detailsChargeType
    val penaltyType = getPenaltyType(detailsChargeType)

    val originalChargeUrl = psaFSDetail.psaSourceChargeInfo match {
      case Some(sourceChargeRef) =>
        routes.PsaPenaltiesAndChargeDetailsController.onPageLoad(psaFSDetail.pstr, sourceChargeRef.index.toString, All).url
      case _ => ""
    }

    Json.obj(
      "heading" ->   detailsChargeTypeHeading.toString,
      "isOverdue" ->        psaPenaltiesAndChargesService.isPaymentOverdue(psaFSDetail),
      "period" ->           period,
      "chargeReference" ->  psaFSDetail.chargeReference,
      "penaltyAmount" ->    psaFSDetail.totalAmount,
      "htmlInsetText" ->    setInsetText(psaFSDetail, interestUrl, originalChargeUrl).toString,
      "returnUrl" ->        getReturnUrl(psaFSDetail, penaltyType, journeyType),
      "isInterestPresent" -> isInterestPresent,
      "list" ->             psaPenaltiesAndChargesService.chargeDetailsRows(psaFSDetail, journeyType)
    ) ++ getReturnUrlText(psaFSDetail, penaltyType, journeyType)
  }

  def getReturnUrl(fs: PsaFSDetail, penaltyType: PenaltyType,
                   journeyType: ChargeDetailsFilter): String = {
    (journeyType, penaltyType) match {
      case (All, AccountingForTaxPenalties) =>
        AllPenaltiesAndChargesController.onPageLoad(fs.periodStartDate.toString, fs.pstr, penaltyType).url
      case (All, EventReportingCharges) =>
        AllPenaltiesAndChargesController.onPageLoad(fs.periodEndDate.getYear.toString, fs.pstr, penaltyType).url
      case (All, _) =>
        AllPenaltiesAndChargesController.onPageLoad(fs.periodStartDate.getYear.toString, fs.pstr, penaltyType).url
      case _ => routes.PsaPaymentsAndChargesController.onPageLoad(journeyType).url
    }
  }

  private def getReturnUrlText(fs: PsaFSDetail, penaltyType: PenaltyType, journeyType: ChargeDetailsFilter)
                              (implicit messages: Messages): JsObject = {
    (journeyType, penaltyType) match {
      case (All, AccountingForTaxPenalties) =>
        val startDate = formatStartDate(fs.periodStartDate)
        val endDate = formatDateDMY(fs.periodEndDate)
        Json.obj("returnLinkBasedOnJourney" -> messages("psa.financial.overview.penalties.all.aft.returnLink", startDate, endDate))
      case (All, EventReportingCharges) =>
        Json.obj("returnLinkBasedOnJourney" -> messages("psa.financial.overview.penalties.all.returnLink", fs.periodEndDate.getYear.toString))
      case (All, _) =>
        Json.obj("returnLinkBasedOnJourney" -> messages("psa.financial.overview.penalties.all.returnLink", fs.periodStartDate.getYear.toString))
      case _ => Json.obj("returnLinkBasedOnJourney" -> messages("financialPaymentsAndCharges.returnLink." +s"${journeyType.toString}"))
    }
  }
}
