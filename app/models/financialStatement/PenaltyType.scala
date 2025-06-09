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

import models.Enumerable
import models.financialStatement.PsaFSChargeType._
import play.api.data.Form
import play.api.i18n.Messages
import play.api.mvc.PathBindable
import uk.gov.hmrc.govukfrontend.views.Aliases.{Hint, Text}
import uk.gov.hmrc.govukfrontend.views.viewmodels.radios.RadioItem
import viewmodels.Radios.Radio
import viewmodels.{LabelClasses, Radios}

import scala.language.implicitConversions

trait PenaltyType

object PenaltyType extends Enumerable.Implicits {

  case object AccountingForTaxPenalties extends PenaltyType
  case object ContractSettlementCharges extends PenaltyType
  case object InformationNoticePenalties extends PenaltyType
  case object PensionsPenalties extends PenaltyType
  case object EventReportingCharges extends PenaltyType

  def getPenaltyType(chargeType: PsaFSChargeType): PenaltyType =
    chargeType match {
      case PSS_PENALTY => PensionsPenalties
      case PSS_INFO_NOTICE => InformationNoticePenalties
      case CONTRACT_SETTLEMENT | CONTRACT_SETTLEMENT_INTEREST | INTEREST_ON_CONTRACT_SETTLEMENT => ContractSettlementCharges
      case SSC_30_DAY_LPP | SSC_6_MONTH_LPP | SSC_12_MONTH_LPP => EventReportingCharges
      case _ => AccountingForTaxPenalties
    }

  val values: Seq[PenaltyType] =
    Seq(AccountingForTaxPenalties, ContractSettlementCharges, EventReportingCharges, InformationNoticePenalties, PensionsPenalties)

  def radiosWithHint(form: Form[_], penaltyTypes: Seq[DisplayPenaltyType], hintClass: Seq[String] = Nil,
             areLabelsBold: Boolean = true)(implicit messages: Messages): Seq[RadioItem] =
    {
      val x: Seq[Radio] = penaltyTypes.map { penaltyType =>

        Radios.Radio(label = Text(Messages(s"penaltyType.${penaltyType.penaltyType.toString}")),
          value = penaltyType.penaltyType.toString,
          hint = getHint(penaltyType, hintClass),
          labelClasses = Some(LabelClasses(classes = if(areLabelsBold) Seq("govuk-!-font-weight-bold") else Nil)))
      }

      Radios(form("value"), x)
    }

  def radios(form: Form[_], penaltyTypes: Seq[DisplayPenaltyType],
             areLabelsBold: Boolean = true)(implicit messages: Messages): Seq[RadioItem] =
  {
    val x: Seq[Radio] = penaltyTypes.map { penaltyType =>

      Radios.Radio(label = Text(Messages(s"penaltyType.${penaltyType.penaltyType.toString}")),
        value = penaltyType.penaltyType.toString,
        labelClasses = Some(LabelClasses(classes = if(areLabelsBold) Seq("govuk-!-font-weight-bold") else Nil)))
    }

    Radios(form("value"), x)
  }


  implicit val enumerable: Enumerable[PenaltyType] = Enumerable(values.map(v => v.toString -> v): _*)

  implicit def modePathBindable(implicit stringBinder: PathBindable[String]): PathBindable[PenaltyType] =
    new PathBindable[PenaltyType] {

      override def bind(key: String, value: String): Either[String, PenaltyType] =
        stringBinder.bind(key, value) match {
          case Right("accounting-for-tax") => Right(AccountingForTaxPenalties)
          case Right("contract-settlement") => Right(ContractSettlementCharges)
          case Right("event-reporting") => Right(EventReportingCharges)
          case Right("information-notice") => Right(InformationNoticePenalties)
          case Right("pensions-penalty") => Right(PensionsPenalties)
          case _ => Left("PenaltyType binding failed")
        }

      override def unbind(key: String, value: PenaltyType): String = {
        val modeValue = values.find(_ == value).map(_.toString).getOrElse(throw UnknownPenaltyTypeException())
        stringBinder.unbind(key, modeValue)
      }
    }

  implicit def penaltyTypeToString(value: PenaltyType): String =
    value match {
      case AccountingForTaxPenalties => "accounting-for-tax"
      case ContractSettlementCharges => "contract-settlement"
      case EventReportingCharges => "event-reporting"
      case InformationNoticePenalties => "information-notice"
      case PensionsPenalties => "pensions-penalty"
      case _ => throw new RuntimeException("Penalty type not found")
    }

  implicit def stringToPenaltyType(value: String): PenaltyType =
    value match {
      case "accounting-for-tax" => AccountingForTaxPenalties
      case "contract-settlement" => ContractSettlementCharges
      case "event-reporting" => EventReportingCharges
      case "information-notice" => InformationNoticePenalties
      case "pensions-penalty" => PensionsPenalties
    }

  case class UnknownPenaltyTypeException() extends Exception

  private def getHint(penaltyTypes: DisplayPenaltyType, hintClass: Seq[String])(implicit messages: Messages): Option[Hint] =
    penaltyTypes.hintText match {
      case Some(hint) => Some(Hint(content = Text(Messages(s"${hint.toString}")), id = Some("hint-id"), classes = hintClass.mkString(" ")))
      case _ => None

    }
}
