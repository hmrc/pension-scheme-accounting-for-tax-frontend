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

import config.FrontendAppConfig
import play.api.data.Form
import play.api.i18n.Messages
import uk.gov.hmrc.govukfrontend.views.Aliases.{Hint, RadioItem, Text}
import utils.DateHelper._
import viewmodels.Radios.Radio
import viewmodels.{LabelClasses, Radios}

import java.time.{LocalDate, Month}
import scala.language.implicitConversions

sealed trait QuarterType {
  def startMonth: Int
  def endMonth: Int
  def startDay: Int = 1
  def endDay: Int
}

trait CommonQuarters {
  def currentYear: Int = today.getYear

  case object Q1 extends WithName("q1") with QuarterType {
    override def startMonth: Int = Month.JANUARY.getValue
    override def endMonth: Int = Month.MARCH.getValue
    override def endDay: Int = Month.MARCH.maxLength()
  }

  case object Q2 extends WithName("q2") with QuarterType {
    override def startMonth: Int = Month.APRIL.getValue
    override def endMonth: Int = Month.JUNE.getValue
    override def endDay: Int = Month.JUNE.maxLength()
  }

  case object Q3 extends WithName("q3") with QuarterType {
    override def startMonth: Int = Month.JULY.getValue
    override def endMonth: Int = Month.SEPTEMBER.getValue
    override def endDay: Int = Month.SEPTEMBER.maxLength()
  }

  case object Q4 extends WithName("q4") with QuarterType {
    override def startMonth: Int = Month.OCTOBER.getValue
    override def endMonth: Int = Month.DECEMBER.getValue
    override def endDay: Int = Month.DECEMBER.maxLength()
  }

  def getCurrentYearQuarters(implicit config: FrontendAppConfig): Seq[QuarterType] = {
    val quartersCY = today.getMonthValue match {
      case i if i > 9 => Seq(Q1, Q2, Q3, Q4)
      case i if i > 6 => Seq(Q1, Q2, Q3)
      case i if i  > 3 => Seq(Q1, Q2)
      case _ => Seq(Q1)
    }

    if(currentYear == config.minimumYear) {
      quartersCY.filter(_ != Q1)
    }
    else {
      quartersCY
    }
  }

  def getAllQuartersForYear(year: Int): Seq[DisplayQuarter] = {
    Seq(
      getQuarter(Q1, year),
      getQuarter(Q2, year),
      getQuarter(Q3, year),
      getQuarter(Q4, year)
    ).map { aftQuarter =>
      DisplayQuarter(
        quarter = aftQuarter,
        displayYear = false,
        lockedBy = None,
        hintText = None
      )
    }
  }

  def getQuarter(quarter: QuarterType, year: Int): AFTQuarter = {
    AFTQuarter(LocalDate.of(year, quarter.startMonth, quarter.startDay),
      LocalDate.of(year, quarter.endMonth, quarter.endDay))
  }

  def getStartDate(quarter: QuarterType, year: Int): LocalDate =
    LocalDate.of(year, quarter.startMonth, quarter.startDay)

  def getQuarter(startDate: LocalDate): AFTQuarter =
    getQuarter(getQuartersFromDate(startDate), startDate.getYear)

  def getQuartersFromDate(date: LocalDate): QuarterType =
    date.getMonthValue match {
      case i if i <= 3 => Q1
      case i if i <= 6 => Q2
      case i if i <= 9 => Q3
      case _ => Q4
    }

  def availableQuarters(selectedYear: Int)(implicit config: FrontendAppConfig): Seq[QuarterType] =
    selectedYear match {
      case _ if selectedYear == currentYear => getCurrentYearQuarters
      case _ if selectedYear == config.minimumYear => Seq(Q2, Q3, Q4)
      case _ => Seq(Q1, Q2, Q3, Q4)
    }
}

object Quarters extends CommonQuarters with Enumerable.Implicits {

  def values(displayQuarters: Seq[DisplayQuarter]): Seq[AFTQuarter] = displayQuarters.map(_.quarter)

  def radios(form: Form[_], displayQuarters: Seq[DisplayQuarter], hintClass: Seq[String] = Nil, areLabelsBold: Boolean = true)
            (implicit messages: Messages): Seq[RadioItem] = {
    val x: Seq[Radio] = displayQuarters.map { displayQuarter =>
      Radios.Radio(label = getLabel(displayQuarter),
        value = displayQuarter.quarter.toString,
        hint = getHint(displayQuarter, hintClass),
        labelClasses = Some(LabelClasses(classes = if(areLabelsBold) Seq("govuk-!-font-weight-bold") else Nil)))
    }

    Radios(form("value"), x)
  }

  implicit def enumerable(quarters: Seq[AFTQuarter]): Enumerable[AFTQuarter] =
    Enumerable(quarters.map(v => v.toString -> v): _*)

  def getLabel(displayQuarter: DisplayQuarter)(implicit messages: Messages): Text = {
    val q =  getQuartersFromDate(displayQuarter.quarter.startDate)
    val year: String = if(displayQuarter.displayYear) displayQuarter.quarter.startDate.getYear.toString else ""
    val lockedString = displayQuarter.lockedBy match {
      case Some(lockingPsa) => messages("quarters.lockDetail", lockingPsa)
      case _ => ""
    }

    Text(s"${messages(s"quarters.${q.toString}.label")} $year $lockedString")
  }

  private def getHint(displayQuarter: DisplayQuarter, hintClass: Seq[String])(implicit messages: Messages): Option[Hint] =
    displayQuarter.hintText match {
      case Some(hint) => Some(Hint(content = Text(Messages(s"${hint.toString}")), id = Some("hint-id"), classes = hintClass.mkString(" ")))
      case _ => None
  }
}
