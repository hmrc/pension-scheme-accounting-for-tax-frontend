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

package controllers.chargeA

import java.time.LocalDate

import models.LocalDateBinder._
import config.FrontendAppConfig
import connectors.cache.UserAnswersCacheConnector
import controllers.DataRetrievals
import controllers.actions._
import forms.chargeA.ChargeDetailsFormProvider
import javax.inject.Inject
import models.SessionData
import models.chargeA.ChargeDetails
import models.GenericViewModel
import models.Mode
import navigators.CompoundNavigator
import pages.chargeA.ChargeDetailsPage
import play.api.data.Form
import play.api.i18n.I18nSupport
import play.api.i18n.Messages
import play.api.i18n.MessagesApi
import play.api.libs.json.Json
import play.api.mvc.Action
import play.api.mvc.AnyContent
import play.api.mvc.MessagesControllerComponents
import renderer.Renderer
import uk.gov.hmrc.play.bootstrap.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.NunjucksSupport

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

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
                                        renderer: Renderer)(implicit ec: ExecutionContext)
    extends FrontendBaseController
    with I18nSupport
    with NunjucksSupport {

  private def form(minimumChargeValue:BigDecimal)(implicit messages: Messages): Form[ChargeDetails] =
    formProvider(minimumChargeValueAllowed = minimumChargeValue)

  private def viewModel(mode: Mode, srn: String, startDate: LocalDate, schemeName: String): GenericViewModel =
    GenericViewModel(
      submitUrl = routes.ChargeDetailsController.onSubmit(mode, srn, startDate).url,
      returnUrl = controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate).url,
      schemeName = schemeName
    )

  def onPageLoad(mode: Mode, srn: String, startDate: LocalDate): Action[AnyContent] =
    (identify andThen getData(srn, startDate) andThen requireData andThen allowAccess(srn, startDate)).async { implicit request =>
      DataRetrievals.retrieveSchemeName { schemeName =>

        val mininimumChargeValue:BigDecimal = request.sessionData.deriveMinimumChargeValueAllowed

        val preparedForm: Form[ChargeDetails] = request.userAnswers.get(ChargeDetailsPage) match {
          case Some(value) => form(mininimumChargeValue).fill(value)
          case None        => form(mininimumChargeValue)
        }

        val json = Json.obj(
          "srn" -> srn,
          "startDate" -> Some(startDate),
          "form" -> preparedForm,
          "viewModel" -> viewModel(mode, srn, startDate, schemeName)
        )

        renderer.render(template = "chargeA/chargeDetails.njk", json).map(Ok(_))
      }
    }

  def onSubmit(mode: Mode, srn: String, startDate: LocalDate): Action[AnyContent] =
    (identify andThen getData(srn, startDate) andThen requireData).async { implicit request =>
      DataRetrievals.retrieveSchemeName { schemeName =>
        val mininimumChargeValue:BigDecimal = request.sessionData.deriveMinimumChargeValueAllowed
        form(mininimumChargeValue)
          .bindFromRequest()
          .fold(
            formWithErrors => {
              val json = Json.obj(
                "srn" -> srn,
                "startDate" -> Some(startDate),
                "form" -> formWithErrors.copy(errors = formWithErrors.errors.distinct),
                "viewModel" -> viewModel(mode, srn, startDate, schemeName)
              )
              renderer.render(template = "chargeA/chargeDetails.njk", json).map(BadRequest(_))
            },
            value =>
              for {
                updatedAnswers <- Future.fromTry(request.userAnswers.set(ChargeDetailsPage, value))
                _ <- userAnswersCacheConnector.save(request.internalId, updatedAnswers.data)
              } yield Redirect(navigator.nextPage(ChargeDetailsPage, mode, updatedAnswers, srn, startDate))
          )
      }
    }
}
