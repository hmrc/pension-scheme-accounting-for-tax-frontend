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

package models.financialStatement

import models.{Enumerable, WithName}

sealed trait PsaFSChargeType

object PsaFSChargeType extends Enumerable.Implicits {

  case object AFT_INITIAL_LFP extends WithName("Accounting for Tax Late Filing Penalty") with PsaFSChargeType
  case object AFT_DAILY_LFP extends WithName("Accounting for Tax Further Late Filing Penalty") with PsaFSChargeType
  case object AFT_30_DAY_LPP extends WithName("Accounting for Tax 30 Days Late Payment Penalty") with PsaFSChargeType
  case object AFT_6_MONTH_LPP extends WithName("Accounting for Tax 6 Months Late Payment Penalty") with PsaFSChargeType
  case object AFT_12_MONTH_LPP extends WithName("Accounting for Tax 12 Months Late Payment Penalty") with PsaFSChargeType
  case object OTC_30_DAY_LPP extends WithName("Overseas Transfer Charge 30 Days Late Payment Penalty") with PsaFSChargeType
  case object OTC_6_MONTH_LPP extends WithName("Overseas Transfer Charge 6 Months Late Payment Penalty") with PsaFSChargeType
  case object OTC_12_MONTH_LPP extends WithName("Overseas Transfer Charge 12 Months Late Payment Penalty") with PsaFSChargeType
  case object PSS_PENALTY extends WithName("Pensions Penalty") with PsaFSChargeType
  case object PSS_INFO_NOTICE extends WithName("Information Notice Penalty") with PsaFSChargeType
  case object CONTRACT_SETTLEMENT extends WithName("Contract Settlement") with PsaFSChargeType
  case object CONTRACT_SETTLEMENT_INTEREST extends WithName("Contract Settlement Interest") with PsaFSChargeType
  case object INTEREST_ON_CONTRACT_SETTLEMENT extends WithName("Interest on contract settlement charge") with PsaFSChargeType
  case object PAYMENT_ON_ACCOUNT extends WithName("Payment on Account") with PsaFSChargeType
  case object REPAYMENT_INTEREST extends WithName("Repayment Interest") with PsaFSChargeType
  case object SSC_30_DAY_LPP extends WithName("Scheme Sanction Charge 30 Days Late Payment Penalty") with PsaFSChargeType
  case object SSC_6_MONTH_LPP extends WithName("Scheme Sanction Charge 6 Months Late Payment Penalty") with PsaFSChargeType
  case object SSC_12_MONTH_LPP extends WithName("Scheme Sanction Charge 12 Months Late Payment Penalty") with PsaFSChargeType
  case object LTA_DISCHARGE_ASSESSMENT_30_DAY_LPP extends WithName("Lifetime Allowance Discharge Assessment 30 Days Late Payment Penalty") with PsaFSChargeType
  case object LTA_DISCHARGE_ASSESSMENT_6_MONTH_LPP extends WithName("Lifetime Allowance Discharge Assessment 6 Months Late Payment Penalty") with PsaFSChargeType
  case object LTA_DISCHARGE_ASSESSMENT_12_MONTH_LPP extends WithName("Lifetime Allowance Discharge Assessment 12 Months Late Payment Penalty") with PsaFSChargeType

  val values: Seq[PsaFSChargeType] = Seq(
    AFT_INITIAL_LFP,
    AFT_DAILY_LFP,
    AFT_30_DAY_LPP,
    AFT_6_MONTH_LPP,
    AFT_12_MONTH_LPP,
    OTC_30_DAY_LPP,
    OTC_6_MONTH_LPP,
    OTC_12_MONTH_LPP,
    PSS_PENALTY,
    PSS_INFO_NOTICE,
    CONTRACT_SETTLEMENT,
    CONTRACT_SETTLEMENT_INTEREST,
    INTEREST_ON_CONTRACT_SETTLEMENT,
    PAYMENT_ON_ACCOUNT,
    REPAYMENT_INTEREST,
    SSC_30_DAY_LPP,
    SSC_6_MONTH_LPP,
    SSC_12_MONTH_LPP,
    LTA_DISCHARGE_ASSESSMENT_30_DAY_LPP,
    LTA_DISCHARGE_ASSESSMENT_6_MONTH_LPP,
    LTA_DISCHARGE_ASSESSMENT_12_MONTH_LPP
  )

  implicit val enumerable: Enumerable[PsaFSChargeType] =
  Enumerable(values.map(v => v.toString -> v): _*)
}
