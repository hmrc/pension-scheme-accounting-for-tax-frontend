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

package connectors.cache

import com.google.inject.Inject
import config.FrontendAppConfig
import connectors.Reference
import models.{Failed, InProgress, Status, UploadId, UploadStatus, UploadedSuccessfully}
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

  private val uploadedSuccessfullyFormat: OFormat[UploadedSuccessfully] = Json.format[UploadedSuccessfully]

  override def getUploadResult(id: UploadId)
                              (implicit ec: ExecutionContext, headerCarrier: HeaderCarrier): Future[Option[UploadStatus]] = {

    val headers: Seq[(String, String)] = Seq(("Content-Type", "application/json"), ("uploadId", id.value))
    val hc: HeaderCarrier = headerCarrier.withExtraHeaders(headers: _*)

    implicit val read: Reads[UploadStatus] = (json: JsValue) => {
      val jsObject = json.asInstanceOf[JsObject]
      jsObject.value.get("_type") match {
        case Some(JsString("InProgress")) => JsSuccess(InProgress)
        case Some(JsString("Failed")) => JsSuccess(Failed)
        case Some(JsString("UploadedSuccessfully")) => Json.fromJson[UploadedSuccessfully](jsObject)(uploadedSuccessfullyFormat)
        case Some(value) => JsError(s"Unexpected value of _type: $value")
        case None => JsError("Missing _type field")
      }
    }
    http.GET[HttpResponse](url)(implicitly, hc, implicitly)
      .recoverWith(mapExceptionsToStatus)
      .map { response =>
        response.status match {
          case NOT_FOUND =>
            None
          case OK =>
            Some(response.json.as[UploadStatus])
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
          case OK => logger.warn(s"requestUpload for uploadId ${uploadId.value} return response with status ${response.status}")
          case _ =>
            throw new HttpException(response.body, response.status)
        }
      }
  }

  override def registerUploadResult(reference: Reference, uploadStatus: UploadStatus)
                                   (implicit ec: ExecutionContext, headerCarrier: HeaderCarrier): Future[Unit] = {
    val headers: Seq[(String, String)] = Seq(("Content-Type", "application/json"), ("reference", reference.reference))
    val hc: HeaderCarrier = headerCarrier.withExtraHeaders(headers: _*)

    val status = uploadStatus match {
      case InProgress => Status(InProgress.toString, None, None, None, None)
      case Failed => Status(Failed.toString, None, None, None, None)
      case s: UploadedSuccessfully => Status("UploadedSuccessfully", Some(s.downloadUrl), Some(s.mimeType), Some(s.name), s.size)
    }
    http.POST[JsValue, HttpResponse](urlUploadResult, Json.toJson(status))(implicitly, implicitly, hc, implicitly)
      .map { response =>
        response.status match {
          case OK => logger.warn(s"registerUploadResult for Reference ${reference.reference} return response with status ${response.status}")
          case _ =>
            throw new HttpException(response.body, response.status)
        }
      }
  }

  private def mapExceptionsToStatus: PartialFunction[Throwable, Future[HttpResponse]] = {
    case _: NotFoundException =>
      Future.successful(HttpResponse(NOT_FOUND, "Not found"))
  }
}
