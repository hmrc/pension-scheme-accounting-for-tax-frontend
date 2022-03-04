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

sealed trait SchemeFSClearingReason

object SchemeFSClearingReason extends Enumerable.Implicits {

  case object CLEARED_WITH_PAYMENT extends WithName("C1") with SchemeFSClearingReason
  case object CLEARED_WITH_DELTA_CREDIT extends WithName("C2") with SchemeFSClearingReason
  case object REPAYMENT_TO_THE_CUSTOMER extends WithName("C3") with SchemeFSClearingReason
  case object WRITTEN_OFF extends WithName("C4") with SchemeFSClearingReason
  case object TRANSFERRED_TO_ANOTHER_ACCOUNT extends WithName( "C5") with SchemeFSClearingReason
  case object OTHER_REASONS extends WithName("C6") with SchemeFSClearingReason


  val values: Seq[SchemeFSClearingReason] = Seq(
    CLEARED_WITH_PAYMENT,
    CLEARED_WITH_DELTA_CREDIT,
    REPAYMENT_TO_THE_CUSTOMER,
    WRITTEN_OFF,
    TRANSFERRED_TO_ANOTHER_ACCOUNT,
    OTHER_REASONS
  )

  implicit val enumerable: Enumerable[SchemeFSClearingReason] =
    Enumerable(values.map(v => v.toString -> v): _*)
}



