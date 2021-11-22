/*
 * Copyright 2021 HM Revenue & Customs
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

import java.time.LocalDate
import connectors.cache.UserAnswersCacheConnector
import controllers.DataRetrievals
import controllers.actions._
import forms.chargeB.ChargeDetailsFormProvider
import helpers.DeleteChargeHelper

import javax.inject.Inject
import models.LocalDateBinder._
import models.{ChargeType, Mode, AccessType, GenericViewModel}
import models.chargeB.ChargeBDetails
import navigators.CompoundNavigator
import pages.chargeB.ChargeBDetailsPage
import play.api.data.Form
import play.api.i18n.{MessagesApi, I18nSupport}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import renderer.Renderer
import services.UserAnswersService
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.NunjucksSupport

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
                                        renderer: Renderer)(implicit ec: ExecutionContext)
    extends FrontendBaseController
    with I18nSupport
    with NunjucksSupport {

  private def form(minimumChargeValue:BigDecimal): Form[ChargeBDetails] =
    formProvider(minimumChargeValueAllowed = minimumChargeValue)

  private def viewModel(mode: Mode, srn: String, startDate: LocalDate, schemeName: String, accessType: AccessType, version: Int): GenericViewModel =
    GenericViewModel(
      submitUrl = routes.ChargeDetailsController.onSubmit(mode, srn, startDate, accessType, version).url,
      returnUrl = controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, version).url,
      schemeName = schemeName
    )

  def onPageLoad(mode: Mode, srn: String, startDate: LocalDate, accessType: AccessType, version: Int): Action[AnyContent] =
    (identify andThen getData(srn, startDate) andThen requireData andThen allowAccess(srn, startDate, None, version, accessType)).async { implicit request =>
      DataRetrievals.retrieveSchemeName { schemeName =>

        val mininimumChargeValue:BigDecimal = request.sessionData.deriveMinimumChargeValueAllowed
        def shouldPrepop(chargeDetails: ChargeBDetails): Boolean =
          chargeDetails.totalAmount > BigDecimal(0.00) || deleteChargeHelper.isLastCharge(request.userAnswers)

        val preparedForm: Form[ChargeBDetails] = request.userAnswers.get(ChargeBDetailsPage) match {
          case Some(value) if shouldPrepop(value) => form(mininimumChargeValue).fill(value)
          case _        => form(mininimumChargeValue)
        }

        val json = Json.obj(
          "srn" -> srn,
          "startDate" -> Some(localDateToString(startDate)),
          "form" -> preparedForm,
          "viewModel" -> viewModel(mode, srn, startDate, schemeName, accessType, version)
        )

        renderer.render(template = "chargeB/chargeDetails.njk", json).map(Ok(_))
      }
    }

  def onSubmit(mode: Mode, srn: String, startDate: LocalDate, accessType: AccessType, version: Int): Action[AnyContent] =
    (identify andThen getData(srn, startDate) andThen requireData).async { implicit request =>
      DataRetrievals.retrieveSchemeName { schemeName =>

        val mininimumChargeValue:BigDecimal = request.sessionData.deriveMinimumChargeValueAllowed

        form(mininimumChargeValue)
          .bindFromRequest()
          .fold(
            formWithErrors => {
              val json = Json.obj(
                "srn" -> srn,
                "startDate" -> Some(localDateToString(startDate)),
                "form" -> formWithErrors,
                "viewModel" -> viewModel(mode, srn, startDate, schemeName, accessType, version)
              )
              renderer.render(template = "chargeB/chargeDetails.njk", json).map(BadRequest(_))
            },
            value =>
              for {
                updatedAnswers <- Future.fromTry(userAnswersService.set(ChargeBDetailsPage, value, mode, isMemberBased = false))
                _ <- userAnswersCacheConnector.saveCharge(request.internalId, updatedAnswers.data, ChargeType.ChargeTypeLumpSumDeath)
              } yield Redirect(navigator.nextPage(ChargeBDetailsPage, mode, updatedAnswers, srn, startDate, accessType, version))
          )
      }
    }
}
