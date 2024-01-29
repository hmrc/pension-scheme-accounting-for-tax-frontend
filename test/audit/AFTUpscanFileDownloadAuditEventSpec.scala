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

package audit

import models.AdministratorOrPractitioner.{Administrator, Practitioner}
import models.ChargeType.ChargeTypeAnnualAllowance
import models.{FileUploadDataCache, FileUploadStatus}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.time.LocalDateTime

class AFTUpscanFileDownloadAuditEventSpec extends AnyFlatSpec with Matchers {

  "AFTUpscanFileDownloadAuditEvent" should "output the correct map of data" in {
    val dateTime = LocalDateTime.now()
    val fileUploadDataCache: FileUploadDataCache =
      FileUploadDataCache("uploadId", "reference", FileUploadStatus("InProgress"), dateTime, dateTime, dateTime)

    val event = AFTUpscanFileDownloadAuditEvent(
      psaOrPspId = "A2500001",
      Administrator,
      ChargeTypeAnnualAllowance,
      "pstr",
      fileUploadDataCache,
      "200",
      1000L
    )

    val expected: Map[String, String] = Map(
      "psaId" -> "A2500001",
      "pstr" -> "pstr",
      "chargeType" -> ChargeTypeAnnualAllowance.toString,
      "downloadStatus" -> "200",
      "downloadTimeInMilliSeconds" -> 1000L.toString,
      "reference" -> "reference"
    )

    event.auditType shouldBe "AFTFileUpscanDownloadCheck"
    event.details shouldBe expected
  }

  "AFTUpscanFileDownloadAuditEvent" should "output the correct map of data for  Practitioner" in {

    val dateTime = LocalDateTime.now()
    val fileUploadDataCache: FileUploadDataCache =
      FileUploadDataCache("uploadId", "reference", FileUploadStatus("InProgress"), dateTime, dateTime, dateTime)

    val event = AFTUpscanFileDownloadAuditEvent(
      psaOrPspId = "2500001",
      Practitioner,
      ChargeTypeAnnualAllowance,
      "pstr",
      fileUploadDataCache,
      "200",
      1000L
    )

    val expected: Map[String, String] = Map(
      "pspId" -> "2500001",
      "pstr" -> "pstr",
      "chargeType" -> ChargeTypeAnnualAllowance.toString,
      "downloadStatus" -> "200",
      "downloadTimeInMilliSeconds" -> 1000L.toString,
      "reference" -> "reference"
    )

    event.auditType shouldBe "AFTFileUpscanDownloadCheck"
    event.details shouldBe expected
  }
}
