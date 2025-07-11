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

package models.chargeA

import play.api.libs.json.{Format, Json}

case class ChargeDetails(
                          numberOfMembers: Int,
                          totalAmtOfTaxDueAtLowerRate: Option[BigDecimal],
                          totalAmtOfTaxDueAtHigherRate: Option[BigDecimal],
                          totalAmount: BigDecimal
                        ) {
  def calcTotalAmount: BigDecimal =
    totalAmtOfTaxDueAtLowerRate.getOrElse(BigDecimal(0.00)) + totalAmtOfTaxDueAtHigherRate.getOrElse(BigDecimal(0.00))
}

object ChargeDetails {
  implicit lazy val formats: Format[ChargeDetails] =
    Json.format[ChargeDetails]

  def unapply(details: ChargeDetails): Option[(Int, Option[BigDecimal], Option[BigDecimal], BigDecimal)] =
    Some((
      details.numberOfMembers,
      details.totalAmtOfTaxDueAtLowerRate,
      details.totalAmtOfTaxDueAtHigherRate,
      details.totalAmount
    ))
}
