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

package fileUploadParsers

import com.google.inject.Inject
import config.FrontendAppConfig
import controllers.fileUpload.FileUploadHeaders.MemberDetailsFieldNames
import controllers.fileUpload.FileUploadHeaders.OverseasTransferFieldNames._
import forms.chargeG.{ChargeAmountsFormProvider, ChargeDetailsFormProvider, MemberDetailsFormProvider}
import models.Quarters
import models.chargeG.{ChargeAmounts, ChargeDetails, MemberDetails}
import pages.chargeG.{ChargeAmountsPage, ChargeDetailsPage, MemberDetailsPage}
import play.api.data.Form
import play.api.i18n.Messages

import java.time.LocalDate


class OverseasTransferParser @Inject()(
                                        memberDetailsFormProvider: MemberDetailsFormProvider,
                                        chargeDetailsFormProvider: ChargeDetailsFormProvider,
                                        chargeAmountsFormProvider: ChargeAmountsFormProvider,
                                        config: FrontendAppConfig
                                      ) extends Parser {
  override protected def validHeader: String = config.validOverseasTransferHeader

  override protected val totalFields: Int = 8

  private final val FieldNoDateOfBirth = 3
  private final val FieldNoQropsRefNo = 4
  private final val FieldNoDateOfTransfer = 5
  private final val FieldNoAmountTransferred = 6
  private final val FieldNoAmountTaxDue = 7

  def chargeMemberDetailsValidation(index: Int, chargeFields: Seq[String],
                                    memberDetailsForm: Form[MemberDetails]): Either[Seq[ParserValidationError], MemberDetails] = {
    val parsedDOB = splitDayMonthYear(chargeFields(FieldNoDateOfBirth))
    val fields = Seq(
      Field(MemberDetailsFieldNames.firstName, chargeFields(FieldNoFirstName), MemberDetailsFieldNames.firstName, FieldNoFirstName),
      Field(MemberDetailsFieldNames.lastName, chargeFields(FieldNoLastName), MemberDetailsFieldNames.lastName, FieldNoLastName),
      Field(MemberDetailsFieldNames.nino, chargeFields(FieldNoNino), MemberDetailsFieldNames.nino, FieldNoNino),
      Field(dateOfBirthDay, parsedDOB.day, dateOfBirth, FieldNoDateOfBirth),
      Field(dateOfBirthMonth, parsedDOB.month, dateOfBirth, FieldNoDateOfBirth),
      Field(dateOfBirthYear, parsedDOB.year, dateOfBirth, FieldNoDateOfBirth)
    )
    memberDetailsForm
      .bind(Field.seqToMap(fields))
      .fold(
        formWithErrors => Left(errorsFromForm(formWithErrors, fields, index)),
        value => Right(value)
      )
  }

  private def chargeDetailsValidation(startDate: LocalDate,
                                      index: Int,
                                      chargeFields: Seq[String])(implicit messages: Messages): Either[Seq[ParserValidationError], ChargeDetails] = {

    val parsedDateOfTransfer = splitDayMonthYear(chargeFields(FieldNoDateOfTransfer))
    val fields = Seq(
      Field(dateOfTransferDay, parsedDateOfTransfer.day, dateOfTransfer, FieldNoDateOfTransfer),
      Field(dateOfTransferMonth, parsedDateOfTransfer.month, dateOfTransfer, FieldNoDateOfTransfer),
      Field(dateOfTransferYear, parsedDateOfTransfer.year, dateOfTransfer, FieldNoDateOfTransfer),
      Field(qropsReferenceNumber, chargeFields(FieldNoQropsRefNo), qropsReferenceNumber, FieldNoQropsRefNo)
    )
    val chargeDetailsForm: Form[ChargeDetails] = chargeDetailsFormProvider(
      min = startDate,
      max = Quarters.getQuarter(startDate).endDate
    )
    chargeDetailsForm.bind(
      Field.seqToMap(fields)
    ).fold(
      formWithErrors => Left(errorsFromForm(formWithErrors, fields, index)),
      value => Right(value)
    )
  }

  private def chargeAmountsValidation(memberName: String,
                                      index: Int,
                                      chargeFields: Seq[String])(implicit messages: Messages): Either[Seq[ParserValidationError], ChargeAmounts] = {
    val fields = Seq(
      Field(amountTransferred, chargeFields(FieldNoAmountTransferred), amountTransferred, FieldNoAmountTransferred),
      Field(amountTaxDue, chargeFields(FieldNoAmountTaxDue), amountTaxDue, FieldNoAmountTaxDue)
    )
    val chargeDetailsForm: Form[ChargeAmounts] = chargeAmountsFormProvider(
      memberName = memberName,
      minimumChargeValueAllowed = minChargeValueAllowed
    )
    chargeDetailsForm.bind(
      Field.seqToMap(fields)
    ).fold(
      formWithErrors => Left(errorsFromForm(formWithErrors, fields, index)),
      value => Right(value)
    )
  }

  private def getMemberName(chargeFields: Seq[String])(implicit messages: Messages) =
    (chargeFields(FieldNoFirstName) + " " + chargeFields(FieldNoLastName)).trim match {
      case fullName if fullName.isEmpty => messages("fileUpload.theMember")
      case fullName => fullName
    }

  override protected def validateFields(startDate: LocalDate,
                                        index: Int,
                                        chargeFields: Seq[String])(implicit messages: Messages): Result = {
    val a = resultFromFormValidationResult[MemberDetails](
      chargeMemberDetailsValidation(index, chargeFields, memberDetailsFormProvider()), createCommitItem(index, MemberDetailsPage.apply)
    )

    val b = resultFromFormValidationResult[ChargeDetails](
      chargeDetailsValidation(startDate, index, chargeFields), createCommitItem(index, ChargeDetailsPage.apply)
    )

    val c = resultFromFormValidationResult[ChargeAmounts](
      chargeAmountsValidation(getMemberName(chargeFields), index, chargeFields), createCommitItem(index, ChargeAmountsPage.apply)
    )
    combineResults(a, b, c)
  }
}