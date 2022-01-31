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
import forms.chargeG.{ChargeDetailsFormProvider, MemberDetailsFormProvider}
import models.chargeG.{ChargeDetails, MemberDetails}
import models.Quarters
import pages.chargeG.{ChargeDetailsPage, MemberDetailsPage}
import play.api.data.Form
import play.api.i18n.Messages
import play.api.libs.json.Json

import java.time.LocalDate

class OverseasTransferParser @Inject()(
                                         memberDetailsFormProvider: MemberDetailsFormProvider,
                                         chargeDetailsFormProvider: ChargeDetailsFormProvider,
                                         config: FrontendAppConfig
                                       ) extends Parser {
  //scalastyle:off magic.number
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
  }

  def chargeMemberDetailsValidation(index: Int, chargeFields: Array[String],
                                        memberDetailsForm: Form[MemberDetails]): Either[Seq[ParserValidationError], MemberDetails] = {


    splitDayMonthYear(chargeFields(5)) match {
      case Tuple3(dotDay, dotMonth, dotYear) =>
        val fields = Seq(
          Field(MemberDetailsFieldNames.firstName, firstNameField(chargeFields), MemberDetailsFieldNames.firstName, 0),
          Field(MemberDetailsFieldNames.lastName, lastNameField(chargeFields), MemberDetailsFieldNames.lastName, 1),
          Field(MemberDetailsFieldNames.nino, ninoField(chargeFields), MemberDetailsFieldNames.nino, 2),
          Field(ChargeGetailsFieldNames.dateOfBirthDay, dotDay, ChargeGetailsFieldNames.dateOfBirth, 3),
          Field(ChargeGetailsFieldNames.dateOfBirthMonth, dotMonth, ChargeGetailsFieldNames.dateOfBirth, 3),
          Field(ChargeGetailsFieldNames.dateOfBirthYear, dotYear, ChargeGetailsFieldNames.dateOfBirth, 3)
        )
        memberDetailsForm
          .bind(Field.seqToMap(fields))
          .fold(
            formWithErrors => {
            println("\n>>>>>>" + formWithErrors)
              Left(errorsFromForm(formWithErrors, fields, index))},
            value => Right(value)
          )
    }
  }

//First name,Last name,National Insurance number,Date of birth,Reference number,Transfer date,Amount,Tax due
  private def chargeDetailsValidation(startDate: LocalDate,
                                      index: Int,
                                      chargeFields: Array[String])(implicit messages: Messages): Either[Seq[ParserValidationError], ChargeDetails] = {

    splitDayMonthYear(chargeFields(5)) match {
      case Tuple3(dotDay, dotMonth, dotYear) =>
        val fields = Seq(
          Field(ChargeGetailsFieldNames.dateOfTransferDay, dotDay, ChargeGetailsFieldNames.dateOfTransfer, 5),
          Field(ChargeGetailsFieldNames.dateOfTransferMonth, dotMonth, ChargeGetailsFieldNames.dateOfTransfer, 5),
          Field(ChargeGetailsFieldNames.dateOfTransferYear, dotYear, ChargeGetailsFieldNames.dateOfTransfer, 5),
          Field(ChargeGetailsFieldNames.qropsReferenceNumber, chargeFields(4), ChargeGetailsFieldNames.qropsReferenceNumber, 4)
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
  }

  override protected def validateFields(startDate: LocalDate,
                                        index: Int,
                                        chargeFields: Array[String])(implicit messages: Messages): Either[Seq[ParserValidationError], Seq[CommitItem]] = {
    combineValidationResults[MemberDetails, ChargeDetails](
      chargeMemberDetailsValidation(index, chargeFields, memberDetailsFormProvider()),
      chargeDetailsValidation(startDate, index, chargeFields),
      MemberDetailsPage(index - 1).path,
      Json.toJson(_),
      ChargeDetailsPage(index - 1).path,
      Json.toJson(_)
    )
  }
}
