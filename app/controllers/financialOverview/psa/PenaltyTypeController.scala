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
                                      penaltiesNavService: PenaltiesNavigationService)
                                     (implicit ec: ExecutionContext) extends FrontendBaseController
  with I18nSupport {

  private def form: Form[PenaltyType] = formProvider()

  def onPageLoad(journeyType: ChargeDetailsFilter): Action[AnyContent] = (identify andThen allowAccess()).async { implicit request =>
    psaPenaltiesAndChargesService.getPenaltiesForJourney(request.psaIdOrException.id, journeyType).flatMap { penaltiesCache =>
        val penaltyTypes = getPenaltyTypes(penaltiesCache.penalties.toSeq)

        val title = getTitle(journeyType)

        val historicalPenalties = penaltiesCache.penalties.filter(_.outstandingAmount <= 0)
        val historyChargeTypes = getPenaltyTypes(historicalPenalties)

        val radios = if (journeyType == ChargeDetailsFilter.History) {
            PenaltyType.radios(form, historyChargeTypes, areLabelsBold = false)
        } else {
            PenaltyType.radiosWithHint(form, penaltyTypes, Seq("govuk-tag govuk-tag--red govuk-!-display-inline"), areLabelsBold = false)
        }

        Future.successful(Ok(
          penaltyTypeView(
            form = form,
            title = title,
            psaName = penaltiesCache.psaName,
            radios = radios,
            submitCall = routes.PenaltyTypeController.onSubmit(journeyType),
            returnUrl = appConfig.managePensionsSchemeOverviewUrl,
            journeyType = journeyType
          )
        ))
      }
    }

  def onSubmit(journeyType: ChargeDetailsFilter): Action[AnyContent] = identify.async { implicit request =>
    psaPenaltiesAndChargesService.getPenaltiesForJourney(request.psaIdOrException.id, journeyType).flatMap { penaltiesCache =>
      form.bindFromRequest().fold(
        formWithErrors => {
          val title = getTitle(journeyType)
          val historicalPenalties = penaltiesCache.penalties.filter(_.outstandingAmount <= 0)
          val historyChargeTypes = getPenaltyTypes(historicalPenalties)

          val radios = if (journeyType == ChargeDetailsFilter.History) {
            PenaltyType.radios(formWithErrors, historyChargeTypes, areLabelsBold = false)
          } else {
            PenaltyType.radiosWithHint(
              formWithErrors,
              getPenaltyTypes(penaltiesCache.penalties.toSeq),
              Seq("govuk-tag govuk-tag--red govuk-!-display-inline"),
              areLabelsBold = false
            )
          }

          Future.successful(BadRequest(
            penaltyTypeView(
              formWithErrors,
              title = title,
              psaName = penaltiesCache.psaName,
              radios = radios,
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

  private def getPenaltyTypes(penalties: Seq[PsaFSDetail]): Seq[DisplayPenaltyType] =
    penalties.map(p => getPenaltyType(p.chargeType)).distinct.sortBy(_.toString).map { category =>

      val isOverdue: Boolean = penalties.filter(p => getPenaltyType(p.chargeType) == category).exists(psaPenaltiesAndChargesService.isPaymentOverdue)
      val hint: Option[DisplayHint] = if (isOverdue) Some(PaymentOverdue) else None

      DisplayPenaltyType(category, hint)

    }

  private def getTitle(journeyType: ChargeDetailsFilter)(implicit messages: Messages) = {
    if (journeyType == ChargeDetailsFilter.History) {
        messages("financial.overview.historyChargeType.title")
    } else {
        messages("penaltyType.title")
    }
  }
}
