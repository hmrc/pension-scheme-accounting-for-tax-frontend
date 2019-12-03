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

import config.FrontendAppConfig
import connectors.SchemeDetailsConnector
import connectors.cache.UserAnswersCacheConnector
import controllers.actions._
import forms.ChargeTypeFormProvider
import javax.inject.Inject
import models.{ChargeType, Mode, UserAnswers}
import navigators.CompoundNavigator
import pages.{ChargeTypePage, SchemeNameQuery}
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import renderer.Renderer
import uk.gov.hmrc.play.bootstrap.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.NunjucksSupport

import scala.concurrent.{ExecutionContext, Future}

class ChargeTypeController @Inject()(
                                      override val messagesApi: MessagesApi,
                                      userAnswersCacheConnector: UserAnswersCacheConnector,
                                      navigator: CompoundNavigator,
                                      identify: IdentifierAction,
                                      getData: DataRetrievalAction,
                                      requireData: DataRequiredAction,
                                      formProvider: ChargeTypeFormProvider,
                                      val controllerComponents: MessagesControllerComponents,
                                      renderer: Renderer,
                                      config: FrontendAppConfig,
                                      schemeDetailsConnector: SchemeDetailsConnector
                                    )(implicit ec: ExecutionContext) extends FrontendBaseController with I18nSupport with NunjucksSupport {

  private val form = formProvider()

  def onPageLoad(mode: Mode, srn: String): Action[AnyContent] = (identify andThen getData).async {
    implicit request =>
      val ua = request.userAnswers.getOrElse(UserAnswers(Json.obj()))
      schemeDetailsConnector.getSchemeName(request.psaId.id, "srn", srn).flatMap { schemeName =>
        Future.fromTry(ua.set(SchemeNameQuery, schemeName)).flatMap { answers =>
          userAnswersCacheConnector.save(request.internalId, answers.data).flatMap { _ =>
            val preparedForm = ua.get(ChargeTypePage) match {
              case None => form
              case Some(value) => form.fill(value)
            }

            val json = Json.obj(
              "form" -> preparedForm,
              "submitUrl" -> routes.ChargeTypeController.onSubmit(mode, srn).url,
              "returnUrl" -> config.managePensionsSchemeSummaryUrl.format(srn),
              "radios" -> ChargeType.radios(preparedForm),
              "schemeName" -> schemeName
            )

            renderer.render(template = "chargeType.njk", json).map(Ok(_))
          }
        }
      }
  }

  def onSubmit(mode: Mode, srn: String): Action[AnyContent] = (identify andThen getData).async {
    implicit request =>
      val ua = request.userAnswers.getOrElse(UserAnswers(Json.obj()))
      form.bindFromRequest().fold(
        formWithErrors => {
         val schemeName = ua.get[String](SchemeNameQuery).getOrElse("")
          val json = Json.obj(
            "form" -> formWithErrors,
            "submitUrl" -> routes.ChargeTypeController.onSubmit(mode, srn).url,
            "returnUrl" -> config.managePensionsSchemeSummaryUrl.format(srn),
            "radios" -> ChargeType.radios(formWithErrors),
            "schemeName" -> schemeName
          )

          renderer.render(template = "chargeType.njk", json).map(BadRequest(_))
        },
        value =>
          for {
            updatedAnswers <- Future.fromTry(ua.set(ChargeTypePage, value))
            _ <- userAnswersCacheConnector.save(request.internalId, updatedAnswers.data)
          } yield Redirect(navigator.nextPage(ChargeTypePage, mode, updatedAnswers, srn))
      )
  }
}
