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

package models

sealed trait SchemeStatus

object SchemeStatus extends Enumerable.Implicits {

  case object Pending extends WithName("Pending") with SchemeStatus
  case object PendingInfoRequired extends WithName("Pending Info Required") with SchemeStatus
  case object PendingInfoReceived extends WithName("Pending Info Received") with SchemeStatus
  case object Rejected extends WithName("Rejected") with SchemeStatus
  case object Open extends WithName("Open") with SchemeStatus
  case object Deregistered extends WithName("Deregistered") with SchemeStatus
  case object WoundUp extends WithName("Wound-up") with SchemeStatus
  case object RejectedUnderAppeal extends WithName("Rejected Under Appeal") with SchemeStatus

  val values: Seq[SchemeStatus] = Seq(
    Pending,
    PendingInfoRequired,
    PendingInfoReceived,
    Rejected,
    Open,
    Deregistered,
    WoundUp,
    RejectedUnderAppeal
  )

  def statusByName(name: String): SchemeStatus =
    values.find(_.toString == name).getOrElse(throw new IllegalArgumentException("Unknown value:" + name))

  implicit val enumerable: Enumerable[SchemeStatus] =
    Enumerable(values.map(v => v.toString -> v)*)
}
