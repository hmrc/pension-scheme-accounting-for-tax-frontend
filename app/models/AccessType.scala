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

sealed trait AccessType

case object Draft extends WithName("draft") with AccessType
case object Submission extends WithName("submission") with AccessType

object AccessType {

  case class UnknownAccessTypeException() extends Exception

  implicit val jsLiteral: JavascriptLiteral[AccessType] = new JavascriptLiteral[AccessType] {
    override def to(value: AccessType): String = value match {
      case Draft => "draft"
      case Submission => "submission"
    }
  }

  implicit def accessTypePathBindable(implicit stringBinder: PathBindable[String]): PathBindable[AccessType] = new PathBindable[AccessType] {

    val accessTypes = Seq(Draft, Submission)

    override def bind(key: String, value: String): Either[String, AccessType] = {
      stringBinder.bind(key, value) match {
        case Right(Draft.toString) => Right(Draft)
        case Right(Submission.toString) => Right(Submission)
        case _ => Left("AccessType binding failed")
      }
    }

    override def unbind(key: String, value: AccessType): String = {
      val accessTypeValue = accessTypes.find(_ == value).map(_.toString).getOrElse(throw UnknownAccessTypeException())
      stringBinder.unbind(key, accessTypeValue)
    }
  }
}
