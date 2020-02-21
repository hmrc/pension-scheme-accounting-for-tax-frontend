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

package controllers.chargeB

import config.FrontendAppConfig
import connectors.cache.UserAnswersCacheConnector
import controllers.DataRetrievals
import controllers.actions._
import forms.chargeB.ChargeDetailsFormProvider
import javax.inject.Inject
import models.chargeB.ChargeBDetails
import models.{GenericViewModel, Mode, UserAnswers}
import navigators.CompoundNavigator
import pages.chargeB.ChargeBDetailsPage
import play.api.data.Form
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import renderer.Renderer
import uk.gov.hmrc.play.bootstrap.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.NunjucksSupport

import scala.concurrent.{ExecutionContext, Future}

class ChargeDetailsController @Inject()(override val messagesApi: MessagesApi,
                                        userAnswersCacheConnector: UserAnswersCacheConnector,
                                        navigator: CompoundNavigator,
                                        identify: IdentifierAction,
                                        getData: DataRetrievalAction,
                                        allowAccess: AllowAccessActionProvider,
                                        requireData: DataRequiredAction,
                                        formProvider: ChargeDetailsFormProvider,
                                        val controllerComponents: MessagesControllerComponents,
                                        config: FrontendAppConfig,
                                        renderer: Renderer
                                       )(implicit ec: ExecutionContext) extends FrontendBaseController with I18nSupport with NunjucksSupport {

  private def form(ua:UserAnswers)(implicit messages: Messages): Form[ChargeBDetails] =
    formProvider(minimumChargeValueAllowed = UserAnswers.deriveMinimumChargeValueAllowed(ua))

  private def viewModel(mode: Mode, srn: String, startDate: LocalDate, schemeName: String): GenericViewModel =
    GenericViewModel(
      submitUrl = routes.ChargeDetailsController.onSubmit(mode, srn).url,
      returnUrl = config.managePensionsSchemeSummaryUrl.format(srn),
      schemeName = schemeName
    )

  def onPageLoad(mode: Mode, srn: String, startDate: LocalDate): Action[AnyContent] = (identify andThen getData(srn, startDate) andThen allowAccess(srn)  andThen requireData).async {
    implicit request =>
      DataRetrievals.retrieveSchemeName { schemeName =>

        val preparedForm: Form[ChargeBDetails] = request.userAnswers.get(ChargeBDetailsPage) match {
          case Some(value) => form(request.userAnswers).fill(value)
          case None => form(request.userAnswers)
        }

        val json = Json.obj(
          "srn" -> srn,
          "form" -> preparedForm,
          "viewModel" -> viewModel(mode, srn, schemeName)
        )

        renderer.render(template = "chargeB/chargeDetails.njk", json).map(Ok(_))
      }
  }

  def onSubmit(mode: Mode, srn: String, startDate: LocalDate): Action[AnyContent] = (identify andThen getData(srn, startDate) andThen requireData).async {
    implicit request =>
      DataRetrievals.retrieveSchemeName { schemeName =>

        form(request.userAnswers).bindFromRequest().fold(
          formWithErrors => {
            val json = Json.obj(
          "srn" -> srn,
          "form" -> formWithErrors,
              "viewModel" -> viewModel(mode, srn, schemeName)
            )
            renderer.render(template = "chargeB/chargeDetails.njk", json).map(BadRequest(_))
          },
          value =>
            for {
              updatedAnswers <- Future.fromTry(request.userAnswers.set(ChargeBDetailsPage, value))
              _ <- userAnswersCacheConnector.save(request.internalId, updatedAnswers.data)
            } yield Redirect(navigator.nextPage(ChargeBDetailsPage, mode, updatedAnswers, srn))
        )
      }
  }
}
