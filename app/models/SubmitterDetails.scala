/*
 * Copyright 2021 HM Revenue & Customs
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

import play.api.libs.json.{Format, Json}

import java.time.LocalDate

sealed trait SubmitterType

object SubmitterType extends Enumerable.Implicits {

  case object PSA extends WithName("PSA") with SubmitterType

  case object PSP extends WithName("PSP") with SubmitterType

  val values: Seq[SubmitterType] = Seq(PSA, PSP)

  implicit val enumerable: Enumerable[SubmitterType] =
    Enumerable(values.map(v => v.toString -> v): _*)
}

case class SubmitterDetails(submitterType: SubmitterType, submitterName: String, submitterID: String, authorisingPsaId: Option[String], receiptDate: LocalDate)

object SubmitterDetails {
  implicit val formats: Format[SubmitterDetails] = Json.format[SubmitterDetails]
}

case class VersionsWithSubmitter(versionDetails: AFTVersion, submitterDetails: Option[SubmitterDetails])

object VersionsWithSubmitter {
  implicit val formats: Format[VersionsWithSubmitter] = Json.format[VersionsWithSubmitter]
}