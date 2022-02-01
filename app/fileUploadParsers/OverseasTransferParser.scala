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

package fileUploadParsers

import com.google.inject.Inject
import config.FrontendAppConfig
import forms.chargeG.{ChargeAmountsFormProvider, ChargeDetailsFormProvider, MemberDetailsFormProvider}
import models.Quarters
import models.chargeG.{ChargeAmounts, ChargeDetails, MemberDetails}
import pages.chargeG.{ChargeAmountsPage, ChargeDetailsPage, MemberDetailsPage}
import play.api.data.Form
import play.api.i18n.Messages
import play.api.libs.json.Json

import java.time.LocalDate

class OverseasTransferParser @Inject()(
                                        memberDetailsFormProvider: MemberDetailsFormProvider,
                                        chargeDetailsFormProvider: ChargeDetailsFormProvider,
                                        chargeAmountsFormProvider: ChargeAmountsFormProvider,
                                        config: FrontendAppConfig
                                      ) extends Parser {
  override protected def validHeader: String = config.validOverseasTransferHeader

  override protected val totalFields: Int = 8

  private final object ChargeGetailsFieldNames {
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

  private final val FieldNoDOB = 3

  private final val FieldNoDateOfTransfer = 5
  private final val FieldNoQropsRefNo = 4
  private final val FieldNoAmountTransferred = 6
  private final val FieldNoAmountTaxDue = 7

  def chargeMemberDetailsValidation(index: Int, chargeFields: Array[String],
                                    memberDetailsForm: Form[MemberDetails]): Either[Seq[ParserValidationError], MemberDetails] = {
    val parsedDOB = splitDayMonthYear(chargeFields(FieldNoDOB))
    val fields = Seq(
      Field(MemberDetailsFieldNames.firstName, chargeFields(FieldNoFirstName), MemberDetailsFieldNames.firstName, FieldNoFirstName),
      Field(MemberDetailsFieldNames.lastName, chargeFields(FieldNoLastName), MemberDetailsFieldNames.lastName, FieldNoLastName),
      Field(MemberDetailsFieldNames.nino, chargeFields(FieldNoNino), MemberDetailsFieldNames.nino, FieldNoNino),
      Field(ChargeGetailsFieldNames.dateOfBirthDay, parsedDOB.day, ChargeGetailsFieldNames.dateOfBirth, FieldNoDOB),
      Field(ChargeGetailsFieldNames.dateOfBirthMonth, parsedDOB.month, ChargeGetailsFieldNames.dateOfBirth, FieldNoDOB),
      Field(ChargeGetailsFieldNames.dateOfBirthYear, parsedDOB.year, ChargeGetailsFieldNames.dateOfBirth, FieldNoDOB)
    )
    memberDetailsForm
      .bind(Field.seqToMap(fields))
      .fold(
        formWithErrors => Left(errorsFromForm(formWithErrors, fields, index)),
        value => Right(value)
      )
  }

  //First name,Last name,National Insurance number,Date of birth,Reference number,Transfer date,Amount,Tax due
  private def chargeDetailsValidation(startDate: LocalDate,
                                      index: Int,
                                      chargeFields: Array[String])(implicit messages: Messages): Either[Seq[ParserValidationError], ChargeDetails] = {

    val parsedDateOfTransfer = splitDayMonthYear(chargeFields(FieldNoDateOfTransfer))
    val fields = Seq(
      Field(ChargeGetailsFieldNames.dateOfTransferDay,
        parsedDateOfTransfer.day, ChargeGetailsFieldNames.dateOfTransfer, FieldNoDateOfTransfer),
      Field(ChargeGetailsFieldNames.dateOfTransferMonth,
        parsedDateOfTransfer.month, ChargeGetailsFieldNames.dateOfTransfer, FieldNoDateOfTransfer),
      Field(ChargeGetailsFieldNames.dateOfTransferYear,
        parsedDateOfTransfer.year, ChargeGetailsFieldNames.dateOfTransfer, FieldNoDateOfTransfer),
      Field(ChargeGetailsFieldNames.qropsReferenceNumber,
        chargeFields(FieldNoQropsRefNo), ChargeGetailsFieldNames.qropsReferenceNumber, FieldNoQropsRefNo)
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
                                      chargeFields: Array[String])(implicit messages: Messages): Either[Seq[ParserValidationError], ChargeAmounts] = {
    val fields = Seq(
      Field(ChargeGetailsFieldNames.amountTransferred,
        chargeFields(FieldNoAmountTransferred), ChargeGetailsFieldNames.amountTransferred, FieldNoAmountTransferred),
      Field(ChargeGetailsFieldNames.amountTaxDue,
        chargeFields(FieldNoAmountTaxDue), ChargeGetailsFieldNames.amountTaxDue, FieldNoAmountTaxDue)
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

  override protected def validateFields(startDate: LocalDate,
                                        index: Int,
                                        chargeFields: Array[String])(implicit messages: Messages): Either[Seq[ParserValidationError], Seq[CommitItem]] = {
    addToValidationResults[ChargeAmounts](
      chargeAmountsValidation("", index, chargeFields),
      addToValidationResults[ChargeDetails](
        chargeDetailsValidation(startDate, index, chargeFields),
        addToValidationResults[MemberDetails](
          chargeMemberDetailsValidation(index, chargeFields, memberDetailsFormProvider()),
          Right(Nil),
          MemberDetailsPage(index - 1).path,
          Json.toJson(_)
        ),
        ChargeDetailsPage(index - 1).path,
        Json.toJson(_)
      ),
      ChargeAmountsPage(index - 1).path,
      Json.toJson(_)
    )
  }
}
