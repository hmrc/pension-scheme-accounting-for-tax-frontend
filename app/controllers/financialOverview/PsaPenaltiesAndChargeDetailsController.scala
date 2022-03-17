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
import models.ChargeDetailsFilter
import models.financialStatement.PsaFSChargeType.INTEREST_ON_CONTRACT_SETTLEMENT
import models.financialStatement.{PsaFS, PsaFSChargeType}
import models.requests.IdentifierRequest
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.libs.json.{JsObject, Json}
import play.api.mvc._
import renderer.Renderer
import services.SchemeService
import services.financialOverview.PsaPenaltiesAndChargesService
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.{Html, NunjucksSupport}
import utils.DateHelper.dateFormatterDMY

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
        def penaltyOpt: Option[PsaFS] = penaltiesCache.penalties.find(_.chargeReference == chargeRefs(chargeReferenceIndex.toInt))

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

  private val isChargeTypeVowel: PsaFS => Boolean = psaFS => psaFS.chargeType.toString.toLowerCase().charAt(0)  match {
    case 'a' | 'e' | 'i' | 'o' | 'u' => true
    case _ => false
  }

  private def setInsetText(isChargeAssigned: Boolean, psaFS: PsaFS, interestUrl: String) (implicit messages: Messages): Html = {
    (isChargeAssigned, psaFS.dueDate, psaFS.accruedInterestTotal > 0, psaFS.amountDue > 0, isChargeTypeVowel(psaFS))  match {
      case (false, Some(date), true, true, _) =>
        Html(
          s"<h2 class=govuk-heading-s>${messages("paymentsAndCharges.chargeDetails.interestAccruing")}</h2>" +
            s"<p class=govuk-body>${messages("financialPaymentsAndCharges.chargeDetails.amount.not.paid.by.dueDate.line1")}" +
            s" <span class=govuk-!-font-weight-bold>${messages("financialPaymentsAndCharges.chargeDetails.amount.not.paid.by.dueDate.line2",
              psaFS.accruedInterestTotal)}</span>" +
            s" <span>${messages("financialPaymentsAndCharges.chargeDetails.amount.not.paid.by.dueDate.line3", date.format(dateFormatterDMY))}<span>" +
            s"<p class=govuk-body><span><a id='breakdown' class=govuk-link href=$interestUrl>" +
            s" ${messages("paymentsAndCharges.chargeDetails.interest.paid")}</a></span></p>"
        )
      case (true, _, _, _, _) =>
        Html(
          s"<p class=govuk-body>${messages("financialPaymentsAndCharges.interest.chargeReference.text2", psaFS.chargeType.toString.toLowerCase())}</p>" +
            s"<p class=govuk-body><a id='breakdown' class=govuk-link href=$interestUrl>" +
            s"${messages("financialPaymentsAndCharges.interest.chargeReference.linkText")}</a></p>"
        )
      case (true, _, _, _, true) =>
        Html(
          s"<p class=govuk-body>${messages("financialPaymentsAndCharges.interest.chargeReference.text1_vowel", psaFS.chargeType.toString.toLowerCase())}</p>" +
            s"<p class=govuk-body><a id='breakdown' class=govuk-link href=$interestUrl>" +
            s"${messages("financialPaymentsAndCharges.interest.chargeReference.linkText")}</a></p>"
        )
      case (true, _, _, _, false) =>
        Html(
          s"<p class=govuk-body>${messages("financialPaymentsAndCharges.interest.chargeReference.text1_consonant", psaFS.chargeType.toString.toLowerCase())}</p>" +
            s"<p class=govuk-body><a id='breakdown' class=govuk-link href=$interestUrl>" +
            s"${messages("financialPaymentsAndCharges.interest.chargeReference.linkText")}</a></p>"
        )
      case _ =>
        Html("")
    }
  }
  /*private def setInsetText(psaFS: PsaFS, interestUrl: String) (implicit messages: Messages): Html = {

    if(psaFS.chargeType == CONTRACT_SETTLEMENT && psaFS.accruedInterestTotal > 0) {
      Html(
        s"<h2 class=govuk-heading-s>${messages("paymentsAndCharges.chargeDetails.interestAccruing")}</h2>" +
          s"<p class=govuk-body>${messages("financialPaymentsAndCharges.chargeDetails.amount.not.paid.by.dueDate.line1")}" +
          s" <span class=govuk-!-font-weight-bold>${messages("financialPaymentsAndCharges.chargeDetails.amount.not.paid.by.dueDate.line2",
            psaFS.accruedInterestTotal)}</span>" +
          s" <span>${messages("financialPaymentsAndCharges.chargeDetails.amount.not.paid.by.dueDate.line3", formatDateDMY(psaFS.dueDate.get))}<span>" +
          s"<p class=govuk-body><span><a id='breakdown' class=govuk-link href=$interestUrl>" +
          s" ${messages("paymentsAndCharges.chargeDetails.interest.paid")}</a></span></p>"
      )
    } else {
      Html("")
    }
  }*/


  private def commonJson(
                          fs: PsaFS,
                          psaFS: Seq[PsaFS],
                          chargeRefs: Seq[String],
                          chargeReferenceIndex: String,
                          journeyType: ChargeDetailsFilter
                        )(implicit request: IdentifierRequest[AnyContent]): JsObject = {
    val psaFSDetails = psaFS.filter(_.chargeReference == chargeRefs(chargeReferenceIndex.toInt)).head
    val period = psaPenaltiesAndChargesService.setPeriod(fs.chargeType, fs.periodStartDate, fs.periodEndDate)
    val interestUrl = controllers.financialOverview.routes.PsaPaymentsAndChargesInterestController
      .onPageLoad(fs.pstr, chargeReferenceIndex, journeyType).url
    val isInterestAccruing = fs.accruedInterestTotal > 0
    val detailsChargeType = psaFS.filter(_.chargeReference == chargeRefs(chargeReferenceIndex.toInt)).head.chargeType
    val detailsChargeTypeHeading = if (detailsChargeType == PsaFSChargeType.CONTRACT_SETTLEMENT_INTEREST) INTEREST_ON_CONTRACT_SETTLEMENT else detailsChargeType

    val insetText = fs.sourceChargeRefForInterest match {
      case sourceChargeRef =>
        val originalCharge = psaFS.find(_.chargeReference.equals(sourceChargeRef))
        val index = originalCharge.map(_.chargeReference) match {
          case Some(chargeValue) => chargeReferenceIndex.indexOf(chargeValue).toString
          case None => ""
        }
        val interestUrl = controllers.financialOverview.routes.PsaPaymentsAndChargesInterestController
          .onPageLoad(fs.pstr, chargeReferenceIndex, journeyType).url
        setInsetText(true, fs, interestUrl)
      case _ =>
        setInsetText(false, fs, interestUrl)
    }


    Json.obj(
      "heading" ->   detailsChargeTypeHeading.toString,
      "isOverdue" ->        psaPenaltiesAndChargesService.isPaymentOverdue(psaFS.filter(_.chargeReference == chargeRefs(chargeReferenceIndex.toInt)).head),
      "period" ->           period,
      "chargeReference" ->  fs.chargeReference,
      "penaltyAmount" ->    psaFSDetails.totalAmount,
      "htmlInsetText" ->    insetText,
      "returnLinkBasedOnJourney" -> msg"financialPaymentsAndCharges.returnLink.${journeyType.toString}",
      "returnUrl" -> routes.PsaPaymentsAndChargesController.onPageLoad(journeyType).url,
      "isInterestAccruing" -> isInterestAccruing,
      "list" ->             psaPenaltiesAndChargesService.chargeDetailsRows(psaFS.filter(_.chargeReference == chargeRefs(chargeReferenceIndex.toInt)).head)
    )
  }

}
