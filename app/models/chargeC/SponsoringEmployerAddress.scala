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

package models.chargeC

import play.api.libs.json.{Format, Json}

case class SponsoringEmployerAddress(line1: String,
                                     line2: Option[String],
                                     townOrCity: String,
                                     county: Option[String],
                                     country: String,
                                     postcode: Option[String])

object SponsoringEmployerAddress {
  implicit lazy val formats: Format[SponsoringEmployerAddress] =
    Json.format[SponsoringEmployerAddress]

  def unapply(address: SponsoringEmployerAddress): Option[(String, Option[String], String, Option[String], String, Option[String])] =
    Some((address.line1, address.line2, address.townOrCity, address.county, address.country, address.postcode))
}
