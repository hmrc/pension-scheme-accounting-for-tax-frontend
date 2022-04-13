/*
 * Copyright 2022 HM Revenue & Customs
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

sealed trait SchemeFSChargeType

object SchemeFSChargeType extends Enumerable.Implicits {

  case object PSS_AFT_RETURN extends WithName("Accounting for Tax return") with SchemeFSChargeType
  case object PSS_AFT_RETURN_INTEREST extends WithName("Interest on Accounting for Tax return") with SchemeFSChargeType
  case object PSS_OTC_AFT_RETURN extends WithName("Overseas transfer charge") with SchemeFSChargeType
  case object PSS_OTC_AFT_RETURN_INTEREST extends WithName("Interest on overseas transfer charge") with SchemeFSChargeType
  case object AFT_MANUAL_ASST extends WithName( "Accounting for Tax return manual assessment") with SchemeFSChargeType
  case object AFT_MANUAL_ASST_INTEREST extends WithName("Interest on Accounting for Tax return manual assessment") with SchemeFSChargeType
  case object OTC_MANUAL_ASST extends WithName("Overseas transfer charge manual assessment") with SchemeFSChargeType
  case object OTC_MANUAL_ASST_INTEREST extends WithName("Interest on overseas transfer charge manual assessment") with SchemeFSChargeType
  case object PSS_CHARGE extends WithName("Pensions charge") with SchemeFSChargeType
  case object PSS_CHARGE_INTEREST extends WithName("Interest on pensions charge") with SchemeFSChargeType
  case object CONTRACT_SETTLEMENT extends WithName("Contract settlement charge") with SchemeFSChargeType
  case object CONTRACT_SETTLEMENT_INTEREST extends WithName("Contract settlement interest charge") with SchemeFSChargeType
  case object REPAYMENT_INTEREST extends WithName("Repayment interest") with SchemeFSChargeType
  case object EXCESS_RELIEF_PAID extends WithName("Excess relief paid charge") with SchemeFSChargeType
  case object EXCESS_RELIEF_INTEREST extends WithName("Interest on excess relief charge") with SchemeFSChargeType
  case object PAYMENT_ON_ACCOUNT extends WithName("Payment on account") with SchemeFSChargeType

  val values: Seq[SchemeFSChargeType] = Seq(
    PSS_AFT_RETURN,
    PSS_AFT_RETURN_INTEREST,
    PSS_OTC_AFT_RETURN,
    PSS_OTC_AFT_RETURN_INTEREST,
    AFT_MANUAL_ASST,
    AFT_MANUAL_ASST_INTEREST,
    OTC_MANUAL_ASST,
    OTC_MANUAL_ASST_INTEREST,
    PSS_CHARGE,
    PSS_CHARGE_INTEREST,
    CONTRACT_SETTLEMENT,
    CONTRACT_SETTLEMENT_INTEREST,
    REPAYMENT_INTEREST,
    EXCESS_RELIEF_PAID,
    EXCESS_RELIEF_INTEREST,
    PAYMENT_ON_ACCOUNT
  )

  def isAFTOrOTCNonInterestChargeType(schemeFSChargeType:SchemeFSChargeType):Boolean =
    schemeFSChargeType == PSS_AFT_RETURN || schemeFSChargeType == PSS_OTC_AFT_RETURN

  def isAFTOrOTCInclInterestChargeType(schemeFSChargeType:SchemeFSChargeType):Boolean =
    isAFTOrOTCNonInterestChargeType(schemeFSChargeType) ||
      schemeFSChargeType == PSS_AFT_RETURN_INTEREST || schemeFSChargeType == PSS_OTC_AFT_RETURN_INTEREST

  def isDisplayInterestChargeType(schemeFSChargeType:SchemeFSChargeType):Boolean =
    schemeFSChargeType == PSS_AFT_RETURN || schemeFSChargeType == PSS_OTC_AFT_RETURN ||
      schemeFSChargeType == AFT_MANUAL_ASST || schemeFSChargeType == OTC_MANUAL_ASST ||
      schemeFSChargeType == PSS_CHARGE || schemeFSChargeType == CONTRACT_SETTLEMENT

  def getInterestChargeTypeText(schemeFSChargeType:SchemeFSChargeType):String ={
  val interestChargeType =
    schemeFSChargeType match {
      case PSS_AFT_RETURN => PSS_AFT_RETURN_INTEREST
      case AFT_MANUAL_ASST => AFT_MANUAL_ASST_INTEREST
      case OTC_MANUAL_ASST => OTC_MANUAL_ASST_INTEREST
      case PSS_CHARGE => PSS_CHARGE_INTEREST
      case CONTRACT_SETTLEMENT => CONTRACT_SETTLEMENT_INTEREST
      case PSS_OTC_AFT_RETURN => PSS_OTC_AFT_RETURN_INTEREST
      case _ => ""
    }
  interestChargeType.toString
}

  implicit val enumerable: Enumerable[SchemeFSChargeType] =
    Enumerable(values.map(v => v.toString -> v): _*)
}
