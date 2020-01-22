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

package models.chargeD

import java.time.LocalDate

import play.api.libs.json.{Format, Json}

case class ChargeDDetails(
                           dateOfEvent: LocalDate,
                           taxAt25Percent: Option[BigDecimal],
                           taxAt55Percent: Option[BigDecimal]
                         ){

  def total: BigDecimal = taxAt25Percent.getOrElse(BigDecimal(0.00)) + taxAt55Percent.getOrElse(BigDecimal(0.00))
}

object ChargeDDetails {
  implicit lazy val formats: Format[ChargeDDetails] =
    Json.format[ChargeDDetails]
}
