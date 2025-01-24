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

package controllers

import connectors.cache.UserAnswersCacheConnector
import controllers.actions._
import forms.ConfirmSubmitAFTReturnFormProvider
import models.LocalDateBinder._
import models.{AccessType, NormalMode}
import navigators.CompoundNavigator
import pages.ConfirmSubmitAFTReturnPage
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import viewmodels.TwirlRadios

import java.time.LocalDate
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import views.html.ConfirmSubmitAFTReturnView

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
                                                 confirmSubmitAFTReturnView: ConfirmSubmitAFTReturnView)(implicit ec: ExecutionContext)
    extends FrontendBaseController
    with I18nSupport {

  private val form = formProvider()

  def onPageLoad(srn: String, startDate: LocalDate, accessType: AccessType, version: Int): Action[AnyContent] =
    (identify andThen getData(srn, startDate) andThen
      requireData andThen allowAccess(srn, startDate, None, version, accessType) andThen allowSubmission ).async { implicit request =>
      DataRetrievals.retrieveSchemeName { schemeName =>
        val preparedForm = request.userAnswers.get(ConfirmSubmitAFTReturnPage) match {
          case None        => form
          case Some(value) => form.fill(value)
        }

        Future.successful(Ok(confirmSubmitAFTReturnView(
          preparedForm,
          TwirlRadios.yesNo(preparedForm("value")),
          routes.ConfirmSubmitAFTReturnController.onSubmit(srn, startDate),
          controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, version).url,
          schemeName
        )))
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
              Future.successful(BadRequest(confirmSubmitAFTReturnView(
                formWithErrors,
                TwirlRadios.yesNo(formWithErrors("value")),
                routes.ConfirmSubmitAFTReturnController.onSubmit(srn, startDate),
                controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, version).url,
                schemeName
              )))
            },
            value =>
              for {
                updatedAnswers <- Future.fromTry(request.userAnswers.set(ConfirmSubmitAFTReturnPage, value))
                _ <- userAnswersCacheConnector.savePartial(request.internalId, updatedAnswers.data)
              } yield Redirect(navigator.nextPage(ConfirmSubmitAFTReturnPage, NormalMode, updatedAnswers, srn, startDate, accessType, version))
          )
      }
    }
}
