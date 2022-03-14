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

import controllers.actions.{AllowAccessActionProviderForIdentifierRequest, IdentifierAction}
import models.ChargeDetailsFilter
import models.financialStatement.PsaFSChargeType.{AFT_12_MONTH_LPP, AFT_30_DAY_LPP, AFT_6_MONTH_LPP, AFT_DAILY_LFP, AFT_INITIAL_LFP, CONTRACT_SETTLEMENT, CONTRACT_SETTLEMENT_INTEREST, OTC_12_MONTH_LPP, OTC_30_DAY_LPP, OTC_6_MONTH_LPP, PSS_INFO_NOTICE, PSS_PENALTY}
import models.financialStatement.{PsaFS, PsaFSChargeType}
import models.requests.IdentifierRequest
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import renderer.Renderer
import services.SchemeService
import services.financialOverview.PsaPenaltiesAndChargesService
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.NunjucksSupport
import utils.DateHelper.{formatDateDMY, formatStartDate}

import java.time.LocalDate
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
                 chargeReferenceIndex: String,
                 journeyType: ChargeDetailsFilter): Action[AnyContent] =
    (identify andThen allowAccess()).async {
      implicit request =>
        psaPenaltiesAndChargesService.getPenaltiesForJourney(request.idOrException, journeyType).flatMap { penaltiesCache =>

          val chargeRefs: Seq[String] = penaltiesCache.penalties.map(_.chargeReference)
          def penaltyOpt: Option[PsaFS] = penaltiesCache.penalties.find(_.chargeReference == chargeRefs(chargeReferenceIndex.toInt))

          if(chargeRefs.length > chargeReferenceIndex.toInt && penaltyOpt.nonEmpty) {
            val psaFs = penaltyOpt.head
            schemeService.retrieveSchemeDetails(request.idOrException, psaFs.pstr, "pstr") flatMap {
              schemeDetails =>
                val json = Json.obj(
                  "psaName" -> penaltiesCache.psaName,
                  "schemeAssociated" -> true,
                  "schemeName" -> schemeDetails.schemeName,
                  "penaltyAmount" -> psaFs.totalAmount,
                  "returnLinkBasedOnJourney" -> msg"financialPaymentsAndCharges.returnLink.${journeyType.toString}",
                  "returnUrl" -> routes.PsaPaymentsAndChargesController.onPageLoad(journeyType).url
                ) ++ commonJson(penaltyOpt.head, penaltiesCache.penalties, chargeRefs, chargeReferenceIndex)

                renderer.render(template = "financialOverview/psaInterestDetails.njk", json).map(Ok(_))
            }
          } else {
            Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad))
          }
        }
    }

  def setPeriod(chargeType: PsaFSChargeType, periodStartDate: LocalDate, periodEndDate: LocalDate): String = {
    "Tax period: " + formatDateDMY(periodStartDate) + " to " + formatDateDMY(periodEndDate)
    chargeType match {
      case AFT_INITIAL_LFP | AFT_DAILY_LFP | AFT_30_DAY_LPP | AFT_6_MONTH_LPP
           | AFT_12_MONTH_LPP | OTC_30_DAY_LPP | OTC_6_MONTH_LPP | OTC_12_MONTH_LPP =>
        "Quarter: " + formatStartDate(periodStartDate) + " to " + formatDateDMY(periodEndDate)
      case PSS_PENALTY | PSS_INFO_NOTICE | CONTRACT_SETTLEMENT | CONTRACT_SETTLEMENT_INTEREST =>
        "Period: " + formatStartDate(periodStartDate) + " to " + formatDateDMY(periodEndDate)
      case _ => ""
    }
  }

  private def commonJson(
                          fs: PsaFS,
                          psaFS: Seq[PsaFS],
                          chargeRefs: Seq[String],
                          chargeReferenceIndex: String
                        )(implicit request: IdentifierRequest[AnyContent]): JsObject = {
    val psaFSDetails = psaFS.filter(_.chargeReference == chargeRefs(chargeReferenceIndex.toInt)).head
    val period = setPeriod(fs.chargeType, fs.periodStartDate, fs.periodEndDate)
    val originalChargeRefsIndex: String => String = cr => psaFS.map(_.chargeReference).indexOf(cr).toString

    Json.obj(
      "heading" ->   psaFS.filter(_.chargeReference == chargeRefs(chargeReferenceIndex.toInt)).head.chargeType.toString,
      "isOverdue" ->        psaPenaltiesAndChargesService.isPaymentOverdue(psaFS.filter(_.chargeReference == chargeRefs(chargeReferenceIndex.toInt)).head),
      "period" ->           period,
      "chargeReference" ->  Messages("penalties.column.chargeReference.toBeAssigned"),
      "penaltyAmount" ->    psaFSDetails.totalAmount,
      "list" ->             psaPenaltiesAndChargesService.interestRows(psaFS.filter(_.chargeReference == chargeRefs(chargeReferenceIndex.toInt)).head),
      "originalAmountURL" -> controllers.financialOverview.routes.PsaPenaltiesAndChargeDetailsController
        .onPageLoad(fs.pstr, originalChargeRefsIndex(fs.chargeReference).toString).url
    )
  }

}
