/*
 * Copyright 2019 HM Revenue & Customs
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

import connectors.cache.UserAnswersCacheConnector
import controllers.actions._
import forms.ChargeDetailsFormProvider
import javax.inject.Inject
import models.{Mode, UserAnswers}
import models.chargeF.ChargeDetails
import navigators.{CompoundNavigator, Navigator}
import pages.ChargeDetailsPage
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import renderer.Renderer
import uk.gov.hmrc.play.bootstrap.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.{DateInput, NunjucksSupport}

import scala.concurrent.{ExecutionContext, Future}

class ChargeDetailsController @Inject()(
                                         override val messagesApi: MessagesApi,
                                         userAnswersCacheConnector: UserAnswersCacheConnector,
                                         navigator: CompoundNavigator,
                                         identify: IdentifierAction,
                                         getData: DataRetrievalAction,
                                         requireData: DataRequiredAction,
                                         formProvider: ChargeDetailsFormProvider,
                                         val controllerComponents: MessagesControllerComponents,
                                         renderer: Renderer
)(implicit ec: ExecutionContext) extends FrontendBaseController with I18nSupport with NunjucksSupport {

  val form = formProvider()

  def onPageLoad(mode: Mode, srn: String): Action[AnyContent] = (identify andThen getData).async {
    implicit request =>
      
      val ua = request.userAnswers.getOrElse(UserAnswers(Json.obj()))

      val preparedForm = ua.get(ChargeDetailsPage) match {
        case Some(value) => form.fill(ChargeDetails(value))
        case None        => form
      }

      println("\n\n\n "+preparedForm)

      val viewModel = DateInput.localDate(preparedForm("value"))

      val json = Json.obj(
        "form" -> preparedForm,
        "date" -> viewModel
      )

      renderer.render("chargeDetails.njk", json).map(Ok(_))
  }

  def onSubmit(mode: Mode, srn: String): Action[AnyContent] = (identify andThen getData).async {
    implicit request =>

      val ua = request.userAnswers.getOrElse(UserAnswers(Json.obj()))

      form.bindFromRequest().fold(
        formWithErrors =>  {

          val viewModel = DateInput.localDate(formWithErrors("value"))

          val json = Json.obj(
            "form" -> formWithErrors,
            "date" -> viewModel
          )

          renderer.render("chargeDetails.njk", json).map(BadRequest(_))
        },
        value =>
          for {
            updatedAnswers <- Future.fromTry(ua.set(ChargeDetailsPage, value.deRegistrationDate))
            _              <- userAnswersCacheConnector.save(request.internalId, updatedAnswers.data)
          } yield Redirect(navigator.nextPage(ChargeDetailsPage, mode, updatedAnswers))
      )
  }
}
