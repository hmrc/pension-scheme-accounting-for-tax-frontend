/*
 * Copyright 2021 HM Revenue & Customs
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

import controllers.actions._
import controllers.financialStatement.penalties.routes._
import forms.SelectSchemeFormProvider
import models.PenaltySchemes
import models.financialStatement.PenaltyType._
import models.financialStatement.{PenaltyType, PsaFS}
import models.requests.IdentifierRequest
import play.api.data.Form
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, Call, MessagesControllerComponents}
import renderer.Renderer
import services.PenaltiesService
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.NunjucksSupport

import java.time.LocalDate
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class SelectSchemeController @Inject()(
                                        identify: IdentifierAction,
                                        allowAccess: AllowAccessActionProviderForIdentifierRequest,
                                        override val messagesApi: MessagesApi,
                                        val controllerComponents: MessagesControllerComponents,
                                        formProvider: SelectSchemeFormProvider,
                                        penaltiesService: PenaltiesService,
                                        renderer: Renderer
                                      )(implicit ec: ExecutionContext)
                                        extends FrontendBaseController
                                          with I18nSupport
                                          with NunjucksSupport {

  private def form(schemes: Seq[PenaltySchemes], typeParam: String)
                  (implicit messages: Messages): Form[PenaltySchemes] = formProvider(schemes, messages("selectScheme.error", typeParam))

  def onPageLoad(penaltyType: PenaltyType, period: String): Action[AnyContent] = (identify andThen allowAccess()).async {
    implicit request =>
      val (penaltySchemesFunction, _) = getSchemesAndUrl(penaltyType, period, request.psaIdOrException.id)
      penaltiesService.getPenaltiesFromCache(request.psaIdOrException.id).flatMap { penalties =>
        penaltySchemesFunction(penalties).flatMap { penaltySchemes =>
          if (penaltySchemes.nonEmpty) {

            val typeParam = penaltiesService.getTypeParam(penaltyType)

            val json = Json.obj(
              "typeParam" -> typeParam,
              "form" -> form(penaltySchemes, typeParam),
              "radios" -> PenaltySchemes.radios(form(penaltySchemes, typeParam), penaltySchemes))

            renderer.render(template = "financialStatement/penalties/selectScheme.njk", json).map(Ok(_))
          } else {
            Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad()))
          }
        }
      }
  }

  def onSubmit(penaltyType: PenaltyType, period: String): Action[AnyContent] = identify.async {
    implicit request =>

      val (penaltySchemesFunction, redirectUrl) = getSchemesAndUrl(penaltyType, period, request.psaIdOrException.id)

      penaltiesService.getPenaltiesFromCache(request.psaIdOrException.id).flatMap { penalties =>
        penaltySchemesFunction(penalties).flatMap { penaltySchemes =>

          val typeParam = penaltiesService.getTypeParam(penaltyType)

          form(penaltySchemes, typeParam).bindFromRequest().fold(
            formWithErrors => {

              val json = Json.obj(
                "typeParam" -> typeParam,
                "form" -> formWithErrors,
                "radios" -> PenaltySchemes.radios(formWithErrors, penaltySchemes))

              renderer.render(template = "financialStatement/penalties/selectScheme.njk", json).map(BadRequest(_))
            },
            value => {
              value.srn match {
                case Some(srn) =>
                  Future.successful(Redirect(redirectUrl(srn)))
                case _ =>
                  penaltiesService.getPenaltiesFromCache(request.psaIdOrException.id).map { penalties =>
                    val pstrIndex: String = penalties.map(_.pstr).indexOf(value.pstr).toString
                    Redirect(redirectUrl(pstrIndex))
                  }
              }
            }
          )


        }
      }
  }

  def getSchemesAndUrl(penaltyType: PenaltyType, period: String, psaId: String)
                      (implicit request: IdentifierRequest[AnyContent]): (Seq[PsaFS] => Future[Seq[PenaltySchemes]], String => Call) =
    penaltyType match {
      case AccountingForTaxPenalties =>
        (penalties => penaltiesService.penaltySchemes(LocalDate.parse(period), psaId, penalties),
          identifier => PenaltiesController.onPageLoadAft(period, identifier))
      case ContractSettlementCharges =>
        (penalties => penaltiesService.penaltySchemes(period.toInt, psaId, penaltyType, penalties),
          identifier => PenaltiesController.onPageLoadContract(period, identifier))
      case InformationNoticePenalties =>
        (penalties => penaltiesService.penaltySchemes(period.toInt, psaId, penaltyType, penalties),
          identifier => PenaltiesController.onPageLoadInfoNotice(period, identifier))
      case PensionsPenalties =>
        (penalties => penaltiesService.penaltySchemes(period.toInt, psaId, penaltyType, penalties),
          identifier => PenaltiesController.onPageLoadPension(period, identifier))

    }

}
