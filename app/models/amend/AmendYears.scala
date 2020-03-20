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

package models.amend

import models.{Enumerable, Year, Years}
import play.api.data.Form
import play.api.i18n.Messages
import play.api.libs.json.{JsString, JsValue, Writes}
import uk.gov.hmrc.viewmodels.Radios
import uk.gov.hmrc.viewmodels.Text.Literal

import scala.language.implicitConversions

sealed trait AmendYears {
  def getYear: Int = this.asInstanceOf[Year].year
}

object AmendYears extends Enumerable.Implicits {

  def values(years: Seq[Int]): Seq[AmendYear] = {
    years.map(x => AmendYear(x))
  }

  def radios(form: Form[_], years: Seq[Int])(implicit messages: Messages): Seq[Radios.Item] = {
    Radios(form("value"), years.map(year => Radios.Radio(Literal(year.toString), year.toString)))
  }

  implicit def enumerable(implicit years: Seq[Int]): Enumerable[AmendYears] =
    Enumerable(values(years).map(v => v.toString -> v): _*)

}

case class AmendYear(year: Int) extends AmendYears {
  override def toString: String = year.toString
}

object AmendYear {
  implicit val writes: Writes[AmendYear] = new Writes[AmendYear] {
    def writes(year: AmendYear): JsValue = JsString(year.toString)
  }
}