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

import play.api.libs.json._

sealed trait AdministratorOrPractitioner

object AdministratorOrPractitioner {

  case object Administrator extends WithName("administrator") with AdministratorOrPractitioner

  case object Practitioner extends WithName("practitioner") with AdministratorOrPractitioner

  val values: Seq[AdministratorOrPractitioner] = Seq(
    Administrator, Practitioner
  )

  private val mappings: Map[String, AdministratorOrPractitioner] = values.map(v => (v.toString, v)).toMap

  implicit val reads: Reads[AdministratorOrPractitioner] =
    JsPath.read[String].flatMap {
      case aop if mappings.keySet.contains(aop) => Reads(_ => JsSuccess(mappings.apply(aop)))
      case invalidValue => Reads(_ => JsError(s"Invalid administrator or practitioner type: $invalidValue"))
    }

  implicit val enumerable: Enumerable[AdministratorOrPractitioner] = Enumerable(values.map(v => v.toString -> v): _*)
}
