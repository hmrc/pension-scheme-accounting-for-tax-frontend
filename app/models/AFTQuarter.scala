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

import play.api.libs.json.{Format, Json}

import java.time.LocalDate
import java.time.format.DateTimeFormatter

case class AFTQuarter(startDate: LocalDate, endDate: LocalDate)

object AFTQuarter {

  implicit lazy val formats: Format[AFTQuarter] =
    Json.format[AFTQuarter]

  private val dateFormatterDMY: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM")

  def monthDayStringFormat(aftQuarter: AFTQuarter): String = {
    s"${aftQuarter.startDate.format(dateFormatterDMY)} to ${aftQuarter.endDate.format(dateFormatterDMY)} "
  }

  def formatForDisplay(aftQuarter: AFTQuarter): String = {
    monthDayStringFormat(aftQuarter) + s"${aftQuarter.startDate.getYear} to ${aftQuarter.startDate.getYear + 1}"
  }

  def formatForDisplayOneYear(aftQuarter: AFTQuarter): String = {
    monthDayStringFormat(aftQuarter) + aftQuarter.startDate.getYear
  }
}
