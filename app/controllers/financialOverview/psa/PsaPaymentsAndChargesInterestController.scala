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

import controllers.actions.{AllowAccessActionProviderForIdentifierRequest, IdentifierAction}
import controllers.financialOverview.psa.routes.AllPenaltiesAndChargesController
import models.ChargeDetailsFilter
import models.ChargeDetailsFilter.All
import models.financialStatement.PenaltyType.{AccountingForTaxPenalties, getPenaltyType}
import models.financialStatement.PsaFSChargeType.INTEREST_ON_CONTRACT_SETTLEMENT
import models.financialStatement.{PenaltyType, PsaFSChargeType, PsaFSDetail}
import models.requests.IdentifierRequest
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import renderer.Renderer
import services.SchemeService
import services.financialOverview.psa.PsaPenaltiesAndChargesService
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.{Html, NunjucksSupport}
import utils.DateHelper.{formatDateDMY, formatStartDate}

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class PsaPaymentsAndChargesInterestController @Inject()(identify: IdentifierAction,
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
                 index: String,
                 journeyType: ChargeDetailsFilter): Action[AnyContent] =
    (identify andThen allowAccess()).async {
      implicit request =>
        psaPenaltiesAndChargesService.getPenaltiesForJourney(request.idOrException, journeyType).flatMap { penaltiesCache =>

          def penaltyOpt: Option[PsaFSDetail] = penaltiesCache.penalties.find(_.index.toString == index)

          if(penaltyOpt.nonEmpty) {
            schemeService.retrieveSchemeDetails(request.idOrException, identifier, "pstr") flatMap {
              schemeDetails =>
                val json = Json.obj(
                  "psaName" -> penaltiesCache.psaName,
                  "schemeAssociated" -> true,
                  "schemeName" -> schemeDetails.schemeName
                ) ++ commonJson(penaltyOpt.head, journeyType)

                renderer.render(template = "financialOverview/psa/psaInterestDetails.njk", json).map(Ok(_))
            }
          } else {
            Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad))
          }
        }
    }

  private def commonJson(
                          sourcePsaFSDetail: PsaFSDetail,
                          journeyType: ChargeDetailsFilter
                        )(implicit request: IdentifierRequest[AnyContent]): JsObject = {
    val period = psaPenaltiesAndChargesService.setPeriod(sourcePsaFSDetail.chargeType, sourcePsaFSDetail.periodStartDate, sourcePsaFSDetail.periodEndDate)
    val originalAmountURL = routes.PsaPenaltiesAndChargeDetailsController.onPageLoad(sourcePsaFSDetail.pstr, sourcePsaFSDetail.index.toString, journeyType).url
    val detailsChargeType = sourcePsaFSDetail.chargeType
    val detailsChargeTypeHeading = if (detailsChargeType == PsaFSChargeType.CONTRACT_SETTLEMENT) INTEREST_ON_CONTRACT_SETTLEMENT else detailsChargeType
    val penaltyType = getPenaltyType(detailsChargeType)

    val htmlInsetText =
      Html(
        s"<p class=govuk-body>${Messages("psa.financial.overview.hint1")}" +
          s" <span><a id='breakdown' class=govuk-link href=$originalAmountURL>" +
          s" ${Messages("psa.financial.overview.hintLink")}</a></span>" +
          s" ${Messages("psa.financial.overview.hint2")}</p>"
      )

    Json.obj(
      "heading" -> detailsChargeTypeHeading.toString,
      "isOverdue" -> psaPenaltiesAndChargesService.isPaymentOverdue(sourcePsaFSDetail),
      "period" -> period,
      "chargeReference" -> Messages("penalties.column.chargeReference.toBeAssigned"),
      "penaltyAmount" -> sourcePsaFSDetail.totalAmount,
      "returnUrl" -> getReturnUrl(sourcePsaFSDetail, penaltyType, journeyType),
      "list" -> psaPenaltiesAndChargesService.interestRows(sourcePsaFSDetail),
      "htmlInsetText" -> htmlInsetText
    ) ++ getReturnUrlText(sourcePsaFSDetail, penaltyType, journeyType)
  }

  def getReturnUrl(fs: PsaFSDetail, penaltyType: PenaltyType,
                   journeyType: ChargeDetailsFilter): String = {
    (journeyType, penaltyType) match {
      case (All, AccountingForTaxPenalties) =>
        AllPenaltiesAndChargesController.onPageLoad(fs.periodStartDate.toString, fs.pstr, penaltyType).url
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
      case (All, _) =>
        Json.obj("returnLinkBasedOnJourney" -> messages("psa.financial.overview.penalties.all.returnLink", fs.periodStartDate.getYear.toString))
      case _ =>
        Json.obj("returnLinkBasedOnJourney" -> messages("financialPaymentsAndCharges.returnLink." +s"${journeyType.toString}"))
    }
  }
}
