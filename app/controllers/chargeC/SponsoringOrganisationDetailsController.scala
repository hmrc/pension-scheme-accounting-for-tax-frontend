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

package controllers.chargeC

import config.FrontendAppConfig
import connectors.cache.UserAnswersCacheConnector
import controllers.DataRetrievals
import controllers.actions._
import forms.chargeC.SponsoringOrganisationDetailsFormProvider
import models.LocalDateBinder._
import models.{AccessType, ChargeType, Index, Mode}
import navigators.CompoundNavigator
import pages.chargeC.SponsoringOrganisationDetailsPage
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import services.UserAnswersService
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import views.html.chargeC.SponsoringOrganisationDetailsView

import java.time.LocalDate
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class SponsoringOrganisationDetailsController @Inject()(override val messagesApi: MessagesApi,
                                                        userAnswersCacheConnector: UserAnswersCacheConnector,
                                                        userAnswersService: UserAnswersService,
                                                        navigator: CompoundNavigator,
                                                        identify: IdentifierAction,
                                                        getData: DataRetrievalAction,
                                                        allowAccess: AllowAccessActionProvider,
                                                        requireData: DataRequiredAction,
                                                        formProvider: SponsoringOrganisationDetailsFormProvider,
                                                        val controllerComponents: MessagesControllerComponents,
                                                        config: FrontendAppConfig,
                                                        view: SponsoringOrganisationDetailsView)(implicit ec: ExecutionContext)
    extends FrontendBaseController
    with I18nSupport {

  private val form = formProvider()

  def onPageLoad(mode: Mode, srn: String, startDate: LocalDate, accessType: AccessType, version: Int, index: Index): Action[AnyContent] =
    (identify andThen getData(srn, startDate) andThen requireData andThen allowAccess(srn, startDate, None, version, accessType)).async { implicit request =>
      DataRetrievals.retrieveSchemeName { schemeName =>
        val preparedForm = request.userAnswers.get(SponsoringOrganisationDetailsPage(index)) match {
          case None        => form
          case Some(value) => form.fill(value)
        }
        val submitCall = routes.SponsoringOrganisationDetailsController.onSubmit(mode, srn, startDate, accessType, version, index)
        val returnUrl = controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, version).url
        Future.successful(Ok(view(preparedForm, schemeName, submitCall, returnUrl)))
      }
    }

  def onSubmit(mode: Mode, srn: String, startDate: LocalDate, accessType: AccessType, version: Int, index: Index): Action[AnyContent] =
    (identify andThen getData(srn, startDate) andThen requireData).async { implicit request =>
      DataRetrievals.retrieveSchemeName { schemeName =>
        form
          .bindFromRequest()
          .fold(
            formWithErrors => {
              val submitCall = routes.SponsoringOrganisationDetailsController.onSubmit(mode, srn, startDate, accessType, version, index)
              val returnUrl = controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, version).url
              Future.successful(BadRequest(view(formWithErrors, schemeName, submitCall, returnUrl)))
            },
            value =>
              for {
                updatedAnswers <- Future.fromTry(userAnswersService.set(SponsoringOrganisationDetailsPage(index), value, mode))
                _ <- userAnswersCacheConnector.savePartial(request.internalId, updatedAnswers.data,
                  chargeType = Some(ChargeType.ChargeTypeAuthSurplus), memberNo = Some(index.id))
              } yield Redirect(navigator.nextPage(SponsoringOrganisationDetailsPage(index), mode, updatedAnswers, srn, startDate, accessType, version))
          )
      }
    }
}
