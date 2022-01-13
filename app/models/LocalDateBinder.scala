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

import play.api.mvc._
import utils.DateHelper.dateFormatterYMD

import java.time.LocalDate
import scala.language.implicitConversions

object LocalDateBinder {

  implicit def datePathBindable(implicit stringBinder: PathBindable[String]):
  PathBindable[LocalDate] = new PathBindable[LocalDate] {

    override def bind(key: String, value: String): Either[String, LocalDate] = {
      stringBinder.bind(key, value) match {
        case Right(right) => Right(LocalDate.from(dateFormatterYMD.parse(right)))
        case _ => Left("LocalDate binding failed")
      }
    }

    override def unbind(key: String, value: LocalDate): String = {
      stringBinder.unbind(key, value.format(dateFormatterYMD))
    }
  }

  implicit def localDateToString(date: LocalDate): String = date.format(dateFormatterYMD)

  implicit def stringToLocalDate(date: String): LocalDate =
    LocalDate.from(dateFormatterYMD.parse(date))
}


