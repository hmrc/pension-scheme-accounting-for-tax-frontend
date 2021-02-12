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


import config.FrontendAppConfig
import play.api.data.Form
import play.api.libs.json.{JsString, JsValue, Writes}

import uk.gov.hmrc.viewmodels.Text.Literal
import utils.DateHelper
import viewmodels.Radios.Radio
import viewmodels.{Radios, Hint, LabelClasses}
import uk.gov.hmrc.viewmodels._

case class Year(year: Int) {
  def getYear: Int = this.asInstanceOf[Year].year
  override def toString: String = year.toString
}

object Year {
  implicit val writes: Writes[Year] = new Writes[Year] {
    def writes(year: Year): JsValue = JsString(year.toString)
  }
}

trait CommonYears extends Enumerable.Implicits {

  def currentYear: Int = {
    DateHelper.today.getYear
  }

  def minYear(implicit config: FrontendAppConfig): Int = {
    val earliestYear = currentYear - 6
    if(earliestYear > config.minimumYear) {
      earliestYear
    }
    else {
      config.minimumYear
    }
  }

  def startYears(implicit config: FrontendAppConfig): Seq[Int] = (minYear to currentYear).reverse
}

object StartYears extends CommonYears with Enumerable.Implicits {

  def values(implicit config: FrontendAppConfig): Seq[Year] = (minYear to currentYear).reverse.map(Year(_))

  def radios(form: Form[_])(implicit config: FrontendAppConfig): Seq[Radios.Item] = {
    Radios(form("value"), values.map(year => Radios.Radio(Literal(year.toString), year.toString)))
  }

  implicit def enumerable(implicit config: FrontendAppConfig): Enumerable[Year] =
    Enumerable(values.map(v => v.toString -> v): _*)
}

object AmendYears extends CommonYears with Enumerable.Implicits {

  def values(years: Seq[Int]): Seq[Year] = years.map(x => Year(x))

  def radios(form: Form[_], years: Seq[Int]): Seq[Radios.Item] = {
    Radios(form("value"), years.map(year => Radios.Radio(Literal(year.toString), year.toString)))
  }

  implicit def enumerable(implicit years: Seq[Int]): Enumerable[Year] =
    Enumerable(values(years).map(v => v.toString -> v): _*)

}

object FSYears extends CommonYears with Enumerable.Implicits {

  def values(years: Seq[DisplayYear]): Seq[Year] = years.map(x => Year(x.year))

  def radios(form: Form[_], displayYears: Seq[DisplayYear]): Seq[Radios.Item] = {
    val x: Seq[Radio] = displayYears.map { displayYear =>

      Radios.Radio(label = Literal(displayYear.year.toString),
        value = displayYear.year.toString,
        hint = getHint(displayYear),
        labelClasses = Some(LabelClasses(classes = Seq("govuk-!-font-weight-bold"))))
    }
    Radios(form("value"), x)
  }

  implicit def enumerable(implicit years: Seq[Int]): Enumerable[Year] =
    Enumerable(years.map(v => v.toString -> Year(v)): _*)

  private def getHint(displayYear: DisplayYear): Option[Hint] =
    displayYear.hintText match {
      case Some(hint) => Some(Hint(msg"${hint.toString}", "hint-id", Seq("govuk-tag govuk-tag--red")))
      case _ => None
    }

}