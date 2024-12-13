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
import connectors.EventReportingConnector
import controllers.actions._
import forms.financialStatement.PenaltyTypeFormProvider
import models.financialStatement.PenaltyType.{EventReportingCharges, getPenaltyType}
import models.financialStatement.{DisplayPenaltyType, PenaltyType, PsaFSDetail}
import models.{ChargeDetailsFilter, DisplayHint, PaymentOverdue}
import play.api.data.Form
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import renderer.Renderer
import services.financialOverview.psa.{PenaltiesNavigationService, PsaPenaltiesAndChargesService}
import uk.gov.hmrc.nunjucks.NunjucksSupport
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import utils.TwirlMigration
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
                                      eventReportingConnector: EventReportingConnector,
                                      navService: PenaltiesNavigationService)
                                     (implicit ec: ExecutionContext) extends FrontendBaseController
  with I18nSupport {

  private def form: Form[PenaltyType] = formProvider()

  def onPageLoad(journeyType: ChargeDetailsFilter): Action[AnyContent] = (identify andThen allowAccess()).async { implicit request =>
    psaPenaltiesAndChargesService.getPenaltiesForJourney(request.psaIdOrException.id, journeyType).flatMap { penaltiesCache =>
      eventReportingConnector.getFeatureToggle("event-reporting").flatMap { toggleDetail =>
        val penaltyTypes = filterPenaltyTypesByFeatureToggle(getPenaltyTypes(penaltiesCache.penalties.toSeq), toggleDetail.isEnabled)

        Future.successful(Ok(
          penaltyTypeView(
            form = form,
            psaName = penaltiesCache.psaName,
            radios = TwirlMigration.toTwirlRadiosWithHintText(PenaltyType.radios(form, penaltyTypes, Seq("govuk-tag govuk-tag--red govuk-!-display-inline"), areLabelsBold = false)),
            submitCall = routes.PenaltyTypeController.onSubmit(),
            returnUrl = appConfig.managePensionsSchemeOverviewUrl
          )
        ))
      }
    }
  }

  def onSubmit(journeyType: ChargeDetailsFilter): Action[AnyContent] = identify.async { implicit request =>
    psaPenaltiesAndChargesService.getPenaltiesForJourney(request.psaIdOrException.id, journeyType).flatMap { penaltiesCache =>
      form.bindFromRequest().fold(
        formWithErrors => {

          Future.successful(BadRequest(
            penaltyTypeView(
              formWithErrors,
              psaName = penaltiesCache.psaName,
              radios = TwirlMigration.toTwirlRadiosWithHintText(PenaltyType.radios(formWithErrors, getPenaltyTypes(penaltiesCache.penalties.toSeq))),
              submitCall = routes.PenaltyTypeController.onSubmit(),
              returnUrl = appConfig.managePensionsSchemeOverviewUrl
            )
          ))
        },
        value => navService.navFromPenaltiesTypePage(penaltiesCache.penalties, request.psaIdOrException.id, value)
      )
    }
  }

  private def getPenaltyTypes(penalties: Seq[PsaFSDetail]): Seq[DisplayPenaltyType] =
    penalties.map(p => getPenaltyType(p.chargeType)).distinct.sortBy(_.toString).map { category =>

      val isOverdue: Boolean = penalties.filter(p => getPenaltyType(p.chargeType) == category).exists(psaPenaltiesAndChargesService.isPaymentOverdue)
      val hint: Option[DisplayHint] = if (isOverdue) Some(PaymentOverdue) else None

      DisplayPenaltyType(category, hint)

    }

  private def filterPenaltyTypesByFeatureToggle(seqDisplayPenaltyType : Seq[DisplayPenaltyType], isEnabled: Boolean): Seq[DisplayPenaltyType] = {
    if(isEnabled) seqDisplayPenaltyType else seqDisplayPenaltyType.filter(category => {category.penaltyType != EventReportingCharges})
    }
}
