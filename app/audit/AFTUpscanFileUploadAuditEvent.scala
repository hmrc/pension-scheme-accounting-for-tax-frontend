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

import models.{AdministratorOrPractitioner, ChargeType, FileUploadDataCache}

case class AFTUpscanFileUploadAuditEvent(
                                          psaOrPspId: String,
                                          pstr: String,
                                          schemeAdministratorType: AdministratorOrPractitioner,
                                          chargeType: ChargeType,
                                          outcome: Either[String, FileUploadDataCache],
                                          uploadTimeInMilliSeconds: Long
                                        ) extends AuditEvent {
  override def auditType: String = "AFTFileUpscanUploadCheck"

  override def details: Map[String, String] = {
    val psaOrPspIdJson = schemeAdministratorType match {
      case AdministratorOrPractitioner.Administrator =>
        Map("psaId" -> psaOrPspId)
      case _ => Map("pspId" -> psaOrPspId)
    }

    val detailMap = outcome match {
      case Left(error) =>
        Map(
          "uploadStatus" -> "Failed",
          "failureReason" -> "Service Unavailable",
          "failureDetail" -> error
        )
      case Right(fileUploadDataCache) =>
        val failureReasonMap = fileUploadDataCache.status.failureReason match {
          case Some(failureReason) =>
            Map(
              "failureReason" -> failureReason
            )
          case _ => Map.empty
        }

        val fileSizeMap = fileUploadDataCache.status.size match {
          case Some(v) =>
            Map(
              "fileSize" -> v.toString
            )
          case _ => Map.empty
        }

        Map(
          "uploadStatus" -> fileUploadDataCache.status._type) ++
          failureReasonMap ++ fileSizeMap ++
          Map(
            "reference" -> fileUploadDataCache.reference
          )
    }


    psaOrPspIdJson ++
      Map(
        "pstr" -> pstr,
        "chargeType" -> chargeType.toString,
        "uploadTimeInMillSeconds" -> uploadTimeInMilliSeconds.toString
      ) ++ detailMap
  }
}