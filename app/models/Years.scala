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

package models


import java.time.LocalDate

import play.api.data.Form
import play.api.i18n.Messages
import play.api.libs.json.{JsString, JsValue, Writes}
import uk.gov.hmrc.viewmodels.Radios
import uk.gov.hmrc.viewmodels.Text.Literal

sealed trait Years {
  def getYear: Int = this.asInstanceOf[Year].year
}

object Years extends Enumerable.Implicits {

  def currentYear: Int = LocalDate.now().getYear

  def minYear: Int = {
    val earliestYear = currentYear - 6
    if(earliestYear > 2020)
      earliestYear
    else
      2020
  }

  def values: Seq[Year] = (minYear to currentYear).reverse.map(Year(_))

  def radios(form: Form[_])(implicit messages: Messages): Seq[Radios.Item] = {

    Radios(form("value"), values.map(year => Radios.Radio(Literal(year.toString), year.toString)))
  }

  implicit def enumerable: Enumerable[Years] =
    Enumerable(values.map(v => v.toString -> v): _*)
}

case class Year(year: Int) extends Years {
  override def toString: String = year.toString
}

object Year {
  implicit val writes: Writes[Year] = new Writes[Year] {
    def writes(year: Year): JsValue = JsString(year.toString)
  }
}
