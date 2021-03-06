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

package models

import play.api.data.Form
import uk.gov.hmrc.viewmodels._

sealed trait ChargeType

object ChargeType extends Enumerable.Implicits {

  case object ChargeTypeAnnualAllowance extends WithName("annualAllowance") with ChargeType
  case object ChargeTypeAuthSurplus extends WithName("authSurplus") with ChargeType
  case object ChargeTypeDeRegistration extends WithName("deRegistration") with ChargeType
  case object ChargeTypeLifetimeAllowance extends WithName("lifeTimeAllowance") with ChargeType
  case object ChargeTypeOverseasTransfer extends WithName("overseasTransfer") with ChargeType
  case object ChargeTypeShortService extends WithName("shortService") with ChargeType
  case object ChargeTypeLumpSumDeath extends WithName("lumpSumDeath") with ChargeType

  val values: Seq[ChargeType] = Seq(
    ChargeTypeAnnualAllowance,
    ChargeTypeAuthSurplus,
    ChargeTypeDeRegistration,
    ChargeTypeLifetimeAllowance,
    ChargeTypeOverseasTransfer,
    ChargeTypeShortService,
    ChargeTypeLumpSumDeath
  )

  def radios(form: Form[_]): Seq[Radios.Item] = {

    val field = form("value")
    val items = Seq(
      Radios.Radio(msg"chargeType.radio.annualAllowance", ChargeTypeAnnualAllowance.toString),
      Radios.Radio(msg"chargeType.radio.authSurplus", ChargeTypeAuthSurplus.toString),
      Radios.Radio(msg"chargeType.radio.deRegistration", ChargeTypeDeRegistration.toString),
      Radios.Radio(msg"chargeType.radio.lifeTimeAllowance", ChargeTypeLifetimeAllowance.toString),
      Radios.Radio(msg"chargeType.radio.overseasTransfer", ChargeTypeOverseasTransfer.toString),
      Radios.Radio(msg"chargeType.radio.shortService", ChargeTypeShortService.toString),
      Radios.Radio(msg"chargeType.radio.lumpSumDeath", ChargeTypeLumpSumDeath.toString)
    )

    Radios(field, items)
  }

  implicit val enumerable: Enumerable[ChargeType] =
    Enumerable(values.map(v => v.toString -> v): _*)
}
