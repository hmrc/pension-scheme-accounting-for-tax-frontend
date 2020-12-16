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

sealed trait ChargeDetailsFilter

object ChargeDetailsFilter
  extends Enumerable.Implicits {

  case object All extends WithName("all") with ChargeDetailsFilter

  case object Upcoming extends WithName("upcoming") with ChargeDetailsFilter

  case object Overdue extends WithName("overdue") with ChargeDetailsFilter

  val values: Seq[ChargeDetailsFilter] = Seq(
    All,
    Upcoming,
    Overdue
  )

  implicit val enumerable: Enumerable[ChargeDetailsFilter] =
    Enumerable(values.map(v => v.toString -> v): _*)
}
