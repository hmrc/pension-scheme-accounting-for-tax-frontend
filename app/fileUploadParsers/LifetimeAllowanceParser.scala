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
import forms.MemberDetailsFormProvider
import forms.chargeD.ChargeDetailsFormProvider
import models.chargeD.ChargeDDetails
import models.{MemberDetails, Quarters}
import pages.chargeD.{ChargeDetailsPage, MemberDetailsPage}
import play.api.data.Form
import play.api.i18n.Messages
import play.api.libs.json.Json

import java.time.LocalDate

class LifetimeAllowanceParser @Inject()(
                                         memberDetailsFormProvider: MemberDetailsFormProvider,
                                         chargeDetailsFormProvider: ChargeDetailsFormProvider
                                       ) extends Parser {
  //First name,Last name,National Insurance number,Date,Tax due 25%,Tax due 55%

  //scalastyle:off magic.number
  override protected val totalFields: Int = 6

  private def memberDetailsValidation(index: Int, chargeFields: Array[String]): Either[Seq[ParserValidationError], MemberDetails] = {
    val fields = Seq(
      Field(MemberDetailsFieldNames.firstName, firstNameField(chargeFields), MemberDetailsFieldNames.firstName, 0),
      Field(MemberDetailsFieldNames.lastName, lastNameField(chargeFields), MemberDetailsFieldNames.lastName, 1),
      Field(MemberDetailsFieldNames.nino, ninoField(chargeFields), MemberDetailsFieldNames.nino, 2)
    )
    val memberDetailsForm = memberDetailsFormProvider()
    memberDetailsForm
      .bind(Field.seqToMap(fields))
      .fold(
        formWithErrors => Left(errorsFromForm(formWithErrors, fields, index)),
        value => Right(value)
      )
  }

  private object ChargeDetailsFieldNames {
    val dateOfEventDay: String = "dateOfEvent.day"
    val dateOfEventMonth: String = "dateOfEvent.month"
    val dateOfEventYear: String = "dateOfEvent.year"
    val taxAt25Percent: String = "taxAt25Percent"
    val taxAt55Percent: String = "taxAt55Percent"
    val dateOfEvent: String = "dateOfEvent"
  }

  private def chargeDetailsValidation(startDate: LocalDate,
                                      index: Int,
                                      chargeFields: Array[String])(implicit messages: Messages): Either[Seq[ParserValidationError], ChargeDDetails] = {

    splitDayMonthYear(chargeFields(3)) match {
      case Tuple3(day, month, year) =>
        val fields = Seq(
          Field(ChargeDetailsFieldNames.dateOfEventDay, day, ChargeDetailsFieldNames.dateOfEvent, 3),
          Field(ChargeDetailsFieldNames.dateOfEventMonth, month, ChargeDetailsFieldNames.dateOfEvent, 3),
          Field(ChargeDetailsFieldNames.dateOfEventYear, year, ChargeDetailsFieldNames.dateOfEvent, 3),
          Field(ChargeDetailsFieldNames.taxAt25Percent, chargeFields(4), ChargeDetailsFieldNames.taxAt25Percent, 4),
          Field(ChargeDetailsFieldNames.taxAt55Percent, chargeFields(5), ChargeDetailsFieldNames.taxAt55Percent, 5)
        )
        val chargeDetailsForm: Form[ChargeDDetails] = chargeDetailsFormProvider(
          min = startDate,
          max = Quarters.getQuarter(startDate).endDate,
          minimumChargeValueAllowed = minChargeValueAllowed
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
    combineValidationResults[MemberDetails, ChargeDDetails](
      memberDetailsValidation(index, chargeFields),
      chargeDetailsValidation(startDate, index, chargeFields),
      MemberDetailsPage(index).path,
      Json.toJson(_),
      ChargeDetailsPage(index).path,
      Json.toJson(_)
    )
  }
}
