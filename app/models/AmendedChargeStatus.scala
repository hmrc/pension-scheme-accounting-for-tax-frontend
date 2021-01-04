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

sealed trait AmendedChargeStatus

object AmendedChargeStatus extends Enumerable.Implicits {
  case object Added extends WithName("New") with AmendedChargeStatus
  case object Deleted extends WithName("Deleted") with AmendedChargeStatus
  case object Updated extends WithName("Changed") with AmendedChargeStatus
  case object Unknown extends WithName("Unknown") with AmendedChargeStatus

  def validStatus: Seq[AmendedChargeStatus] = Seq(
    Added,
    Deleted,
    Updated
  )

  def amendedChargeStatus(name: String): AmendedChargeStatus =
    AmendedChargeStatus.validStatus
      .find { status => status.toString.equals(name)
      }
      .getOrElse(Unknown)
}
