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
import play.api.data.Form
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import renderer.Renderer
import services.PenaltiesService
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.NunjucksSupport

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class SelectSchemeController @Inject()(
                                        identify: IdentifierAction,
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

  def onPageLoad(startDate: String): Action[AnyContent] = identify.async {
    implicit request =>
      penaltiesService.penaltySchemes(startDate, request.psaIdOrException.id).flatMap {
        penaltySchemes =>

          if (penaltySchemes.nonEmpty) {

            val json = Json.obj(
              "form" -> form(penaltySchemes),
              "radios" -> PenaltySchemes.radios(form(penaltySchemes), penaltySchemes),
              "submitUrl" -> controllers.financialStatement.penalties.routes.SelectSchemeController.onSubmit(startDate).url)

            renderer.render(template = "financialStatement/penalties/selectScheme.njk", json).map(Ok(_))
          } else {
            Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad()))
          }
      }
  }

  def onSubmit(startDate: String): Action[AnyContent] = identify.async {
    implicit request =>
      penaltiesService.penaltySchemes(startDate, request.psaIdOrException.id).flatMap {
        penaltySchemes =>
          form(penaltySchemes).bindFromRequest().fold(
            formWithErrors => {

              val json = Json.obj(
                "form" -> formWithErrors,
                "radios" -> PenaltySchemes.radios(formWithErrors, penaltySchemes),
                "submitUrl" -> controllers.financialStatement.penalties.routes.SelectSchemeController.onSubmit(startDate).url)

              renderer.render(template = "financialStatement/penalties/selectScheme.njk", json).map(BadRequest(_))
            },
            value => {
                value.srn match {
                  case Some(srn) =>
                    Future.successful(Redirect(controllers.financialStatement.penalties.routes.PenaltiesController.onPageLoad(startDate, srn)))
                  case _ =>
                    penaltiesService.getPenaltiesFromCache(request.psaIdOrException.id).map { penalties =>
                      val pstrIndex: String = penalties.map(_.pstr).indexOf(value.pstr).toString
                      Redirect(controllers.financialStatement.penalties.routes.PenaltiesController.onPageLoad(startDate, pstrIndex))
                }
              }
            }
          )
      }
  }
}
