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

import models.Enumerable
import models.financialStatement.SchemeFSChargeType._
import play.api.data.Form
import play.api.mvc.PathBindable
import uk.gov.hmrc.viewmodels._
import viewmodels.{Hint, LabelClasses, Radios}
import viewmodels.Radios.Radio

import scala.language.implicitConversions

sealed trait PaymentOrChargeType

object PaymentOrChargeType extends Enumerable.Implicits {

  case object AccountingForTaxCharges extends PaymentOrChargeType
  case object ContractSettlementCharges extends PaymentOrChargeType
  case object ExcessReliefPaidCharges extends PaymentOrChargeType
  case object InterestOnExcessRelief extends PaymentOrChargeType
  case object PensionsCharges extends PaymentOrChargeType

  def getPaymentOrChargeType(chargeType: SchemeFSChargeType): PaymentOrChargeType =
    chargeType match {
      case EXCESS_RELIEF_PAID => ExcessReliefPaidCharges
      case EXCESS_RELIEF_INTEREST => InterestOnExcessRelief
      case CONTRACT_SETTLEMENT | CONTRACT_SETTLEMENT_INTEREST => ContractSettlementCharges
      case PSS_CHARGE | PSS_CHARGE_INTEREST => PensionsCharges
      case _ => AccountingForTaxCharges
    }

  val values: Seq[PaymentOrChargeType] =
    Seq(AccountingForTaxCharges, ContractSettlementCharges, ExcessReliefPaidCharges, InterestOnExcessRelief, PensionsCharges)

  def radios(form: Form[_], chargeTypes: Seq[DisplayPaymentOrChargeType], hintClass: Seq[String] = Nil, areLabelsBold: Boolean = true)
            : Seq[Radios.Item] =
  {
    val x: Seq[Radio] = chargeTypes.map { chargeType =>

      Radios.Radio(label = msg"paymentOrChargeType.${chargeType.chargeType.toString}",
        value = chargeType.chargeType.toString,
        hint = getHint(chargeType, hintClass),
        labelClasses = Some(LabelClasses(classes = if(areLabelsBold) Seq("govuk-!-font-weight-bold") else Nil)))
    }

    Radios(form("value"), x)
  }
  
  implicit val enumerable: Enumerable[PaymentOrChargeType] = Enumerable(values.map(v => v.toString -> v): _*)

  implicit def paymentOrChargePathBindable(implicit stringBinder: PathBindable[String]): PathBindable[PaymentOrChargeType] = new
      PathBindable[PaymentOrChargeType] {

    override def bind(key: String, value: String): Either[String, PaymentOrChargeType] = {
      stringBinder.bind(key, value) match {
        case Right("accounting-for-tax") => Right(AccountingForTaxCharges)
        case Right("contract-settlement") => Right(ContractSettlementCharges)
        case Right("excess-relief-paid") => Right(ExcessReliefPaidCharges)
        case Right("interest-on-excess-relief-paid") => Right(InterestOnExcessRelief)
        case Right("pensions-charge") => Right(PensionsCharges)
        case _ => Left("ChargeType binding failed")
      }
    }

    override def unbind(key: String, value: PaymentOrChargeType): String = {
      val modeValue = values.find(_ == value).map(_.toString).getOrElse(throw UnknownChargeTypeException())
      stringBinder.unbind(key, modeValue)
    }

  }

  implicit def penaltyTypeToString(value: PaymentOrChargeType): String = value match {
    case AccountingForTaxCharges => "accounting-for-tax"
    case ContractSettlementCharges => "contract-settlement"
    case ExcessReliefPaidCharges => "excess-relief-paid"
    case InterestOnExcessRelief => "interest-on-excess-relief-paid"
    case PensionsCharges => "pensions-charge"
  }

  implicit def stringToPenaltyType(value: String): PaymentOrChargeType =
    value match {
      case "accounting-for-tax" => AccountingForTaxCharges
      case "contract-settlement" => ContractSettlementCharges
      case "excess-relief-paid" => ExcessReliefPaidCharges
      case "interest-on-excess-relief-paid" => InterestOnExcessRelief
      case "pensions-charge" => PensionsCharges
      case e => throw new RuntimeException("Unknown value:" + e)
    }

  case class UnknownChargeTypeException() extends Exception

  private def getHint(chargeTypes: DisplayPaymentOrChargeType, hintClass: Seq[String]): Option[Hint] =
    chargeTypes.hintText match {
      case Some(hint) => Some(Hint(msg"${hint.toString}", "hint-id", hintClass))
      case _ => None
    }
}
