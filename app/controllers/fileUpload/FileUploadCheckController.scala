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
import connectors.cache.UserAnswersCacheConnector
import controllers.actions._
import forms.fileUpload.UploadCheckSelectionFormProvider
import models.LocalDateBinder._
import models.fileUpload.UploadCheckSelection
import models.fileUpload.UploadCheckSelection.{No, Yes}
import models.requests.DataRequest
import models.{AccessType, ChargeType, FileUploadDataCache, UploadId}
import pages.fileUpload.{UploadCheckPage, UploadedFileName}
import pages.{PSTRQuery, SchemeNameQuery}
import play.api.Logger
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, Result}
import services.fileUpload.{UploadProgressTracker, UpscanErrorHandlingService}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import views.html.fileUpload.FileUploadResultView

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class FileUploadCheckController @Inject()(
                                           override val messagesApi: MessagesApi,
                                           identify: IdentifierAction,
                                           getData: DataRetrievalAction,
                                           allowAccess: AllowAccessActionProvider,
                                           requireData: DataRequiredAction,
                                           val controllerComponents: MessagesControllerComponents,
                                           formProvider: UploadCheckSelectionFormProvider,
                                           userAnswersCacheConnector: UserAnswersCacheConnector,
                                           uploadProgressTracker: UploadProgressTracker,
                                           upscanErrorHandlingService: UpscanErrorHandlingService,
                                           auditService: AuditService,
                                           view: FileUploadResultView
                                         )(implicit ec: ExecutionContext)
  extends FrontendBaseController
    with I18nSupport {

  private val form = formProvider()
  private val logger = Logger(classOf[FileUploadCheckController])

  def onPageLoad(srn: String,
                 startDate: String,
                 accessType: AccessType,
                 version: Int,
                 chargeType: ChargeType,
                 uploadId: UploadId): Action[AnyContent] =
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
                  renderPage(fileUploadStatus.name.getOrElse(""), srn, startDate, accessType, version, chargeType, uploadId).map {
                    result =>
                      sendAuditEvent(chargeType, fileUploadDataCache,startTime)
                      result
                  }
                case "InProgress" =>
                  renderPage("InProgress", srn, startDate, accessType, version, chargeType, uploadId)
                case "Failed" =>
                  upscanErrorHandlingService.handleFailureResponse(fileUploadStatus.failureReason.getOrElse(""), srn, startDate, accessType, version).map {
                    result =>
                      sendAuditEvent(chargeType, fileUploadDataCache, startTime)
                      result
                  }
              }
            case _ => throw new RuntimeException("No upload data cache")
          }
    }

  private def sendAuditEvent(
                              chargeType: ChargeType,
                              fileUploadDataCache: FileUploadDataCache,
                              startTime: Long)(implicit request: DataRequest[AnyContent]): Unit = {
    val pstr = request.userAnswers.get(PSTRQuery).getOrElse(s"No PSTR found in Mongo cache.")
    val endTime = System.currentTimeMillis
    val duration = endTime - startTime
    auditService.sendEvent(AFTUpscanFileUploadAuditEvent
    (psaOrPspId = request.idOrException,
      pstr = pstr,
      schemeAdministratorType = request.schemeAdministratorType,
      chargeType= chargeType,
      outcome = Right(fileUploadDataCache),
      uploadTimeInMilliSeconds = duration
    ))
  }

  private def renderPage(name: String, srn: String, startDate: String, accessType: AccessType, version: Int, chargeType: ChargeType,
                         uploadId: UploadId)
                        (implicit request: DataRequest[AnyContent]): Future[Result] = {
    val ua = request.userAnswers
    val preparedForm = ua.get(UploadCheckPage(chargeType)).fold(form)(form.fill)
    val schemeName = ua.get(SchemeNameQuery).getOrElse("the scheme")
    val submitUrl = routes.FileUploadCheckController.onSubmit(srn, startDate, accessType, version, chargeType, uploadId)
    val returnUrl = controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, version).url
    Future.successful(Ok(view(preparedForm, schemeName, ChargeType.fileUploadText(chargeType), submitUrl, returnUrl,
      name, UploadCheckSelection.radios(preparedForm))))
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
                  val submitUrl = routes.FileUploadCheckController.onSubmit(srn, startDate, accessType, version, chargeType, uploadId)
                  val returnUrl = controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, version).url
                  Future.successful(BadRequest(view(formWithErrors, schemeName, ChargeType.fileUploadText(chargeType), submitUrl, returnUrl,
                    fileName, UploadCheckSelection.radios(formWithErrors))))
                },
                value =>
                  for {
                    updatedAnswers <- Future.fromTry(request.userAnswers.set(UploadCheckPage(chargeType), value))
                    updatedUa <- Future.fromTry(updatedAnswers.set(UploadedFileName(chargeType), fileName))
                    _ <- userAnswersCacheConnector.savePartial(request.internalId, updatedUa.data, Some(chargeType))
                  } yield {
                    value match {
                      case Yes =>
                        Redirect(routes.ValidationController.onPageLoad(srn, startDate, accessType, version, chargeType, uploadId))
                      case No => Redirect(routes.FileUploadController.onPageLoad(srn, startDate, accessType, version, chargeType))
                    }
                  }
              )
          }
    }

  private def getFileName(uploadStatus: Option[FileUploadDataCache]): String = {
    logger.info("FileUploadCheckController.getFileName")
    uploadStatus.map { result =>
        val status = result.status
        status._type match {
          case "UploadedSuccessfully" => status.name.getOrElse("No File Found")
          case "InProgress" => "InProgress"
          case _ => "No File Found"
        }
    }.getOrElse("No File Found")
  }
}
