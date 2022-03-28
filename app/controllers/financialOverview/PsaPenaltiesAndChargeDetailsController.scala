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

package controllers.financialOverview

import controllers.actions._
import controllers.financialOverview.psa.routes.AllPenaltiesAndChargesController
import models.ChargeDetailsFilter
import models.ChargeDetailsFilter.All
import models.financialStatement.PenaltyType.{AccountingForTaxPenalties, getPenaltyType}
import models.financialStatement.PsaFSChargeType.{CONTRACT_SETTLEMENT, CONTRACT_SETTLEMENT_INTEREST, INTEREST_ON_CONTRACT_SETTLEMENT}
import models.financialStatement.{PenaltyType, PsaFSChargeType, PsaFSDetail}
import models.requests.IdentifierRequest
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.libs.json.{JsObject, Json}
import play.api.mvc._
import renderer.Renderer
import services.SchemeService
import services.financialOverview.PsaPenaltiesAndChargesService
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.{Html, NunjucksSupport}
import utils.DateHelper.{dateFormatterDMY, formatDateDMY, formatStartDate}

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class PsaPenaltiesAndChargeDetailsController @Inject()(identify: IdentifierAction,
                                                        allowAccess: AllowAccessActionProviderForIdentifierRequest,
                                                        override val messagesApi: MessagesApi,
                                                        val controllerComponents: MessagesControllerComponents,
                                                        psaPenaltiesAndChargesService: PsaPenaltiesAndChargesService,
                                                        schemeService: SchemeService,
                                                        renderer: Renderer
                                                      )(implicit ec: ExecutionContext)
  extends FrontendBaseController
    with I18nSupport
    with NunjucksSupport {

  def onPageLoad(identifier: String,
                 chargeReferenceIndex: String,
                 journeyType: ChargeDetailsFilter): Action[AnyContent] =
    (identify andThen allowAccess()).async {
    implicit request =>
      psaPenaltiesAndChargesService.getPenaltiesForJourney(request.idOrException, journeyType).flatMap { penaltiesCache =>

        val chargeRefs: Seq[String] = penaltiesCache.penalties.map(_.chargeReference)
        def penaltyOpt: Option[PsaFSDetail] = penaltiesCache.penalties.find(_.chargeReference == chargeRefs(chargeReferenceIndex.toInt))

        if(chargeRefs.length > chargeReferenceIndex.toInt && penaltyOpt.nonEmpty) {
          schemeService.retrieveSchemeDetails(request.idOrException, identifier, "pstr") flatMap {
            schemeDetails =>
              val json = Json.obj(
                "psaName" -> penaltiesCache.psaName,
                "schemeAssociated" -> true,
                "schemeName" -> schemeDetails.schemeName
              ) ++ commonJson(penaltyOpt.head, penaltiesCache.penalties, chargeRefs, chargeReferenceIndex, journeyType)

              renderer.render(template = "financialOverview/psaChargeDetails.njk", json).map(Ok(_))
            }
          } else {
          Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad))
        }
      }
  }

  private def setInsetText(psaFS: PsaFSDetail, interestUrl: String, originalChargeUrl: String)
                           (implicit messages: Messages): Html = {
    if (psaFS.chargeType == CONTRACT_SETTLEMENT && psaFS.accruedInterestTotal > 0) {
      setInsetTextForContractCharge(psaFS, interestUrl, messages)
    } else if (psaFS.chargeType == CONTRACT_SETTLEMENT_INTEREST) {
      setInsetTextForContractInterest(psaFS, originalChargeUrl, messages)
    }
    else {
      Html("")
    }
  }

  private def setInsetTextForContractInterest(psaFS: PsaFSDetail, originalChargeUrl: String, messages: Messages) = {
    Html(
      s"<p class=govuk-body>${messages("psa.financial.overview.interest.late.payment.text", psaFS.chargeType.toString.toLowerCase())}</p>" +
        s"<p class=govuk-body><a id='breakdown' class=govuk-link href=$originalChargeUrl>" +
        s"${messages("financialPaymentsAndCharges.interest.chargeReference.linkText")}</a></p>"
    )
  }

  private def setInsetTextForContractCharge(psaFS: PsaFSDetail, interestUrl: String, messages: Messages) = {
    psaFS.dueDate match {
      case Some(dueDateValue) =>
        Html(
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
        Html("")
    }
  }


  private def commonJson(fs: PsaFSDetail,
                         psaFS: Seq[PsaFSDetail],
                         chargeRefs: Seq[String],
                         chargeReferenceIndex: String,
                         journeyType: ChargeDetailsFilter
                        )(implicit request: IdentifierRequest[AnyContent]): JsObject = {
    val psaFSDetails = psaFS.filter(_.chargeReference == chargeRefs(chargeReferenceIndex.toInt)).head
    val period = psaPenaltiesAndChargesService.setPeriod(fs.chargeType, fs.periodStartDate, fs.periodEndDate)
    val interestUrl = controllers.financialOverview.routes.PsaPaymentsAndChargesInterestController
      .onPageLoad(fs.pstr, chargeReferenceIndex, journeyType).url
    val isInterestAccruing: Boolean = fs.accruedInterestTotal > 0
    val detailsChargeType = psaFS.filter(_.chargeReference == chargeRefs(chargeReferenceIndex.toInt)).head.chargeType
    val detailsChargeTypeHeading = if (detailsChargeType == PsaFSChargeType.CONTRACT_SETTLEMENT_INTEREST) INTEREST_ON_CONTRACT_SETTLEMENT else detailsChargeType
    val penaltyType = getPenaltyType(detailsChargeType)

    val originalChargeUrl = fs.sourceChargeRefForInterest match {
      case Some(sourceChargeRef) =>
        val originalCharge = psaFS.find(_.chargeReference.equals(sourceChargeRef))
        val index = originalCharge.map(_.chargeReference) match {
          case Some(chargeValue) => chargeRefs.indexOf(chargeValue).toString
          case None => ""
        }
        controllers.financialOverview.routes.PsaPenaltiesAndChargeDetailsController
          .onPageLoad(fs.pstr, index, journeyType).url
      case _ => ""
    }

    Json.obj(
      "heading" ->   detailsChargeTypeHeading.toString,
      "isOverdue" ->        psaPenaltiesAndChargesService.isPaymentOverdue(psaFS.filter(_.chargeReference == chargeRefs(chargeReferenceIndex.toInt)).head),
      "period" ->           period,
      "chargeReference" ->  fs.chargeReference,
      "penaltyAmount" ->    psaFSDetails.totalAmount,
      "htmlInsetText" ->    setInsetText(fs, interestUrl, originalChargeUrl),
      "returnUrl" ->        getReturnUrl(fs, fs.pstr, penaltyType, journeyType),//routes.PsaPaymentsAndChargesController.onPageLoad(journeyType).url,
      "isInterestAccruing" -> isInterestAccruing,
      "list" ->             psaPenaltiesAndChargesService.chargeDetailsRows(psaFS.filter(_.chargeReference ==
        chargeRefs(chargeReferenceIndex.toInt)).head, journeyType)
    ) ++ getReturnUrlText(fs, penaltyType, journeyType)
  }

  def getReturnUrl(fs: PsaFSDetail, pstr: String, penaltyType: PenaltyType,
                   journeyType: ChargeDetailsFilter): String = {
    (journeyType, penaltyType) match {
      case (All, AccountingForTaxPenalties) =>
        AllPenaltiesAndChargesController.onPageLoad(fs.periodStartDate.toString, fs.pstr, penaltyType).url
      case (All, _) =>
        AllPenaltiesAndChargesController.onPageLoad(fs.periodStartDate.getYear.toString, pstr, penaltyType).url
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
      case (All, _) =>
        Json.obj("returnLinkBasedOnJourney" -> messages("psa.financial.overview.penalties.all.returnLink", fs.periodStartDate.getYear.toString))
      case _ => Json.obj("returnLinkBasedOnJourney" -> messages("psa.financial.overview.penalties.all.returnLink", journeyType.toString))
    }
  }
}
