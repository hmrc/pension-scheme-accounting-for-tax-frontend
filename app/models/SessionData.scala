/*
 * Copyright 2020 HM Revenue & Customs
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

import play.api.libs.json.Reads
import play.api.libs.json.Writes
import play.api.libs.functional.syntax._
import play.api.libs.json._

import scala.language.implicitConversions

case class SessionAccessData(version: Int, accessMode: AccessMode)

case class SessionData(sessionId: String, name: Option[String], sessionAccessData: SessionAccessData) {

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
      (JsPath \ "name").writeNullable[String] and
      (JsPath \ "version").write[Int] and
      (JsPath \ "accessMode").write[AccessMode])(sd => (sd.sessionId, sd.name, sd.sessionAccessData.version, sd.sessionAccessData.accessMode))

  implicit val reads: Reads[SessionData] =
    ((JsPath \ "sessionId").read[String] and
      (JsPath \ "name").readNullable[String] and
      (JsPath \ "version").read[Int] and
      (JsPath \ "accessMode").read[AccessMode])(
      (sessionId, optionName, version, accessMode) => SessionData(sessionId, optionName, SessionAccessData(version, accessMode))
    )
}
