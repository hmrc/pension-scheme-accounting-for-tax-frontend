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

package controllers.mccloud

import connectors.cache.UserAnswersCacheConnector
import controllers.DataRetrievals
import controllers.actions._
import forms.mccloud.EnterPstrFormProvider
import models.LocalDateBinder._
import models.{AccessType, ChargeType, Index, Mode}
import navigators.CompoundNavigator
import pages.mccloud.EnterPstrPage
import play.api.data.Form
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import services.UserAnswersService
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import views.html.ErrorView
import views.html.mccloud.EnterPstr

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
                                    val controllerComponents: MessagesControllerComponents,
                                    enterPstrView: EnterPstr,
                                    errorView: ErrorView)(implicit ec: ExecutionContext)
  extends FrontendBaseController
    with I18nSupport
    with CommonMcCloud {

  private def form(): Form[String] = formProvider()

  def onPageLoad(chargeType: ChargeType,
                 mode: Mode,
                 srn: String,
                 startDate: LocalDate,
                 accessType: AccessType,
                 version: Int,
                 index: Index,
                 schemeIndex: Index): Action[AnyContent] =
    (identify andThen getData(srn, startDate) andThen requireData andThen allowAccess(srn, startDate, None, version, accessType)).async { implicit request =>
      DataRetrievals.retrieveSchemeName { schemeName =>
        val preparedForm = request.userAnswers.get(EnterPstrPage(chargeType, index, schemeIndex)) match {
          case Some(value) => form().fill(value)
          case None => form()
        }

        (ordinal(Some(schemeIndex)).map(_.value).getOrElse(""), twirlLifetimeOrAnnual(chargeType)) match {
          case (ordinalValue, Some(chargeTypeDesc)) =>
            Future.successful(Ok(enterPstrView(
              form = preparedForm,
              ordinal = ordinalValue,
              chargeTypeDesc = chargeTypeDesc,
              submitCall = routes.EnterPstrController.onSubmit(chargeType, mode, srn, startDate, accessType, version, index, schemeIndex),
              returnUrl = controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, version).url,
              schemeName = schemeName
            )))
          case _ =>
            Future.successful(BadRequest(errorView()))
        }
      }
    }

  def onSubmit(chargeType: ChargeType,
               mode: Mode,
               srn: String,
               startDate: LocalDate,
               accessType: AccessType,
               version: Int,
               index: Index,
               schemeIndex: Index): Action[AnyContent] =
    (identify andThen getData(srn, startDate) andThen requireData andThen allowAccess(srn, startDate, None, version, accessType)).async { implicit request =>
      DataRetrievals.retrieveSchemeName { schemeName =>

        form()
          .bindFromRequest()
          .fold(
            formWithErrors => {
              (ordinal(Some(schemeIndex)).map(_.value).getOrElse(""), twirlLifetimeOrAnnual(chargeType)) match {
                case (ordinalValue, Some(chargeTypeDesc)) =>
                  Future.successful(BadRequest(enterPstrView(
                    formWithErrors,
                    ordinalValue,
                    chargeTypeDesc,
                    routes.EnterPstrController.onSubmit(chargeType, mode, srn, startDate, accessType, version, index, schemeIndex),
                    controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, version).url,
                    schemeName
                  )))
                case _ =>
                  Future.successful(BadRequest(errorView()))
              }
            },
            value =>
              for {
                updatedAnswers <- Future.fromTry(userAnswersService.set(EnterPstrPage(chargeType, index, schemeIndex), value, mode))
                _ <- userAnswersCacheConnector.savePartial(request.internalId, updatedAnswers.data,
                  chargeType = Some(chargeType), memberNo = Some(index.id))
              } yield Redirect(navigator.nextPage(EnterPstrPage(chargeType, index, schemeIndex), mode, updatedAnswers, srn, startDate, accessType, version))
          )

      }
    }
}
