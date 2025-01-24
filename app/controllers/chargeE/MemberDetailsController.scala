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

package controllers.chargeE

import java.time.LocalDate
import connectors.cache.UserAnswersCacheConnector
import controllers.DataRetrievals
import controllers.actions._
import forms.MemberDetailsFormProvider

import javax.inject.Inject
import models.LocalDateBinder._
import models.{AccessType, ChargeType, Index, Mode, NormalMode}
import navigators.CompoundNavigator
import pages.MemberFormCompleted
import pages.chargeE.MemberDetailsPage
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import services.UserAnswersService
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Success
import views.html.MemberDetailsView

class MemberDetailsController @Inject()(override val messagesApi: MessagesApi,
                                        userAnswersCacheConnector: UserAnswersCacheConnector,
                                        userAnswersService: UserAnswersService,
                                        navigator: CompoundNavigator,
                                        identify: IdentifierAction,
                                        getData: DataRetrievalAction,
                                        allowAccess: AllowAccessActionProvider,
                                        requireData: DataRequiredAction,
                                        formProvider: MemberDetailsFormProvider,
                                        val controllerComponents: MessagesControllerComponents,
                                        memberDetailsView: MemberDetailsView)(implicit ec: ExecutionContext)
    extends FrontendBaseController
    with I18nSupport {

  private val form = formProvider()

  def onPageLoad(mode: Mode, srn: String, startDate: LocalDate, accessType: AccessType, version: Int, index: Index): Action[AnyContent] =
    (identify andThen getData(srn, startDate) andThen requireData andThen allowAccess(srn, startDate, None, version, accessType)).async { implicit request =>
      DataRetrievals.retrieveSchemeName { schemeName =>
        val preparedForm = request.userAnswers.get(MemberDetailsPage(index)) match {
          case None        => form
          case Some(value) => form.fill(value)
        }

        Future.successful(Ok(memberDetailsView(
          "chargeE",
          preparedForm,
          routes.MemberDetailsController.onSubmit(mode, srn, startDate, accessType, version, index),
          controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, version).url,
          schemeName
        )))
      }
    }

  def onSubmit(mode: Mode, srn: String, startDate: LocalDate, accessType: AccessType, version: Int, index: Index): Action[AnyContent] =
    (identify andThen getData(srn, startDate) andThen requireData).async { implicit request =>
      DataRetrievals.retrieveSchemeName { schemeName =>
        form
          .bindFromRequest()
          .fold(
            formWithErrors => {
              Future.successful(BadRequest(memberDetailsView(
                "chargeE",
                formWithErrors,
                routes.MemberDetailsController.onSubmit(mode, srn, startDate, accessType, version, index),
                controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, version).url,
                schemeName
              )))
            },
            value =>
              for {
                updatedAnswersInProgress <- Future.fromTry(userAnswersService.set(MemberDetailsPage(index), value, mode))
                updatedAnswers <- Future.fromTry(mode match{
                  case NormalMode => updatedAnswersInProgress.set(MemberFormCompleted("chargeEDetails",index), false)
                  case _ => Success(updatedAnswersInProgress)
                })
                _ <- userAnswersCacheConnector.savePartial(request.internalId, updatedAnswers.data,
                  chargeType = Some(ChargeType.ChargeTypeAnnualAllowance), memberNo = Some(index.id))
              } yield Redirect(navigator.nextPage(MemberDetailsPage(index), mode, updatedAnswers, srn, startDate, accessType, version))
          )
      }
    }
}
