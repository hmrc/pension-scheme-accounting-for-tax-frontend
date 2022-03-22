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

import audit.AuditEvent
import models.{AdministratorOrPractitioner, ChargeType}

import java.time.LocalDateTime


case class AFTUpscanFileUploadAuditEvent(
                                          psaOrPspId: String,
                                          pstr: String,
                                          schemeAdministratorType: AdministratorOrPractitioner,
                                          chargeType: ChargeType,
                                          uploadStatus: String,
                                          failureReason: Option[String]= None,
                                          uploadTime: Long,
                                          fileSize: Option[Long]=None,
                                          reference: String
                                        ) extends AuditEvent {
  override def auditType: String = "AFTFileUpscanUploadCheck"

  override def details:Map[String, String] = {
    val psaOrPspIdJson = schemeAdministratorType match {
      case AdministratorOrPractitioner.Administrator =>
        Map("psaId" -> psaOrPspId)
      case _ => Map("pspId" -> psaOrPspId)
    }
    Map(
      "pstr" -> pstr,
      "chargeType" -> chargeType.toString,
      "uploadStatus" -> uploadStatus,
      "failureReason" -> failureReason.getOrElse(""),
      "uploadTime" -> uploadTime.toString(),
      "fileSize" -> fileSize.getOrElse("").toString,
      "reference" -> reference
    ) ++ psaOrPspIdJson
  }
}