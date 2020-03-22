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

import config.FrontendAppConfig
import play.api.data.Form
import play.api.i18n.Messages
import uk.gov.hmrc.viewmodels.Radios
import uk.gov.hmrc.viewmodels.Text.Literal
import utils.DateHelper._

import scala.language.implicitConversions

sealed trait Quarters {
  def startDay: Int = 1
  def endDay: Int = 31
  def startMonth: Int
  def endMonth: Int
}

trait CommonQuarters {
  def currentYear: Int = today.getYear

  case object Q1 extends WithName("q1") with Quarters {
    override def startMonth = 1
    override def endMonth = 3
  }

  case object Q2 extends WithName("q2") with Quarters {
    override def endDay = 30
    override def startMonth = 4
    override def endMonth = 6
  }

  case object Q3 extends WithName("q3") with Quarters {
    override def endDay = 30
    override def startMonth = 7
    override def endMonth = 9
  }

  case object Q4 extends WithName("q4") with Quarters {
    override def startMonth = 10
    override def endMonth = 12
  }

  def getCurrentYearQuarters(implicit config: FrontendAppConfig): Seq[Quarters] ={
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

  def getQuarter(quarter: Quarters, year: Int): Quarter = {
    Quarter(LocalDate.of(year, quarter.startMonth, quarter.startDay),
      LocalDate.of(year, quarter.endMonth, quarter.endDay))
  }

  def getStartDate(quarter: Quarters, year: Int): LocalDate =
    LocalDate.of(year, quarter.startMonth, quarter.startDay)

  def getQuarter(startDate: LocalDate): Quarter =
    getQuarter(getQuartersFromDate(startDate), startDate.getYear)

  def getQuartersFromDate(date: LocalDate): Quarters =
    date.getMonthValue match {
      case i if i <= 3 => Q1
      case i if i <= 6 => Q2
      case i if i <= 9 => Q3
      case _ => Q4
    }



}

sealed trait StartQuarters extends Quarters

object StartQuarters extends CommonQuarters with Enumerable.Implicits {

  def values(selectedYear: Int)(implicit config: FrontendAppConfig): Seq[Quarters] =
    selectedYear match {
      case _ if selectedYear == currentYear => getCurrentYearQuarters
      case _ if selectedYear == config.minimumYear => Seq(Q2, Q3, Q4)
      case _ => Seq(Q1, Q2, Q3, Q4)
    }

  def radios(form: Form[_], year: Int)(implicit messages: Messages, config: FrontendAppConfig): Seq[Radios.Item] = {
    Radios(form("value"), values(year).map { quarter =>
      Radios.Radio(Literal(messages(s"quarters.${quarter.toString}.label")), quarter.toString)
    })
  }

  implicit def enumerable(year: Int)(implicit config: FrontendAppConfig): Enumerable[Quarters] =
    Enumerable(values(year).map(v => v.toString -> v): _*)
}

sealed trait AmendQuarters extends Quarters

object AmendQuarters extends CommonQuarters with Enumerable.Implicits {

  def values(quarters: Seq[Quarters]): Seq[Quarters] = quarters

  def radios(form: Form[_], quarters: Seq[Quarters])(implicit messages: Messages): Seq[Radios.Item] = {
    Radios(form("value"), values(quarters).map { quarter =>
      Radios.Radio(Literal(messages(s"quarters.${quarter.toString}.label")), quarter.toString)
    })
  }

  implicit def enumerable(quarters: Seq[Quarters]): Enumerable[Quarters] =
    Enumerable(values(quarters).map(v => v.toString -> v): _*)
}