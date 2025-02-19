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

package controllers.financialStatement.penalties

import config.FrontendAppConfig
import controllers.actions._
import forms.financialStatement.PenaltyTypeFormProvider
import models.financialStatement.PenaltyType.getPenaltyType
import models.financialStatement.{DisplayPenaltyType, PenaltyType, PsaFSDetail}
import models.{DisplayHint, PaymentOverdue, PenaltiesFilter}
import play.api.data.Form
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import services.PenaltiesService
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import views.html.financialStatement.penalties.PenaltyTypeView

class PenaltyTypeController @Inject()(override val messagesApi: MessagesApi,
                                      identify: IdentifierAction,
                                      allowAccess: AllowAccessActionProviderForIdentifierRequest,
                                      formProvider: PenaltyTypeFormProvider,
                                      val controllerComponents: MessagesControllerComponents,
                                      config: FrontendAppConfig,
                                      service: PenaltiesService,
                                      penaltyTypeView: PenaltyTypeView)
                                     (implicit ec: ExecutionContext) extends FrontendBaseController
  with I18nSupport {

  private def form: Form[PenaltyType] = formProvider()

  def onPageLoad(journeyType: PenaltiesFilter): Action[AnyContent] = (identify andThen allowAccess()).async { implicit request =>
    service.getPenaltiesForJourney(request.psaIdOrException.id, journeyType).flatMap { penaltiesCache =>
      val penaltyTypes = getPenaltyTypes(penaltiesCache.penalties)
      Future.successful(Ok(penaltyTypeView(
        form,
        PenaltyType.radios(form, penaltyTypes, Seq("govuk-tag govuk-tag--red govuk-!-display-inline"), areLabelsBold = false),
        routes.PenaltyTypeController.onSubmit(journeyType),
        config.managePensionsSchemeOverviewUrl,
        penaltiesCache.psaName
      )))
    }
  }

  def onSubmit(journeyType: PenaltiesFilter): Action[AnyContent] = identify.async { implicit request =>
    service.getPenaltiesForJourney(request.psaIdOrException.id, journeyType).flatMap { penaltiesCache =>
      form.bindFromRequest().fold(
        formWithErrors => {
          Future.successful(BadRequest(penaltyTypeView(
            formWithErrors,
            PenaltyType.radios(formWithErrors, getPenaltyTypes(penaltiesCache.penalties)),
            routes.PenaltyTypeController.onSubmit(journeyType),
            config.managePensionsSchemeOverviewUrl,
            penaltiesCache.psaName
          )))
        },
        value => service.navFromPenaltiesTypePage(penaltiesCache.penalties, value, request.psaIdOrException.id, journeyType)
      )
    }
  }

  private def getPenaltyTypes(penalties: Seq[PsaFSDetail]): Seq[DisplayPenaltyType] =
    penalties.map(p => getPenaltyType(p.chargeType)).distinct.sortBy(_.toString).map { category =>

      val isOverdue: Boolean = penalties.filter(p => getPenaltyType(p.chargeType) == category).exists(service.isPaymentOverdue)
      val hint: Option[DisplayHint] = if (isOverdue) Some(PaymentOverdue) else None

      DisplayPenaltyType(category, hint)
    }
}
