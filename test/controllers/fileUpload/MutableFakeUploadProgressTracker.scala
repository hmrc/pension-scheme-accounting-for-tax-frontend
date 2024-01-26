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

import connectors.Reference
import models.{FileUploadDataCache, FileUploadStatus, UploadId, UploadStatus}
import services.fileUpload.UploadProgressTracker
import uk.gov.hmrc.http.HeaderCarrier

import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}

class MutableFakeUploadProgressTracker extends UploadProgressTracker {
  private var dataToReturn: FileUploadDataCache =
        FileUploadDataCache(
          uploadId = "uploadID",
          reference ="reference",
          status=  FileUploadStatus(
            _type= "someString"),
          created= LocalDateTime.now,
          lastUpdated= LocalDateTime.now,
          expireAt= LocalDateTime.now
    )
  def setDataToReturn(result: FileUploadDataCache): Unit = dataToReturn = result

  override def requestUpload(uploadId: UploadId, fileReference: Reference)(implicit ec: ExecutionContext, hc: HeaderCarrier): Future[Unit] =
    Future.successful(())

  override def registerUploadResult(reference: Reference, uploadStatus: UploadStatus)(implicit ec: ExecutionContext,
                                                                                      hc: HeaderCarrier): Future[Unit] = Future.successful(())

  override def getUploadResult(id: UploadId)(implicit ec: ExecutionContext, hc: HeaderCarrier): Future[Option[FileUploadDataCache]] =
    Future.successful(Some(dataToReturn))
}
