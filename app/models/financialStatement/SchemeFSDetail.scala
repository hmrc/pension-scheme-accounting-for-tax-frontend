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

import play.api.libs.functional.syntax.toFunctionalBuilderOps
import play.api.libs.json._

import java.time.LocalDate

case class SchemeFS(
                     inhibitRefundSignal: Boolean = false,
                     seqSchemeFSDetail: Seq[SchemeFSDetail]
                   )

object SchemeFS {
  implicit val formats: Format[SchemeFS] = Json.format[SchemeFS]
}

case class DocumentLineItemDetail(clearedAmountItem: BigDecimal, clearingDate: Option[LocalDate], clearingReason: Option[FSClearingReason])

object DocumentLineItemDetail {
  implicit val formats: Format[DocumentLineItemDetail] = Json.format[DocumentLineItemDetail]
}

case class SchemeFSDetail(
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
                   )

object SchemeFSDetail {

  implicit val writesSchemeFS: Writes[SchemeFSDetail] = (
    (JsPath \ "chargeReference").write[String] and
      (JsPath \ "chargeType").write[SchemeFSChargeType] and
      (JsPath \ "dueDate").writeNullable[LocalDate] and
      (JsPath \ "totalAmount").write[BigDecimal] and
      (JsPath \ "amountDue").write[BigDecimal] and
      (JsPath \ "outstandingAmount").write[BigDecimal] and
      (JsPath \ "accruedInterestTotal").write[BigDecimal] and
      (JsPath \ "stoodOverAmount").write[BigDecimal] and
      (JsPath \ "periodStartDate").writeNullable[LocalDate] and
      (JsPath \ "periodEndDate").writeNullable[LocalDate] and
      (JsPath \ "formBundleNumber").writeNullable[String] and
      (JsPath \ "sourceChargeRefForInterest").writeNullable[String] and
      (JsPath \ "documentLineItemDetails").write[Seq[DocumentLineItemDetail]]
    ) (x => (
    x.chargeReference,
    x.chargeType,
    x.dueDate,
    x.totalAmount,
    x.amountDue,
    x.outstandingAmount,
    x.accruedInterestTotal,
    x.stoodOverAmount,
    x.periodStartDate,
    x.periodEndDate,
    x.formBundleNumber,
    x.sourceChargeRefForInterest,
    x.documentLineItemDetails
  ))

  implicit val rdsSchemeFSDetail: Reads[SchemeFSDetail] = (
    (JsPath \ "chargeReference").read[String] and
      (JsPath \ "chargeType").read[SchemeFSChargeType] and
      (JsPath \ "dueDate").readNullable[LocalDate] and
      (JsPath \ "totalAmount").read[BigDecimal] and
      (JsPath \ "amountDue").read[BigDecimal] and
      (JsPath \ "outstandingAmount").read[BigDecimal] and
      (JsPath \ "accruedInterestTotal").read[BigDecimal] and
      (JsPath \ "stoodOverAmount").read[BigDecimal] and
      (JsPath \ "periodStartDate").readNullable[LocalDate] and
      (JsPath \ "periodEndDate").readNullable[LocalDate] and
      (JsPath \ "formBundleNumber").readNullable[String] and
      (JsPath \ "sourceChargeRefForInterest").readNullable[String] and
      (JsPath \ "documentLineItemDetails").read[Seq[DocumentLineItemDetail]]
    ) (
    (chargeReference, chargeType, dueDateOpt, totalAmount, amountDue, outstandingAmount,
     accruedInterestTotal, stoodOverAmount, periodStartDateOpt, periodEndDateOpt,
     formBundleNumberOpt, sourceChargeRefForInterestOpt, documentLineItemDetails) =>
      SchemeFSDetail(
        chargeReference,
        chargeType,
        dueDateOpt,
        totalAmount,
        amountDue,
        outstandingAmount,
        accruedInterestTotal,
        stoodOverAmount,
        periodStartDateOpt,
        periodEndDateOpt,
        formBundleNumberOpt,
        sourceChargeRefForInterestOpt,
        documentLineItemDetails
      )
  )
}
