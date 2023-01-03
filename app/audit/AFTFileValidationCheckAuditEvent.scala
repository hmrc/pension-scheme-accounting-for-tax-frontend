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

package audit

import models.AdministratorOrPractitioner.Administrator
import models.{AdministratorOrPractitioner, ChargeType}

case class AFTFileValidationCheckAuditEvent(administratorOrPractitioner: AdministratorOrPractitioner,
                                            id: String,
                                            pstr: String,
                                            numberOfEntries: Int,
                                            chargeType: ChargeType,
                                            validationCheckSuccessful: Boolean,
                                            fileValidationTimeInSeconds: Int,
                                            failureReason: Option[String],
                                            numberOfFailures: Int,
                                            validationFailureContent: Option[String]
                                           ) extends AuditEvent {

  override def auditType: String = "AFTFileValidationCheck"

  override def details: Map[String, String] = {
    val idMap = administratorOrPractitioner match {
      case Administrator => Map("psaId" -> id)
      case _ => Map("pspId" -> id)
    }

    val failureFields = (failureReason match {
      case None => Map.empty
      case Some(reason) => Map("failureReason" -> reason)
    }) ++ (validationFailureContent match {
      case Some(c) => Map("validationFailureContent" -> c)
      case _ => Map.empty
    })

    idMap ++
      Map(
        "pstr" -> pstr,
        "numberOfEntries" -> numberOfEntries.toString,
        "chargeType" -> chargeType.toString,
        "validationCheckStatus" -> (if (validationCheckSuccessful) "Success" else "Failure"),
        "fileValidationTimeInSeconds" -> fileValidationTimeInSeconds.toString,
        "numberOfFailures" -> numberOfFailures.toString
      ) ++ failureFields
  }
}

