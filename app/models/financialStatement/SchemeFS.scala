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



case class DocumentLineItemDetail(clearedAmountItem: BigDecimal, clearingDate: Option[LocalDate], clearingReason:Option[SchemeFSClearingReason])

object DocumentLineItemDetail {
  implicit val formats: Format[DocumentLineItemDetail] = Json.format[DocumentLineItemDetail]
}

case class SchemeFS(
                     chargeReference: String,
                     chargeType: SchemeFSChargeType,
                     dueDate: Option[LocalDate],
                     totalAmount: BigDecimal,
                     amountDue: BigDecimal,
                     outstandingAmount: BigDecimal,
                     accruedInterestTotal: BigDecimal,
                     stoodOverAmount: BigDecimal,
                     periodStartDate: LocalDate,
                     periodEndDate: LocalDate,
                     formBundleNumber: Option[String],
                     sourceChargeRefForInterest: Option[String],
                     documentLineItemDetails: Seq[DocumentLineItemDetail]
                   )

case class SchemeFSOptDate(
                     chargeReference: String,
                     chargeType: SchemeFSChargeType,
                     dueDate: Option[LocalDate],
                     totalAmount: BigDecimal,
                     amountDue: BigDecimal,
                     outstandingAmount: BigDecimal,
                     accruedInterestTotal: BigDecimal,
                     stoodOverAmount: BigDecimal,
                     periodStartDate: Option[LocalDate],
                     periodEndDate: Option[LocalDate],
                     formBundleNumber: Option[String],
                     sourceChargeRefForInterest: Option[String],
                     documentLineItemDetails: Seq[DocumentLineItemDetail]
                   ) {
  def toSchemeFS: SchemeFS = SchemeFS(
    chargeReference,
    chargeType,
    dueDate,
    totalAmount,
    amountDue,
    outstandingAmount,
    accruedInterestTotal,
    stoodOverAmount,
    periodStartDate.getOrElse(LocalDate.of(1900,1,1)),
    periodEndDate.getOrElse(LocalDate.of(2900,12,31)),
    formBundleNumber,
    sourceChargeRefForInterest,
    documentLineItemDetails
  )
                   }

object SchemeFSOptDate {
  implicit val formats: Format[SchemeFSOptDate] = Json.format[SchemeFSOptDate]

}

object SchemeFS {
  implicit val formats: Format[SchemeFS] = Json.format[SchemeFS]
}
