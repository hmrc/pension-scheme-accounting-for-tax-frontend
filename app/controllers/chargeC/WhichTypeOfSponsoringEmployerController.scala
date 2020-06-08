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

package controllers.chargeC

import java.time.LocalDate

import config.FrontendAppConfig
import connectors.cache.UserAnswersCacheConnector
import controllers.DataRetrievals
import controllers.actions._
import forms.chargeC.IsSponsoringEmployerIndividualFormProvider
import javax.inject.Inject
import models.LocalDateBinder._
import models.{AccessType, GenericViewModel, Index, Mode, SponsoringEmployerType}
import navigators.CompoundNavigator
import pages.chargeC.WhichTypeOfSponsoringEmployerPage
import play.api.data.Field
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import renderer.Renderer
import services.UserAnswersService
import uk.gov.hmrc.play.bootstrap.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.Radios.Item
import uk.gov.hmrc.viewmodels.{NunjucksSupport, Radios}

import scala.concurrent.{ExecutionContext, Future}

class WhichTypeOfSponsoringEmployerController @Inject()(override val messagesApi: MessagesApi,
                                                        userAnswersCacheConnector: UserAnswersCacheConnector,
                                                        userAnswersService: UserAnswersService,
                                                        navigator: CompoundNavigator,
                                                        identify: IdentifierAction,
                                                        getData: DataRetrievalAction,
                                                        allowAccess: AllowAccessActionProvider,
                                                        requireData: DataRequiredAction,
                                                        formProvider: IsSponsoringEmployerIndividualFormProvider,
                                                        val controllerComponents: MessagesControllerComponents,
                                                        config: FrontendAppConfig,
                                                        renderer: Renderer)(implicit ec: ExecutionContext)
    extends FrontendBaseController
    with I18nSupport
    with NunjucksSupport {

  private val form = formProvider()

  def onPageLoad(mode: Mode, srn: String, startDate: LocalDate, accessType: AccessType, version: Int, index: Index): Action[AnyContent] =
    (identify andThen getData(srn, startDate) andThen requireData andThen allowAccess(srn, startDate, None, version, accessType)).async { implicit request =>
      DataRetrievals.retrieveSchemeName { schemeName =>
        val preparedForm = request.userAnswers.get(WhichTypeOfSponsoringEmployerPage(index)) match {
          case None        => form
          case Some(value) => form.fill(value)
        }

        val viewModel = GenericViewModel(
          submitUrl = routes.WhichTypeOfSponsoringEmployerController.onSubmit(mode, srn, startDate, accessType, version, index).url,
          returnUrl = controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, version).url,
          schemeName = schemeName
        )

        val json = Json.obj(
          "srn" -> srn,
          "startDate" -> Some(startDate),
          "form" -> preparedForm,
          "viewModel" -> viewModel,
          "radios" -> SponsoringEmployerType.radios(preparedForm)
        )

        renderer.render("chargeC/whichTypeOfSponsoringEmployer.njk", json).map(Ok(_))
      }
    }

  def onSubmit(mode: Mode, srn: String, startDate: LocalDate, accessType: AccessType, version: Int, index: Index): Action[AnyContent] =
    (identify andThen getData(srn, startDate) andThen requireData).async { implicit request =>
      DataRetrievals.retrieveSchemeName { schemeName =>
        form
          .bindFromRequest()
          .fold(
            formWithErrors => {

              val viewModel = GenericViewModel(
                submitUrl = routes.WhichTypeOfSponsoringEmployerController.onSubmit(mode, srn, startDate, accessType, version, index).url,
                returnUrl = controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, version).url,
                schemeName = schemeName
              )

              val json = Json.obj(
                "form" -> formWithErrors,
                "viewModel" -> viewModel,
                "radios" -> SponsoringEmployerType.radios(formWithErrors)
              )

              renderer.render("chargeC/whichTypeOfSponsoringEmployer.njk", json).map(BadRequest(_))
            },
            value =>
              for {
                updatedAnswers <- Future.fromTry(userAnswersService.set(WhichTypeOfSponsoringEmployerPage(index), value, mode))
                _ <- userAnswersCacheConnector.save(request.internalId, updatedAnswers.data)
              } yield Redirect(navigator.nextPage(WhichTypeOfSponsoringEmployerPage(index), mode, updatedAnswers, srn, startDate, accessType, version))
          )
      }
    }
}
