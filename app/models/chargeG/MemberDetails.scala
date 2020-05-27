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

package models.chargeG

import java.time.LocalDate

import play.api.libs.json._

case class MemberDetails (firstName: String, lastName: String, dob: LocalDate, nino: String) {
  def fullName: String = s"$firstName $lastName"
}

object MemberDetails {
  implicit val format = Json.format[MemberDetails]

  def applyDelete(firstName: String, lastName: String, dob: LocalDate, nino: String): MemberDetails = {
    MemberDetails(firstName, lastName, dob, nino)
  }

  def unapplyDelete(member: MemberDetails): Option[(String, String, LocalDate, String)] = {
    Some((member.firstName, member.lastName, member.dob, member.nino))
  }
}
