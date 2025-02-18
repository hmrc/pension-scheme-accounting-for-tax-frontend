/*
 * Copyright 2025 HM Revenue & Customs
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

import uk.gov.hmrc.govukfrontend.views.Aliases.HtmlContent
import viewmodels.ChargeDetailsViewModel
import config.FrontendAppConfig
import controllers.actions._
import helpers.FormatHelper.formatCurrencyAmountAsString
import models.ChargeDetailsFilter.All
import models.LocalDateBinder._
import models.financialStatement.PaymentOrChargeType.{AccountingForTaxCharges, getPaymentOrChargeType}
import models.financialStatement.SchemeFSChargeType._
import models.financialStatement.{PaymentOrChargeType, SchemeFSChargeType, SchemeFSDetail, SchemeSourceChargeInfo}
import models.requests.IdentifierRequest
import models.{ChargeDetailsFilter, SchemeDetails, Submission}
import play.api.Logger
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.mvc._
import services.financialOverview.scheme.PaymentsAndChargesService
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import utils.DateHelper.{dateFormatterDMY, formatDateYMD}
import views.html.financialOverview.scheme.{PaymentsAndChargeDetailsView, PaymentsAndChargeDetailsNewView}

import java.time.LocalDate
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class PaymentsAndChargeDetailsController @Inject()(
                                                    override val messagesApi: MessagesApi,
                                                    identify: IdentifierAction,
                                                    allowAccess: AllowAccessActionProviderForIdentifierRequest,
                                                    val controllerComponents: MessagesControllerComponents,
                                                    paymentsAndChargesService: PaymentsAndChargesService,
                                                    config: FrontendAppConfig,
                                                    newView: PaymentsAndChargeDetailsNewView,
                                                    view: PaymentsAndChargeDetailsView
                                                  )(implicit ec: ExecutionContext)
  extends FrontendBaseController
    with I18nSupport {
  private val logger = Logger(classOf[PaymentsAndChargeDetailsController])

  def onPageLoad(srn: String, period: String, index: String,
                 paymentOrChargeType: PaymentOrChargeType, version: Option[Int],
                 submittedDate: Option[String], journeyType: ChargeDetailsFilter): Action[AnyContent] =
    (identify andThen allowAccess(Some(srn))).async {
      implicit request =>
        paymentsAndChargesService.getPaymentsForJourney(request.idOrException, srn, journeyType).flatMap { paymentsCache =>
          val schemeFSDetail: Seq[SchemeFSDetail] = getFilteredPayments(paymentsCache.schemeFSDetail, period, paymentOrChargeType)
          if(config.podsNewFinancialCredits) {
            buildPageV2(schemeFSDetail, period, index, paymentsCache.schemeDetails, srn, paymentOrChargeType, journeyType, submittedDate, version)
          } else {
            buildPage(schemeFSDetail, period, index, paymentsCache.schemeDetails.schemeName, srn, paymentOrChargeType, journeyType, submittedDate, version)
          }
        }
    }

  private val isQuarterApplicable: SchemeFSDetail => Boolean = schemeFSDetail => schemeFSDetail.chargeType match {
    case PSS_AFT_RETURN_INTEREST | PSS_OTC_AFT_RETURN_INTEREST | AFT_MANUAL_ASST_INTEREST | OTC_MANUAL_ASST_INTEREST
    => true
    case _ => false
  }

  private val isChargeTypeVowel: SchemeFSDetail => Boolean = schemeFSDetail => schemeFSDetail.chargeType.toString.toLowerCase().charAt(0) match {
    case 'a' | 'e' | 'i' | 'o' | 'u' => true
    case _ => false
  }

  //scalastyle:off parameter.number
  // scalastyle:off method.length
  private def buildPage(filteredCharges: Seq[SchemeFSDetail],
                        period: String,
                        index: String,
                        schemeName: String,
                        srn: String,
                        paymentOrChargeType: PaymentOrChargeType,
                        journeyType: ChargeDetailsFilter,
                        submittedDate: Option[String],
                        version: Option[Int]
                       )(
                         implicit request: IdentifierRequest[AnyContent]
                       ): Future[Result] = {

    def summaryListData(schemeFSDetail: SchemeFSDetail,
                        interestUrl: String,
                        version: Option[Int],
                        isChargeAssigned: Boolean
                       ): ChargeDetailsViewModel = {

    ChargeDetailsViewModel(
        chargeDetailsList = paymentsAndChargesService.getChargeDetailsForSelectedCharge(schemeFSDetail, journeyType, submittedDate),
        tableHeader = Some(tableHeader(schemeFSDetail)),
        schemeName = schemeName,
        chargeType = version match {
          case Some(value) => schemeFSDetail.chargeType.toString + s" submission $value"
          case _ => schemeFSDetail.chargeType.toString
        },
        versionValue = version match {
          case Some(value) => Some(s" submission $value")
          case _ => None
        },
        isPaymentOverdue = isPaymentOverdue(schemeFSDetail),
        insetText = setInsetText(isChargeAssigned, schemeFSDetail, interestUrl),
        interest = Some(schemeFSDetail.accruedInterestTotal),
        returnLinkBasedOnJourney = paymentsAndChargesService.getReturnLinkBasedOnJourney(journeyType, schemeName),
        returnUrl = paymentsAndChargesService.getReturnUrl(srn, request.psaId, request.pspId, config, journeyType),
        returnHistoryUrl = returnHistoryUrl(srn, period, paymentOrChargeType, version.getOrElse(0)),
        hintText = Some(optHintText(schemeFSDetail))
      )
    }

    val optSchemeFsDetail = filteredCharges.find(_.index == index.toInt)
    val optionSourceChargeInfo = optSchemeFsDetail.flatMap(_.sourceChargeInfo)
    (optSchemeFsDetail, optionSourceChargeInfo) match {
      case (Some(schemeFs), None) =>
        val interestUrl = controllers.financialOverview.scheme.routes.PaymentsAndChargesInterestController.onPageLoad(srn, period, index,
          paymentOrChargeType, version, submittedDate, journeyType).url
        Future.successful(Ok(view(summaryListData(schemeFs, interestUrl, version, isChargeAssigned = false))))
      case (Some(schemeFs), Some(sourceChargeInfo)) =>
        sourceChargePeriod(schemeFs.chargeType, sourceChargeInfo) match {
          case Some(sourceChargePeriod) =>
            val originalAmountUrl = controllers.financialOverview.scheme.routes.PaymentsAndChargeDetailsController.onPageLoad(
              srn = srn,
              period = sourceChargePeriod,
              index = sourceChargeInfo.index.toString,
              paymentsType = paymentOrChargeType,
              version = sourceChargeInfo.version,
              submittedDate = sourceChargeInfo.receiptDate.map(formatDateYMD),
              journeyType = All
            ).url

            Future.successful(Ok(view(summaryListData(schemeFs, originalAmountUrl, version, isChargeAssigned = true))
            ))

          case _ =>
            logger.warn(s"No source charge period found for ${schemeFs.chargeType} and $sourceChargeInfo")
            Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad))
        }

      case _ =>
        logger.warn(s"No scheme FS item found for index $index")
        Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad))
    }
  }

  //scalastyle:off parameter.number
  // scalastyle:off method.length
  private def buildPageV2(filteredCharges: Seq[SchemeFSDetail],
                        period: String,
                        index: String,
                        schemeDetails: SchemeDetails,
                        srn: String,
                        paymentOrChargeType: PaymentOrChargeType,
                        journeyType: ChargeDetailsFilter,
                        submittedDate: Option[String],
                        version: Option[Int]
                       )(
                         implicit request: IdentifierRequest[AnyContent]
                       ): Future[Result] = {

    def summaryListData(schemeFSDetail: SchemeFSDetail,
                        interestUrl: String,
                        version: Option[Int],
                        isChargeAssigned: Boolean
                       ): ChargeDetailsViewModel = {

      ChargeDetailsViewModel(
        chargeDetailsList = paymentsAndChargesService.getChargeDetailsForSelectedChargeV2(schemeFSDetail, schemeDetails),
        schemeName = schemeDetails.schemeName,
        chargeType = version match {
          case Some(value) => schemeFSDetail.chargeType.toString + s" submission $value"
          case _ => schemeFSDetail.chargeType.toString
        },
        versionValue = version match {
          case Some(value) => Some(s" submission $value")
          case _ => None
        },
        isPaymentOverdue = isPaymentOverdue(schemeFSDetail),
        paymentDueDate = Some(paymentDueDate(schemeFSDetail)),
        chargeAmountDetails = Some(paymentsAndChargesService.chargeAmountDetailsRowsV2(schemeFSDetail)),
        paymentDueAmount = Some(paymentDueAmountCharges(schemeFSDetail)),
        insetText = setInsetTextV2(isChargeAssigned, schemeFSDetail, interestUrl),
        interest = Some(schemeFSDetail.accruedInterestTotal),
        returnLinkBasedOnJourney = paymentsAndChargesService.getReturnLinkBasedOnJourney(journeyType, schemeDetails.schemeName),
        returnUrl = paymentsAndChargesService.getReturnUrl(srn, request.psaId, request.pspId, config, journeyType),
        returnHistoryUrl = returnHistoryUrl(srn, period, paymentOrChargeType, version.getOrElse(0)),
        hintText = Some(optHintText(schemeFSDetail))
      )

    }

    val optSchemeFsDetail = filteredCharges.find(_.index == index.toInt)
    val optionSourceChargeInfo = optSchemeFsDetail.flatMap(_.sourceChargeInfo)
    (optSchemeFsDetail, optionSourceChargeInfo) match {
      case (Some(schemeFs), None) =>
        val interestUrl = controllers.financialOverview.scheme.routes.PaymentsAndChargesInterestController.onPageLoad(srn, period, index,
          paymentOrChargeType, version, submittedDate, journeyType).url
        Future.successful(Ok(newView(summaryListData(schemeFs, interestUrl, version, isChargeAssigned = false)))
        )
      case (Some(schemeFs), Some(sourceChargeInfo)) =>
        sourceChargePeriod(schemeFs.chargeType, sourceChargeInfo) match {
          case Some(sourceChargePeriod) =>
            val originalAmountUrl = controllers.financialOverview.scheme.routes.PaymentsAndChargeDetailsController.onPageLoad(
              srn = srn,
              period = sourceChargePeriod,
              index = sourceChargeInfo.index.toString,
              paymentsType = paymentOrChargeType,
              version = sourceChargeInfo.version,
              submittedDate = sourceChargeInfo.receiptDate.map(formatDateYMD),
              journeyType = All
            ).url

            Future.successful(Ok(newView(summaryListData(schemeFs, originalAmountUrl, version, isChargeAssigned = true)))
            )
          case _ =>
            logger.warn(s"No source charge period found for ${schemeFs.chargeType} and $sourceChargeInfo")
            Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad))
        }

      case _ =>
        logger.warn(s"No scheme FS item found for index $index")
        Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad))
    }
  }

  private def paymentDueDate(schemeFSDetail: SchemeFSDetail): String = {
    (schemeFSDetail.dueDate, schemeFSDetail.amountDue > 0) match {
      case (Some(date), true) =>
        date.format(dateFormatterDMY)
      case _ => ""
    }
  }

  private def paymentDueAmountCharges(schemeFSDetail: SchemeFSDetail): String = {
    if (schemeFSDetail.totalAmount > 0) {
      formatCurrencyAmountAsString(schemeFSDetail.amountDue)
    } else {
      "£0.00"
    }
  }

  private def sourceChargePeriod(chargeType: SchemeFSChargeType, sourceChargeInfo: SchemeSourceChargeInfo): Option[String] = {
    val paymentOrChargeType = getPaymentOrChargeType(chargeType)
    (sourceChargeInfo.periodStartDate, sourceChargeInfo.periodEndDate) match {
      case (Some(startDate), Some(endDate)) =>
        Some(
          if (paymentOrChargeType.toString == AccountingForTaxCharges.toString) {
            formatDateYMD(startDate)
          } else {
            endDate.getYear.toString
          }
        )
      case _ =>
        None
    }
  }


  private def setInsetText(isChargeAssigned: Boolean, schemeFSDetail: SchemeFSDetail, interestUrl: String)(implicit messages: Messages): HtmlContent = {
    (isChargeAssigned, schemeFSDetail.dueDate, schemeFSDetail.accruedInterestTotal > 0, schemeFSDetail.amountDue > 0,
      isQuarterApplicable(schemeFSDetail), isChargeTypeVowel(schemeFSDetail)) match {
      case (false, Some(date), true, true, _, _) => // ACT
        HtmlContent(
          s"<h2 class=govuk-heading-s>${messages("paymentsAndCharges.chargeDetails.interestAccruing")}</h2>" +
            s"<p class=govuk-body>${messages("financialPaymentsAndCharges.chargeDetails.amount.not.paid.by.dueDate.line1")}" +
            s" <span class=govuk-!-font-weight-bold>${
              messages("financialPaymentsAndCharges.chargeDetails.amount.not.paid.by.dueDate.line2",
                schemeFSDetail.accruedInterestTotal)
            }</span>" +
            s" <span>${messages("financialPaymentsAndCharges.chargeDetails.amount.not.paid.by.dueDate.line3", date.format(dateFormatterDMY))}<span>" +
            s"<p class=govuk-body><span><a id='breakdown' class=govuk-link href=$interestUrl>" +
            s" ${messages("paymentsAndCharges.chargeDetails.interest.paid")}</a></span></p>"
        )
      case (true, _, _, _, true, _) => // EXP
        HtmlContent(
          s"<p class=govuk-body>${
            messages("financialPaymentsAndCharges.interest.chargeReference.text2",
              schemeFSDetail.chargeType.toString.toLowerCase())
          }</p>" +
            s"<p class=govuk-body><a id='breakdown' class=govuk-link href=$interestUrl>" +
            s"${messages("financialPaymentsAndCharges.interest.chargeReference.linkText")}</a></p>"
        )
      case (true, _, _, _, false, true) =>
        HtmlContent(
          s"<p class=govuk-body>${
            messages("financialPaymentsAndCharges.interest.chargeReference.text1_vowel",
              schemeFSDetail.chargeType.toString.toLowerCase())
          }</p>" +
            s"<p class=govuk-body><a id='breakdown' class=govuk-link href=$interestUrl>" +
            s"${messages("financialPaymentsAndCharges.interest.chargeReference.linkText")}</a></p>"
        )
      case (true, _, _, _, false, false) =>
        HtmlContent(
          s"<p class=govuk-body>${
            messages("financialPaymentsAndCharges.interest.chargeReference.text1_consonant",
              schemeFSDetail.chargeType.toString.toLowerCase())
          }</p>" +
            s"<p class=govuk-body><a id='breakdown' class=govuk-link href=$interestUrl>" +
            s"${messages("financialPaymentsAndCharges.interest.chargeReference.linkText")}</a></p>"
        )
      case _ =>
        HtmlContent("")
    }
  }

  private def setInsetTextV2(isChargeAssigned: Boolean, schemeFSDetail: SchemeFSDetail, interestUrl: String)(implicit messages: Messages): HtmlContent = {
    (isChargeAssigned, schemeFSDetail.dueDate, schemeFSDetail.accruedInterestTotal > 0, schemeFSDetail.amountDue > 0,
      isQuarterApplicable(schemeFSDetail), isChargeTypeVowel(schemeFSDetail)) match {
      case (false, Some(date), true, true, _, _) => // ACT
        HtmlContent(
            s"<p class=govuk-body>${messages("financialPaymentsAndCharges.chargeDetails.amount.not.paid.by.dueDate.line1")}" +
            s" <span class=govuk-!-font-weight-regular>${
              messages("financialPaymentsAndCharges.chargeDetails.amount.not.paid.by.dueDate.line2",
                schemeFSDetail.accruedInterestTotal)
            }</span>" +
            s" <span>${messages("financialPaymentsAndCharges.chargeDetails.amount.not.paid.by.dueDate.line3", date.format(dateFormatterDMY))}<span>" +
            s"<p class=govuk-body><span><a id='breakdown' class=govuk-link href=$interestUrl>" +
            s" ${messages("paymentsAndCharges.chargeDetails.interest.paid")}</a></span></p>"
        )
      case (true, _, _, _, true, _) => // EXP
        HtmlContent(
          s"<p class=govuk-body>${
            messages("financialPaymentsAndCharges.interest.chargeReference.text2",
              schemeFSDetail.chargeType.toString.toLowerCase())
          }</p>" +
            s"<p class=govuk-body><a id='breakdown' class=govuk-link href=$interestUrl>" +
            s"${messages("financialPaymentsAndCharges.interest.chargeReference.linkText")}</a></p>"
        )
      case (true, _, _, _, false, true) =>
        HtmlContent(
          s"<p class=govuk-body>${
            messages("financialPaymentsAndCharges.interest.chargeReference.text1_vowel",
              schemeFSDetail.chargeType.toString.toLowerCase())
          }</p>" +
            s"<p class=govuk-body><a id='breakdown' class=govuk-link href=$interestUrl>" +
            s"${messages("financialPaymentsAndCharges.interest.chargeReference.linkText")}</a></p>"
        )
      case (true, _, _, _, false, false) =>
        HtmlContent(
          s"<p class=govuk-body>${
            messages("financialPaymentsAndCharges.interest.chargeReference.text1_consonant",
              schemeFSDetail.chargeType.toString.toLowerCase())
          }</p>" +
            s"<p class=govuk-body><a id='breakdown' class=govuk-link href=$interestUrl>" +
            s"${messages("financialPaymentsAndCharges.interest.chargeReference.linkText")}</a></p>"
        )
      case _ =>
        HtmlContent("")
    }
  }


  private def optHintText(schemeFSDetail: SchemeFSDetail)(implicit messages: Messages): String =
    if (schemeFSDetail.chargeType == PSS_AFT_RETURN_INTEREST && schemeFSDetail.amountDue == BigDecimal(0.00)) {
      messages("paymentsAndCharges.interest.hint")
    } else {
      ""
    }

  private def returnHistoryUrl(srn: String, period: String, paymentOrChargeType: PaymentOrChargeType, version: Int): String =
    if (paymentOrChargeType == AccountingForTaxCharges) {
      controllers.routes.AFTSummaryController.onPageLoad(srn, LocalDate.parse(period), Submission, version).url
    } else {
      ""
    }

  private def isPaymentOverdue(schemeFSDetail: SchemeFSDetail): Boolean =
    schemeFSDetail.amountDue > 0 && schemeFSDetail.dueDate.exists(_.isBefore(LocalDate.now()))

  private def tableHeader(schemeFSDetail: SchemeFSDetail): String =
    paymentsAndChargesService.setPeriod(
      schemeFSDetail.chargeType,
      schemeFSDetail.periodStartDate,
      schemeFSDetail.periodEndDate
    )

  private def getFilteredPayments(payments: Seq[SchemeFSDetail], period: String, paymentOrChargeType: PaymentOrChargeType): Seq[SchemeFSDetail] =
    if (paymentOrChargeType == AccountingForTaxCharges) {
      val startDate: LocalDate = LocalDate.parse(period)
      payments.filter(p => getPaymentOrChargeType(p.chargeType) == AccountingForTaxCharges).filter(_.periodStartDate.contains(startDate))
    } else {
      payments.filter(p => getPaymentOrChargeType(p.chargeType) == paymentOrChargeType).filter(_.periodEndDate.exists(_.getYear == period.toInt))
    }
}
