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

import cats.implicits.toFoldableOps
import com.google.inject.Inject
import config.FrontendAppConfig
import controllers.fileUpload.FileUploadHeaders.{MemberDetailsFieldNames, OverseasTransferFieldNames}
import controllers.fileUpload.FileUploadHeaders.OverseasTransferFieldNames._
import fileUploadParsers.Parser.Result
import forms.chargeG.{ChargeAmountsFormProvider, ChargeDetailsFormProvider, MemberDetailsFormProvider}
import models.Quarters
import models.chargeG.{ChargeAmounts, ChargeDetails, MemberDetails}
import pages.chargeG.{ChargeAmountsPage, ChargeDetailsPage, MemberDetailsPage}
import play.api.data.Form
import play.api.i18n.Messages
import fileUploadParsers.Parser.resultMonoid

import java.time.LocalDate


class OverseasTransferParser @Inject()(
                                        memberDetailsFormProvider: MemberDetailsFormProvider,
                                        chargeDetailsFormProvider: ChargeDetailsFormProvider,
                                        chargeAmountsFormProvider: ChargeAmountsFormProvider,
                                        config: FrontendAppConfig
                                      ) extends Parser {
  override protected def validHeader: String = config.validOverseasTransferHeader

  private val fieldNoDateOfBirth = 3
  private val fieldNoQropsRefNo = 4
  private val fieldNoDateOfTransfer = 5
  private val fieldNoAmountTransferred = 6
  private val fieldNoAmountTaxDue = 7

  def chargeMemberDetailsValidation(index: Int, chargeFields: Seq[String],
                                    memberDetailsForm: Form[MemberDetails]): Either[Seq[ParserValidationError], MemberDetails] = {
    val parsedDOB = splitDayMonthYear(chargeFields(fieldNoDateOfBirth))
    val fields = Seq(
      Field(MemberDetailsFieldNames.firstName, chargeFields(fieldNoFirstName), MemberDetailsFieldNames.firstName, fieldNoFirstName),
      Field(MemberDetailsFieldNames.lastName, chargeFields(fieldNoLastName), MemberDetailsFieldNames.lastName, fieldNoLastName),
      Field(MemberDetailsFieldNames.nino, chargeFields(fieldNoNino), MemberDetailsFieldNames.nino, fieldNoNino),
      Field(dateOfBirthDay, parsedDOB.day, dateOfBirth, fieldNoDateOfBirth, Some(OverseasTransferFieldNames.dateOfBirth)),
      Field(dateOfBirthMonth, parsedDOB.month, dateOfBirth, fieldNoDateOfBirth, Some(OverseasTransferFieldNames.dateOfBirth)),
      Field(dateOfBirthYear, parsedDOB.year, dateOfBirth, fieldNoDateOfBirth, Some(OverseasTransferFieldNames.dateOfBirth))
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

    val parsedDateOfTransfer = splitDayMonthYear(chargeFields(fieldNoDateOfTransfer))
    val fields = Seq(
      Field(dateOfTransferDay, parsedDateOfTransfer.day, dateOfTransfer, fieldNoDateOfTransfer, Some(OverseasTransferFieldNames.dateOfTransfer)),
      Field(dateOfTransferMonth, parsedDateOfTransfer.month, dateOfTransfer, fieldNoDateOfTransfer, Some(OverseasTransferFieldNames.dateOfTransfer)),
      Field(dateOfTransferYear, parsedDateOfTransfer.year, dateOfTransfer, fieldNoDateOfTransfer, Some(OverseasTransferFieldNames.dateOfTransfer)),
      Field(qropsReferenceNumber, chargeFields(fieldNoQropsRefNo), qropsReferenceNumber, fieldNoQropsRefNo)
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
      Field(amountTransferred, chargeFields(fieldNoAmountTransferred), amountTransferred, fieldNoAmountTransferred),
      Field(amountTaxDue, chargeFields(fieldNoAmountTaxDue), amountTaxDue, fieldNoAmountTaxDue)
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
    (chargeFields(fieldNoFirstName) + " " + chargeFields(fieldNoLastName)).trim match {
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
    Seq(a, b, c).combineAll
  }
}