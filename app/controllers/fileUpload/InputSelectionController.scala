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

package controllers.fileUpload

import connectors.cache.UserAnswersCacheConnector
import controllers.DataRetrievals
import controllers.actions._
import forms.fileUpload.InputSelectionFormProvider
import models.LocalDateBinder._
import models.fileUpload.InputSelection
import models.{AccessType, ChargeType, NormalMode}
import navigators.CompoundNavigator
import pages.fileUpload.InputSelectionPage
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import views.html.fileUpload.InputSelectionView

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class InputSelectionController @Inject()(
    override val messagesApi: MessagesApi,
    identify: IdentifierAction,
    getData: DataRetrievalAction,
    allowAccess: AllowAccessActionProvider,
    requireData: DataRequiredAction,
    val controllerComponents: MessagesControllerComponents,
    navigator: CompoundNavigator,
    formProvider: InputSelectionFormProvider,
    userAnswersCacheConnector: UserAnswersCacheConnector,
    view: InputSelectionView
)(implicit ec: ExecutionContext)
    extends FrontendBaseController
    with I18nSupport {

  private val form = formProvider()

  def onPageLoad(srn: String, startDate: String, accessType: AccessType, version: Int, chargeType: ChargeType): Action[AnyContent] =
    (identify andThen getData(srn, startDate) andThen requireData andThen allowAccess(srn, startDate, None, version, accessType)).async { implicit request =>
      val preparedForm = request.userAnswers.get(InputSelectionPage(chargeType)).fold(form)(form.fill)

      DataRetrievals.retrieveSchemeName { schemeName =>
        val submitUrl =  controllers.fileUpload.routes.InputSelectionController.onSubmit(srn, startDate, accessType, version, chargeType)
        val returnUrl = controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, version).url

        Future.successful(Ok(view(preparedForm, schemeName, submitUrl, returnUrl,ChargeType.fileUploadText(chargeType),
          InputSelection.radios(preparedForm))))
      }
    }

  def onSubmit(srn: String, startDate: String, accessType: AccessType, version: Int, chargeType: ChargeType): Action[AnyContent] =
    (identify andThen getData(srn, startDate) andThen requireData andThen allowAccess(srn, startDate, None, version, accessType)).async {

      implicit request => DataRetrievals.retrieveSchemeName { schemeName =>
        val ua = request.userAnswers
        form
          .bindFromRequest()
          .fold(
            formWithErrors => {
              val submitUrl =  controllers.fileUpload.routes.InputSelectionController.onSubmit(srn, startDate, accessType, version, chargeType)
              val returnUrl = controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, version).url
              Future.successful(BadRequest(view(formWithErrors, schemeName, submitUrl, returnUrl,ChargeType.fileUploadText(chargeType),
                InputSelection.radios(formWithErrors))))
            },
            { inputSelection =>
              val updatedUA = ua.setOrException(InputSelectionPage(chargeType), inputSelection)
              userAnswersCacheConnector.savePartial(request.internalId, updatedUA.data).map{ _ =>
                Redirect(navigator.nextPage(InputSelectionPage(chargeType), NormalMode, updatedUA, srn, startDate, accessType, version))
              }
            }
          )
      }
    }
}
