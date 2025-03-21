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
import forms.ChargeTypeFormProvider
import models.LocalDateBinder._
import models.{AccessType, ChargeType, NormalMode}
import navigators.CompoundNavigator
import pages._
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import services.SchemeService
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController

import java.time.LocalDate
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import views.html.ChargeTypeView

class ChargeTypeController @Inject()(
                                      override val messagesApi: MessagesApi,
                                      userAnswersCacheConnector: UserAnswersCacheConnector,
                                      navigator: CompoundNavigator,
                                      identify: IdentifierAction,
                                      getData: DataRetrievalAction,
                                      updateData: DataSetupAction,
                                      allowAccess: AllowAccessActionProvider,
                                      requireData: DataRequiredAction,
                                      formProvider: ChargeTypeFormProvider,
                                      val controllerComponents: MessagesControllerComponents,
                                      chargeTypeView: ChargeTypeView,
                                      schemeService: SchemeService
                                    )(implicit ec: ExecutionContext)
  extends FrontendBaseController
    with I18nSupport {

  private val form = formProvider()

  def onPageLoad(srn: String, startDate: LocalDate, accessType: AccessType, version: Int): Action[AnyContent] =
    (identify andThen updateData(srn, startDate, version, accessType, optionCurrentPage = Some(ChargeTypePage)) andThen
      requireData andThen allowAccess(srn, startDate, optionPage = Some(ChargeTypePage), version, accessType)).async { implicit request =>
      schemeService.retrieveSchemeDetails(
        psaId = request.idOrException,
        srn = srn
      ) flatMap { schemeDetails =>
        val preparedForm = request.userAnswers.get(ChargeTypePage).fold(form)(form.fill)
        Future.successful(Ok(chargeTypeView(
          preparedForm,
          ChargeType.radios(preparedForm),
          routes.ChargeTypeController.onSubmit(srn, startDate, accessType, version),
          controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, version).url,
          schemeDetails.schemeName
        )))
      }
    }

  def onSubmit(srn: String, startDate: LocalDate, accessType: AccessType, version: Int): Action[AnyContent] =
    (identify andThen getData(srn, startDate) andThen requireData andThen
      allowAccess(srn, startDate, optionPage = Some(ChargeTypePage), version, accessType)).async {
      implicit request =>
        DataRetrievals.retrieveSchemeName { schemeName =>
          form
            .bindFromRequest()
            .fold(
              formWithErrors => {
                Future.successful(BadRequest(chargeTypeView(
                  formWithErrors,
                  ChargeType.radios(formWithErrors),
                  routes.ChargeTypeController.onSubmit(srn, startDate, accessType, version),
                  controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, version).url,
                  schemeName
                )))
              },
              value =>
                for {
                  updatedAnswers <- Future.fromTry(request.userAnswers.set(ChargeTypePage, value))
                  _ <- userAnswersCacheConnector.save(request.internalId, updatedAnswers.data)
                } yield Redirect(navigator.nextPage(ChargeTypePage, NormalMode, updatedAnswers, srn, startDate, accessType, version))

            )
        }
    }
}
