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

import java.time.{LocalDate, Month, Year}

import play.api.data.Form
import play.api.i18n.Messages
import uk.gov.hmrc.viewmodels.Text.Literal
import uk.gov.hmrc.viewmodels._

sealed trait YearRange

object YearRange extends Enumerable.Implicits {

  private val earliestAllowableEndTaxYear = "2019"

  case object CurrentYearPlusOne extends WithName(yearPlus(1)) with YearRange
  case object CurrentYear extends WithName(Year.now.getValue.toString) with YearRange
  case object CurrentYearMinusOne extends WithName(yearMinus(1)) with YearRange
  case object CurrentYearMinusTwo extends WithName(yearMinus(2)) with YearRange
  case object CurrentYearMinusThree extends WithName(yearMinus(3)) with YearRange
  case object CurrentYearMinusFour extends WithName(yearMinus(4)) with YearRange
  case object CurrentYearMinusFive extends WithName(yearMinus(5)) with YearRange
  case object CurrentYearMinusSix extends WithName(yearMinus(6)) with YearRange
  case object CurrentYearMinusSeven extends WithName(yearMinus(7)) with YearRange
  case object CurrentYearMinusEight extends WithName(yearMinus(8)) with YearRange

  val values: Seq[YearRange] =
    Seq(
      CurrentYear,
      CurrentYearMinusOne,
      CurrentYearMinusTwo,
      CurrentYearMinusThree,
      CurrentYearMinusFour,
      CurrentYearMinusFive,
      CurrentYearMinusSix,
      CurrentYearMinusSeven,
      CurrentYearMinusEight
    ).filter(_.toString >= earliestAllowableEndTaxYear) ++ (if (LocalDate.now.getMonthValue >= 4) Seq(CurrentYearPlusOne) else Seq.empty)


  def getLabel(yearRange: YearRange)(implicit messages: Messages): Literal =
    yearRange match {
      case CurrentYear => Literal(msg"yearRangeRadio".withArgs(yearMinus(1), Year.now.getValue.toString).resolve)
      case CurrentYearMinusOne => Literal(msg"yearRangeRadio".withArgs(yearMinus(2), yearMinus(1)).resolve)
      case CurrentYearMinusTwo => Literal(msg"yearRangeRadio".withArgs(yearMinus(3), yearMinus(2)).resolve)
      case CurrentYearMinusThree => Literal(msg"yearRangeRadio".withArgs(yearMinus(4), yearMinus(3)).resolve)
      case CurrentYearMinusFour => Literal(msg"yearRangeRadio".withArgs(yearMinus(5), yearMinus(4)).resolve)
      case CurrentYearMinusFive => Literal(msg"yearRangeRadio".withArgs(yearMinus(6), yearMinus(5)).resolve)
      case CurrentYearMinusSix => Literal(msg"yearRangeRadio".withArgs(yearMinus(7), yearMinus(6)).resolve)
      case CurrentYearMinusSeven => Literal(msg"yearRangeRadio".withArgs(yearMinus(8), yearMinus(7)).resolve)
      case CurrentYearMinusEight => Literal(msg"yearRangeRadio".withArgs(yearMinus(9), yearMinus(8)).resolve)
    }

  def yearMinus(noOfYears: Int): String = (Year.now.getValue-noOfYears).toString
  def yearPlus(noOfYears: Int): String = (Year.now.getValue+noOfYears).toString

  def radios(form: Form[_])(implicit messages: Messages): Seq[Radios.Item] = {
    val field = form("value")
    val items =
      values.map { yy =>
        Radios.Radio(getLabel(yy), values.toString)
      }
    Radios(field, items)
  }

  implicit val enumerable: Enumerable[YearRange] =
    Enumerable(values.map(v => v.toString -> v): _*)
}
