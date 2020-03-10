/*
 * Copyright 2020 HM Revenue & Customs
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

package controllers

import java.time.LocalDate
import java.time.format.DateTimeFormatter

import config.FrontendAppConfig
import controllers.actions.DataRequiredAction
import controllers.actions.DataRetrievalAction
import controllers.actions.IdentifierAction
import javax.inject.Inject
import play.api.i18n.I18nSupport
import play.api.libs.json.Json
import play.api.mvc.Action
import play.api.mvc.AnyContent
import play.api.mvc.MessagesControllerComponents
import renderer.Renderer
import uk.gov.hmrc.play.bootstrap.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.NunjucksSupport

import scala.concurrent.ExecutionContext
import models.LocalDateBinder._

class CannotChangeAFTReturnController @Inject()(
    identify: IdentifierAction,
    getData: DataRetrievalAction,
    requireData: DataRequiredAction,
    val controllerComponents: MessagesControllerComponents,
    renderer: Renderer,
    config: FrontendAppConfig
)(implicit ec: ExecutionContext)
    extends FrontendBaseController
    with I18nSupport
    with NunjucksSupport {

  private val dateFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy")
  private val dateFormatterStartDate = DateTimeFormatter.ofPattern("d MMMM")

  def onPageLoad(srn: String, startDate: LocalDate, optionVersion: Option[String]): Action[AnyContent] =
    (identify andThen getData(srn, startDate) andThen requireData).async { implicit request =>
      DataRetrievals.retrieveSchemeAndQuarter { (schemeName, quarter) =>
        renderer
          .render(
            "cannot-change-aft-return.njk",
            Json.obj(
              "schemeName" -> schemeName,
              "returnUrl" -> config.managePensionsSchemeSummaryUrl.format(srn),
              "quarterStart" -> quarter.startDate.format(dateFormatterStartDate),
              "quarterEnd" -> quarter.endDate.format(dateFormatter),
              "viewVersionURL" -> controllers.routes.AFTSummaryController.onPageLoad(srn, startDate, optionVersion).url
            )
          )
          .map(Ok(_))
      }
    }
}
