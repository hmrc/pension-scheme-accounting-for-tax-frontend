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

import base.SpecBase
import controllers.fileUpload.routes._
import data.SampleData._
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.mockito.MockitoSugar
import play.api.mvc.Results.Redirect

class UpscanErrorHandlingServiceSpec extends SpecBase with MockitoSugar with ScalaFutures {

  private val upscanErrorHandlingService = new UpscanErrorHandlingService

  "handleFailureResponse" must {
    "redirect to quarantine error when failureResponse is QUARANTINE" in {
      whenReady(upscanErrorHandlingService.handleFailureResponse("QUARANTINE", srn, startDate.toString, accessType, versionInt)) {
        _ mustBe Redirect(UpscanErrorController.quarantineError(srn, startDate.toString, accessType, versionInt))
      }
    }

    "redirect to quarantine error when failureResponse is REJECTED" in {
      whenReady(upscanErrorHandlingService.handleFailureResponse("REJECTED", srn, startDate.toString, accessType, versionInt)) {
        _ mustBe Redirect(UpscanErrorController.rejectedError(srn, startDate.toString, accessType, versionInt))
      }
    }

    "redirect to quarantine error when failureResponse is UNKNOWN" in {
      whenReady(upscanErrorHandlingService.handleFailureResponse("UNKNOWN", srn, startDate.toString, accessType, versionInt)) {
        _ mustBe Redirect(UpscanErrorController.unknownError(srn, startDate.toString, accessType, versionInt))
      }
    }
  }
}
