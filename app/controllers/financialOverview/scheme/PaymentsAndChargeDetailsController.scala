/*
 * Copyright 2022 HM Revenue & Customs
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
import models.LocalDateBinder._
import models.financialStatement.PaymentOrChargeType.{AccountingForTaxCharges, getPaymentOrChargeType}
import models.financialStatement.SchemeFSChargeType._
import models.financialStatement.{PaymentOrChargeType, SchemeFSDetail}
import models.requests.IdentifierRequest
import models.{ChargeDetailsFilter, Submission}
import play.api.Logger
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.libs.json.{JsObject, Json}
import play.api.mvc._
import renderer.Renderer
import services.financialOverview.PaymentsAndChargesService
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.{Html, NunjucksSupport}
import utils.DateHelper.dateFormatterDMY

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
                                                    renderer: Renderer
                                                  )(implicit ec: ExecutionContext)
  extends FrontendBaseController
    with I18nSupport
    with NunjucksSupport {

  private val logger = Logger(classOf[PaymentsAndChargeDetailsController])

  def onPageLoad(srn: String, pstr: String, period: String, index: String,
                 paymentOrChargeType: PaymentOrChargeType, version: Option[Int],
                 submittedDate: Option[String], journeyType: ChargeDetailsFilter): Action[AnyContent] =
    (identify andThen allowAccess()).async {
    implicit request =>
      paymentsAndChargesService.getPaymentsForJourney(request.idOrException, srn, journeyType).flatMap { paymentsCache =>
        val schemeFSDetail: Seq[SchemeFSDetail] = getFilteredPayments(paymentsCache.schemeFSDetail, period, paymentOrChargeType)
        buildPage(schemeFSDetail, period, index, paymentsCache.schemeDetails.schemeName, srn, pstr, paymentOrChargeType, journeyType, submittedDate, version)
      }
  }

  val chargeRefs: Seq[SchemeFSDetail] => Seq[String] = filteredCharges => filteredCharges.map(_.chargeReference)
  val interestChargeRefs: Seq[SchemeFSDetail] => Seq[String] = filteredCharges =>
    filteredCharges.map{
    schemeFSDetail => schemeFSDetail.chargeType  match {
      case PSS_AFT_RETURN_INTEREST | PSS_OTC_AFT_RETURN_INTEREST |  CONTRACT_SETTLEMENT_INTEREST |
           AFT_MANUAL_ASST_INTEREST | OTC_MANUAL_ASST_INTEREST | PSS_CHARGE_INTEREST
      => schemeFSDetail.sourceChargeRefForInterest.getOrElse("")
      case _ => ""
    }
  }

  private val isQuarterApplicable: SchemeFSDetail => Boolean = schemeFSDetail => schemeFSDetail.chargeType  match {
    case PSS_AFT_RETURN_INTEREST | PSS_OTC_AFT_RETURN_INTEREST | AFT_MANUAL_ASST_INTEREST | OTC_MANUAL_ASST_INTEREST
      => true
    case _ => false
  }

  private val isChargeTypeVowel: SchemeFSDetail => Boolean = schemeFSDetail => schemeFSDetail.chargeType.toString.toLowerCase().charAt(0)  match {
    case 'a' | 'e' | 'i' | 'o' | 'u' => true
    case _ => false
  }

  //scalastyle:off parameter.number
  // scalastyle:off method.length
  //scalastyle:off cyclomatic.complexity
  private def buildPage(
                         filteredCharges: Seq[SchemeFSDetail],
                         period: String,
                         index: String,
                         schemeName: String,
                         srn: String,
                         pstr: String,
                         paymentOrChargeType: PaymentOrChargeType,
                         journeyType: ChargeDetailsFilter,
                         submittedDate: Option[String],
                         version: Option[Int]
                       )(
                         implicit request: IdentifierRequest[AnyContent]
                       ): Future[Result] = {
    val interestUrl = routes.PaymentsAndChargesInterestController.onPageLoad(srn, pstr, period, index,
      paymentOrChargeType, version, submittedDate, journeyType).url
    if (chargeRefs(filteredCharges).size > index.toInt) {
      val chargeRef = filteredCharges.find(_.chargeReference == chargeRefs(filteredCharges)(index.toInt))
      val interestRef =  filteredCharges.find(_.sourceChargeRefForInterest.contains(interestChargeRefs(filteredCharges)(index.toInt)))
        (chargeRef, interestRef) match {
         case (Some(schemeFs), None) =>
           renderer.render(
             template = "financialOverview/scheme/paymentsAndChargeDetails.njk",
             ctx =
               summaryListData(srn, pstr, period, schemeFs, schemeName, paymentOrChargeType, interestUrl, version, submittedDate, journeyType, false)
           ).map(Ok(_))
         case (_, Some(schemeFs)) =>
           schemeFs.sourceChargeRefForInterest match {
             case Some(sourceChargeRef) =>
               val originalCharge = filteredCharges.find(_.chargeReference.equals(sourceChargeRef))
               val seqChargeRefs = paymentsAndChargesService.chargeRefs(filteredCharges)
                 .find{x => originalCharge.map (charge => getPaymentOrChargeType(charge.chargeType).toString).contains(x._1._1)
                   x._1._2.equals(period)}  match {
                 case Some(found) =>
                   found._2
                 case _ => Nil
               }
               val index = originalCharge.map(_.chargeReference) match {
                 case Some(chargeValue) => seqChargeRefs.indexOf(chargeValue).toString
                 case None => ""
               }
               val originalAmountUrl = routes.PaymentsAndChargeDetailsController.onPageLoad(srn, pstr, period, index,
                 paymentOrChargeType, version, submittedDate, journeyType).url
               renderer.render(
                 template = "financialOverview/scheme/paymentsAndChargeDetails.njk",
                 ctx =
                   summaryListData(srn, pstr, period, schemeFs, schemeName, paymentOrChargeType, originalAmountUrl,
                     version, submittedDate, journeyType, true)
               ).map(Ok(_))
             case _ => logger.warn(
               s"No Payments and Charge details found for the " +
                 s"selected charge reference ${chargeRefs(filteredCharges)(index.toInt)}"
             )
               Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad))
           }
        case _ =>
          logger.warn(
            s"No sourceChargeRefForInterest found for the " +
              s"selected charge reference ${chargeRefs(filteredCharges)(index.toInt)}"
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

  private def setInsetText(isChargeAssigned: Boolean, schemeFSDetail: SchemeFSDetail, interestUrl: String)(implicit messages: Messages): Html = {
    (isChargeAssigned, schemeFSDetail.dueDate, schemeFSDetail.accruedInterestTotal > 0, schemeFSDetail.amountDue > 0, isQuarterApplicable(schemeFSDetail), isChargeTypeVowel(schemeFSDetail))  match {
      case (false, Some(date), true, true, _, _) =>
            Html(
              s"<h2 class=govuk-heading-s>${messages("paymentsAndCharges.chargeDetails.interestAccruing")}</h2>" +
                s"<p class=govuk-body>${messages("financialPaymentsAndCharges.chargeDetails.amount.not.paid.by.dueDate.line1")}" +
                s" <span class=govuk-!-font-weight-bold>${messages("financialPaymentsAndCharges.chargeDetails.amount.not.paid.by.dueDate.line2",
                  schemeFSDetail.accruedInterestTotal)}</span>" +
                s" <span>${messages("financialPaymentsAndCharges.chargeDetails.amount.not.paid.by.dueDate.line3", date.format(dateFormatterDMY))}<span>" +
                s"<p class=govuk-body><span><a id='breakdown' class=govuk-link href=$interestUrl>" +
                s" ${messages("paymentsAndCharges.chargeDetails.interest.paid")}</a></span></p>"
            )
      case (true, _, _, _, true, _) =>
        Html(
          s"<p class=govuk-body>${messages("financialPaymentsAndCharges.interest.chargeReference.text2", schemeFSDetail.chargeType.toString.toLowerCase())}</p>" +
            s"<p class=govuk-body><a id='breakdown' class=govuk-link href=$interestUrl>" +
            s"${messages("financialPaymentsAndCharges.interest.chargeReference.linkText")}</a></p>"
        )
      case (true, _, _, _, false, true) =>
        Html(
          s"<p class=govuk-body>${messages("financialPaymentsAndCharges.interest.chargeReference.text1_vowel", schemeFSDetail.chargeType.toString.toLowerCase())}</p>" +
            s"<p class=govuk-body><a id='breakdown' class=govuk-link href=$interestUrl>" +
            s"${messages("financialPaymentsAndCharges.interest.chargeReference.linkText")}</a></p>"
        )
      case (true, _, _, _, false, false) =>
        Html(
          s"<p class=govuk-body>${messages("financialPaymentsAndCharges.interest.chargeReference.text1_consonant", schemeFSDetail.chargeType.toString.toLowerCase())}</p>" +
            s"<p class=govuk-body><a id='breakdown' class=govuk-link href=$interestUrl>" +
            s"${messages("financialPaymentsAndCharges.interest.chargeReference.linkText")}</a></p>"
        )
      case _ =>
        Html("")
    }
  }

  private def summaryListData(srn: String, pstr:String, period: String, schemeFSDetail: SchemeFSDetail, schemeName: String,
                              paymentOrChargeType: PaymentOrChargeType, interestUrl: String, version: Option[Int],
                              submittedDate: Option[String], journeyType: ChargeDetailsFilter, isChargeAssigned: Boolean)
                             (implicit request: IdentifierRequest[AnyContent]): JsObject = {
    Json.obj(
      "chargeDetailsList" -> paymentsAndChargesService.getChargeDetailsForSelectedCharge(schemeFSDetail, journeyType, submittedDate),
      "tableHeader" -> tableHeader(schemeFSDetail),
      "schemeName" -> schemeName,
      "chargeType" ->  (version match {
        case Some(value) => schemeFSDetail.chargeType.toString + s" submission $value"
        case _ => schemeFSDetail.chargeType.toString
      }),
        "versionValue" -> (version match {
        case Some(value) => s" submission $value"
        case _ => Nil
      }),
      "isPaymentOverdue" -> isPaymentOverdue(schemeFSDetail),
      "insetText" -> setInsetText(isChargeAssigned, schemeFSDetail, interestUrl),
      "interest" -> schemeFSDetail.accruedInterestTotal,
      "returnLinkBasedOnJourney" -> paymentsAndChargesService.getReturnLinkBasedOnJourney(journeyType, schemeName),
      "returnUrl" -> paymentsAndChargesService.getReturnUrl(srn, pstr, request.psaId, request.pspId, config, journeyType)
    ) ++ returnHistoryUrl(srn, period, paymentOrChargeType, version.getOrElse(0)) ++ optHintText(schemeFSDetail)
  }

  private def optHintText(schemeFSDetail: SchemeFSDetail)(implicit messages: Messages): JsObject =
    if (schemeFSDetail.chargeType == PSS_AFT_RETURN_INTEREST && schemeFSDetail.amountDue == BigDecimal(0.00)) {
      Json.obj("hintText" -> messages("paymentsAndCharges.interest.hint"))
    } else {
      Json.obj()
    }

  private def returnHistoryUrl(srn: String, period: String, paymentOrChargeType: PaymentOrChargeType, version:Int): JsObject =
    if(paymentOrChargeType == AccountingForTaxCharges) {
      Json.obj("returnHistoryURL" -> controllers.routes.AFTSummaryController.onPageLoad(srn, LocalDate.parse(period), Submission, version).url)
    } else {
      Json.obj()
    }

  private def isPaymentOverdue(schemeFSDetail: SchemeFSDetail): Boolean =
    (schemeFSDetail.amountDue > 0 && schemeFSDetail.accruedInterestTotal > 0
      && (schemeFSDetail.chargeType == PSS_AFT_RETURN || schemeFSDetail.chargeType == PSS_OTC_AFT_RETURN))

  private def tableHeader(schemeFSDetail: SchemeFSDetail): String =
    paymentsAndChargesService.setPeriod(
      schemeFSDetail.chargeType,
      schemeFSDetail.periodStartDate,
      schemeFSDetail.periodEndDate
    )

  private def getFilteredPayments(payments: Seq[SchemeFSDetail], period: String, paymentOrChargeType: PaymentOrChargeType): Seq[SchemeFSDetail] =
    if(paymentOrChargeType == AccountingForTaxCharges) {
      val startDate: LocalDate = LocalDate.parse(period)
      payments.filter(p => getPaymentOrChargeType(p.chargeType) == AccountingForTaxCharges).filter(_.periodStartDate.contains(startDate))
    } else {
        payments.filter(p => getPaymentOrChargeType(p.chargeType) == paymentOrChargeType).filter(_.periodEndDate.exists(_.getYear == period.toInt))
    }

}
