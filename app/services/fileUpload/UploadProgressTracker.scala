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

package services.fileUpload

import com.google.inject.ImplementedBy
import connectors.Reference
import connectors.cache.FileUploadCacheConnector
import models.{UploadId, UploadStatus}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}


@ImplementedBy(classOf[FileUploadCacheConnector])
trait UploadProgressTracker {

  def requestUpload(uploadId: UploadId, fileReference: Reference)(implicit ec: ExecutionContext, hc: HeaderCarrier): Future[Unit]

  def registerUploadResult(reference: Reference, uploadStatus: UploadStatus)(implicit ec: ExecutionContext, hc: HeaderCarrier): Future[Unit]

  def getUploadResult(id: UploadId)(implicit ec: ExecutionContext, hc: HeaderCarrier): Future[Option[UploadStatus]]

}