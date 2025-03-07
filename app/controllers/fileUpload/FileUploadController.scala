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

import audit.{AFTUpscanFileUploadAuditEvent, AuditService}
import config.FrontendAppConfig
import connectors.cache.UserAnswersCacheConnector
import connectors.{Reference, UpscanInitiateConnector}
import controllers.actions._
import models.LocalDateBinder._
import models.requests.DataRequest
import models.{AccessType, ChargeType, FileUploadDataCache, UploadId}
import pages.fileUpload.UploadedFileName
import pages.{PSTRQuery, SchemeNameQuery}
import play.api.Logger
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.mvc.{Action, AnyContent, Call, MessagesControllerComponents}
import services.fileUpload.{UploadProgressTracker, UpscanErrorHandlingService}
import uk.gov.hmrc.govukfrontend.views.Aliases.{ErrorMessage, Text}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import views.html.fileUpload.FileUploadView

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class FileUploadController @Inject()(
                                      override val messagesApi: MessagesApi,
                                      identify: IdentifierAction,
                                      getData: DataRetrievalAction,
                                      allowAccess: AllowAccessActionProvider,
                                      requireData: DataRequiredAction,
                                      val controllerComponents: MessagesControllerComponents,
                                      auditService: AuditService,
                                      upscanInitiateConnector: UpscanInitiateConnector,
                                      uploadProgressTracker: UploadProgressTracker,
                                      userAnswersCacheConnector: UserAnswersCacheConnector,
                                      upscanErrorHandlingService: UpscanErrorHandlingService,
                                      view: FileUploadView
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
      upscanInitiateConnector.initiateV2(Some(successRedirectUrl), Some(errorRedirectUrl), chargeType).flatMap { uir =>
        uploadProgressTracker.requestUpload(uploadId, Reference(uir.fileReference.reference)).flatMap { _ =>
          val submitUrl = Call("POST", uir.postTarget)
          val returnUrl = controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, version).url

          logger.info("FileUploadController.onPageLoad AF upscanInitiate")
          Future.successful(Ok(view(
            schemeName = request.userAnswers.get(SchemeNameQuery).getOrElse("the scheme"),
            chargeType.toString,
            ChargeType.fileUploadText(chargeType), submitUrl, returnUrl,collectErrors(),
            uir.formFields
          )))
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
              val startTime = System.currentTimeMillis
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
                  upscanErrorHandlingService.handleFailureResponse(fileUploadStatus.failureReason.getOrElse(""), srn, startDate, accessType, version).map {
                    result =>
                      sendAuditEvent(chargeType, fileUploadDataCache,startTime)
                      result
                  }
                case _ => Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad))
              }
            case _ => throw new RuntimeException("No upload data cache found")
          }
    }


   private def sendAuditEvent(
                               chargeType: ChargeType,
                               fileUploadDataCache: FileUploadDataCache,
                               startTime: Long)(implicit request: DataRequest[AnyContent]): Unit = {
    val pstr = request.userAnswers.get(PSTRQuery).getOrElse(s"No PSTR found in Mongo cache.")
    val endTime = System.currentTimeMillis
    val duration = endTime- startTime
    auditService.sendEvent(AFTUpscanFileUploadAuditEvent
    (psaOrPspId = request.idOrException,
      pstr = pstr,
      schemeAdministratorType = request.schemeAdministratorType,
      chargeType= chargeType,
      outcome = Right(fileUploadDataCache),
      uploadTimeInMilliSeconds = duration
    ))
  }

  private def collectErrors()(implicit request: DataRequest[AnyContent], messages: Messages): Option[ErrorMessage] = {
    request.getQueryString("errorCode").zip(request.getQueryString("errorMessage")).flatMap {
      case ("EntityTooLarge", _) =>
        Some(ErrorMessage(content = Text(messages("generic.upload.error.size" , appConfig.maxUploadFileSize))))
      case ("InvalidArgument", "'file' field not found") =>
        Some(ErrorMessage(content = Text(messages("generic.upload.error.required"))))
      case ("InvalidArgument", "'file' invalid file format") =>
        Some(ErrorMessage(content = Text(messages("generic.upload.error.format"))))
      case ("REJECTED", _) =>
        Some(ErrorMessage(content = Text(messages("generic.upload.error.format"))))
      case ("EntityTooSmall", _) =>
        Some(ErrorMessage(content = Text(messages("generic.upload.error.required"))))
      case ("QUARANTINE", _) =>
        Some(ErrorMessage(content = Text(messages("generic.upload.error.malicious"))))
      case ("UNKNOWN", _) =>
        Some(ErrorMessage(content = Text(messages("generic.upload.error.unknown"))))
      case _ => None
    }
  }
}
