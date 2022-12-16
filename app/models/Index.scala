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

import play.api.mvc.{JavascriptLiteral, PathBindable}

import scala.language.implicitConversions

case class Index(id: Int)

object Index {

  implicit val jsLiteral: JavascriptLiteral[Index] = new JavascriptLiteral[Index] {
    override def to(value: Index): String = value.id.toString
  }

  implicit def indexPathBindable(implicit intBinder: PathBindable[Int]): PathBindable[Index] = new PathBindable[Index] {

    override def bind(key: String, value: String): Either[String, Index] = {
      intBinder.bind(key, value) match {
        case Right(x) if x > 0 => Right(Index(x - 1))
        case _ => Left("Index binding failed")
      }
    }

    override def unbind(key: String, value: Index): String = {

      val tt = intBinder.unbind(key, value.id + 1)
      println( "\n>>>" +key + "/" + value + "==="+ tt)
      tt
    }
  }

  implicit def optionIndexPathBindable(implicit intBinder: PathBindable[Int]): PathBindable[Option[Index]] = new PathBindable[Option[Index]] {

    override def bind(key: String, value: String): Either[String, Option[Index]] = {
      intBinder.bind(key, value) match {
        case Right(x) if x > 0 => Right(Some(Index(x - 1)))
        case _ => Left("Index binding failed")
      }
    }

    override def unbind(key: String, value: Option[Index]): String = {

      val tt = intBinder.unbind(key, value.map(_.id + 1).getOrElse(-1))
      println("\n>>dsdsdsds>" + key + "/" + value + "===" + tt)
      tt
    }
  }

  implicit def indexToInt(index: Index): Int =
    index.id

  implicit def indexToOptionInt(index: Option[Index]): Option[Int] =
    index.map(_.id)

  implicit def intToIndex(index: Int): Index =
    Index(index)

  implicit def optionIntToOptionIndex(index: Option[Int]): Option[Index] =
    index.map(Index(_))

}
