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

import audit.{AFTUpscanFileUploadAuditEvent, AuditService}
import config.FrontendAppConfig
import connectors.cache.UserAnswersCacheConnector
import connectors.{Reference, UpscanInitiateConnector}
import controllers.actions._
import models.LocalDateBinder._
import models.requests.DataRequest
import models.{AccessType, AdministratorOrPractitioner, ChargeType, FileUploadDataCache, FileUploadStatus, GenericViewModel, SchemeDetails, UploadId}
import pages.{PSTRQuery, SchemeNameQuery}
import pages.fileUpload.UploadedFileName
import play.api.Logger
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import renderer.Renderer
import services.fileUpload.{UploadProgressTracker, UpscanErrorHandlingService}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController

import java.time.Duration
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
                                      auditService: AuditService,
                                      upscanInitiateConnector: UpscanInitiateConnector,
                                      uploadProgressTracker: UploadProgressTracker,
                                      userAnswersCacheConnector: UserAnswersCacheConnector,
                                      upscanErrorHandlingService: UpscanErrorHandlingService
                                    )(implicit ec: ExecutionContext, appConfig: FrontendAppConfig)
  extends FrontendBaseController
    with I18nSupport {
  private val logger = Logger(classOf[FileUploadController])

  def onPageLoad(srn: String, startDate: String, accessType: AccessType, version: Int, chargeType: ChargeType): Action[AnyContent] =
    (identify andThen getData(srn, startDate) andThen requireData andThen allowAccess(srn, startDate, None, version, accessType)).async { implicit request =>

      val uploadId = UploadId.generate

      val successRedirectUrl = appConfig.successEndpointTarget(srn, startDate, accessType, version, chargeType, uploadId)

      val errorRedirectUrl = appConfig.failureEndpointTarget(srn, startDate, accessType, version, chargeType)
      logger.info("FileUploadController.onPageLoad BF upscanInitiate")
      upscanInitiateConnector.initiateV2(Some(successRedirectUrl), Some(errorRedirectUrl)).flatMap{ uir =>
        uploadProgressTracker.requestUpload(uploadId, Reference(uir.fileReference.reference)).flatMap{ _ =>
          val viewModel = GenericViewModel(
            submitUrl = uir.postTarget,
            returnUrl = controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, version).url,
            schemeName = request.userAnswers.get(SchemeNameQuery).getOrElse("the scheme")
          )
          logger.info("FileUploadController.onPageLoad AF upscanInitiate")
          renderer.render(template = "fileUpload/fileupload.njk",
            Json.obj(
              "chargeType" -> chargeType.toString,
              "chargeTypeText" -> ChargeType.fileUploadText(chargeType),
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
            case Some(fileUploadDataCache) =>
              val fileUploadStatus = fileUploadDataCache.status
              fileUploadStatus._type match {
                case "UploadedSuccessfully" =>
                  logger.info("FileUploadController.showResult UploadedSuccessfully")
               for {
                    updatedAnswers <- Future.fromTry(request.userAnswers.set(UploadedFileName(chargeType), fileUploadStatus.name.getOrElse("")))
                    _ <- userAnswersCacheConnector.savePartial(request.internalId, updatedAnswers.data, Some(chargeType))
                  } yield {
                   Redirect(routes.FileUploadCheckController.onPageLoad(srn, startDate, accessType, version, chargeType, uploadId))
                  }
                case "InProgress" =>
                  logger.info("FileUploadController.showResult InProgress")
                  Future.successful(Redirect(routes.FileUploadCheckController.onPageLoad(srn, startDate, accessType, version, chargeType, uploadId)))
                case "Failed" =>
                  upscanErrorHandlingService.handleFailureResponse(fileUploadStatus.failureReason.getOrElse(""), srn, startDate, accessType, version).map{
                    result =>
                      sendAuditEvent(chargeType,fileUploadDataCache)
                      result
                  }
              }
          }
    }


  private def sendAuditEvent(chargeType: ChargeType,fileUploadDataCache: FileUploadDataCache)(implicit request: DataRequest[AnyContent]) = {
    val fileUploadStatus = fileUploadDataCache.status
    val pstr = request.userAnswers.get(PSTRQuery).getOrElse(s"No PSTR found in Mongo cache.")
    val duration = Duration.between(fileUploadDataCache.created, fileUploadDataCache.lastUpdated)
    val uploadTime = duration.getSeconds()
    auditService.sendEvent(AFTUpscanFileUploadAuditEvent(request.idOrException, pstr,
      request.schemeAdministratorType, chargeType, fileUploadStatus._type, fileUploadStatus.failureReason, uploadTime = uploadTime,
      fileUploadStatus.size, fileUploadDataCache.reference))
  }

  private def getErrorCode(request: DataRequest[AnyContent]):Option[String] = {
    if (request.queryString.contains("errorCode") && request.queryString("errorCode").nonEmpty) {
      Some(request.queryString("errorCode").head)
    } else {
      None
    }
  }
}