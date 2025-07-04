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

sealed trait AccessMode

object AccessMode extends Enumerable.Implicits {

  case object PageAccessModeViewOnly extends WithName("viewOnly") with AccessMode
  case object PageAccessModePreCompile extends WithName("preCompile") with AccessMode
  case object PageAccessModeCompile extends WithName("compile") with AccessMode

  val values: Seq[AccessMode] = Seq(
    PageAccessModeViewOnly,
    PageAccessModePreCompile,
    PageAccessModeCompile
  )

  implicit val enumerable: Enumerable[AccessMode] =
    Enumerable(values.map(v => v.toString -> v)*)
}
