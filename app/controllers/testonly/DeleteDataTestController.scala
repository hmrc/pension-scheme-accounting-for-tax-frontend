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

package controllers.testonly

import com.google.inject.{Inject, Singleton}
import connectors.cache.UserAnswersCacheConnector
import forms.mappings.Mappings
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.{Format, Json}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import renderer.Renderer
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.{DateInput, NunjucksSupport}

import java.time.LocalDate
import scala.concurrent.ExecutionContext

@Singleton
class DeleteDataTestController @Inject()(
                                          override val messagesApi: MessagesApi,
                                          renderer: Renderer,
                                          userAnswersCacheConnector: UserAnswersCacheConnector,
                                          val controllerComponents: MessagesControllerComponents
                                        )(implicit ec: ExecutionContext)
  extends FrontendBaseController
    with I18nSupport
    with NunjucksSupport
    with Mappings {

  def form =
    Form(
      mapping(
        "srn" -> text("Enter the scheme reference number"),
        "startDate" -> localDate(
          invalidKey = "Enter a real quarter start date",
          allRequiredKey = "Enter the quarter start date",
          twoRequiredKey = "The quarter start date must include a day, month and year",
          requiredKey = "Enter the quarter start date"
        )
      )(MongoId.apply)(MongoId.unapply))

  def present: Action[AnyContent] = Action.async { implicit request =>
    val json = Json.obj(
      "form" -> form,
      "date" -> DateInput.localDate(form("startDate")),
      "submitUrl" -> controllers.testonly.routes.DeleteDataTestController.delete().url
    )
    renderer.render(template = "testonly/deleteData.njk", json).map(Ok(_))
  }

  def delete: Action[AnyContent] = Action.async { implicit request =>
    form.bindFromRequest().fold(
      invalidForm => {
        val json = Json.obj(
          "form" -> invalidForm,
          "date" -> DateInput.localDate(invalidForm("startDate"))
        )
        renderer.render(template = "testonly/deleteData.njk", json).map(BadRequest(_))
      },
      value => {
        val id = s"${value.srn}${value.startDate}"
        userAnswersCacheConnector.removeAll(id).map { _ =>
          Redirect(controllers.testonly.routes.DeleteDataTestController.present())
        }
      }
    )
  }
}

case class MongoId(srn: String, startDate: LocalDate)

object MongoId {
  implicit lazy val formats: Format[MongoId] =
    Json.format[MongoId]
}
