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
import forms.financialStatement.PenaltyTypeFormProvider
import models.financialStatement.PenaltyType.getPenaltyType
import models.financialStatement.{DisplayPenaltyType, PenaltyType, PsaFSDetail}
import models.{ChargeDetailsFilter, DisplayHint, PaymentOverdue}
import play.api.data.Form
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import services.AFTPartialService
import services.financialOverview.psa.{PenaltiesNavigationService, PsaPenaltiesAndChargesService}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import views.html.financialOverview.psa.PenaltyTypeView

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class PenaltyTypeController @Inject()(override val messagesApi: MessagesApi,
                                      identify: IdentifierAction,
                                      appConfig: FrontendAppConfig,
                                      allowAccess: AllowAccessActionProviderForIdentifierRequest,
                                      formProvider: PenaltyTypeFormProvider,
                                      val controllerComponents: MessagesControllerComponents,
                                      penaltyTypeView: PenaltyTypeView,
                                      psaPenaltiesAndChargesService: PsaPenaltiesAndChargesService,
                                      penaltiesNavService: PenaltiesNavigationService,
                                      aftPartialService: AFTPartialService)
                                     (implicit ec: ExecutionContext) extends FrontendBaseController
  with I18nSupport {

  private def form: Form[PenaltyType] = formProvider()

  def onPageLoad(journeyType: ChargeDetailsFilter): Action[AnyContent] = (identify andThen allowAccess()).async { implicit request =>
    psaPenaltiesAndChargesService.getPenaltiesForJourney(request.psaIdOrException.id, journeyType).flatMap { penaltiesCache =>
        val penaltyTypes = getPenaltyTypes(penaltiesCache.penalties.toSeq)

        val (title, buttonText) = getParameters(journeyType)

        val historyChargeTypes = getHistoryChargeTypes(penaltiesCache.penalties.toSeq)

        val radios = if (journeyType == ChargeDetailsFilter.History) {
            PenaltyType.radios(form, historyChargeTypes, areLabelsBold = false)
        } else {
            PenaltyType.radios(form, penaltyTypes, Seq("govuk-tag govuk-tag--red govuk-!-display-inline"), areLabelsBold = false)
        }

        Future.successful(Ok(
          penaltyTypeView(
            form = form,
            title = title,
            psaName = penaltiesCache.psaName,
            radios = radios,
            buttonText = buttonText,
            submitCall = routes.PenaltyTypeController.onSubmit(journeyType),
            returnUrl = appConfig.managePensionsSchemeOverviewUrl,
            journeyType = journeyType.toString
          )
        ))
      }
    }

  def onSubmit(journeyType: ChargeDetailsFilter): Action[AnyContent] = identify.async { implicit request =>
    psaPenaltiesAndChargesService.getPenaltiesForJourney(request.psaIdOrException.id, journeyType).flatMap { penaltiesCache =>
      form.bindFromRequest().fold(
        formWithErrors => {
          val (title, buttonText) = getParameters(journeyType)
          val historyChargeTypes = getHistoryChargeTypes(penaltiesCache.penalties.toSeq)

          val radios = if (journeyType == ChargeDetailsFilter.History) {
            PenaltyType.radios(formWithErrors, historyChargeTypes, areLabelsBold = false)
          } else {
            PenaltyType.radios(formWithErrors, getPenaltyTypes(penaltiesCache.penalties.toSeq))
          }

          Future.successful(BadRequest(
            penaltyTypeView(
              formWithErrors,
              title = title,
              psaName = penaltiesCache.psaName,
              radios = radios,
              buttonText = buttonText,
              submitCall = routes.PenaltyTypeController.onSubmit(journeyType),
              returnUrl = appConfig.managePensionsSchemeOverviewUrl,
              journeyType = journeyType
            )
          ))
        },
        value => penaltiesNavService.navFromPenaltiesTypePage(penaltiesCache.penalties, request.psaIdOrException.id, value, journeyType)
      )
    }
  }

  private def getHistoryChargeTypes(psaFs: Seq[PsaFSDetail]) = {
    val historyChargeTypes = aftPartialService.retrievePaidPenaltiesAndCharges(psaFs)
      .map(psaFSDetail => {
        getPenaltyType(psaFSDetail.chargeType)
      }).distinct.sortBy(_.toString)
    historyChargeTypes.map(chargeType => DisplayPenaltyType(chargeType, None))
  }

  private def getPenaltyTypes(penalties: Seq[PsaFSDetail]): Seq[DisplayPenaltyType] =
    penalties.map(p => getPenaltyType(p.chargeType)).distinct.sortBy(_.toString).map { category =>

      val isOverdue: Boolean = penalties.filter(p => getPenaltyType(p.chargeType) == category).exists(psaPenaltiesAndChargesService.isPaymentOverdue)
      val hint: Option[DisplayHint] = if (isOverdue) Some(PaymentOverdue) else None

      DisplayPenaltyType(category, hint)

    }

  private def getParameters(journeyType: ChargeDetailsFilter)(implicit messages: Messages) = {
    if (journeyType == ChargeDetailsFilter.History) {
        (messages("psa.financial.overview.historyChargeType.title"), messages("site.continue"))
    } else {
        (messages("penaltyType.title"), messages("site.save_and_continue"))
    }
  }
}
