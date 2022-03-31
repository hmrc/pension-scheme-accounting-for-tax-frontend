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

package connectors

import audit.{AFTUpscanFileUploadAuditEvent, AuditService}
import config.FrontendAppConfig
import models.requests.DataRequest
import models.{ChargeType, FileUploadDataCache, FileUploadStatus, UploadId, UpscanFileReference, UpscanInitiateResponse}
import pages.PSTRQuery
import play.api.libs.json.{Json, OFormat, Reads, Writes}
import play.api.mvc.AnyContent
import play.mvc.Http.HeaderNames
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpResponse}

import java.time.LocalDateTime
import java.util.UUID
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Failure

sealed trait UpscanInitiateRequest

// TODO expectedContentType is also an optional value
case class UpscanInitiateRequestV2(
  callbackUrl: String,
  successRedirect: Option[String] = None,
  errorRedirect: Option[String]   = None,
  minimumFileSize: Option[Int]    = None,
  maximumFileSize: Option[Int]    = Some(512),
  expectedContentType: Option[String] = None)
    extends UpscanInitiateRequest

case class UploadForm(href: String, fields: Map[String, String])

case class Reference(reference: String) extends AnyVal

object Reference {
  implicit val referenceReader: Reads[Reference] = Reads.StringReads.map(Reference(_))
  implicit val referenceWrites = Json.writes[Reference]
}

case class PreparedUpload(reference: Reference, uploadRequest: UploadForm)

object UpscanInitiateRequestV2 {
  implicit val format: OFormat[UpscanInitiateRequestV2] = Json.format[UpscanInitiateRequestV2]
}

object PreparedUpload {

  implicit val uploadFormFormat: Reads[UploadForm] = Json.reads[UploadForm]

  implicit val format: Reads[PreparedUpload] = Json.reads[PreparedUpload]
}

class UpscanInitiateConnector @Inject()(httpClient: HttpClient, appConfig: FrontendAppConfig,auditService: AuditService)(implicit ec: ExecutionContext) {

  private val headers = Map(
    HeaderNames.CONTENT_TYPE -> "application/json"
  )

  def initiateV2(redirectOnSuccess: Option[String], redirectOnError: Option[String], chargeType: ChargeType)
                (implicit request: DataRequest[AnyContent], headerCarrier: HeaderCarrier): Future[UpscanInitiateResponse] = {


    val req = UpscanInitiateRequestV2(
      callbackUrl = appConfig.upScanCallBack,
      successRedirect = redirectOnSuccess,
      errorRedirect = redirectOnError,
      maximumFileSize = Some(appConfig.maxUploadFileSize  * (1024 * 1024))
    )
    initiate(appConfig.initiateV2Url,req, chargeType)
  }
  private def sendAuditEvent(chargeType: ChargeType, fileUploadDataCache: FileUploadDataCache, startTime: Long)(implicit request: DataRequest[AnyContent]): Unit = {
    val pstr = request.userAnswers.get(PSTRQuery).getOrElse(s"No PSTR found in Mongo cache.")
    val endTime = System.currentTimeMillis
    val duration = endTime- startTime
    auditService.sendEvent(AFTUpscanFileUploadAuditEvent
    (psaOrPspId = request.idOrException,
      pstr = pstr,
      schemeAdministratorType = request.schemeAdministratorType,
      chargeType= chargeType,
      fileUploadDataCache =fileUploadDataCache,
      uploadTimeInMilliSeconds = duration
    ))
  }
  private def initiate[T](url: String, initialRequest: T, chargeType: ChargeType)(
    implicit request: DataRequest[AnyContent], headerCarrier: HeaderCarrier, wts: Writes[T]): Future[UpscanInitiateResponse] = {
    val startTime = System.currentTimeMillis

    val fileUploadDataCache = FileUploadDataCache(uploadId = "", reference = UUID.randomUUID().toString, status =
      FileUploadStatus(_type = "Failed", failureReason = Some("SERVICE UNAVAILABLE"), message = None, downloadUrl = None, mimeType = None, name = None, size = None),
      created = LocalDateTime.now(), lastUpdated = LocalDateTime.now(), expireAt = LocalDateTime.now())
    (for {
      response <- httpClient.POST[T, PreparedUpload](url, initialRequest, headers.toSeq)
      fileReference = UpscanFileReference(response.reference.reference)
      postTarget = response.uploadRequest.href
      formFields = response.uploadRequest.fields
    } yield {
      println("\n\n\n\n\n\n\n\n"+ response)
      UpscanInitiateResponse(fileReference, postTarget, formFields)}).andThen {
      case Failure(e) =>
        println("\n\n\n\n\n@@@@@@@@@@@@"+e)
      sendAuditEvent(chargeType, fileUploadDataCache, startTime)
      Future.failed(UpscanInitiateError(e))
    }
  }
  def download(downloadUrl: String)(implicit hc: HeaderCarrier): Future[HttpResponse] = {
    httpClient.GET(downloadUrl)
  }
  case class UpscanInitiateError(e: Throwable) extends RuntimeException(e)
}