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

package helpers
import models.UserAnswers

class AmendmentHelper {

  def getTotalAmount(ua: UserAnswers): (BigDecimal, BigDecimal) = {
    val amountUK = Seq(
      ua.get(pages.chargeE.TotalChargeAmountPage).getOrElse(BigDecimal(0)),
      ua.get(pages.chargeC.TotalChargeAmountPage).getOrElse(BigDecimal(0)),
      ua.get(pages.chargeF.ChargeDetailsPage).map(_.amountTaxDue).getOrElse(BigDecimal(0)),
      ua.get(pages.chargeD.TotalChargeAmountPage).getOrElse(BigDecimal(0)),
      ua.get(pages.chargeA.ChargeDetailsPage).map(_.totalAmount).getOrElse(BigDecimal(0)),
      ua.get(pages.chargeB.ChargeBDetailsPage).map(_.amountTaxDue).getOrElse(BigDecimal(0))).sum

    val amountNonUK = ua.get(pages.chargeG.TotalChargeAmountPage).getOrElse(BigDecimal(0))

    (amountUK, amountNonUK)
  }

}
