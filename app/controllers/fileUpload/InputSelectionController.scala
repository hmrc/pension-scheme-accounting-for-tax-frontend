/*
 * Copyright 2023 HM Revenue & Customs
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
import models.{AccessType, ChargeType, GenericViewModel, NormalMode}
import navigators.CompoundNavigator
import pages.fileUpload.InputSelectionPage
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import renderer.Renderer
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.NunjucksSupport

import javax.inject.Inject
import scala.concurrent.ExecutionContext

class InputSelectionController @Inject()(
    override val messagesApi: MessagesApi,
    identify: IdentifierAction,
    getData: DataRetrievalAction,
    allowAccess: AllowAccessActionProvider,
    requireData: DataRequiredAction,
    val controllerComponents: MessagesControllerComponents,
    renderer: Renderer,
    navigator: CompoundNavigator,
    formProvider: InputSelectionFormProvider,
    userAnswersCacheConnector: UserAnswersCacheConnector
)(implicit ec: ExecutionContext)
    extends FrontendBaseController
    with I18nSupport
      with NunjucksSupport  {

  private val form = formProvider()

  def onPageLoad(srn: String, startDate: String, accessType: AccessType, version: Int, chargeType: ChargeType): Action[AnyContent] =
    (identify andThen getData(srn, startDate) andThen requireData andThen allowAccess(srn, startDate, None, version, accessType)).async { implicit request =>
      val preparedForm = request.userAnswers.get(InputSelectionPage(chargeType)).fold(form)(form.fill)

      DataRetrievals.retrieveSchemeName { schemeName =>
        renderer
          .render(
            template = "fileUpload/inputSelection.njk",
            Json.obj(
              "chargeType" -> ChargeType.fileUploadText(chargeType),
              "srn" -> srn,
              "startDate" -> Some(startDate),
              "radios" -> InputSelection.radios(preparedForm),
              "form" -> preparedForm,
              "viewModel" -> viewModel(schemeName, srn, startDate, accessType, version, chargeType)
            )
          )
          .map(Ok(_))
      }
    }

  def onSubmit(srn: String, startDate: String, accessType: AccessType, version: Int, chargeType: ChargeType): Action[AnyContent] =
    (identify andThen getData(srn, startDate) andThen requireData).async {

      implicit request => DataRetrievals.retrieveSchemeName { schemeName =>
        val ua = request.userAnswers
        form
          .bindFromRequest()
          .fold(
            formWithErrors => {
              val json = Json.obj(
                "chargeType" -> chargeType.toString,
                "srn" -> srn,
                "startDate" -> Some(startDate),
                "form" -> formWithErrors,
                "radios" -> InputSelection.radios(formWithErrors),
                "viewModel" -> viewModel(schemeName, srn, startDate, accessType, version, chargeType)
              )
              renderer.render(template = "fileUpload/inputSelection.njk", json).map(BadRequest(_))
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

  private def viewModel(schemeName: String, srn: String, startDate: String, accessType: AccessType, version: Int, chargeType: ChargeType) = GenericViewModel(
    submitUrl =  controllers.fileUpload.routes.InputSelectionController.onSubmit(srn, startDate, accessType, version, chargeType).url,
    returnUrl = controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, version).url,
    schemeName = schemeName
  )
}
