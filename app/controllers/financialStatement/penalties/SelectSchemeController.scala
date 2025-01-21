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
import controllers.financialStatement.penalties.routes._
import forms.SelectSchemeFormProvider
import models.financialStatement.PenaltyType._
import models.financialStatement.{PenaltyType, PsaFSDetail}
import models.requests.IdentifierRequest
import models.{PenaltiesFilter, PenaltySchemes}
import play.api.data.Form
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.mvc.{Action, AnyContent, Call, MessagesControllerComponents}
import services.PenaltiesService
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import utils.TwirlMigration

import java.time.LocalDate
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import views.html.financialStatement.penalties.SelectSchemeView

class SelectSchemeController @Inject()(
                                        identify: IdentifierAction,
                                        allowAccess: AllowAccessActionProviderForIdentifierRequest,
                                        override val messagesApi: MessagesApi,
                                        val controllerComponents: MessagesControllerComponents,
                                        formProvider: SelectSchemeFormProvider,
                                        penaltiesService: PenaltiesService,
                                        selectSchemeView: SelectSchemeView,
                                        config: FrontendAppConfig
                                      )(implicit ec: ExecutionContext)
                                        extends FrontendBaseController
                                          with I18nSupport {

  private def form(schemes: Seq[PenaltySchemes], typeParam: String)
                  (implicit messages: Messages): Form[PenaltySchemes] = formProvider(schemes, messages("selectScheme.error", typeParam))

  def onPageLoad(penaltyType: PenaltyType, period: String, journeyType: PenaltiesFilter): Action[AnyContent] = (identify andThen allowAccess()).async {
    implicit request =>
      val (penaltySchemesFunction, _) = getSchemesAndUrl(penaltyType, period, request.psaIdOrException.id, journeyType)
      penaltiesService.getPenaltiesForJourney(request.psaIdOrException.id, journeyType).flatMap { penaltiesCache =>
        penaltySchemesFunction(penaltiesCache.penalties).flatMap { penaltySchemes =>
          if (penaltySchemes.nonEmpty) {

            val typeParam = penaltiesService.getTypeParam(penaltyType)
            Future.successful(Ok(selectSchemeView(
              form(penaltySchemes, typeParam),
              typeParam,
              TwirlMigration.toTwirlRadios(PenaltySchemes.radios(form(penaltySchemes, typeParam), penaltySchemes)),
              routes.SelectSchemeController.onSubmit(penaltyType, period, journeyType),
              config.managePensionsSchemeOverviewUrl,
              penaltiesCache.psaName
            )))
          } else {
            Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad))
          }
        }
      }
  }

  def onSubmit(penaltyType: PenaltyType, period: String, journeyType: PenaltiesFilter): Action[AnyContent] = identify.async {
    implicit request =>

      val (penaltySchemesFunction, redirectUrl) = getSchemesAndUrl(penaltyType, period, request.psaIdOrException.id, journeyType: PenaltiesFilter)

      penaltiesService.getPenaltiesForJourney(request.psaIdOrException.id, journeyType).flatMap { penaltiesCache =>
        penaltySchemesFunction(penaltiesCache.penalties).flatMap { penaltySchemes =>

          val typeParam = penaltiesService.getTypeParam(penaltyType)

          form(penaltySchemes, typeParam).bindFromRequest().fold(
            formWithErrors => {
              Future.successful(BadRequest(selectSchemeView(
                formWithErrors,
                typeParam,
                TwirlMigration.toTwirlRadios(PenaltySchemes.radios(formWithErrors, penaltySchemes)),
                routes.SelectSchemeController.onSubmit(penaltyType, period, journeyType),
                config.managePensionsSchemeOverviewUrl,
                penaltiesCache.psaName
              )))
            },
            value => {
              value.srn match {
                case Some(srn) =>
                  Future.successful(Redirect(redirectUrl(srn)))
                case _ =>
                  penaltiesService.getPenaltiesForJourney(request.psaIdOrException.id, journeyType).map { penalties =>
                    val pstrIndex: String = penaltiesCache.penalties.map(_.pstr).indexOf(value.pstr).toString
                    Redirect(redirectUrl(pstrIndex))
                  }
              }
            }
          )


        }
      }
  }

  def getSchemesAndUrl(penaltyType: PenaltyType, period: String, psaId: String, journeyType: PenaltiesFilter)
                      (implicit request: IdentifierRequest[AnyContent]): (Seq[PsaFSDetail] => Future[Seq[PenaltySchemes]], String => Call) =
    penaltyType match {
      case AccountingForTaxPenalties =>
        (penalties => penaltiesService.penaltySchemes(LocalDate.parse(period), psaId, penalties),
          identifier => PenaltiesController.onPageLoadAft(period, identifier, journeyType))
      case ContractSettlementCharges =>
        (penalties => penaltiesService.penaltySchemes(period.toInt, psaId, penaltyType, penalties),
          identifier => PenaltiesController.onPageLoadContract(period, identifier, journeyType))
      case InformationNoticePenalties =>
        (penalties => penaltiesService.penaltySchemes(period.toInt, psaId, penaltyType, penalties),
          identifier => PenaltiesController.onPageLoadInfoNotice(period, identifier, journeyType))
      case PensionsPenalties =>
        (penalties => penaltiesService.penaltySchemes(period.toInt, psaId, penaltyType, penalties),
          identifier => PenaltiesController.onPageLoadPension(period, identifier, journeyType))

    }

}
