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

import java.time.LocalDate

import connectors.cache.UserAnswersCacheConnector
import controllers.actions._
import forms.ConfirmSubmitAFTReturnFormProvider
import javax.inject.Inject
import models.{NormalMode, GenericViewModel, AccessType}
import navigators.CompoundNavigator
import pages.ConfirmSubmitAFTReturnPage
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.Json
import play.api.mvc.{AnyContent, MessagesControllerComponents, Action}
import renderer.Renderer
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.{NunjucksSupport, Radios}
import models.LocalDateBinder._

import scala.concurrent.{Future, ExecutionContext}

class ConfirmSubmitAFTReturnController @Inject()(override val messagesApi: MessagesApi,
                                                 userAnswersCacheConnector: UserAnswersCacheConnector,
                                                 navigator: CompoundNavigator,
                                                 identify: IdentifierAction,
                                                 getData: DataRetrievalAction,
                                                 allowAccess: AllowAccessActionProvider,
                                                 allowSubmission: AllowSubmissionAction,
                                                 requireData: DataRequiredAction,
                                                 formProvider: ConfirmSubmitAFTReturnFormProvider,
                                                 val controllerComponents: MessagesControllerComponents,
                                                 renderer: Renderer)(implicit ec: ExecutionContext)
    extends FrontendBaseController
    with I18nSupport
    with NunjucksSupport {

  private val form = formProvider()

  def onPageLoad(srn: String, startDate: LocalDate, accessType: AccessType, version: Int): Action[AnyContent] =
    (identify andThen getData(srn, startDate) andThen
      requireData andThen allowAccess(srn, startDate, None, version, accessType) andThen allowSubmission ).async { implicit request =>
      DataRetrievals.retrieveSchemeName { schemeName =>
        val preparedForm = request.userAnswers.get(ConfirmSubmitAFTReturnPage) match {
          case None        => form
          case Some(value) => form.fill(value)
        }

        val viewModel = GenericViewModel(
          submitUrl = routes.ConfirmSubmitAFTReturnController.onSubmit(srn, startDate).url,
          returnUrl = controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, version).url,
          schemeName = schemeName
        )

        val json = Json.obj(
          fields = "srn" -> srn,
          "startDate" -> Some(startDate),
          "form" -> preparedForm,
          "viewModel" -> viewModel,
          "radios" -> Radios.yesNo(preparedForm("value"))
        )

        renderer.render(template = "confirmSubmitAFTReturn.njk", json).map(Ok(_))
      }
    }

  def onSubmit(srn: String, startDate: LocalDate, accessType: AccessType, version: Int): Action[AnyContent] =
    (identify andThen getData(srn, startDate) andThen requireData andThen
      allowAccess(srn, startDate, None, version, accessType) andThen allowSubmission ).async { implicit request =>
      DataRetrievals.retrieveSchemeName { schemeName =>
        form
          .bindFromRequest()
          .fold(
            formWithErrors => {

              val viewModel = GenericViewModel(
                submitUrl = routes.ConfirmSubmitAFTReturnController.onSubmit(srn, startDate).url,
                returnUrl = controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, version).url,
                schemeName = schemeName
              )

              val json = Json.obj(
                fields = "srn" -> srn,
                "startDate" -> Some(startDate),
                "form" -> formWithErrors,
                "viewModel" -> viewModel,
                "radios" -> Radios.yesNo(formWithErrors("value"))
              )

              renderer.render(template = "confirmSubmitAFTReturn.njk", json).map(BadRequest(_))
            },
            value =>
              for {
                updatedAnswers <- Future.fromTry(request.userAnswers.set(ConfirmSubmitAFTReturnPage, value))
                _ <- userAnswersCacheConnector.save(request.internalId, updatedAnswers.data)
              } yield Redirect(navigator.nextPage(ConfirmSubmitAFTReturnPage, NormalMode, updatedAnswers, srn, startDate, accessType, version))
          )
      }
    }
}
