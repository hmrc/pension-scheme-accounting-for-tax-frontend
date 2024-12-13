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

package controllers.chargeA

import java.time.LocalDate
import config.FrontendAppConfig
import connectors.cache.UserAnswersCacheConnector
import controllers.DataRetrievals
import controllers.actions._
import forms.chargeA.ChargeDetailsFormProvider
import helpers.DeleteChargeHelper

import javax.inject.Inject
import models.LocalDateBinder._
import models.chargeA.ChargeDetails
import models.{AccessType, ChargeType, GenericViewModel, Mode}
import navigators.CompoundNavigator
import pages.chargeA.ChargeDetailsPage
import play.api.data.Form
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import renderer.Renderer
import services.UserAnswersService
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.NunjucksSupport
import views.html.chargeA.ChargeDetailsView

import scala.concurrent.{ExecutionContext, Future}

class ChargeDetailsController @Inject()(override val messagesApi: MessagesApi,
                                        userAnswersCacheConnector: UserAnswersCacheConnector,
                                        userAnswersService: UserAnswersService,
                                        navigator: CompoundNavigator,
                                        identify: IdentifierAction,
                                        getData: DataRetrievalAction,
                                        allowAccess: AllowAccessActionProvider,
                                        requireData: DataRequiredAction,
                                        formProvider: ChargeDetailsFormProvider,
                                        val controllerComponents: MessagesControllerComponents,
                                        deleteChargeHelper: DeleteChargeHelper,
                                        config: FrontendAppConfig,
                                        view : ChargeDetailsView,
                                        renderer: Renderer)(implicit ec: ExecutionContext)
    extends FrontendBaseController
    with I18nSupport
    with NunjucksSupport {

  private def form(minimumChargeValue:BigDecimal)(implicit messages: Messages): Form[ChargeDetails] =
    formProvider(minimumChargeValueAllowed = minimumChargeValue)

  private def viewModel(mode: Mode, srn: String, startDate: LocalDate, schemeName: String, accessType: AccessType, version: Int): GenericViewModel =
    GenericViewModel(
      submitUrl = routes.ChargeDetailsController.onSubmit(mode, srn, startDate, accessType, version).url,
      returnUrl = controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, version).url,
      schemeName = schemeName
    )

  def onPageLoad(mode: Mode, srn: String, startDate: LocalDate, accessType: AccessType, version: Int): Action[AnyContent] =
    (identify andThen getData(srn, startDate) andThen requireData andThen allowAccess(srn, startDate, None, version, accessType)).async { implicit request =>
      DataRetrievals.retrieveSchemeName { _ =>

        val mininimumChargeValue:BigDecimal = request.sessionData.deriveMinimumChargeValueAllowed
        def shouldPrepop(chargeDetails: ChargeDetails): Boolean =
          chargeDetails.totalAmount > BigDecimal(0.00) || deleteChargeHelper.isLastCharge(request.userAnswers)

        val preparedForm: Form[ChargeDetails] = request.userAnswers.get(ChargeDetailsPage) match {
          case Some(value) if shouldPrepop(value) => form(mininimumChargeValue).fill(value)
          case _        => form(mininimumChargeValue)
        }

        Future.successful(Ok(view(preparedForm,
          routes.ChargeDetailsController.onSubmit(mode, srn, startDate, accessType, version))))
      }
    }

  def onSubmit(mode: Mode, srn: String, startDate: LocalDate, accessType: AccessType, version: Int): Action[AnyContent] =
    (identify andThen getData(srn, startDate) andThen requireData andThen allowAccess(srn, startDate, None, version, accessType)).async { implicit request =>
      DataRetrievals.retrieveSchemeName { schemeName =>
        val mininimumChargeValue:BigDecimal = request.sessionData.deriveMinimumChargeValueAllowed
        form(mininimumChargeValue)
          .bindFromRequest()
          .fold(
            formWithErrors => {
              val json = Json.obj(
                "srn" -> srn,
                "startDate" -> Some(localDateToString(startDate)),
                "form" -> formWithErrors.copy(errors = formWithErrors.errors.distinct),
                "viewModel" -> viewModel(mode, srn, startDate, schemeName, accessType, version)
              )
//              renderer.render(template = "chargeA/chargeDetails.njk", json).map(BadRequest(_))
              println("======> "+ formWithErrors.copy(errors = formWithErrors.errors.distinct))
              Future.successful(BadRequest(view(formWithErrors.copy(errors = formWithErrors.errors.distinct),
                routes.ChargeDetailsController.onSubmit(mode, srn, startDate, accessType, version))))
            },
            value =>
              for {
                updatedAnswers <- Future.fromTry(userAnswersService.set(ChargeDetailsPage, value, mode, isMemberBased = false))
                _ <- userAnswersCacheConnector.savePartial(request.internalId, updatedAnswers.data,
                  chargeType = Some(ChargeType.ChargeTypeShortService))
              } yield Redirect(navigator.nextPage(ChargeDetailsPage, mode, updatedAnswers, srn, startDate, accessType, version))
          )
      }
    }
}
