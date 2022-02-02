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
import connectors.{Reference, UpscanInitiateConnector}
import controllers.actions._
import models.LocalDateBinder._
import models.requests.DataRequest
import models.{AccessType, ChargeType, Failed, GenericViewModel, InProgress, UploadId}
import navigators.CompoundNavigator
import pages.SchemeNameQuery
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, Result}
import renderer.Renderer
import services.fileUpload.UploadProgressTracker
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class FileUploadController @Inject()(
                                      override val messagesApi: MessagesApi,
                                      identify: IdentifierAction,
                                      getData: DataRetrievalAction,
                                      allowAccess: AllowAccessActionProvider,
                                      requireData: DataRequiredAction,
                                      val controllerComponents: MessagesControllerComponents,
                                      renderer: Renderer,
                                      navigator: CompoundNavigator,
                                      upscanInitiateConnector: UpscanInitiateConnector,
                                      uploadProgressTracker: UploadProgressTracker
                                    )(implicit ec: ExecutionContext, appConfig: FrontendAppConfig)
  extends FrontendBaseController
    with I18nSupport {

  def onPageLoad(srn: String, startDate: String, accessType: AccessType, version: Int, chargeType: ChargeType): Action[AnyContent] =
    (identify andThen getData(srn, startDate) andThen requireData andThen allowAccess(srn, startDate, None, version, accessType)).async { implicit request =>

      val uploadId = UploadId.generate
      val successRedirectUrl = appConfig.urlInThisService(routes.FileUploadController
        .showResult(srn, startDate, accessType, version, chargeType, uploadId).url)

      val errorRedirectUrl = appConfig.urlInThisService( routes.FileUploadController
        .onPageLoad(srn, startDate, accessType, version, chargeType).url)

      upscanInitiateConnector.initiateV2(Some(successRedirectUrl), Some(errorRedirectUrl)).flatMap{ uir =>
        uploadProgressTracker.requestUpload(uploadId, Reference(uir.fileReference.reference)).flatMap{ _ =>
          val viewModel = GenericViewModel(
            submitUrl = uir.postTarget,
            returnUrl = controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, version).url,
            schemeName = request.userAnswers.get(SchemeNameQuery).getOrElse("the scheme")
          )

          renderer.render(template = "fileUpload/fileupload.njk",
            Json.obj(
              "chargeType" -> chargeType.toString,
              "chargeTypeText" -> chargeType.toString,
              "srn" -> srn,
              "startDate" -> Some(startDate),
              "formFields" -> uir.formFields.toList,
              "error" -> getErrorCode(request),
              "maxFileUploadSize" -> appConfig.maxUploadFileSize,
              "viewModel" -> viewModel))
            .map(Ok(_))

        }
      }
    }

  def showResult(srn: String, startDate: String, accessType: AccessType, version: Int, chargeType: ChargeType, uploadId: UploadId): Action[AnyContent] =
    (identify andThen getData(srn, startDate) andThen requireData andThen allowAccess(srn, startDate, None, version, accessType)).async {
      implicit request =>
        uploadProgressTracker
          .getUploadResult(uploadId)
          .flatMap {
            uploadStatus =>
              uploadStatus match {
                case Some(status) => {
                  status match {
                    //case UploadedSuccessfully(_, _, _, _) => Future.successful(
                    //Redirect(routes.FileUploadController.showResult(srn, startDate, accessType, version, chargeType, uploadId)))
                    //TODO need to discussed with team for approach
                    case InProgress => Future.successful(
                      Redirect(routes.FileUploadController.showResult(srn, startDate, accessType, version, chargeType, uploadId)))
                    case Failed(failureReason, _) => handleFailureResponse(failureReason, srn, startDate, accessType, version)
                  }
                }
              }
          }
    }

  private def getErrorCode(request: DataRequest[AnyContent]):Option[String] = {
    if (request.queryString.contains("errorCode") && request.queryString("errorCode").nonEmpty) {
      Some(request.queryString("errorCode").head)
    } else {
      None
    }
  }

  private def handleFailureResponse(failureResponse: String,srn: String, startDate: String, accessType: AccessType,
                                    version: Int)(implicit request: DataRequest[_]): Future[Result]  = {
    val returnUrl = controllers.routes.ChargeTypeController.onPageLoad(srn, startDate, accessType, version).url
    failureResponse match {
      case "QUARANTINE" =>
        Future.successful(Redirect(routes.UpscanErrorController.quarantineError(returnUrl)))
      case "REJECTED" =>
        Future.successful(Redirect(routes.UpscanErrorController.rejectedError(returnUrl)))
      case "UNKNOWN" =>
        Future.successful(Redirect(routes.UpscanErrorController.unknownError))
    }
  }
}