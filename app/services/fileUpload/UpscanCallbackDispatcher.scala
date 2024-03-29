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

package services.fileUpload

import models._
import uk.gov.hmrc.http.HeaderCarrier
import upscan.callback.{CallbackBody, FailedCallbackBody, ReadyCallbackBody}

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class UpscanCallbackDispatcher @Inject()(sessionStorage: UploadProgressTracker) {

  def handleCallback(callback: CallbackBody)(implicit ec: ExecutionContext, headerCarrier: HeaderCarrier): Future[Unit] = {

    val uploadStatus = callback match {
      case s: ReadyCallbackBody =>
        UploadedSuccessfully(
          s.uploadDetails.fileName,
          s.uploadDetails.fileMimeType,
          s.downloadUrl.toString,
          Some(s.uploadDetails.size)
        )
      case f: FailedCallbackBody =>
        Failed(f.failureDetails.failureReason,
          f.failureDetails.message)
    }

    sessionStorage.registerUploadResult(callback.reference, uploadStatus)
  }
}
