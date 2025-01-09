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
import play.api.mvc._
import services.SchemeService
import services.financialOverview.psa.PsaPenaltiesAndChargesService
import uk.gov.hmrc.govukfrontend.views.Aliases.HtmlContent
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import utils.DateHelper.{dateFormatterDMY, formatDateDMY, formatStartDate}
import viewmodels.{ChargeDetailsNewViewModel, ChargeDetailsViewModel, Table}
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
                                                       newView: PsaChargeDetailsNewView,
                                                       view: PsaChargeDetailsView
                                                      )(implicit ec: ExecutionContext)
  extends FrontendBaseController
    with I18nSupport {

  def onPageLoad(identifier: String,
                 index: String,
                 journeyType: ChargeDetailsFilter): Action[AnyContent] =
    (identify andThen allowAccess()).async {
      implicit request =>
        psaPenaltiesAndChargesService.getPenaltiesForJourney(request.idOrException, journeyType).flatMap {
          penaltiesCache =>

            val penaltyOpt: Option[PsaFSDetail] = penaltiesCache.penalties.find(_.index.toString == index)

            penaltyOpt match {
              case Some(penalty) =>
              schemeService.retrieveSchemeDetails(request.idOrException, identifier, "pstr").flatMap { schemeDetails =>
                if (config.podsNewFinancialCredits) {
                  val viewModel = createChargeDetailsNewViewModel(penalty, journeyType, request)
                  Future.successful(
                    Ok(
                      newView(
                        heading = viewModel.heading,
                        schemeAssociated = viewModel.schemeAssociated,
                        schemeName = schemeDetails.schemeName,
                        isOverdue = viewModel.isOverdue,
                        paymentDueAmount = viewModel.paymentDueAmount,
                        paymentDueDate = viewModel.paymentDueDate,
                        chargeReference = viewModel.chargeReference,
                        penaltyAmount = viewModel.penaltyAmount,
                        insetText = viewModel.insetText,
                        isInterestPresent = viewModel.isInterestPresent,
                        chargeHeaderDetails = viewModel.chargeHeaderDetails,
                        chargeAmountDetails = viewModel.chargeAmountDetails,
                        returnUrl = viewModel.returnUrl,
                        returnUrlText = viewModel.returnUrlText
                      )
                    )
                  )
                } else {
                  val viewModel = createChargeDetailsViewModel(penalty, journeyType, request)
                  Future.successful(
                    Ok(
                      view(
                        heading = viewModel.heading,
                        schemeAssociated = viewModel.schemeAssociated,
                        schemeName = schemeDetails.schemeName,
                        isOverdue = viewModel.isOverdue,
                        period = viewModel.period,
                        chargeReference = viewModel.chargeReference,
                        penaltyAmount = viewModel.penaltyAmount,
                        insetText = viewModel.insetText,
                        isInterestPresent = viewModel.isInterestPresent,
                        list = viewModel.list,
                        returnUrl = viewModel.returnUrl,
                        returnUrlText = viewModel.returnUrlText
                      )
                    )
                  )
                }
              }
            case None =>
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

  private def setInsetTextForContractInterest(psaFS: PsaFSDetail, originalChargeUrl: String, messages: Messages): HtmlContent = {
    HtmlContent(
      s"<p class=govuk-body>${messages("psa.financial.overview.interest.late.payment.text", psaFS.chargeType.toString.toLowerCase())}</p>" +
        s"<p class=govuk-body><a id='breakdown' class=govuk-link href=$originalChargeUrl>" +
        s"${messages("financialPaymentsAndCharges.interest.chargeReference.linkText")}</a></p>"
    )
  }

  private def setInsetTextForContractCharge(psaFS: PsaFSDetail, interestUrl: String, messages: Messages): HtmlContent = {
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

  private def setInsetTextForContractChargeNew(psaFS: PsaFSDetail, interestUrl: String, messages: Messages): HtmlContent = {
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

  private def createChargeDetailsNewViewModel(psaFSDetail: PsaFSDetail,
                              journeyType: ChargeDetailsFilter,
                                              requestHeader: RequestHeader
                             )(implicit request: IdentifierRequest[AnyContent], messages: Messages): ChargeDetailsNewViewModel = {
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

    //case class - separate viewmodel
    ChargeDetailsNewViewModel(
      heading = detailsChargeTypeHeading.toString,
      schemeAssociated = true,
      schemeName = "",
      isOverdue = psaPenaltiesAndChargesService.isPaymentOverdue(psaFSDetail),
      paymentDueAmount = Some(FormatHelper.formatCurrencyAmountAsString(psaFSDetail.amountDue)),
      paymentDueDate = Some(psaPenaltiesAndChargesService.getPaymentDueDate(psaFSDetail)),
      chargeReference = psaFSDetail.chargeReference,
      penaltyAmount = psaFSDetail.totalAmount,
      insetText = setInsetText(psaFSDetail, interestUrl, originalChargeUrl)(messages),
      isInterestPresent = isInterestPresent,
      chargeHeaderDetails = psaPenaltiesAndChargesService.chargeHeaderDetailsRows(psaFSDetail)(messages),
      chargeAmountDetails = psaPenaltiesAndChargesService.chargeAmountDetailsRows(psaFSDetail)(messages),
      returnUrl = getReturnUrl(psaFSDetail, penaltyType, journeyType),
      returnUrlText = getReturnUrlText(psaFSDetail, penaltyType, journeyType)(messages)
    )

  }

  private def createChargeDetailsViewModel(psaFSDetail: PsaFSDetail,
                         journeyType: ChargeDetailsFilter,
                                           requestHeader: RequestHeader
                        )(implicit request: IdentifierRequest[AnyContent], messages: Messages): ChargeDetailsViewModel = {
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

    ChargeDetailsViewModel(
      heading = detailsChargeTypeHeading.toString,
      schemeAssociated = true,
      schemeName = "",
      isOverdue = psaPenaltiesAndChargesService.isPaymentOverdue(psaFSDetail),
      period = period,
      chargeReference = psaFSDetail.chargeReference,
      penaltyAmount = psaFSDetail.totalAmount,
      insetText = setInsetText(psaFSDetail, interestUrl, originalChargeUrl)(messages),
      isInterestPresent = isInterestPresent,
      list = psaPenaltiesAndChargesService.chargeDetailsRows(psaFSDetail, journeyType)(messages),
      returnUrl = getReturnUrl(psaFSDetail, penaltyType, journeyType),
      returnUrlText = getReturnUrlText(psaFSDetail, penaltyType, journeyType)(messages)
    )

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
                              (implicit messages: Messages): String = {
    (journeyType, penaltyType) match {
      case (All, AccountingForTaxPenalties) =>
        val startDate = formatStartDate(fs.periodStartDate)
        val endDate = formatDateDMY(fs.periodEndDate)
          messages("psa.financial.overview.penalties.all.aft.returnLink", startDate, endDate)
      case (All, EventReportingCharges) =>
          messages("psa.financial.overview.penalties.all.returnLink", fs.periodEndDate.getYear.toString)
      case (All, _) =>
          messages("psa.financial.overview.penalties.all.returnLink", fs.periodStartDate.getYear.toString)
      case _ => messages("financialPaymentsAndCharges.returnLink." +s"${journeyType.toString}")
    }
  }
}
