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
import forms.MemberDetailsFormProvider
import forms.chargeE.ChargeDetailsFormProvider
import forms.mappings.Constraints
import models.MemberDetails
import models.chargeE.ChargeEDetails
import pages.chargeE.{ChargeDetailsPage, MemberDetailsPage}
import play.api.data.Form
import play.api.data.validation.{Invalid, Valid}
import play.api.i18n.Messages
import play.api.libs.json.Json

import java.time.LocalDate

class AnnualAllowanceParser @Inject()(
                                       memberDetailsFormProvider: MemberDetailsFormProvider,
                                       chargeDetailsFormProvider: ChargeDetailsFormProvider,
                                       config: FrontendAppConfig
                                     ) extends Parser with Constraints {
  //scalastyle:off magic.number
  override protected val totalFields: Int = 7
  //First name,Last name,National Insurance number,Tax year,Charge amount,Date,Payment type mandatory
  private def memberDetailsValidation(index: Int, chargeFields: Array[String]): Either[Seq[ParserValidationError], MemberDetails] = {
    val fields = Seq(
      Field("firstName", firstNameField(chargeFields), "firstName", 0),
      Field("lastName", lastNameField(chargeFields), "lastName", 1),
        Field("nino", ninoField(chargeFields), "nino", 2)
    )
    val memberDetailsForm = memberDetailsFormProvider()
    memberDetailsForm.bind(
      Field.seqToMap(fields)
    ).fold(
      formWithErrors => Left(errorsFromForm(formWithErrors, fields, index)),
      value => Right(value)
    )
  }

  private def chargeDetailsValidation(startDate: LocalDate, index: Int, chargeFields: Array[String]): Either[Seq[ParserValidationError], ChargeEDetails] = {
    val taxYearsErrors = validateTaxYear(startDate, index, chargeFields(3))
    splitDayMonthYear(chargeFields(5)) match {
      case Tuple3(day, month, year) =>
        val fields = Seq(
            Field("chargeAmount", chargeFields(4), "chargeAmount", 4),
            Field("dateNoticeReceived.day", day, "dateNoticeReceived", 5),
            Field("dateNoticeReceived.month", month, "dateNoticeReceived", 5),
            Field("dateNoticeReceived.year", year, "dateNoticeReceived", 5),
            Field("isPaymentMandatory", stringToBoolean(chargeFields(6)), "isPaymentMandatory", 6)

        )
        val chargeDetailsForm: Form[ChargeEDetails] = chargeDetailsFormProvider(
          minimumChargeValueAllowed = minChargeValueAllowed,
          minimumDate = config.earliestDateOfNotice
        )
        chargeDetailsForm.bind(
          Field.seqToMap(fields)
        ).fold(
          formWithErrors => Left(errorsFromForm(formWithErrors, fields, index) ++ taxYearsErrors),
          value =>
            if (taxYearsErrors.nonEmpty) {
              Left(taxYearsErrors)
            }  else {
              Right(value)
            }
        )
    }
  }

  private def validateTaxYear(startDate:LocalDate,  index: Int, fieldValue: String): Seq[ParserValidationError] = {
    year(
      minYear = 2011,
      maxYear = startDate.getYear,
      requiredKey = "annualAllowanceYear.fileUpload.error.required",
      invalidKey = "annualAllowanceYear.fileUpload.error.invalid",
      minKey = "annualAllowanceYear.fileUpload.error.past",
      maxKey = "annualAllowanceYear.fileUpload.error.future"
    )(fieldValue) match {
      case Valid => Nil
      case Invalid(errors) => errors.map(error => ParserValidationError(index, 3, error.message))
    }
  }

  override protected def validateFields(startDate: LocalDate,
                                        index: Int,
                                        chargeFields: Array[String])(implicit messages: Messages): Either[Seq[ParserValidationError], Seq[CommitItem]] = {
    combineValidationResults[MemberDetails, ChargeEDetails](
      memberDetailsValidation(index, chargeFields),
      chargeDetailsValidation(startDate, index, chargeFields),
      MemberDetailsPage(index).path,
      Json.toJson(_),
      ChargeDetailsPage(index).path,
      Json.toJson(_)
    )
  }
}
