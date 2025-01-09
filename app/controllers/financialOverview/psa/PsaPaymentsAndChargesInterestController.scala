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
import controllers.actions.{AllowAccessActionProviderForIdentifierRequest, IdentifierAction}
import controllers.financialOverview.psa.routes.AllPenaltiesAndChargesController
import helpers.FormatHelper
import models.ChargeDetailsFilter
import models.ChargeDetailsFilter.All
import models.financialStatement.PenaltyType.{AccountingForTaxPenalties, getPenaltyType}
import models.financialStatement.PsaFSChargeType.INTEREST_ON_CONTRACT_SETTLEMENT
import models.financialStatement.{PenaltyType, PsaFSChargeType, PsaFSDetail}
import models.requests.IdentifierRequest
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import services.SchemeService
import services.financialOverview.psa.PsaPenaltiesAndChargesService
import uk.gov.hmrc.govukfrontend.views.viewmodels.content.HtmlContent
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import utils.DateHelper.{formatDateDMY, formatStartDate}
import viewmodels.{InterestDetailsNewViewModel, InterestDetailsViewModel}
import views.html.financialOverview.psa.{PsaInterestDetailsNewView, PsaInterestDetailsView}

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class PsaPaymentsAndChargesInterestController @Inject()(identify: IdentifierAction,
                                                        allowAccess: AllowAccessActionProviderForIdentifierRequest,
                                                        override val messagesApi: MessagesApi,
                                                        val controllerComponents: MessagesControllerComponents,
                                                        config: FrontendAppConfig,
                                                        psaPenaltiesAndChargesService: PsaPenaltiesAndChargesService,
                                                        schemeService: SchemeService,
                                                        view: PsaInterestDetailsView,
                                                        newView: PsaInterestDetailsNewView,
                                                       )(implicit ec: ExecutionContext)
  extends FrontendBaseController
    with I18nSupport {

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

                val viewModel = if (config.podsNewFinancialCredits) {
                  createInterestDetailsNewViewModel(penaltyOpt.head, journeyType)
                } else {
                  createInterestDetailsViewModel(penaltyOpt.head, journeyType)
                }

//                val templateToRender = if (config.podsNewFinancialCredits) {
//                  newView(
//                    heading =
//                  )
//                } else {
//                  view(
//                    heading = viewModel.heading,
//                    isOverdue = viewModel.isOverdue,
//                    period = viewModel.period,
//                    chargeReference = viewModel.chargeReference,
//                    penaltyAmount = viewModel.penaltyAmount,
//                    returnUrl = viewModel.returnUrl,
//                    list = viewModel.list,
//                    htmlInsetText = viewModel.htmlInsetText,
//                    returnUrlText = viewModel.returnUrlR
//                  )
//                }

                Future.successful(Ok("templateToRender"))
            }
          } else {
            Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad))
          }
        }
    }

  private def createInterestDetailsViewModel(
                          sourcePsaFSDetail: PsaFSDetail,
                          journeyType: ChargeDetailsFilter
                        )(implicit request: IdentifierRequest[AnyContent]): InterestDetailsViewModel = {
    val period = psaPenaltiesAndChargesService.setPeriod(sourcePsaFSDetail.chargeType, sourcePsaFSDetail.periodStartDate, sourcePsaFSDetail.periodEndDate)
    val originalAmountURL = routes.PsaPenaltiesAndChargeDetailsController.onPageLoad(sourcePsaFSDetail.pstr, sourcePsaFSDetail.index.toString, journeyType).url
    val detailsChargeType = sourcePsaFSDetail.chargeType
    val detailsChargeTypeHeading = if (detailsChargeType == PsaFSChargeType.CONTRACT_SETTLEMENT) INTEREST_ON_CONTRACT_SETTLEMENT else detailsChargeType
    val penaltyType = getPenaltyType(detailsChargeType)

    val htmlInsetText =
      HtmlContent(
        s"<p class=govuk-body>${Messages("psa.financial.overview.hint1")}" +
          s" <span><a id='breakdown' class=govuk-link href=$originalAmountURL>" +
          s" ${Messages("psa.financial.overview.hintLink")}</a></span>" +
          s" ${Messages("psa.financial.overview.hint2")}</p>"
      )

    InterestDetailsViewModel(
      heading = detailsChargeTypeHeading.toString,
      isOverdue = psaPenaltiesAndChargesService.isPaymentOverdue(sourcePsaFSDetail),
      schemeAssociated = true,
      schemeName = "",
      period = period,
      chargeReference = Messages("penalties.column.chargeReference.toBeAssigned"),
      penaltyAmount = sourcePsaFSDetail.totalAmount,
      returnUrl = getReturnUrl(sourcePsaFSDetail, penaltyType, journeyType),
      list = psaPenaltiesAndChargesService.interestRowsNew(sourcePsaFSDetail),
      htmlInsetText = htmlInsetText,
      returnUrlText = getReturnUrlText(sourcePsaFSDetail, penaltyType, journeyType)
    )
  }

  private def createInterestDetailsNewViewModel(
                               sourcePsaFSDetail: PsaFSDetail,
                               journeyType: ChargeDetailsFilter
                             )(implicit request: IdentifierRequest[AnyContent]): InterestDetailsNewViewModel = {
    val originalAmountURL = routes.PsaPenaltiesAndChargeDetailsController.onPageLoad(sourcePsaFSDetail.pstr, sourcePsaFSDetail.index.toString, journeyType).url
    val detailsChargeType = sourcePsaFSDetail.chargeType
    val detailsChargeTypeHeading = if (detailsChargeType == PsaFSChargeType.CONTRACT_SETTLEMENT) INTEREST_ON_CONTRACT_SETTLEMENT else detailsChargeType
    val penaltyType = getPenaltyType(detailsChargeType)

    val htmlInsetText =
      HtmlContent(
        s"<p class=govuk-body>${Messages("psa.financial.overview.hint1")}" +
          s" <span><a id='breakdown' class=govuk-link href=$originalAmountURL>" +
          s" ${Messages("psa.financial.overview.hintLink")}</a></span>" +
          s" ${Messages("psa.financial.overview.hint2")}</p>"
      )

    InterestDetailsNewViewModel(
      heading = detailsChargeTypeHeading.toString,
      isOverdue = psaPenaltiesAndChargesService.isPaymentOverdue(sourcePsaFSDetail),
      schemeAssociated = true,
      schemeName = "",
      chargeReference = Messages("penalties.column.chargeReference.toBeAssigned"),
      interestDueAmount = FormatHelper.formatCurrencyAmountAsString(sourcePsaFSDetail.accruedInterestTotal),
      penaltyAmount = sourcePsaFSDetail.totalAmount,
      returnUrl = getReturnUrl(sourcePsaFSDetail, penaltyType, journeyType),
      list = psaPenaltiesAndChargesService.interestRowsNew(sourcePsaFSDetail),
      htmlInsetText = htmlInsetText,
      returnUrlText = getReturnUrlText(sourcePsaFSDetail, penaltyType, journeyType)
    )

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
