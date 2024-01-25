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

import scala.concurrent.{ExecutionContext, Future}

class FileUploadCacheConnector @Inject()(
                                          config: FrontendAppConfig,
                                          http: HttpClient
                                        ) extends UploadProgressTracker {

  private val logger = Logger(classOf[FileUploadCacheConnector])

  protected def url = s"${config.aftUrl}/pension-scheme-accounting-for-tax/cache/fileUpload"

  protected def urlUploadResult = s"${config.aftUrl}/pension-scheme-accounting-for-tax/cache/fileUploadResult"

  override def getUploadResult(id: UploadId)
                              (implicit ec: ExecutionContext, headerCarrier: HeaderCarrier): Future[Option[FileUploadDataCache]] = {

    val headers: Seq[(String, String)] = Seq(("Content-Type", "application/json"), ("uploadId", id.value))
    val hc: HeaderCarrier = headerCarrier.withExtraHeaders(headers: _*)


    http.GET[HttpResponse](url)(implicitly, hc, implicitly)
      .recoverWith(mapExceptionsToStatus)
      .map { response =>
        response.status match {
          case NOT_FOUND =>
            None
          case OK =>
            Some(response.json.as[FileUploadDataCache])
          case _ =>
            throw new HttpException(response.body, response.status)
        }
      }
  }

  override def requestUpload(uploadId: UploadId, fileReference: Reference)
                            (implicit ec: ExecutionContext, headerCarrier: HeaderCarrier): Future[Unit] = {
    val headers: Seq[(String, String)] = Seq(("Content-Type", "application/json"), ("uploadId", uploadId.value))
    implicit val hc: HeaderCarrier = headerCarrier.withExtraHeaders(headers: _*)

    http.POST[JsValue, HttpResponse](url, Json.toJson(fileReference))(implicitly, implicitly, hc, implicitly)
      .map { response =>
        response.status match {
          case OK => logger.info(s"requestUpload for uploadId ${uploadId.value} return response with status OK")
          case _ =>
            logger.warn(s"requestUpload for uploadId ${uploadId.value} return response with status ${response.status}")
            throw new HttpException(response.body, response.status)
        }
      }
  }

  override def registerUploadResult(reference: Reference, uploadStatus: UploadStatus)
                                   (implicit ec: ExecutionContext, headerCarrier: HeaderCarrier): Future[Unit] = {
    val headers: Seq[(String, String)] = Seq(("Content-Type", "application/json"), ("reference", reference.reference))
    val hc: HeaderCarrier = headerCarrier.withExtraHeaders(headers: _*)

    val status = uploadStatus match {
      case InProgress => FileUploadStatus(InProgress.toString, None, None, None, None, None, None)
      case f: Failed => FileUploadStatus("Failed", Some(f.failureReason), Some(f.message), None, None, None, None)
      case s: UploadedSuccessfully => FileUploadStatus("UploadedSuccessfully", None, None, Some(s.downloadUrl), Some(s.mimeType), Some(s.name), s.size)
    }
    http.POST[JsValue, HttpResponse](urlUploadResult, Json.toJson(status))(implicitly, implicitly, hc, implicitly)
      .map { response =>
        response.status match {
          case OK => logger.info(s"registerUploadResult for Reference ${reference.reference} return response with status OK")
          case _ =>
            logger.warn(s"registerUploadResult for Reference ${reference.reference} return response with status ${response.status}")
            throw new HttpException(response.body, response.status)
        }
      }
  }

  private def mapExceptionsToStatus: PartialFunction[Throwable, Future[HttpResponse]] = {
    case _: NotFoundException =>
      Future.successful(HttpResponse(NOT_FOUND, "Not found"))
  }
}
