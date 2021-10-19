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

package controllers.fileUpload

import controllers.DataRetrievals
import controllers.actions._
import forms.fileUpload.InputSelectionFormProvider
import models.LocalDateBinder._
import models.fileUpload.InputSelection
import models.fileUpload.InputSelection.{FileUploadInput, ManualInput}
import models.{AccessType, ChargeType, GenericViewModel, NormalMode}
import navigators.CompoundNavigator
import pages.SchemeNameQuery
import pages.fileUpload.{InputSelectionManualPage, InputSelectionPage, InputSelectionUploadPage}
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import renderer.Renderer
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController

import java.time.LocalDate
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class InputSelectionController @Inject()(
    override val messagesApi: MessagesApi,
    identify: IdentifierAction,
    getData: DataRetrievalAction,
    allowAccess: AllowAccessActionProvider,
    requireData: DataRequiredAction,
    val controllerComponents: MessagesControllerComponents,
    renderer: Renderer,
    navigator: CompoundNavigator,
    formProvider: InputSelectionFormProvider
)(implicit ec: ExecutionContext)
    extends FrontendBaseController
    with I18nSupport {

  private val form = formProvider()

  def onPageLoad(srn: String, startDate: LocalDate, accessType: AccessType, version: Int, chargeType: String): Action[AnyContent] =
    (identify andThen getData(srn, startDate) andThen requireData andThen allowAccess(srn, startDate, None, version, accessType)).async { implicit request =>
      val ua = request.userAnswers
      val preparedForm = request.userAnswers.get(InputSelectionPage).fold(form)(form.fill)

      val viewModel = GenericViewModel(
        submitUrl = "",
        returnUrl = controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, version).url,
        schemeName = ua.get(SchemeNameQuery).getOrElse("the scheme")
      )

      renderer.render(template = "fileUpload/inputSelection.njk",
        Json.obj(
          "chargeType" -> chargeType,
          "srn" -> srn, "startDate" -> Some(startDate),
          "radios" -> InputSelection.radios(preparedForm),
          "viewModel" -> viewModel))
        .map(Ok(_))
    }

  def onSubmit(srn: String, startDate: LocalDate, accessType: AccessType, version: Int, chargeType: String): Action[AnyContent] =
    (identify andThen getData(srn, startDate) andThen requireData).async {
      implicit request => DataRetrievals.retrieveSchemeName { _ =>
        val ua = request.userAnswers
        form
          .bindFromRequest()
          .fold(
            formWithErrors => {
              val json = Json.obj(
                "chargeType" -> chargeType,
                "srn" -> srn,
                "startDate" -> Some(startDate),
                "radios" -> InputSelection.radios(formWithErrors)
              )
              renderer.render(template = "fileUpload/inputSelection.njk", json).map(BadRequest(_))
            },
            {
              case ManualInput => Future.successful(Redirect(navigator.nextPage(InputSelectionManualPage, NormalMode, ua, srn, startDate, accessType, version)))
              case FileUploadInput => Future.successful(Redirect(navigator.nextPage(InputSelectionUploadPage, NormalMode, ua, srn, startDate, accessType, version)))
            }
          )
      }
    }
}
