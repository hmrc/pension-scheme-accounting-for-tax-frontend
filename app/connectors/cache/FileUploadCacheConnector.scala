/*
 * Copyright 2025 HM Revenue & Customs
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

package connectors.cache

import com.google.inject.Inject
import config.FrontendAppConfig
import connectors.Reference
import models.{Failed, FileUploadDataCache, FileUploadStatus, InProgress, UploadId, UploadStatus, UploadedSuccessfully}
import play.api.Logger
import play.api.http.Status._
import play.api.libs.json._
import services.fileUpload.UploadProgressTracker
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http._
import uk.gov.hmrc.http.client.HttpClientV2
import play.api.libs.ws.WSBodyWritables.writeableOf_JsValue

import scala.concurrent.{ExecutionContext, Future}

class FileUploadCacheConnector @Inject()(
                                          config: FrontendAppConfig,
                                          http: HttpClientV2
                                        ) extends UploadProgressTracker {

  private val logger = Logger(this.getClass)
  private val ContentTypeJson = ("Content-Type", "application/json")
  private def fileUploadUrl = url"${config.aftUrl}/pension-scheme-accounting-for-tax/cache/fileUpload"
  private def fileUploadResultUrl = url"${config.aftUrl}/pension-scheme-accounting-for-tax/cache/fileUploadResult"
  private def buildHeadersWithUploadId(uploadId: UploadId): Seq[(String, String)] =
    Seq(ContentTypeJson, ("uploadId", uploadId.value))
  private def buildHeadersWithReference(reference: Reference): Seq[(String, String)] =
    Seq(ContentTypeJson, ("reference", reference.reference))
  private def mapUploadStatus(uploadStatus: UploadStatus): FileUploadStatus = uploadStatus match {
    case InProgress => FileUploadStatus(InProgress.toString, None, None, None, None, None, None)
    case f: Failed => FileUploadStatus("Failed", Some(f.failureReason), Some(f.message), None, None, None, None)
    case s: UploadedSuccessfully =>
      FileUploadStatus("UploadedSuccessfully", None, None, Some(s.downloadUrl), Some(s.mimeType), Some(s.name), s.size)
  }

  override def getUploadResult(id: UploadId)
                              (implicit ec: ExecutionContext, headerCarrier: HeaderCarrier): Future[Option[FileUploadDataCache]] = {
    http.get(fileUploadUrl)
      .setHeader(buildHeadersWithUploadId(id)*)
      .execute[HttpResponse]
      .recoverWith(mapExceptionsToStatus)
      .map { response =>
        response.status match {
          case NOT_FOUND => None
          case OK => Some(response.json.as[FileUploadDataCache])
          case _ => throw new HttpException(response.body, response.status)
        }
      }
  }

  override def requestUpload(uploadId: UploadId, fileReference: Reference)
                            (implicit ec: ExecutionContext, headerCarrier: HeaderCarrier): Future[Unit] = {
    val jsonBody = Json.obj(
      "reference" -> fileReference.reference
    )

    http.post(fileUploadUrl)
      .withBody(jsonBody)
      .setHeader(buildHeadersWithUploadId(uploadId)*)
      .execute[HttpResponse]
      .map { response =>
        response.status match {
          case OK => logger.info(s"requestUpload for uploadId ${uploadId.value} returned response with status OK")
          case _ =>
            logger.warn(s"requestUpload for uploadId ${uploadId.value} returned response with status ${response.status}")
            throw new HttpException(response.body, response.status)
        }
      }
  }

  override def registerUploadResult(reference: Reference, uploadStatus: UploadStatus)
                                   (implicit ec: ExecutionContext, headerCarrier: HeaderCarrier): Future[Unit] = {
    val body = Json.toJson(mapUploadStatus(uploadStatus))
    http.post(fileUploadResultUrl)
      .withBody(body)
      .setHeader(buildHeadersWithReference(reference)*)
      .execute[HttpResponse]
      .map { response =>
        response.status match {
          case OK => logger.info(s"registerUploadResult for Reference ${reference.reference} returned response with status OK")
          case _ =>
            logger.warn(s"registerUploadResult for Reference ${reference.reference} returned response with status ${response.status}")
            throw new HttpException(response.body, response.status)
        }
      }
  }

  private def mapExceptionsToStatus: PartialFunction[Throwable, Future[HttpResponse]] = {
    case _: NotFoundException =>
      Future.successful(HttpResponse(NOT_FOUND, "Not found"))
  }
}