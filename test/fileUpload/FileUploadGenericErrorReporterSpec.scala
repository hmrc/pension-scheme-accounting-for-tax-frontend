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

package fileUpload

import base.SpecBase
import controllers.fileUpload.FileUploadGenericErrorReporter
import controllers.fileUpload.FileUploadHeaders.AnnualAllowanceFieldNames
import fileUploadParsers.ParserValidationError
import models.ChargeType
import org.scalatest.matchers.must.Matchers

class FileUploadGenericErrorReporterSpec extends SpecBase with Matchers {

  "File upload generic error reporter" must {

    "return generic list of errors for the failed AnnualAllowance" in {
     val underTest =  FileUploadGenericErrorReporter

     val errors  = Seq(
       ParserValidationError(1, 0, "memberDetails.error.firstName.required", "firstName"),
       ParserValidationError(2, 1, "memberDetails.error.lastName.required", "lastName"),
       ParserValidationError(2, 2, "memberDetails.error.nino.invalid", "nino"),
       ParserValidationError(1, 4, "chargeAmount.error.required", "chargeAmount"),
       ParserValidationError(1, 5, "dateNoticeReceived.error.incomplete", "dateNoticeReceived"),
       ParserValidationError(1, 6, "error.boolean", "isPaymentMandatory"),
       ParserValidationError(1, 3, "annualAllowanceYear.fileUpload.error.required", AnnualAllowanceFieldNames.taxYear),
       ParserValidationError(2, 5, "dateNoticeReceived.error.incomplete", "dateNoticeReceived"),
       ParserValidationError(2, 3, "annualAllowanceYear.fileUpload.error.invalid", AnnualAllowanceFieldNames.taxYear),
       ParserValidationError(3, 3, "annualAllowanceYear.fileUpload.error.future", AnnualAllowanceFieldNames.taxYear),
       ParserValidationError(4, 3, "annualAllowanceYear.fileUpload.error.past", AnnualAllowanceFieldNames.taxYear)
      )

      val result = underTest.generateGenericErrorReport(errors, ChargeType.ChargeTypeAnnualAllowance)
      result mustBe  Seq("fileUpload.memberDetails.generic.error.firstName",
                         "fileUpload.memberDetails.generic.error.lastName",
                         "fileUpload.memberDetails.generic.error.nino",
                         "fileUpload.chargeAmount.generic.error",
                         "fileUpload.dateNoticeReceived.generic.error",
                         "fileUpload.isPayment.generic.error",
                         "fileUpload.taxYear.generic.error")
    }

    "return generic list of errors for the failed LifetimeAllowance" in {
      val underTest =  FileUploadGenericErrorReporter

      val errors  = Seq(
        ParserValidationError(1, 0, "memberDetails.error.firstName.required", "firstName"),
        ParserValidationError(2, 1, "memberDetails.error.lastName.required", "lastName"),
        ParserValidationError(2, 2, "memberDetails.error.nino.invalid", "nino"),
        ParserValidationError(3, 2, "memberDetails.error.nino.invalid", "nino"),
        ParserValidationError(3, 3, "dateOfEvent.error.incomplete", "dateOfEvent"),
        ParserValidationError(4, 3, "taxAt25Percent.error.invalid", "taxAt25Percent"),
        ParserValidationError(4, 3, "taxAt50Percent.error.invalid", "taxAt55Percent")
      )

      val result = underTest.generateGenericErrorReport(errors, ChargeType.ChargeTypeLifetimeAllowance)
      result mustBe  List("fileUpload.memberDetails.generic.error.firstName",
                          "fileUpload.memberDetails.generic.error.lastName",
                          "fileUpload.memberDetails.generic.error.nino",
                          "fileUpload.dateOfEvent.generic.error",
                          "fileUpload.taxAt25Percent.generic.error",
                          "fileUpload.taxAt55Percent.generic.error")
    }

    "return generic list of errors for the failed OverseasTransfer" in {
      val underTest = FileUploadGenericErrorReporter

     val errors =  Seq(
        ParserValidationError(1, 0, "memberDetails.error.firstName.required", "firstName"),
        ParserValidationError(2, 1, "memberDetails.error.lastName.required", "lastName"),
        ParserValidationError(2, 2, "memberDetails.error.nino.invalid", "nino"),
        ParserValidationError(2, 3, "dateOfBirth.error.invalid", "dob"),
       ParserValidationError(2, 4, "qropsReferenceNumber.error.invalid", "qropsReferenceNumber"),
       ParserValidationError(2, 5, "qropsTransferDate.error.invalid", "qropsTransferDate"),
       ParserValidationError(2, 6, "amountTransferred.error.invalid", "amountTransferred"),
       ParserValidationError(2, 7, "amountTaxDue.error.invalid", "amountTaxDue")
      )

      val result = underTest.generateGenericErrorReport(errors, ChargeType.ChargeTypeOverseasTransfer)

      result mustBe  List("fileUpload.memberDetails.generic.error.firstName",
                          "fileUpload.memberDetails.generic.error.lastName",
                          "fileUpload.memberDetails.generic.error.nino",
                          "fileUpload.dateOfBirth.generic.error",
                          "fileUpload.qropsReferenceNumber.generic.error",
                          "fileUpload.dateOfTransfer.generic.error",
                          "fileUpload.amountTransferred.generic.error",
                          "fileUpload.amountTaxDue.generic.error"
                         )
    }
  }

}
