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

import java.time.LocalDate
import java.time.format.DateTimeFormatter

import config.FrontendAppConfig
import connectors.SchemeDetailsConnector
import connectors.cache.UserAnswersCacheConnector
import controllers.actions._
import forms.ChargeTypeFormProvider
import javax.inject.Inject
import models.{ChargeType, GenericViewModel, Mode, Quarter, UserAnswers}
import navigators.CompoundNavigator
import pages.{AFTStatusQuery, ChargeTypePage, PSTRQuery, QuarterPage, SchemeNameQuery}
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
  private val dateFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy")

  def onPageLoad(mode: Mode, srn: String): Action[AnyContent] = (identify andThen getData).async {
    implicit request =>

      val ua = request.userAnswers.getOrElse(UserAnswers())

      schemeDetailsConnector.getSchemeDetails(request.psaId.id, schemeIdType = "srn", srn).flatMap { schemeDetails =>

        Future.fromTry(ua.set(SchemeNameQuery, schemeDetails.schemeName).
          flatMap(_.set(PSTRQuery, schemeDetails.pstr)).flatMap(
          _.set(QuarterPage,
            Quarter(
              LocalDate.of(2020, 4, 1).format(dateFormatter),
              LocalDate.of(2020, 6, 30).format(dateFormatter))).flatMap(
            _.set(AFTStatusQuery, value = "Compiled"))
        )).flatMap { answers =>

          userAnswersCacheConnector.save(request.internalId, answers.data).flatMap { _ =>

            val preparedForm = ua.get(ChargeTypePage).fold(form)(form.fill)

            val json = Json.obj(
              fields = "form" -> preparedForm,
              "radios" -> ChargeType.radios(preparedForm),
              "viewModel" -> viewModel(schemeDetails.schemeName, mode, srn)
            )

            renderer.render(template = "chargeType.njk", json).map(Ok(_))
          }
        }
      }
  }

  def onSubmit(mode: Mode, srn: String): Action[AnyContent] = (identify andThen getData andThen requireData).async {
    implicit request =>
      DataRetrievals.retrieveSchemeName { schemeName =>

        form.bindFromRequest().fold(
          formWithErrors => {
            val json = Json.obj(
              fields = "form" -> formWithErrors,
              "radios" -> ChargeType.radios(formWithErrors),
              "viewModel" -> viewModel(schemeName, mode, srn)
            )
            renderer.render(template = "chargeType.njk", json).map(BadRequest(_))
          },
          value =>
            for {
              updatedAnswers <- Future.fromTry(request.userAnswers.set(ChargeTypePage, value))
              _ <- userAnswersCacheConnector.save(request.internalId, updatedAnswers.data)
            } yield Redirect(navigator.nextPage(ChargeTypePage, mode, updatedAnswers, srn))
        )
      }
  }

  private def viewModel(schemeName: String, mode: Mode, srn: String): GenericViewModel = {
    GenericViewModel(
      submitUrl = routes.ChargeTypeController.onSubmit(mode, srn).url,
      returnUrl = config.managePensionsSchemeSummaryUrl.format(srn),
      schemeName = schemeName
    )
  }
}
