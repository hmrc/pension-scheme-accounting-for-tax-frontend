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

package controllers.chargeF

import config.FrontendAppConfig
import connectors.cache.UserAnswersCacheConnector
import controllers.actions._
import forms.ChargeDetailsFormProvider
import javax.inject.Inject
import models.chargeF.ChargeDetails
import models.{GenericViewModel, Mode, UserAnswers}
import navigators.CompoundNavigator
import pages.{ChargeDetailsPage, SchemeNameQuery}
import play.api.data.Form
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import renderer.Renderer
import uk.gov.hmrc.play.bootstrap.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.{DateInput, NunjucksSupport}

import scala.concurrent.{ExecutionContext, Future}

class ChargeDetailsController @Inject()(override val messagesApi: MessagesApi,
                                        userAnswersCacheConnector: UserAnswersCacheConnector,
                                        navigator: CompoundNavigator,
                                        identify: IdentifierAction,
                                        getData: DataRetrievalAction,
                                        requireData: DataRequiredAction,
                                        formProvider: ChargeDetailsFormProvider,
                                        val controllerComponents: MessagesControllerComponents,
                                        config: FrontendAppConfig,
                                        renderer: Renderer
                                       )(implicit ec: ExecutionContext) extends FrontendBaseController with I18nSupport with NunjucksSupport {

  val form: Form[ChargeDetails] = formProvider()

  def onPageLoad(mode: Mode, srn: String): Action[AnyContent] = (identify andThen getData).async {
    implicit request =>
      val ua = request.userAnswers.getOrElse(UserAnswers(Json.obj()))

      val preparedForm: Form[ChargeDetails] = ua.get(ChargeDetailsPage) match {
        case Some(value) => form.fill(value)
        case None => form
      }
      ua.get(SchemeNameQuery) match {
        case Some(schemeName) =>
          val viewModel = GenericViewModel(
            submitUrl = routes.ChargeDetailsController.onSubmit(mode, srn).url,
            returnUrl = config.managePensionsSchemeSummaryUrl.format(srn),
            schemeName = schemeName)

          val json = Json.obj(
            "form" -> preparedForm,
            "viewModel" -> viewModel,
            "date" -> DateInput.localDate(preparedForm("deregistrationDate"))
          )

          renderer.render(template = "chargeF/chargeDetails.njk", json).map(Ok(_))
        case _ =>
          renderer.render(template = "session-expired.njk").map(Ok(_))
      }
  }

  def onSubmit(mode: Mode, srn: String): Action[AnyContent] = (identify andThen getData).async {
    implicit request =>
      val ua = request.userAnswers.getOrElse(UserAnswers(Json.obj()))
      ua.get(SchemeNameQuery) match {
        case Some(schemeName) =>
          form.bindFromRequest().fold(
            formWithErrors => {
              val viewModel = GenericViewModel(
                submitUrl = routes.ChargeDetailsController.onSubmit(mode, srn).url,
                returnUrl = config.managePensionsSchemeSummaryUrl.format(srn),
                schemeName = schemeName)

              val json = Json.obj(
                "form" -> formWithErrors,
                "viewModel" -> viewModel,
                "date" -> DateInput.localDate(formWithErrors("deregistrationDate"))
              )
              renderer.render(template = "chargeF/chargeDetails.njk", json).map(BadRequest(_))
            },
            value => {
              for {
                updatedAnswers <- Future.fromTry(ua.set(ChargeDetailsPage, value))
                _ <- userAnswersCacheConnector.save(request.internalId, updatedAnswers.data)
              } yield Redirect(navigator.nextPage(ChargeDetailsPage, mode, updatedAnswers, srn))
            }
          )
        case _ =>
          renderer.render(template = "session-expired.njk").map(Ok(_))
      }
  }
}
