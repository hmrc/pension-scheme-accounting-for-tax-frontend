/*
 * Copyright 2019 HM Revenue & Customs
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

import java.time.Year

import play.api.data.Form
import play.api.i18n.Messages
import uk.gov.hmrc.viewmodels._

sealed trait YearRange

object YearRange extends Enumerable.Implicits {

  case object CurrentYear extends WithName("CY-1 to CY") with YearRange
  case object CurrentYearMinusOne extends WithName("CY-2 to CY-1") with YearRange
  case object CurrentYearMinusTwo extends WithName("CY-3 to CY-2") with YearRange
  case object CurrentYearMinusThree extends WithName("CY-4 to CY-3") with YearRange
  case object CurrentYearMinusFour extends WithName("CY-5 to CY-4") with YearRange
  case object CurrentYearMinusFive extends WithName("CY-6 to CY-5") with YearRange
  case object CurrentYearMinusSix extends WithName("CY-7 to CY-6") with YearRange
  case object CurrentYearMinusSeven extends WithName("CY-8 to CY-7") with YearRange
  case object CurrentYearMinusEight extends WithName("CY-9 to CY-8") with YearRange

  val values: Seq[YearRange] = Seq(
    CurrentYear,
    CurrentYearMinusOne,
    CurrentYearMinusTwo,
    CurrentYearMinusThree,
    CurrentYearMinusFour,
    CurrentYearMinusFive,
    CurrentYearMinusSix,
    CurrentYearMinusSeven,
    CurrentYearMinusEight
  )

  def getLabel(yearRange: YearRange)(implicit messages: Messages) =
    yearRange match {
      case CurrentYear => msg"yearRangeRadio".withArgs(yearMinus(1), year.toString).resolve
      case CurrentYearMinusOne => msg"yearRangeRadio".withArgs(yearMinus(2), yearMinus(1)).resolve
      case CurrentYearMinusTwo => msg"yearRangeRadio".withArgs(yearMinus(3), yearMinus(2)).resolve
      case CurrentYearMinusThree => msg"yearRangeRadio".withArgs(yearMinus(4), yearMinus(3)).resolve
      case CurrentYearMinusFour => msg"yearRangeRadio".withArgs(yearMinus(5), yearMinus(4)).resolve
      case CurrentYearMinusFive => msg"yearRangeRadio".withArgs(yearMinus(6), yearMinus(5)).resolve
      case CurrentYearMinusSix => msg"yearRangeRadio".withArgs(yearMinus(7), yearMinus(6)).resolve
      case CurrentYearMinusSeven => msg"yearRangeRadio".withArgs(yearMinus(8), yearMinus(7)).resolve
      case CurrentYearMinusEight => msg"yearRangeRadio".withArgs(yearMinus(9), yearMinus(8)).resolve
    }


  val year = Year.now.getValue
  def yearMinus(noOfYears: Int): String = (year-noOfYears).toString

  def radios(form: Form[_])(implicit messages: Messages): Seq[Radios.Item] = {

    val field = form("value")
    val items = Seq(
      Radios.Radio(msg"yearRangeRadio".withArgs(yearMinus(1), year.toString), CurrentYear.toString),
      Radios.Radio(msg"yearRangeRadio".withArgs(yearMinus(2), yearMinus(1)), CurrentYearMinusOne.toString),
      Radios.Radio(msg"yearRangeRadio".withArgs(yearMinus(3), yearMinus(2)), CurrentYearMinusTwo.toString),
      Radios.Radio(msg"yearRangeRadio".withArgs(yearMinus(4), yearMinus(3)), CurrentYearMinusThree.toString),
      Radios.Radio(msg"yearRangeRadio".withArgs(yearMinus(5), yearMinus(4)), CurrentYearMinusFour.toString),
      Radios.Radio(msg"yearRangeRadio".withArgs(yearMinus(6), yearMinus(5)), CurrentYearMinusFive.toString),
      Radios.Radio(msg"yearRangeRadio".withArgs(yearMinus(7), yearMinus(6)), CurrentYearMinusSix.toString),
      Radios.Radio(msg"yearRangeRadio".withArgs(yearMinus(8), yearMinus(7)), CurrentYearMinusSeven.toString),
      Radios.Radio(msg"yearRangeRadio".withArgs(yearMinus(9), yearMinus(8)), CurrentYearMinusEight.toString)
    )

    Radios(field, items)
  }

  implicit val enumerable: Enumerable[YearRange] =
    Enumerable(values.map(v => v.toString -> v): _*)
}
