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

package models.financialStatement

import models.{Enumerable, WithName}

sealed trait FSChargeType


object FSChargeType extends Enumerable.Implicits {

  case object AFT_INITIAL_LLP extends WithName("AFT Initial LFP") with FSChargeType
  case object AFT_DAILY_LLP extends WithName("AFT Daily LFP") with FSChargeType
  case object AFT_30_DAY_LLP extends WithName("AFT 30 Day LPP") with FSChargeType
  case object AFT_6_MONTH_LLP extends WithName("AFT 6 Month LPP") with FSChargeType
  case object AFT_12_MONTH_LLP extends WithName("AFT 12 Month LPP") with FSChargeType
  case object OTC_30_DAY_LLP extends WithName("OTC 30 Day LPP") with FSChargeType
  case object OTC_6_MONTH_LLP extends WithName("OTC 6 Month LPP") with FSChargeType
  case object OTC_12_MONTH_LLP extends WithName("OTC 12 Month LPP") with FSChargeType
  case object PAYMENT_ON_ACCOUNT extends WithName("Payment on Account") with FSChargeType
  case object PSS_AFT_RETURN extends WithName("PSS AFT Return") with FSChargeType
  case object PSS_AFT_RETURN_INTEREST extends WithName("PSS AFT Return Interest") with FSChargeType
  case object PSS_OTC_AFT_RETURN extends WithName("PSS OTC AFT Return") with FSChargeType
  case object PSS_OTC_AFT_RETURN_INTEREST extends WithName("PSS OTC AFT Return Interest") with FSChargeType

  val values: Seq[FSChargeType] = Seq(
    AFT_INITIAL_LLP,
    AFT_DAILY_LLP,
    AFT_30_DAY_LLP,
    AFT_6_MONTH_LLP,
    AFT_12_MONTH_LLP,
    OTC_30_DAY_LLP,
    OTC_6_MONTH_LLP,
    OTC_12_MONTH_LLP,
    PAYMENT_ON_ACCOUNT,
    PSS_AFT_RETURN,
    PSS_AFT_RETURN_INTEREST,
    PSS_OTC_AFT_RETURN,
    PSS_OTC_AFT_RETURN_INTEREST
  )

  implicit val enumerable: Enumerable[FSChargeType] =
  Enumerable(values.map(v => v.toString -> v): _*)
}