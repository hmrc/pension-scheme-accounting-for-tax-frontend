/*
 * Copyright 2022 HM Revenue & Customs
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

package controllers.mccloud

import config.FrontendAppConfig
import connectors.SchemeDetailsConnector
import connectors.cache.UserAnswersCacheConnector
import controllers.DataRetrievals
import controllers.actions._
import forms.mccloud.EnterPstrFormProvider
import helpers.ChargeServiceHelper
import models.LocalDateBinder._
import models.{AccessType, ChargeType, GenericViewModel, Index, Mode}
import navigators.CompoundNavigator
import pages.mccloud.EnterPstrPage
import play.api.data.Form
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import renderer.Renderer
import services.UserAnswersService
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.NunjucksSupport

import java.time.LocalDate
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class EnterPstrController @Inject()(override val messagesApi: MessagesApi,
                                    userAnswersCacheConnector: UserAnswersCacheConnector,
                                    userAnswersService: UserAnswersService,
                                    navigator: CompoundNavigator,
                                    identify: IdentifierAction,
                                    getData: DataRetrievalAction,
                                    allowAccess: AllowAccessActionProvider,
                                    requireData: DataRequiredAction,
                                    formProvider: EnterPstrFormProvider,
                                    schemeDetailsConnector: SchemeDetailsConnector,
                                    val controllerComponents: MessagesControllerComponents,
                                    chargeServiceHelper: ChargeServiceHelper,
                                    config: FrontendAppConfig,
                                    renderer: Renderer)(implicit ec: ExecutionContext)
  extends FrontendBaseController
    with I18nSupport
    with NunjucksSupport {

  private def form(): Form[String] = formProvider()

  def onPageLoad(chargeType: ChargeType,
                 mode: Mode,
                 srn: String,
                 startDate: LocalDate,
                 accessType: AccessType,
                 version: Int,
                 index: Index): Action[AnyContent] =
    (identify andThen getData(srn, startDate) andThen requireData andThen allowAccess(srn, startDate, None, version, accessType)).async { implicit request =>
      DataRetrievals.retrieveSchemeName { schemeName =>

      val chargeTypeDescription = Messages(s"chargeType.description.${chargeType.toString}")

        val viewModel = GenericViewModel(
          submitUrl = routes.EnterPstrController.onSubmit(chargeType, mode, srn, startDate, accessType, version, index).url,
          returnUrl = controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, version).url,
          schemeName = schemeName
        )

        val preparedForm = request.userAnswers.get(EnterPstrPage(chargeType, index )) match {
          case Some(value) => form().fill(value)
          case None => form()
        }

        val json = Json.obj(
          "srn" -> srn,
          "startDate" -> Some(localDateToString(startDate)),
          "form" -> preparedForm,
          "viewModel" -> viewModel,
          "chargeTypeDescription" -> chargeTypeDescription
        )

        renderer.render("mccloud/enterPstr.njk", json).map(Ok(_))
      }
    }

    def onSubmit(chargeType: ChargeType,
                 mode: Mode,
                 srn: String,
                 startDate: LocalDate,
                 accessType: AccessType,
                 version: Int,
                 index: Index): Action[AnyContent] =
      (identify andThen getData(srn, startDate) andThen requireData).async { implicit request =>
        DataRetrievals.retrieveSchemeName{ schemeName =>

          form()
            .bindFromRequest()
            .fold(
              formWithErrors => {

                val viewModel = GenericViewModel(
                  submitUrl = routes.EnterPstrController.onSubmit(chargeType, mode, srn, startDate, accessType, version, index).url,
                  returnUrl = controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, version).url,
                  schemeName = schemeName
                )

                val json = Json.obj(
                  "srn" -> srn,
                  "startDate" -> Some(localDateToString(startDate)),
                  "form" -> formWithErrors,
                  "viewModel" -> viewModel
                )

                renderer.render("mccloud/enterPstr.njk", json).map(BadRequest(_))
              },
              value =>
                for {
                  updatedAnswers <- Future.fromTry(userAnswersService.set(EnterPstrPage(chargeType, index), value, mode))
                  _ <- userAnswersCacheConnector.savePartial(request.internalId, updatedAnswers.data,
                    chargeType = Some(chargeType), memberNo = Some(index.id))
                } yield Redirect(navigator.nextPage(EnterPstrPage(chargeType, index), mode, updatedAnswers, srn, startDate, accessType, version))
            )

        }
      }
}
