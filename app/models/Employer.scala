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

import scala.language.implicitConversions

case class Employer(index: Int, name: String, amount: BigDecimal, viewLink: String, removeLink: String) {
    def id = s"employer-$index"

    def removeLinkId = s"$id-remove"

    def viewLinkId = s"$id-view"

}

object Employer {
    implicit lazy val formats: Format[Employer] =
        Json.format[Employer]
}

