/*
 * Copyright 2023 HM Revenue & Customs
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

package controllers

import controllers.base.ControllerSpecBase
import models.ChargeType.ChargeTypeAnnualAllowance
import org.scalatest.concurrent.ScalaFutures.whenReady
import play.api.test.FakeRequest
import play.api.test.Helpers._

class FileDownloadControllerSpec extends ControllerSpecBase {

  "templateFile" must {

    "return an OK with a GET" in {

      val application = applicationBuilder(userAnswers = None).build()

      val request = FakeRequest(GET, routes.FileDownloadController.templateFile(ChargeTypeAnnualAllowance).url)

      val result = route(application, request).value

      status(result) mustEqual OK

      whenReady(result) { response =>
        val headers: Option[String] = response.header.headers.get("Content-Disposition")

        headers mustBe Some("""attachment; filename="aft-annual-allowance-charge-upload-template.csv"""")
      }

      application.stop()
    }
  }

  "instructionsFile" must {}
  "return an OK with a GET" in {
    val application = applicationBuilder(userAnswers = None).build()

    val request = FakeRequest(GET, routes.FileDownloadController.instructionsFile(ChargeTypeAnnualAllowance).url)

    val result = route(application, request).value

    status(result) mustEqual OK

    whenReady(result) { response =>
      val headers: Option[String] = response.header.headers.get("Content-Disposition")

      headers mustBe Some("""attachment; filename="aft-annual-allowance-charge-upload-format-instructions.ods"""")
    }

    application.stop()
  }

}