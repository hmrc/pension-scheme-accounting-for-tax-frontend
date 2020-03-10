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

package controllers.testonly

import java.time.LocalDate

import com.google.inject.Inject
import com.google.inject.Singleton
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.I18nSupport
import play.api.i18n.MessagesApi
import play.api.libs.json.Json
import play.api.mvc.Action
import play.api.mvc.AnyContent
import play.api.mvc.MessagesControllerComponents
import renderer.Renderer
import uk.gov.hmrc.play.bootstrap.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.NunjucksSupport
import utils.DateHelper

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

@Singleton
class DateTestController @Inject()(
    override val messagesApi: MessagesApi,
    renderer: Renderer,
    val controllerComponents: MessagesControllerComponents
)(implicit ec: ExecutionContext)
    extends FrontendBaseController
    with I18nSupport
    with NunjucksSupport {

  val form: Form[Option[LocalDate]] = Form("testDate" -> optional(localDate("d MMMM yyyy")))

  def present: Action[AnyContent] = Action.async { implicit request =>
    val json = Json.obj(
      "form" -> form.fill(DateHelper.overriddenDate),
      "submitUrl" -> controllers.testonly.routes.DateTestController.submit().url
    )
    renderer.render(template = "testonly/dateTest.njk", json).map(Ok(_))
  }

  def submit: Action[AnyContent] = Action.async { implicit request =>
    form.bindFromRequest.fold(
      invalidForm => {
        val json = Json.obj(
          "form" -> invalidForm
        )
        renderer.render(template = "testonly/dateTest.njk", json).map(BadRequest(_))
      },
      date => {
        DateHelper.setDate(date)
        Future.successful(Redirect(controllers.testonly.routes.DateTestController.present()))
      }
    )
  }
}
