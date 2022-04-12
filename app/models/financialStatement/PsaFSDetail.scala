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

import play.api.libs.json.{Format, Json}

import java.time.LocalDate

case class PsaFS (
                     inhibitRefundSignal: Boolean = false,
                     seqPsaFSDetail: Seq[PsaFSDetail]
                   )

object PsaFS {
  implicit val formats: Format[PsaFS] = Json.format[PsaFS]
}

case class PsaFSDetail(index: Int,
                        chargeReference: String,
                       chargeType: PsaFSChargeType,
                       dueDate: Option[LocalDate],
                       totalAmount: BigDecimal,
                       amountDue: BigDecimal,
                       outstandingAmount: BigDecimal,
                       stoodOverAmount: BigDecimal,
                       accruedInterestTotal: BigDecimal,
                       periodStartDate: LocalDate,
                       periodEndDate: LocalDate,
                       pstr: String,
                       sourceChargeRefForInterest: Option[String],
                       psaSourceChargeInfo: Option[PsaSourceChargeInfo] = None,
                       documentLineItemDetails: Seq[DocumentLineItemDetail])

object PsaFSDetail {
  implicit val formats: Format[PsaFSDetail] = Json.format[PsaFSDetail]
}

case class PsaSourceChargeInfo(
                             index: Int,
                             chargeType: PsaFSChargeType,
                             periodStartDate: LocalDate,
                             periodEndDate: LocalDate
                           )

object PsaSourceChargeInfo {
  implicit val formats: Format[PsaSourceChargeInfo] = Json.format[PsaSourceChargeInfo]
}
