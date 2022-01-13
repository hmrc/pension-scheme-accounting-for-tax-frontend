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

package models

sealed trait ValueChangeType

object ValueChangeType extends Enumerable.Implicits {
  case object ChangeTypeDecrease extends WithName("decrease") with ValueChangeType
  case object ChangeTypeSame extends WithName("same") with ValueChangeType
  case object ChangeTypeIncrease extends WithName("increase") with ValueChangeType

  val values: Seq[ValueChangeType] = Seq(
    ChangeTypeDecrease, ChangeTypeSame, ChangeTypeIncrease
  )

  def valueChangeType(currentTotalAmount:BigDecimal, previousTotalAmount:BigDecimal):ValueChangeType =
    (currentTotalAmount, previousTotalAmount) match {
    case (c, p) if c == p => ChangeTypeSame
    case (c, p) if c > p => ChangeTypeIncrease
    case _ => ChangeTypeDecrease
  }

  implicit val enumerable: Enumerable[ValueChangeType] =
    Enumerable(values.map(v => v.toString -> v): _*)
}
