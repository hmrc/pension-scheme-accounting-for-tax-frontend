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

import play.api.mvc.PathBindable

import scala.language.implicitConversions

sealed trait ChargeDetailsFilter

object ChargeDetailsFilter
  extends Enumerable.Implicits {

  case object All extends WithName("all") with ChargeDetailsFilter

  case object Upcoming extends WithName("upcoming") with ChargeDetailsFilter

  case object Overdue extends WithName("overdue") with ChargeDetailsFilter

  case object History extends WithName("history") with ChargeDetailsFilter

  val values: Seq[ChargeDetailsFilter] = Seq(
    All,
    Upcoming,
    Overdue,
    History
  )

  implicit val enumerable: Enumerable[ChargeDetailsFilter] =
    Enumerable(values.map(v => v.toString -> v): _*)

  implicit def chargeDetailsFilterPathBindable(implicit stringBinder: PathBindable[String]): PathBindable[ChargeDetailsFilter] =
    new PathBindable[ChargeDetailsFilter] {

    override def bind(key: String, value: String): Either[String, ChargeDetailsFilter] = {
      stringBinder.bind(key, value) match {
        case Right(x)=> Right(stringToFilter(x))
        case _ => Left("Charge details filter binding failed")
      }
    }

    override def unbind(key: String, value: ChargeDetailsFilter): String = {
      stringBinder.unbind(key, value.toString)
    }
  }

  implicit def filterToString(filter: ChargeDetailsFilter): String =
    filter.toString

  implicit def stringToFilter(filter: String): ChargeDetailsFilter =
    filter match {
      case "upcoming" => Upcoming
      case "overdue" => Overdue
      case "history" => History
      case _ => All
    }
}
