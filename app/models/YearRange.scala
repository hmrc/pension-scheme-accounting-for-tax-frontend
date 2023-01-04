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

import play.api.data.Form
import play.api.i18n.Messages
import play.api.libs.json._
import uk.gov.hmrc.viewmodels.Text.Literal
import uk.gov.hmrc.viewmodels._
import utils.DateHelper

import java.time.{LocalDate, Month}

case class YearRange(startYear: String) {
  override def toString: String = startYear
}

object YearRange extends Enumerable.Implicits {
  implicit val writes: Writes[YearRange] = (yr: YearRange) => JsString(yr.toString)

  private val startDayOfNewTaxYear: Int = 6
  private val minYear: Int = 2011

  def currentYear = new YearRange(DateHelper.today.getYear.toString)

  def values: Seq[YearRange] = {
    val currentYear = DateHelper.today.getYear
    val newTaxYearStart = LocalDate.of(currentYear, Month.APRIL.getValue, startDayOfNewTaxYear)

    val maxYear =
      if (DateHelper.today.isAfter(newTaxYearStart) || DateHelper.today.isEqual(newTaxYearStart)) {
        currentYear
      } else {
        currentYear - 1
      }

    (minYear to maxYear).reverseIterator.map(year => YearRange(year.toString)).toSeq
  }

  def getLabel(yr: YearRange)(implicit messages: Messages): Literal = {
    val startYear = yr.toString
    Literal(msg"yearRangeRadio".withArgs(startYear, (startYear.toInt + 1).toString).resolve)
  }

  def radios(form: Form[_])(implicit messages: Messages): Seq[Radios.Item] =
    Radios(form("value"), values.map(yearRange => Radios.Radio(getLabel(yearRange), yearRange.toString)))

  implicit def enumerable: Enumerable[YearRange] = Enumerable(values.map(yearRange => yearRange.toString -> yearRange): _*)
}
