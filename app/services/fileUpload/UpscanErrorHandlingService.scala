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

import controllers.fileUpload.routes
import models.AccessType
import models.requests.DataRequest
import play.api.mvc.Result
import play.api.mvc.Results.Redirect

import scala.concurrent.Future

class UpscanErrorHandlingService {

  def handleFailureResponse(failureResponse: String,srn: String, startDate: String, accessType: AccessType,
                                    version: Int)(implicit request: DataRequest[_]): Future[Result]  = {
    failureResponse match {
      case "QUARANTINE" =>
        Future.successful(Redirect(routes.UpscanErrorController.quarantineError(srn, startDate, accessType, version)))
      case "REJECTED" =>
        Future.successful(Redirect(routes.UpscanErrorController.rejectedError(srn, startDate, accessType, version)))
      case "UNKNOWN" =>
        Future.successful(Redirect(routes.UpscanErrorController.unknownError(srn, startDate, accessType, version)))
    }
  }

}