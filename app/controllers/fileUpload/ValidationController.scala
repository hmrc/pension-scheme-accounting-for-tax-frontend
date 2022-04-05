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

import audit.{AFTFileValidationCheckAuditEvent, AFTUpscanFileDownloadAuditEvent, AuditService}
import config.FrontendAppConfig
import connectors.UpscanInitiateConnector
import controllers.actions._
import controllers.fileUpload.FileUploadGenericErrorReporter.generateGenericErrorReport
import fileUploadParsers.Parser.FileLevelParserValidationErrorTypeHeaderInvalidOrFileEmpty
import fileUploadParsers._
import models.ChargeType.{ChargeTypeAnnualAllowance, ChargeTypeLifetimeAllowance, ChargeTypeOverseasTransfer}
import models.requests.DataRequest
import models.{AccessType, ChargeType, FileUploadDataCache, UploadId, UploadStatus, UserAnswers}
import org.apache.commons.lang3.StringUtils.EMPTY
import pages.{PSTRQuery, SchemeNameQuery}
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.libs.json.{JsObject, JsPath, Json}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, Result}
import renderer.Renderer
import services.AFTService
import services.fileUpload.{FileUploadAftReturnService, UploadProgressTracker}
import uk.gov.hmrc.http.HttpResponse
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.NunjucksSupport

import java.time.LocalDate
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class ValidationController @Inject()(
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
                                      annualAllowanceParser: AnnualAllowanceParser,
                                      lifeTimeAllowanceParser: LifetimeAllowanceParser,
                                      overseasTransferParser: OverseasTransferParser,
                                      aftService: AFTService,
                                      fileUploadAftReturnService: FileUploadAftReturnService
                                    )(implicit ec: ExecutionContext, appConfig: FrontendAppConfig)
  extends FrontendBaseController
    with I18nSupport with NunjucksSupport {

  val maximumNumberOfError = 10

  private def processInvalid(
                              srn: String,
                              startDate: LocalDate,
                              accessType: AccessType,
                              version: Int,
                              chargeType: ChargeType,
                              errors: Seq[ParserValidationError])(implicit request: DataRequest[AnyContent], messages: Messages): Future[Result] = {
    val schemeName = request.userAnswers.get(SchemeNameQuery).getOrElse("the scheme")
    val fileDownloadInstructionLink = controllers.routes.FileDownloadController.instructionsFile(chargeType).url
    val returnToFileUpload = appConfig.failureEndpointTarget(srn, startDate, accessType, version, chargeType)
    val returnToSchemeDetails = controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate.toString, accessType, version).url
    errors match {
      case Seq(FileLevelParserValidationErrorTypeHeaderInvalidOrFileEmpty) =>
        Future.successful(Redirect(routes.UpscanErrorController.invalidHeaderOrBodyError(srn, startDate.toString, accessType, version, chargeType)))
      case _ =>
        if (errors.size <= maximumNumberOfError) {
          val cellErrors: Seq[JsObject] = errorJson(errors, messages)
          renderer.render(template = "fileUpload/invalid.njk",
            Json.obj(
              "chargeType" -> chargeType,
              "chargeTypeText" -> ChargeType.fileUploadText(chargeType)(messages),
              "srn" -> srn, "startDate" -> Some(startDate),
              "errors" -> cellErrors,
              "fileDownloadInstructionsLink" -> fileDownloadInstructionLink,
              "returnToFileUploadURL" -> returnToFileUpload,
              "returnToSchemeDetails" -> returnToSchemeDetails,
              "schemeName" -> schemeName
            )
          ).map(Ok(_))
        }
        else {
          val genericErrors = generateGenericErrorReport(errors, chargeType)
          renderer.render(template = "fileUpload/genericErrors.njk",
            Json.obj(
              "chargeType" -> chargeType,
              "chargeTypeText" -> ChargeType.fileUploadText(chargeType)(messages),
              "srn" -> srn,
              "startDate" -> Some(startDate),
              "totalError" -> errors.size,
              "errors" -> genericErrors,
              "fileDownloadInstructionsLink" -> fileDownloadInstructionLink,
              "returnToFileUploadURL" -> returnToFileUpload,
              "returnToSchemeDetails" -> returnToSchemeDetails,
              "schemeName" -> schemeName
            )
          ).map(Ok(_))
        }
    }
  }

  private def errorJson(errors: Seq[ParserValidationError], messages: Messages): Seq[JsObject] = {
    val cellErrors = errors.map { e =>
      val cell = String.valueOf(('A' + e.col).toChar) + (e.row + 1)
      Json.obj(
        "cell" -> cell,
        "error" -> messages(e.error, e.args: _*)
      )
    }
    cellErrors
  }

  private def removeMemberBasedCharge(ua: UserAnswers, chargeType: ChargeType): UserAnswers =
    chargeType match {
      case ChargeTypeAnnualAllowance => ua.removeWithPath(JsPath \ "chargeEDetails")
      case ChargeTypeLifetimeAllowance => ua.removeWithPath(JsPath \ "chargeDDetails")
      case ChargeTypeOverseasTransfer => ua.removeWithPath(JsPath \ "chargeGDetails")
      case _ => ua
    }

  private def parseAndRenderResult(
                                    srn: String,
                                    startDate: LocalDate,
                                    accessType: AccessType,
                                    version: Int,
                                    chargeType: ChargeType,
                                    csvContent: Seq[Array[String]],
                                    parser: Parser)(implicit request: DataRequest[AnyContent]): Future[Result] = {


    val pstr = request.userAnswers.get(PSTRQuery).getOrElse(s"No PSTR found in Mongo cache. Srn is $srn")
    val updatedUA = removeMemberBasedCharge(request.userAnswers, chargeType)
    val startTime = System.currentTimeMillis
    val parserResult = TimeLogger.logOperationTime(parser.parse(startDate, csvContent, updatedUA), "Parsing and Validation")
    val endTime = System.currentTimeMillis
    val futureResult = parserResult.fold[Future[Result]](
      processInvalid(srn, startDate, accessType, version, chargeType, _),
      updatedUA =>
        TimeLogger.logOperationTime(processSuccessResult(chargeType, updatedUA).map(_ =>
          Redirect(routes.FileUploadSuccessController.onPageLoad(srn, startDate.toString, accessType, version, chargeType))), "processSuccessResult")
    )
    futureResult.map { result =>
      sendAuditEvent(
        pstr = pstr,
        chargeType = chargeType,
        csvContent.size - 1,
        fileValidationTimeInSeconds = ((endTime - startTime) / 1000).toInt,
        parserResult = parserResult)
      result
    }
  }

  private def failureReasonAndErrorReportForAudit(errors: Seq[ParserValidationError],
                                                  chargeType: ChargeType)(implicit messages: Messages): Option[(String, String)] = {
    if (errors.isEmpty) {
      None
    } else if (errors.size <= maximumNumberOfError) {
      val errorReport = errorJson(errors, messages).foldLeft("") { (acc, jsObject) =>
        ((jsObject \ "cell").asOpt[String], (jsObject \ "error").asOpt[String]) match {
          case (Some(cell), Some(error)) => acc ++ ((if (acc.nonEmpty) "\n" else EMPTY) + s"$cell: $error")
          case _ => acc
        }
      }
      Some(Tuple2("Field Validation failure(Less than 10)", errorReport))
    } else {
      val errorReport = generateGenericErrorReport(errors, chargeType).foldLeft(EMPTY) { (acc, c) =>
        acc ++ (if (acc.nonEmpty) "\n" else EMPTY) + messages(c)
      }
      (errors.size, errorReport)
      Some(Tuple2("Generic failure (more than 10)", errorReport))
    }
  }

  private def sendAuditEvent(pstr: String,
                             chargeType: ChargeType,
                             numberOfEntries: Int,
                             fileValidationTimeInSeconds: Int,
                             parserResult: Either[Seq[ParserValidationError], UserAnswers]
                            )(implicit request: DataRequest[AnyContent], messages: Messages): Unit = {

    val numberOfFailures = parserResult.fold(_.size, _ => 0)
    val (failureReason, errorReport) = parserResult match {
      case Left(Seq(FileLevelParserValidationErrorTypeHeaderInvalidOrFileEmpty)) =>
        Tuple2(Some(FileLevelParserValidationErrorTypeHeaderInvalidOrFileEmpty.error), None)
      case Left(errors) =>
        failureReasonAndErrorReportForAudit(errors, chargeType)(messages) match {
          case Some(Tuple2(reason, report)) => Tuple2(Some(reason), Some(report))
          case _ => Tuple2(None, None)
        }
      case _ => Tuple2(None, None)
    }

    auditService.sendEvent(
      AFTFileValidationCheckAuditEvent(
        administratorOrPractitioner = request.schemeAdministratorType,
        id = request.idOrException,
        pstr = pstr,
        numberOfEntries = numberOfEntries,
        chargeType = chargeType,
        validationCheckSuccessful = parserResult.isRight,
        fileValidationTimeInSeconds = fileValidationTimeInSeconds,
        failureReason = failureReason,
        numberOfFailures = numberOfFailures,
        validationFailureContent = errorReport
      )
    )
  }


  private def processSuccessResult(chargeType: ChargeType, ua: UserAnswers)
                                  (implicit request: DataRequest[AnyContent]): Future[UserAnswers] = {

    for {
      updatedAnswers <- fileUploadAftReturnService.preProcessAftReturn(chargeType, ua)
      _ <- aftService.fileCompileReturn(ua.get(PSTRQuery).getOrElse("pstr"), updatedAnswers)
    } yield {
      updatedAnswers
    }
  }

  def onPageLoad(srn: String, startDate: LocalDate, accessType: AccessType, version: Int, chargeType: ChargeType, uploadId: UploadId): Action[AnyContent] =
    (identify andThen getData(srn, startDate) andThen requireData andThen allowAccess(srn, startDate, None, version, accessType)).async {
      implicit request =>
        val parser = findParser(chargeType)
        val startTime = System.currentTimeMillis
        uploadProgressTracker.getUploadResult(uploadId).flatMap {
          case Some(uploadStatus) =>
            (parser, uploadStatus.status._type) match {
              case (Some(_), "" | "Failed" | "InProgress") => sessionExpired
              case (None, _) => sessionExpired
              case (Some(parser), "UploadedSuccessfully") =>
                upscanInitiateConnector.download(uploadStatus.status.downloadUrl.getOrElse("")).flatMap { response =>

                  sendAuditEventUpscanDownload(chargeType, response.status, startTime, uploadStatus)
                  response.status match {
                    case OK =>
                      val linesFromCSV = CsvLineSplitter.split(response.body)
                      parseAndRenderResult(srn, startDate, accessType, version, chargeType, linesFromCSV, parser)
                    case _ =>
                      Future.successful(Redirect(routes.UpscanErrorController.unknownError(srn, startDate.toString, accessType, version)))
                  }
                }
            }
          case _ => sessionExpired
        }
    }

  private def sendAuditEventUpscanDownload(chargeType: ChargeType,
                                           responseStatus: Int,
                                           startTime: Long,
                                           fileUploadDataCache: FileUploadDataCache)(implicit request: DataRequest[AnyContent]): Unit = {
    val pstr = request.userAnswers.get(PSTRQuery).getOrElse(s"No PSTR found in Mongo cache.")
    val endTime = System.currentTimeMillis
    val duration = endTime- startTime
    auditService.sendEvent(AFTUpscanFileDownloadAuditEvent
    (psaOrPspId = request.idOrException,
      pstr = pstr,
      schemeAdministratorType = request.schemeAdministratorType,
      chargeType= chargeType,
      fileUploadDataCache = fileUploadDataCache,
      downloadStatus= responseStatus match{
        case 200 => "Success"
        case _ => "Failed"
      },
      downloadTimeInMilliSeconds= duration
    ))
  }

  private def sessionExpired: Future[Result] = Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad))

  private def findParser(chargeType: ChargeType): Option[Parser] = {
    chargeType match {
      case ChargeTypeAnnualAllowance => Some(annualAllowanceParser)
      case ChargeTypeLifetimeAllowance => Some(lifeTimeAllowanceParser)
      case ChargeTypeOverseasTransfer => Some(overseasTransferParser)
      case _ => None
    }
  }
}