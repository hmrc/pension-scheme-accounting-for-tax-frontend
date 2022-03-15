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

///*
// * Copyright 2022 HM Revenue & Customs
// *
// * Licensed under the Apache License, Version 2.0 (the "License");
// * you may not use this file except in compliance with the License.
// * You may obtain a copy of the License at
// *
// *     http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS,
// * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// * See the License for the specific language governing permissions and
// * limitations under the License.
// */
//
//package services.fileUpload
//
//import connectors.Reference
//import models.{FileUploadDataCache, FileUploadStatus, InProgress, UploadId, UploadStatus}
//import uk.gov.hmrc.http.HeaderCarrier
//
//import java.time.LocalDate
//import java.util.concurrent.atomic.AtomicReference
//import java.util.function.UnaryOperator
//import javax.inject.Singleton
//import scala.concurrent.{ExecutionContext, Future}
//
//@deprecated(message="use FileUploadCacheConnector to store upload result into mongo db",since = "v0.541.0")
//@Singleton
//class InMemoryUploadProgressTracker extends UploadProgressTracker {
//
//  case class Entry(uploadId: UploadId, reference: Reference, uploadStatus: UploadStatus)
//
//  var entries: AtomicReference[Set[Entry]] = new AtomicReference[Set[Entry]](Set.empty)
//
//  def getUploadResult(id : UploadId)(implicit ec: ExecutionContext, hc: HeaderCarrier): Future[Option[FileUploadDataCache]] =
//    Future.successful(entries.get.find(_.uploadId == id).map( x=>
//      FileUploadDataCache(id.value,x.reference.toString,FileUploadStatus(x.uploadStatus.toString),,"")
//    ))
//
//  override def requestUpload(uploadId: UploadId, fileReference: Reference)(implicit ec: ExecutionContext, hc: HeaderCarrier): Future[Unit] = {
//    entries.updateAndGet(new UnaryOperator[Set[Entry]] {
//      override def apply(t: Set[Entry]): Set[Entry] = t.filterNot(in => in.uploadId == uploadId|| in.reference == fileReference) + Entry(uploadId, fileReference, InProgress)
//    })
//    Future.successful(())
//  }
//
//  override def registerUploadResult(reference: Reference, uploadStatus: UploadStatus)(implicit ec: ExecutionContext, hc: HeaderCarrier): Future[Unit] = {
//    entries.updateAndGet(new UnaryOperator[Set[Entry]] {
//      override def apply(t: Set[Entry]): Set[Entry] = {
//        val existing = t.find(_.reference == reference).getOrElse(throw new RuntimeException("Doesn't exist"))
//        t - existing + existing.copy(uploadStatus = uploadStatus)
//      }
//    })
//    Future.successful(())
//  }
//}