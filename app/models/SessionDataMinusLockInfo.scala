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

import play.api.libs.json.Format
import play.api.libs.json.Json

// TODO: PODS-4134 naming???
case class SessionDataMinusLockInfo(version:Int, accessMode: AccessMode)

case class SessionData(sessionId: String, name: Option[String], version:Int, accessMode: AccessMode) {
  def isViewOnly = accessMode == AccessMode.PageAccessModeViewOnly
  def isEditable = !isViewOnly

  def isLocked = name.isDefined
}

object SessionData {
  implicit lazy val formats: Format[SessionData] =
    Json.format[SessionData]
}
