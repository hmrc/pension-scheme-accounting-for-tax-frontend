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
import models.financialStatement.PsaFSChargeType.INTEREST_ON_CONTRACT_SETTLEMENT
import models.financialStatement.{PsaFS, PsaFSChargeType}
import models.requests.IdentifierRequest
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import renderer.Renderer
import services.SchemeService
import services.financialOverview.PsaPenaltiesAndChargesService
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.{Html, NunjucksSupport}

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
            schemeService.retrieveSchemeDetails(request.idOrException, identifier, "pstr") flatMap {
              schemeDetails =>
                val json = Json.obj(
                  "psaName" -> penaltiesCache.psaName,
                  "schemeAssociated" -> true,
                  "schemeName" -> schemeDetails.schemeName
                ) ++ commonJson(penaltyOpt.head, penaltiesCache.penalties, chargeRefs, chargeReferenceIndex, journeyType)

                renderer.render(template = "financialOverview/psaInterestDetails.njk", json).map(Ok(_))
            }
          } else {
            Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad))
          }
        }
    }

  private def commonJson(
                          fs: PsaFS,
                          psaFS: Seq[PsaFS],
                          chargeRefs: Seq[String],
                          chargeReferenceIndex: String,
                          journeyType: ChargeDetailsFilter
                        )(implicit request: IdentifierRequest[AnyContent]): JsObject = {
    val psaFSDetails = psaFS.filter(_.chargeReference == chargeRefs(chargeReferenceIndex.toInt)).head
    val period = psaPenaltiesAndChargesService.setPeriod(fs.chargeType, fs.periodStartDate, fs.periodEndDate)
    val originalChargeRefsIndex: String => String = cr => psaFS.map(_.chargeReference).indexOf(cr).toString
    val originalAmountURL = controllers.financialOverview.routes.PsaPenaltiesAndChargeDetailsController.
      onPageLoad(fs.pstr, originalChargeRefsIndex(fs.chargeReference), journeyType).url
    val detailsChargeType = psaFS.filter(_.chargeReference == chargeRefs(chargeReferenceIndex.toInt)).head.chargeType
    val detailsChargeTypeHeading = if (detailsChargeType == PsaFSChargeType.CONTRACT_SETTLEMENT) INTEREST_ON_CONTRACT_SETTLEMENT else detailsChargeType

    val htmlInsetText =
      Html(
        s"<p class=govuk-body>${Messages("psa.financial.overview.hint1")}" +
          s" <span><a id='breakdown' class=govuk-link href=$originalAmountURL>" +
          s" ${Messages("psa.financial.overview.hintLink")}</a></span>" +
          s" ${Messages("psa.financial.overview.hint2")}</p>"
      )

    Json.obj(
      "heading" -> detailsChargeTypeHeading.toString,
      "isOverdue" -> psaPenaltiesAndChargesService.isPaymentOverdue(psaFS.filter(_.chargeReference == chargeRefs(chargeReferenceIndex.toInt)).head),
      "period" -> period,
      "chargeReference" -> Messages("penalties.column.chargeReference.toBeAssigned"),
      "penaltyAmount" -> psaFSDetails.totalAmount,
      "returnLinkBasedOnJourney" -> msg"financialPaymentsAndCharges.returnLink.${journeyType.toString}",
      "returnUrl" -> routes.PsaPaymentsAndChargesController.onPageLoad(journeyType).url,
      "list" -> psaPenaltiesAndChargesService.interestRows(psaFS.filter(_.chargeReference == chargeRefs(chargeReferenceIndex.toInt)).head),
      "htmlInsetText" -> htmlInsetText
    )
  }

}
