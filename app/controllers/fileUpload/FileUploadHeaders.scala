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

package controllers.fileUpload

object FileUploadHeaders {

  object MemberDetailsFieldNames {
    val firstName = "firstName"
    val lastName = "lastName"
    val nino = "nino"
  }

  object AnnualAllowanceFieldNames {
    val chargeAmount: String = "chargeAmount"
    val dateNoticeReceivedDay: String = "dateNoticeReceived.day"
    val dateNoticeReceivedMonth: String = "dateNoticeReceived.month"
    val dateNoticeReceivedYear: String = "dateNoticeReceived.year"
    val dateNoticeReceived: String = "dateNoticeReceived"
    val isPaymentMandatory = "isPaymentMandatory"
    val taxYear = "TaxYear"
    // MCCLOUD
    val isInAdditionToPrevious: String = "isInAdditionToPrevious"
    val wasPaidByAnotherScheme: String = "wasPaidByAnotherScheme"
    val pstr: String = "pstr"
    val dateReportedAndPaid: String = "dateReportedAndPaid"
    val chargeAmountReported: String = "chargeAmountReported"
  }

  object LifetimeAllowanceFieldNames {
    val dateOfEventDay: String = "dateOfEvent.day"
    val dateOfEventMonth: String = "dateOfEvent.month"
    val dateOfEventYear: String = "dateOfEvent.year"
    val taxAt25Percent: String = "taxAt25Percent"
    val taxAt55Percent: String = "taxAt55Percent"
    val dateOfEvent: String = "dateOfEvent"
  }

   object OverseasTransferFieldNames {
    val dateOfBirthDay: String = "dob.day"
    val dateOfBirthMonth: String = "dob.month"
    val dateOfBirthYear: String = "dob.year"
    val dateOfTransferDay: String = "qropsTransferDate.day"
    val dateOfTransferMonth: String = "qropsTransferDate.month"
    val dateOfTransferYear: String = "qropsTransferDate.year"
    val qropsReferenceNumber: String = "qropsReferenceNumber"
    val dateOfBirth: String = "dob"
    val dateOfTransfer: String = "qropsTransferDate"
    val amountTransferred: String = "amountTransferred"
    val amountTaxDue: String = "amountTaxDue"
  }
}
