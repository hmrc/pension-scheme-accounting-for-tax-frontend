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

package models

import play.api.libs.functional.syntax._
import play.api.libs.json._

case class SessionAccessData(version: Int, accessMode: AccessMode, areSubmittedVersionsAvailable:Boolean)

case class SessionData(sessionId: String, lockDetail: Option[LockDetail], sessionAccessData: SessionAccessData) {

  def deriveMinimumChargeValueAllowed: BigDecimal = {
    (sessionAccessData.accessMode, sessionAccessData.version) match {
      case (AccessMode.PageAccessModePreCompile, 1) => BigDecimal("0.01")
      case _                                        => BigDecimal("0.00")
    }
  }

}

object SessionData {
  implicit val writes: Writes[SessionData] =
    ((JsPath \ "sessionId").write[String] and
      (JsPath \ "lockDetail").writeNullable[LockDetail] and
      (JsPath \ "version").write[Int] and
      (JsPath \ "accessMode").write[AccessMode] and
      (JsPath \ "areSubmittedVersionsAvailable").write[Boolean]
      )(sd => (sd.sessionId, sd.lockDetail, sd.sessionAccessData.version,
      sd.sessionAccessData.accessMode, sd.sessionAccessData.areSubmittedVersionsAvailable))

  implicit val reads: Reads[SessionData] =
    ((JsPath \ "sessionId").read[String] and
      (JsPath \ "lockDetail").readNullable[LockDetail] and
      (JsPath \ "version").read[Int] and
      (JsPath \ "accessMode").read[AccessMode] and
      (JsPath \ "areSubmittedVersionsAvailable").read[Boolean]
      )(
      (sessionId, optionName, version, accessMode, areSubmittedVersionsAvailable) =>
        SessionData(sessionId, optionName, SessionAccessData(version, accessMode, areSubmittedVersionsAvailable))
    )
}
