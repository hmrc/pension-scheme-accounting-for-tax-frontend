/*
 * Copyright 2022 HM Revenue & Customs
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

import config.FrontendAppConfig
import connectors.cache.UserAnswersCacheConnector
import controllers.actions._
import forms.fileUpload.UploadCheckSelectionFormProvider
import models.LocalDateBinder._
import models.fileUpload.UploadCheckSelection
import models.fileUpload.UploadCheckSelection.{No, Yes}
import models.requests.DataRequest
import models.{AccessType, ChargeType, GenericViewModel, InProgress, UploadId, UploadStatus, UploadedSuccessfully}
import pages.SchemeNameQuery
import pages.fileUpload.UploadCheckPage
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import renderer.Renderer
import services.fileUpload.UploadProgressTracker
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.NunjucksSupport

import java.time.LocalDate
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class FileUploadCheckController @Inject()(
                                           override val messagesApi: MessagesApi,
                                           identify: IdentifierAction,
                                           getData: DataRetrievalAction,
                                           allowAccess: AllowAccessActionProvider,
                                           requireData: DataRequiredAction,
                                           val controllerComponents: MessagesControllerComponents,
                                           renderer: Renderer,
                                           formProvider: UploadCheckSelectionFormProvider,
                                           userAnswersCacheConnector: UserAnswersCacheConnector,
                                           uploadProgressTracker: UploadProgressTracker
                                         )(implicit ec: ExecutionContext, appConfig: FrontendAppConfig)
  extends FrontendBaseController
    with I18nSupport
    with NunjucksSupport {

  private val form = formProvider()

  def onPageLoad(srn: String,
                 startDate: String,
                 accessType: AccessType,
                 version: Int,
                 chargeType: ChargeType,
                 uploadId: UploadId): Action[AnyContent] =
    (identify andThen getData(srn, startDate) andThen requireData andThen allowAccess(srn, startDate, None, version, accessType)).async {
      implicit request =>
        val ua = request.userAnswers
        val preparedForm = ua.get(UploadCheckPage(chargeType)).fold(form)(form.fill)
        val schemeName = ua.get(SchemeNameQuery).getOrElse("the scheme")

        uploadProgressTracker
          .getUploadResult(uploadId)
          .map(getFileName)
          .flatMap { fileName =>
            renderer
              .render(
                template = "fileUpload/fileUploadResult.njk",
                Json.obj(
                  "chargeTypeText" -> ChargeType.fileUploadText(chargeType),
                  "fileName" -> fileName,
                  "radios" -> UploadCheckSelection.radios(preparedForm),
                  "form" -> preparedForm,
                  "viewModel" -> viewModel(schemeName, srn, startDate, accessType, version, chargeType, uploadId)
                )
              )
              .map(Ok(_))
          }

    }

  def onSubmit(srn: String, startDate: String, accessType: AccessType, version: Int, chargeType: ChargeType, uploadId: UploadId): Action[AnyContent] =
    (identify andThen getData(srn, startDate) andThen requireData andThen allowAccess(srn, startDate, None, version, accessType)).async {
      implicit request =>
        val ua = request.userAnswers
        val schemeName = ua.get(SchemeNameQuery).getOrElse("the scheme")

        uploadProgressTracker
          .getUploadResult(uploadId)
          .map(getFileName)
          .flatMap { fileName =>
            form
              .bindFromRequest()
              .fold(
                formWithErrors => {
                  val json = Json.obj(
                    fields = "chargeTypeText" -> ChargeType.fileUploadText(chargeType),
                    "fileName" -> fileName,
                    "form" -> formWithErrors,
                    "radios" -> UploadCheckSelection.radios(formWithErrors),
                    "viewModel" -> viewModel(schemeName, srn, startDate, accessType, version, chargeType, uploadId)
                  )
                  renderer.render(template = "fileUpload/fileUploadResult.njk", json).map(BadRequest(_))
                },
                value =>
                  for {
                    updatedAnswers <- Future.fromTry(request.userAnswers.set(UploadCheckPage(chargeType), value))
                    _ <- userAnswersCacheConnector.save(request.internalId, updatedAnswers.data)
                  } yield {
                    value match {
                      case Yes => Redirect(routes.ValidationController.onPageLoad(srn, startDate, accessType, version, chargeType, uploadId))
                      case No  => Redirect(routes.FileUploadController.onPageLoad(srn, startDate, accessType, version, chargeType))
                    }
                  }
              )
          }
    }

  private def getFileName(uploadStatus: Option[UploadStatus])(implicit request: DataRequest[AnyContent]): String = {
    uploadStatus match {
      case Some(UploadedSuccessfully(name, _, _, _)) => name
      case Some(InProgress)                          => "InProgress"
      case _                                         => "No File Found"
    }
  }

  private def viewModel(schemeName: String,
                        srn: String,
                        startDate: LocalDate,
                        accessType: AccessType,
                        version: Int,
                        chargeType: ChargeType,
                        uploadId: UploadId): GenericViewModel = {
    GenericViewModel(
      submitUrl = routes.FileUploadCheckController.onSubmit(srn, startDate, accessType, version, chargeType, uploadId).url,
      returnUrl = controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, version).url,
      schemeName = schemeName
    )
  }
}