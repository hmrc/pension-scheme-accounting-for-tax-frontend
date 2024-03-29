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

package models


import play.api.libs.json._
import play.api.mvc.QueryStringBindable

import java.time.LocalDateTime
import java.util.UUID

case class UpscanFileReference(reference: String)

case class UpscanInitiateResponse(
                                   fileReference: UpscanFileReference,
                                   postTarget: String,
                                   formFields: Map[String, String])

sealed trait UploadStatus

case object InProgress extends UploadStatus

case class Failed(failureReason: String, message: String) extends UploadStatus

object Failed {
  implicit val failedFormat: OFormat[Failed] = Json.format[Failed]
}

case class UploadedSuccessfully(name: String, mimeType: String, downloadUrl: String, size: Option[Long]) extends UploadStatus

object UploadedSuccessfully {
  implicit val uploadedSuccessfullyFormat: OFormat[UploadedSuccessfully] = Json.format[UploadedSuccessfully]
}

case class FileUploadStatus(
                             _type: String,
                             failureReason: Option[String] = None,
                             message: Option[String] = None,
                             downloadUrl: Option[String] = None,
                             mimeType: Option[String] = None,
                             name: Option[String] = None,
                             size: Option[Long] = None
                           )

object FileUploadStatus {
  implicit val reads: OFormat[FileUploadStatus] = Json.format[FileUploadStatus]
}

case class FileUploadDataCache(uploadId: String, reference: String, status: FileUploadStatus, created: LocalDateTime,
                               lastUpdated: LocalDateTime, expireAt: LocalDateTime)


object FileUploadDataCache {
  implicit val reads: OFormat[FileUploadDataCache] = Json.format[FileUploadDataCache]
}

case class UploadId(value: String) extends AnyVal

object UploadId {
  def generate: UploadId = UploadId(UUID.randomUUID().toString)

  implicit def queryBinder(implicit stringBinder: QueryStringBindable[String]): QueryStringBindable[UploadId] =
    stringBinder.transform(UploadId(_), _.value)
}
