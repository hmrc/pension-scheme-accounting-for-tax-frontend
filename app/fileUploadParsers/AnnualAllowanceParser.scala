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

  override protected def validHeader: String = config.validAnnualAllowanceHeader

  private final object ChargeDetailsFieldNames {
    val chargeAmount: String = "chargeAmount"
    val dateNoticeReceivedDay: String = "dateNoticeReceived.day"
    val dateNoticeReceivedMonth: String = "dateNoticeReceived.month"
    val dateNoticeReceivedYear: String = "dateNoticeReceived.year"
    val dateNoticeReceived: String = "dateNoticeReceived"
    val isPaymentMandatory = "isPaymentMandatory"
  }

  private final object TaxYearErrorKeys {
    val requiredKey = "annualAllowanceYear.fileUpload.error.required"
    val invalidKey = "annualAllowanceYear.fileUpload.error.invalid"
    val minKey = "annualAllowanceYear.fileUpload.error.past"
    val maxKey = "annualAllowanceYear.fileUpload.error.future"
  }

  private def chargeDetailsValidation(startDate: LocalDate, index: Int, chargeFields: Array[String]): Either[Seq[ParserValidationError], ChargeEDetails] = {
    val taxYearsErrors = validateTaxYear(startDate, index, chargeFields(3))
    splitDayMonthYear(chargeFields(5)) match {
      case Tuple3(day, month, year) =>
        val fields = Seq(
          Field(ChargeDetailsFieldNames.chargeAmount, chargeFields(4), ChargeDetailsFieldNames.chargeAmount, 4),
          Field(ChargeDetailsFieldNames.dateNoticeReceivedDay, day, ChargeDetailsFieldNames.dateNoticeReceived, 5),
          Field(ChargeDetailsFieldNames.dateNoticeReceivedMonth, month, ChargeDetailsFieldNames.dateNoticeReceived, 5),
          Field(ChargeDetailsFieldNames.dateNoticeReceivedYear, year, ChargeDetailsFieldNames.dateNoticeReceived, 5),
          Field(ChargeDetailsFieldNames.isPaymentMandatory, stringToBoolean(chargeFields(6)),
            ChargeDetailsFieldNames.isPaymentMandatory, 6)

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
            } else {
              Right(value)
            }
        )
    }
  }

  private def validateTaxYear(startDate: LocalDate, index: Int, fieldValue: String): Seq[ParserValidationError] = {
    year(
      minYear = 2011,
      maxYear = startDate.getYear,
      requiredKey = TaxYearErrorKeys.requiredKey,
      invalidKey = TaxYearErrorKeys.invalidKey,
      minKey = TaxYearErrorKeys.minKey,
      maxKey = TaxYearErrorKeys.maxKey
    )(fieldValue) match {
      case Valid => Nil
      case Invalid(errors) => errors.map(error => ParserValidationError(index, 3, error.message))
    }
  }

  override protected def validateFields(startDate: LocalDate,
                                        index: Int,
                                        chargeFields: Array[String])(implicit messages: Messages): Either[Seq[ParserValidationError], Seq[CommitItem]] = {
    combineValidationResults[MemberDetails, ChargeEDetails](
      memberDetailsValidation(index, chargeFields, memberDetailsFormProvider()),
      chargeDetailsValidation(startDate, index, chargeFields),
      MemberDetailsPage(index - 1).path,
      Json.toJson(_),
      ChargeDetailsPage(index - 1).path,
      Json.toJson(_)
    )
  }
}
