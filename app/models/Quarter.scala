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
import java.time.format.DateTimeFormatter

import models.Quarters.{Q1, Q2, Q3, Q4}
import play.api.libs.json.{Format, Json}

case class Quarter(startDate: String, endDate: String) {
  def date(s: String): LocalDate = LocalDate.from(DateTimeFormatter.ofPattern("yyyy-MM-dd").parse(s))

  def getQuarters: Quarters = date(startDate).getMonthValue match {
    case 1 => Q1
    case 4 => Q2
    case 7 => Q3
    case 10 => Q4
  }
}

object Quarter {

  implicit lazy val formats: Format[Quarter] =
    Json.format[Quarter]
}

