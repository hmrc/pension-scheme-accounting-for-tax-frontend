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
import connectors.UpscanInitiateConnector
import controllers.actions._
import controllers.fileUpload.FileUploadGenericErrorReporter.generateGenericErrorReport
import fileUploadParsers.Parser.FileLevelParserValidationErrorTypeHeaderInvalidOrFileEmpty
import fileUploadParsers._
import models.ChargeType.{ChargeTypeAnnualAllowance, ChargeTypeLifetimeAllowance, ChargeTypeOverseasTransfer}
import models.requests.DataRequest
import models.{AccessType, ChargeType, Failed, InProgress, UploadId, UploadedSuccessfully, UserAnswers}
import pages.{PSTRQuery, SchemeNameQuery}
import play.api.Logger
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.libs.json.{JsObject, JsPath, Json}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, Result}
import renderer.Renderer
import services.AFTService
import services.fileUpload.{FileUploadAftReturnService, UploadProgressTracker}
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

  private val logger = Logger("ValidationController")

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
                                    linesFromCSV: List[String],
                                    parser: Parser)(implicit request: DataRequest[AnyContent]): Future[Result] = {

    //removes non-printable characters like ^M$
    val filteredLinesFromCSV = linesFromCSV.map(lines => lines.replaceAll("\\p{C}", ""))

    val updatedUA = removeMemberBasedCharge(request.userAnswers, chargeType)

    logger.warn(s"FileUpload logging parseParallel start is ${System.currentTimeMillis} ms")

    parser.parseParallel(startDate, filteredLinesFromCSV, updatedUA).flatMap{ p =>
      logger.warn(s"FileUpload logging parseParallel end is ${System.currentTimeMillis} ms")
      p.fold[Future[Result]](
        processInvalid(srn, startDate, accessType, version, chargeType, _),
        updatedUA =>
          TimeLogger.logOperationTime(
            processSuccessResult(chargeType, updatedUA).map(_ =>
              Redirect(routes.FileUploadSuccessController.onPageLoad(srn, startDate.toString, accessType, version, chargeType))),
            "processSuccessResult"
          )
      )
    }
  }

  private def processSuccessResult(chargeType: ChargeType, ua: UserAnswers)
                                  (implicit request: DataRequest[AnyContent]) = {

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
        uploadProgressTracker.getUploadResult(uploadId).flatMap { uploadStatus =>
          (parser, uploadStatus) match {
            case (Some(_), None | Some(Failed(_, _)) | Some(InProgress)) => sessionExpired
            case (None, _) => sessionExpired
            case (Some(parser), Some(ud: UploadedSuccessfully)) =>
              upscanInitiateConnector.download(ud.downloadUrl).flatMap { response =>
                response.status match {
                  case OK =>
                    val linesFromCSV = response.body.split("\n").toList
                    parseAndRenderResult(srn, startDate, accessType, version, chargeType, linesFromCSV, parser)
                  case _ =>
                    Future.successful(Redirect(routes.UpscanErrorController.unknownError(srn, startDate.toString, accessType, version)))
                }
              }
          }
        }
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