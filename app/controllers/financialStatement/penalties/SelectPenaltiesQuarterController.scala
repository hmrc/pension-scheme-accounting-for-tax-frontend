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

import config.FrontendAppConfig
import connectors.AFTConnector
import controllers.actions._
import forms.QuartersFormProvider
import models.LocalDateBinder._
import models.financialStatement.PsaFS
import models.requests.IdentifierRequest
import models.{DisplayQuarter, GenericViewModel, Quarter, Quarters}
import play.api.data.Form
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import renderer.Renderer
import services.{PenaltiesService, QuartersService, SchemeService}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.NunjucksSupport

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class SelectPenaltiesQuarterController @Inject()(
                                                  override val messagesApi: MessagesApi,
                                                  identify: IdentifierAction,
                                                  formProvider: QuartersFormProvider,
                                                  val controllerComponents: MessagesControllerComponents,
                                                  renderer: Renderer,
                                                  config: FrontendAppConfig,
                                                  quartersService: QuartersService,
                                                  penaltiesService: PenaltiesService,
                                                  aftConnector: AFTConnector
)(implicit ec: ExecutionContext)
    extends FrontendBaseController
    with I18nSupport
    with NunjucksSupport {

  private def form(quarters: Seq[Quarter])(implicit messages: Messages): Form[Quarter] =
    formProvider(messages("amendQuarters.error.required"), quarters)

  def onPageLoad(year: String): Action[AnyContent] = identify.async { implicit request =>
    penaltiesService.getPenaltiesFromCache.flatMap { penalties =>

      val quarters: Seq[Quarter] = getQuarters(year, penalties)

        if (quarters.nonEmpty) {

          val json = Json.obj(
            "form" -> form(quarters),
            "radios" -> Quarters.radios(form(quarters), getDisplayQuarters(year, penalties)),
            "submitUrl" -> routes.SelectPenaltiesQuarterController.onSubmit(year).url,
            "year" -> year
          )

          renderer.render(template = "amend/amendQuarters.njk", json).map(Ok(_))
        } else {
          Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad()))
        }

    }
  }

  def onSubmit(year: String): Action[AnyContent] = identify.async { implicit request =>
    penaltiesService.getPenaltiesFromCache.flatMap { penalties =>

      val quarters: Seq[Quarter] = getQuarters(year, penalties)
        if (quarters.nonEmpty) {


          form(quarters)
            .bindFromRequest()
            .fold(
              formWithErrors => {

                  val json = Json.obj(
                    "form" -> formWithErrors,
                    "radios" -> Quarters.radios(formWithErrors, getDisplayQuarters(year, penalties)),
                    "submitUrl" -> routes.SelectPenaltiesQuarterController.onSubmit(year).url,
                    "year" -> year
                  )
                  renderer.render(template = "amend/amendQuarters.njk", json).map(BadRequest(_))

              },
              value => {
                Future.successful(Redirect(controllers.amend.routes.ReturnHistoryController.onPageLoad(srn, value.startDate)))
              }
            )
        } else {
          Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad()))
        }
    }
  }

  private def getDisplayQuarters(year: String, penalties: Seq[PsaFS]): Seq[DisplayQuarter] =
    penalties.filter(_.periodStartDate.getYear == year.toInt).distinct
      .map(penalty => Quarters.getQuarter(penalty.periodStartDate))

  private def getQuarters(year: String, penalties: Seq[PsaFS]): Seq[Quarter] =
    penalties.filter(_.periodStartDate.getYear == year.toInt).distinct
      .map(penalty => Quarters.getQuarter(penalty.periodStartDate))
}
