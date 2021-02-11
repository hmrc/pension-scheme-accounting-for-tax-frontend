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
import controllers.actions._
import forms.YearsFormProvider
import models.{AmendYears, Year}
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

class SelectPenaltiesYearController @Inject()(override val messagesApi: MessagesApi,
                                              identify: IdentifierAction,
                                              formProvider: YearsFormProvider,
                                              val controllerComponents: MessagesControllerComponents,
                                              renderer: Renderer,
                                              config: FrontendAppConfig,
                                              service: PenaltiesService)
                                             (implicit ec: ExecutionContext) extends FrontendBaseController
                                                                      with I18nSupport
                                                                      with NunjucksSupport {

  private def form(implicit config: FrontendAppConfig): Form[Year] = formProvider()

  def onPageLoad: Action[AnyContent] = identify.async { implicit request =>
    service.getPenaltiesFromCache.flatMap { penalties =>
      val years = penalties.map(_.periodStartDate.getYear).distinct.sorted.reverse
      val json = Json.obj(
        "form" -> form(config),
        "radios" -> AmendYears.radios(form(config), years),
        "submitUrl" -> routes.SelectPenaltiesYearController.onSubmit().url
      )

      renderer.render(template = "financialStatement/penalties/selectYear.njk", json).map(Ok(_))
    }
  }

  def onSubmit: Action[AnyContent] = identify.async { implicit request =>
    form(config).bindFromRequest().fold(
        formWithErrors =>

          service.getPenaltiesFromCache.flatMap { penalties =>
              val years = penalties.map(_.periodStartDate.getYear).distinct.sorted.reverse
              val json = Json.obj(
                "form" -> formWithErrors,
                "radios" -> AmendYears.radios(formWithErrors, years),
                "submitUrl" -> routes.SelectPenaltiesYearController.onSubmit().url
              )
              renderer.render(template = "financialStatement/penalties/selectYear.njk", json).map(BadRequest(_))
        },
        value => Future.successful(Redirect(routes.SelectPenaltiesQuarterController.onPageLoad(value.getYear.toString)))
      )
  }

}
