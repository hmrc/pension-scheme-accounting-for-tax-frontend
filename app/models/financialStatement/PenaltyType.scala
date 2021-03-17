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

package models.financialStatement

import models.financialStatement.PsaFSChargeType._
import models.{Enumerable, WithName}
import play.api.data.Form
import uk.gov.hmrc.viewmodels._

sealed trait PenaltyType

object PenaltyType extends Enumerable.Implicits {

  case object AccountingForTaxPenalties extends WithName("accountingForTax") with PenaltyType
  case object ContractSettlementCharges extends WithName("contractSettlement") with PenaltyType
  case object InformationNoticePenalties extends WithName("informationNotice") with PenaltyType
  case object PensionsPenalties extends WithName("pensionsPenalties") with PenaltyType

  def getPenaltyType(chargeType: PsaFSChargeType): PenaltyType =
    chargeType match {
      case PSS_PENALTY => PensionsPenalties
      case PSS_INFO_NOTICE => InformationNoticePenalties
      case CONTRACT_SETTLEMENT | CONTRACT_SETTLEMENT_INTEREST => ContractSettlementCharges
      case _ => AccountingForTaxPenalties
    }

  val values: Seq[PenaltyType] = Seq(AccountingForTaxPenalties, ContractSettlementCharges, InformationNoticePenalties, PensionsPenalties)

  def radios(form: Form[_], penaltyTypes: Seq[DisplayPenaltyType]): Seq[Radios.Item] =
    Radios(form("value"), penaltyTypes.map(value => Radios.Radio(msg"penaltyType.${value.penaltyType.toString}", value.penaltyType.toString)))

  implicit val enumerable: Enumerable[PenaltyType] = Enumerable(values.map(v => v.toString -> v): _*)
}
