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
import forms.SelectSchemeFormProvider
import models.PenaltySchemes
import models.financialStatement.PenaltyType._
import models.requests.IdentifierRequest
import play.api.data.Form
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, Call, MessagesControllerComponents, Result}
import renderer.Renderer
import services.PenaltiesService
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.NunjucksSupport
import controllers.financialStatement.penalties.routes._
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

  private def form(schemes: Seq[PenaltySchemes]): Form[PenaltySchemes] = formProvider(schemes)

  def onPageLoadAft(startDate: String): Action[AnyContent] = (identify andThen allowAccess()).async {
    implicit request =>
      penaltiesService.getPenaltiesFromCache(request.psaIdOrException.id).flatMap { penalties =>
        penaltiesService.penaltySchemes(startDate, request.psaIdOrException.id, penalties).flatMap { penaltySchemes =>
          onPageLoad("penaltyType.accountingForTax", penaltySchemes)
        }
      }
  }

  def onSubmitAft(startDate: String): Action[AnyContent] = identify.async {
    implicit request =>
      penaltiesService.getPenaltiesFromCache(request.psaIdOrException.id).flatMap { penalties =>
        penaltiesService.penaltySchemes(startDate, request.psaIdOrException.id, penalties).flatMap { penaltySchemes =>

          val redirectUrl: String => Call = identifier => PenaltiesController.onPageLoadAft(startDate, identifier)
          onSubmit("penaltyType.accountingForTax", penaltySchemes, redirectUrl)
        }
      }
  }

  def onPageLoadContract(year: String): Action[AnyContent] = (identify andThen allowAccess()).async {
    implicit request =>
      penaltiesService.getPenaltiesFromCache(request.psaIdOrException.id).flatMap { penalties =>
        penaltiesService.penaltySchemes(year.toInt, request.psaIdOrException.id, ContractSettlementCharges, penalties).flatMap {
          penaltySchemes =>

            onPageLoad("penaltyType.contractSettlement", penaltySchemes)
        }
      }
  }

  def onSubmitContract(year: String): Action[AnyContent] = identify.async {
    implicit request =>
      penaltiesService.getPenaltiesFromCache(request.psaIdOrException.id).flatMap { penalties =>
        penaltiesService.penaltySchemes(year.toInt, request.psaIdOrException.id, ContractSettlementCharges, penalties).flatMap {
          penaltySchemes =>

            val redirectUrl: String => Call = identifier => PenaltiesController.onPageLoadContract(year, identifier)
            onSubmit("penaltyType.contractSettlement", penaltySchemes, redirectUrl)
        }
      }
  }

  def onPageLoadInfoNotice(year: String): Action[AnyContent] = (identify andThen allowAccess()).async {
    implicit request =>
      penaltiesService.getPenaltiesFromCache(request.psaIdOrException.id).flatMap { penalties =>
        penaltiesService.penaltySchemes(year.toInt, request.psaIdOrException.id, InformationNoticePenalties, penalties).flatMap {
          penaltySchemes =>

            onPageLoad("penaltyType.informationNotice", penaltySchemes)
        }
      }
  }

  def onSubmitInfoNotice(year: String): Action[AnyContent] = identify.async {
    implicit request =>
      penaltiesService.getPenaltiesFromCache(request.psaIdOrException.id).flatMap { penalties =>
        penaltiesService.penaltySchemes(year.toInt, request.psaIdOrException.id, InformationNoticePenalties, penalties).flatMap {
          penaltySchemes =>

            val redirectUrl: String => Call = identifier => PenaltiesController.onPageLoadInfoNotice(year, identifier)
            onSubmit("penaltyType.informationNotice", penaltySchemes, redirectUrl)
        }
      }
  }

  def onPageLoadPension(year: String): Action[AnyContent] = (identify andThen allowAccess()).async {
    implicit request =>
      penaltiesService.getPenaltiesFromCache(request.psaIdOrException.id).flatMap { penalties =>
        penaltiesService.penaltySchemes(year.toInt, request.psaIdOrException.id, PensionsPenalties, penalties).flatMap {
          penaltySchemes =>

            onPageLoad("penaltyType.pensionsPenalties", penaltySchemes)
        }
      }
  }

  def onSubmitPension(year: String): Action[AnyContent] = identify.async {
    implicit request =>
      penaltiesService.getPenaltiesFromCache(request.psaIdOrException.id).flatMap { penalties =>
        penaltiesService.penaltySchemes(year.toInt, request.psaIdOrException.id, PensionsPenalties, penalties).flatMap {
          penaltySchemes =>

            val redirectUrl: String => Call = identifier => PenaltiesController.onPageLoadPension(year, identifier)
            onSubmit("penaltyType.pensionsPenalties", penaltySchemes, redirectUrl)
        }
      }
  }

  private def onPageLoad(typeParam: String, penaltySchemes: Seq[PenaltySchemes])
                        (implicit request: IdentifierRequest[AnyContent]): Future[Result] =
    if (penaltySchemes.nonEmpty) {

      val json = Json.obj(
        "typeParam" -> typeParam,
        "form" -> form(penaltySchemes),
        "radios" -> PenaltySchemes.radios(form(penaltySchemes), penaltySchemes))

      renderer.render(template = "financialStatement/penalties/selectScheme.njk", json).map(Ok(_))
    } else {
      Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad()))
    }

  private def onSubmit(typeParam: String, penaltySchemes: Seq[PenaltySchemes], redirectUrl: String => Call)
                      (implicit request: IdentifierRequest[AnyContent]): Future[Result] =
    form(penaltySchemes).bindFromRequest().fold(
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
