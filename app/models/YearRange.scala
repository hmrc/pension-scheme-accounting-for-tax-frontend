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

import play.api.data.Form
import play.api.i18n.Messages
import play.api.libs.json._
import uk.gov.hmrc.govukfrontend.views.Aliases.{RadioItem, Text}
import viewmodels.Radios
import utils.DateHelper

import java.time.{LocalDate, Month}

case class YearRange(startYear: String) {
  override def toString: String = startYear
}

trait YearRangeCommon extends Enumerable.Implicits {
  implicit val writes: Writes[YearRange] = (yr: YearRange) => JsString(yr.toString)

  private val startDayOfNewTaxYear: Int = 6
  val minYear: Int

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

  def getLabel(yr: YearRange)(implicit messages: Messages): Text = {
    val startYear = yr.toString
    val yearRangeMsg = Messages("yearRangeRadio", startYear, (startYear.toInt + 1).toString)
    Text(yearRangeMsg)
  }

  def radios(form: Form[_])(implicit messages: Messages): Seq[RadioItem] =
    Radios(form("value"), values.map(yearRange => Radios.Radio(getLabel(yearRange), yearRange.toString)))

  implicit def enumerable: Enumerable[YearRange] = Enumerable(values.map(yearRange => yearRange.toString -> yearRange): _*)
}

object YearRange extends YearRangeCommon with Enumerable.Implicits {
  override val minYear: Int = 2011
}

object YearRangeMcCloud extends YearRangeCommon with Enumerable.Implicits {
  override val minYear: Int = 2015
}
