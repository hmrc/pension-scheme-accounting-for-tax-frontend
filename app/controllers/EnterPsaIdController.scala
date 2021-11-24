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

package controllers

import connectors.SchemeDetailsConnector
import connectors.cache.UserAnswersCacheConnector
import controllers.actions._
import forms.EnterPsaIdFormProvider
import models.LocalDateBinder._
import models.{AccessType, GenericViewModel, NormalMode}
import navigators.CompoundNavigator
import pages.EnterPsaIdPage
import play.api.data.Form
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import renderer.Renderer
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.NunjucksSupport

import java.time.LocalDate
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class EnterPsaIdController @Inject()(override val messagesApi: MessagesApi,
                                        userAnswersCacheConnector: UserAnswersCacheConnector,
                                        navigator: CompoundNavigator,
                                        identify: IdentifierAction,
                                        getData: DataRetrievalAction,
                                        allowAccess: AllowAccessActionProvider,
                                        requireData: DataRequiredAction,
                                        formProvider: EnterPsaIdFormProvider,
                                        schemeDetailsConnector: SchemeDetailsConnector,
                                        val controllerComponents: MessagesControllerComponents,
                                        renderer: Renderer)(implicit ec: ExecutionContext)
  extends FrontendBaseController
    with I18nSupport
    with NunjucksSupport {

  private def form(authorisingPsaId: Option[String]):Form[String] = formProvider(authorisingPSAID = authorisingPsaId)

  def onPageLoad(srn: String, startDate: LocalDate, accessType: AccessType, version: Int): Action[AnyContent] =
    (identify andThen getData(srn, startDate) andThen requireData andThen allowAccess(srn, startDate, None, version, accessType)).async { implicit request =>

      DataRetrievals.retrieveSchemeName{ schemeName =>
        val preparedForm = request.userAnswers.get(EnterPsaIdPage) match {
          case Some(value) => form(authorisingPsaId=None).fill(value)
          case None        => form(authorisingPsaId=None)
        }

        val viewModel = GenericViewModel(
          submitUrl = routes.EnterPsaIdController.onSubmit(srn, startDate, accessType, version).url,
          returnUrl = controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, version).url,
          schemeName = schemeName
        )

        val json = Json.obj(
          "srn" -> srn,
          "startDate" -> Some(localDateToString(startDate)),
          "form" -> preparedForm,
          "viewModel" -> viewModel
        )

        renderer.render("enterPsaId.njk", json).map(Ok(_))
      }
    }

  def onSubmit(srn: String, startDate: LocalDate, accessType: AccessType, version: Int): Action[AnyContent] =
    (identify andThen getData(srn, startDate) andThen requireData).async { implicit request =>
      DataRetrievals.retrieveSchemeName{ schemeName =>
        schemeDetailsConnector.getPspSchemeDetails(request.idOrException, srn).map(_.authorisingPSAID).flatMap{ authorisingPsaId =>
        form(authorisingPsaId=authorisingPsaId)
          .bindFromRequest()
          .fold(
            formWithErrors => {

              val viewModel = GenericViewModel(
                submitUrl = routes.EnterPsaIdController.onSubmit(srn, startDate, accessType, version).url,
                returnUrl = controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, version).url,
                schemeName = schemeName
              )

              val json = Json.obj(
                "srn" -> srn,
                "startDate" -> Some(localDateToString(startDate)),
                "form" -> formWithErrors,
                "viewModel" -> viewModel
              )

              renderer.render("enterPsaId.njk", json).map(BadRequest(_))
            },
            value =>
              for {
                updatedAnswers <- Future.fromTry(request.userAnswers.set(EnterPsaIdPage, value))
                _ <- userAnswersCacheConnector.savePartial(request.internalId, updatedAnswers.data)
              } yield Redirect(navigator.nextPage(EnterPsaIdPage, NormalMode, updatedAnswers, srn, startDate, accessType, version))
          )
         }
      }
    }
}
