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

case class DisplayQuarter(quarter: AFTQuarter,
                          displayYear: Boolean,
                          lockedBy: Option[String],
                          hintText: Option[DisplayHint])
//{
//  override def toString: String = quarter.toString
//}

sealed trait DisplayHint

object LockedHint extends WithName("quarters.hint.locked") with DisplayHint

object InProgressHint extends WithName("quarters.hint.inProgress") with DisplayHint

object SubmittedHint extends WithName("quarters.hint.submitted") with DisplayHint

object PaymentOverdue extends WithName("hint.paymentOverdue") with DisplayHint

object TpssReportPresentHint extends WithName("hint.tpssReportPresent") with DisplayHint