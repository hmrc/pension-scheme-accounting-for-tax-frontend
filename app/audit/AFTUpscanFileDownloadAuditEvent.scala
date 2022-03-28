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

package audit

import models.{AdministratorOrPractitioner, ChargeType, FileUploadDataCache}


case class AFTUpscanFileDownloadAuditEvent(
                                            psaOrPspId: String,
                                            schemeAdministratorType: AdministratorOrPractitioner,
                                            chargeType: ChargeType,
                                            pstr: String,
                                            fileUploadDataCache:FileUploadDataCache,
                                            downloadTimeInSeconds: Long
                                          ) extends AuditEvent {
  override def auditType: String = "AFTFileUpscanDownloadCheck"

  override def details:Map[String, String] = {
    val psaOrPspIdJson = schemeAdministratorType match {
      case AdministratorOrPractitioner.Administrator =>
        Map("psaId" -> psaOrPspId)
      case _ => Map("pspId" -> psaOrPspId)
    }

    val failureReason = fileUploadDataCache.status.failureReason match {
      case Some(r) => Map("failureReason" -> r)
      case _ => Map.empty
    }

    psaOrPspIdJson ++
    Map(
      "pstr" -> pstr,
      "chargeType" -> chargeType.toString,
      "downloadStatus" -> fileUploadDataCache.status._type,
      "downloadTimeInSeconds" -> downloadTimeInSeconds.toString,
      "reference" -> fileUploadDataCache.reference
    ) ++ failureReason
  }
}