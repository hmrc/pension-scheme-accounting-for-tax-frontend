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

import play.api.data.Form
import uk.gov.hmrc.viewmodels.Radios
import uk.gov.hmrc.viewmodels.Radios.{Item, Radio}
import uk.gov.hmrc.viewmodels.Text.Literal

import scala.language.implicitConversions

case class PenaltySchemes(name: Option[String], pstr: String, srn: Option[String])

object PenaltySchemes extends Enumerable.Implicits {

  def values(schemes: Seq[PenaltySchemes]): Seq[String] = schemes.map(_.pstr)

  def radios(form: Form[_], schemes: Seq[PenaltySchemes]): Seq[Item] = {
    val radio: Seq[Radio] = schemes.map { scheme =>
      if (scheme.name.isDefined) {
        Radios.Radio(Literal(s"${scheme.name.getOrElse("")} (${scheme.pstr})"), scheme.pstr)
      } else {
        Radios.Radio(Literal(s"${scheme.pstr}"), scheme.pstr)
      }
    }

    Radios(form("value"), radio)
  }

  implicit def enumerable(schemes: Seq[PenaltySchemes]): Enumerable[PenaltySchemes] =
    Enumerable(schemes.map(v => v.pstr -> v): _*)

}
