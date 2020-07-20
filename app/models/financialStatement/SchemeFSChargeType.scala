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
import play.api.mvc.PathBindable

sealed trait SchemeFSChargeType

object SchemeFSChargeType extends Enumerable.Implicits {

  case object PAYMENT_ON_ACCOUNT extends WithName("Payment on account") with SchemeFSChargeType
  case object PSS_AFT_RETURN extends WithName("Accounting for Tax return") with SchemeFSChargeType
  case object PSS_AFT_RETURN_INTEREST extends WithName("Interest on Accounting for Tax return") with SchemeFSChargeType
  case object PSS_OTC_AFT_RETURN extends WithName("Overseas transfer charge") with SchemeFSChargeType
  case object PSS_OTC_AFT_RETURN_INTEREST extends WithName("Interest on overseas transfer charge") with SchemeFSChargeType

  val values: Seq[SchemeFSChargeType] = Seq(
    PSS_AFT_RETURN,
    PSS_AFT_RETURN_INTEREST,
    PSS_OTC_AFT_RETURN,
    PSS_OTC_AFT_RETURN_INTEREST
  )

  implicit val enumerable: Enumerable[SchemeFSChargeType] =
    Enumerable(values.map(v => v.toString -> v): _*)

  case class UnknownChargeTypeException() extends Exception
  implicit def chargeTypePathBindable(implicit stringBinder: PathBindable[String]): PathBindable[SchemeFSChargeType] =
    new PathBindable[SchemeFSChargeType] {

      override def bind(key: String, value: String): Either[String, SchemeFSChargeType] = {
        stringBinder.bind(key, value) match {
          case Right("accounting-for-tax-return")             => Right(PSS_AFT_RETURN)
          case Right("interest-on-accounting-for-tax-return") => Right(PSS_AFT_RETURN_INTEREST)
          case Right("overseas-transfer-charge")              => Right(PSS_OTC_AFT_RETURN)
          case Right("interest-on-overseas-transfer-charge")  => Right(PSS_OTC_AFT_RETURN_INTEREST)
          case _                                              => Left("ChargeType binding failed")
        }
      }

      override def unbind(key: String, value: SchemeFSChargeType): String = {
        val chargeTypeValue = values.find(_ == value).map(_.toString.replaceAll(" ", "-").toLowerCase()).getOrElse(throw UnknownChargeTypeException())
        stringBinder.unbind(key, chargeTypeValue)
      }
    }
}
