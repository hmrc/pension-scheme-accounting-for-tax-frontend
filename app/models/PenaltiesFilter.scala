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

package models

import play.api.mvc.PathBindable

import scala.language.implicitConversions

sealed trait PenaltiesFilter

object PenaltiesFilter
  extends Enumerable.Implicits {

  case object All extends WithName("all") with PenaltiesFilter

  case object Outstanding extends WithName("due") with PenaltiesFilter

  val values: Seq[PenaltiesFilter] = Seq(
    All,
    Outstanding
  )

  implicit val enumerable: Enumerable[PenaltiesFilter] =
    Enumerable(values.map(v => v.toString -> v): _*)

  implicit def chargeDetailsFilterPathBindable(implicit stringBinder: PathBindable[String]): PathBindable[PenaltiesFilter] =
    new PathBindable[PenaltiesFilter] {

      override def bind(key: String, value: String): Either[String, PenaltiesFilter] = {
        stringBinder.bind(key, value) match {
          case Right(x)=> Right(stringToFilter(x))
          case _ => Left("Penalties filter binding failed")
        }
      }

      override def unbind(key: String, value: PenaltiesFilter): String = {
        stringBinder.unbind(key, value.toString)
      }
    }

  implicit def filterToString(filter: PenaltiesFilter): String =
    filter.toString

  implicit def stringToFilter(filter: String): PenaltiesFilter =
    filter match {
      case "due" => Outstanding
      case _ => All
    }
}
