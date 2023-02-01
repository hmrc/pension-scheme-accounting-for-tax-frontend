/*
 * Copyright 2023 HM Revenue & Customs
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

package controllers.fileUpload

import controllers.fileUpload.FileUploadHeaders.{AnnualAllowanceFieldNames, LifetimeAllowanceFieldNames, MemberDetailsFieldNames, OverseasTransferFieldNames}
import fileUploadParsers.ParserValidationError
import models.ChargeType
import models.ChargeType.{ChargeTypeAnnualAllowance, ChargeTypeLifetimeAllowance, ChargeTypeOverseasTransfer}

object FileUploadGenericErrorReporter {

  import MemberDetailsFieldNames._
  case class ColumnAndError(columnName: String, errorDescription: String)

  type ErrorReport = Seq[String]
  type ColumnAndErrorMap = Map[String, String]

  private val commonColumnAndErrorMessageMap = Map(
    firstName -> "fileUpload.memberDetails.generic.error.firstName",
    lastName -> "fileUpload.memberDetails.generic.error.lastName",
    nino -> "fileUpload.memberDetails.generic.error.nino"
  )

  private val annualAllowanceHeader = commonColumnAndErrorMessageMap ++
    Map(
      AnnualAllowanceFieldNames.chargeAmount -> "fileUpload.chargeAmount.generic.error",
      AnnualAllowanceFieldNames.dateNoticeReceived -> "fileUpload.dateNoticeReceived.generic.error",
      AnnualAllowanceFieldNames.isPaymentMandatory -> "fileUpload.isPayment.generic.error",
      AnnualAllowanceFieldNames.taxYear -> "fileUpload.taxYear.generic.error",
      AnnualAllowanceFieldNames.isInAdditionToPrevious -> "fileupload.isInAdditionToPrevious.generic.error",
      AnnualAllowanceFieldNames.wasPaidByAnotherScheme -> "fileupload.wasPaidByAnotherScheme.generic.error",
      AnnualAllowanceFieldNames.pstr -> "fileupload.pstr.generic.error",
      AnnualAllowanceFieldNames.dateReportedAndPaid -> "fileupload.dateReportedAndPaid.generic.error",
      AnnualAllowanceFieldNames.chargeAmountReported -> "fileupload.chargeAmountReported.generic.error"
    )

  private val lifetimeAllowanceHeader = commonColumnAndErrorMessageMap ++
    Map(
      LifetimeAllowanceFieldNames.taxAt25Percent -> "fileUpload.taxAt25Percent.generic.error",
      LifetimeAllowanceFieldNames.taxAt55Percent -> "fileUpload.taxAt55Percent.generic.error",
      LifetimeAllowanceFieldNames.dateOfEvent -> "fileUpload.dateOfEvent.generic.error"
    )

  private val overseasTransferHeader = commonColumnAndErrorMessageMap ++
    Map(
      OverseasTransferFieldNames.dateOfBirth -> "fileUpload.dateOfBirth.generic.error",
      OverseasTransferFieldNames.dateOfTransfer -> "fileUpload.dateOfTransfer.generic.error",
      OverseasTransferFieldNames.qropsReferenceNumber -> "fileUpload.qropsReferenceNumber.generic.error",
      OverseasTransferFieldNames.amountTransferred ->  "fileUpload.amountTransferred.generic.error",
      OverseasTransferFieldNames.amountTaxDue -> "fileUpload.amountTaxDue.generic.error"
    )

  private def getColumnsAndErrorMap(chargeType: ChargeType): ColumnAndErrorMap = chargeType match {
    case ChargeTypeAnnualAllowance => annualAllowanceHeader
    case ChargeTypeLifetimeAllowance => lifetimeAllowanceHeader
    case ChargeTypeOverseasTransfer => overseasTransferHeader
    case _ => throw new RuntimeException("Invalid charge type")
  }

  def generateGenericErrorReport(errors: Seq[ParserValidationError], chargeType: ChargeType): ErrorReport = {
    val chargeTypeHeaderMap = getColumnsAndErrorMap(chargeType)
    val columns = errors.map(_.columnName).intersect(chargeTypeHeaderMap.keySet.toSeq)
    columns.map(col => chargeTypeHeaderMap.apply(col))
  }

}
