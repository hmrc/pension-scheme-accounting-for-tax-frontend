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
import models.{CommonQuarters, MemberDetails}
import models.chargeE.ChargeEDetails
import pages.chargeE.{AnnualAllowanceYearPage, ChargeDetailsPage, MemberDetailsPage}
import play.api.data.Form
import play.api.data.validation.{Invalid, Valid}
import play.api.i18n.Messages
import play.api.libs.json.Json

import java.time.LocalDate
import controllers.fileUpload.FileUploadHeaders.AnnualAllowanceFieldNames

class AnnualAllowanceParser @Inject()(
                                       memberDetailsFormProvider: MemberDetailsFormProvider,
                                       chargeDetailsFormProvider: ChargeDetailsFormProvider,
                                       config: FrontendAppConfig
                                     ) extends Parser with Constraints with CommonQuarters {
  override protected val totalFields: Int = 7

  override protected def validHeader: String = config.validAnnualAllowanceHeader

  private final val FieldNoTaxYear = 3
  private final val FieldNoChargeAmount = 4
  private final val FieldNoDateNoticeReceived = 5
  private final val FieldNoIsPaymentMandatory = 6

  private final object TaxYearErrorKeys {
    val requiredKey = "annualAllowanceYear.fileUpload.error.required"
    val invalidKey = "annualAllowanceYear.fileUpload.error.invalid"
    val minKey = "annualAllowanceYear.fileUpload.error.past"
    val maxKey = "annualAllowanceYear.fileUpload.error.future"
  }

  private def processChargeDetailsValidation(index: Int,
                                             startDate: LocalDate,
                                             chargeFields: Seq[String],
                                             parsedDate: ParsedDate,
                                             taxYearsErrors: Seq[ParserValidationError]): Either[Seq[ParserValidationError], ChargeEDetails] = {
    val fields = Seq(
      Field(AnnualAllowanceFieldNames.chargeAmount, chargeFields(FieldNoChargeAmount), AnnualAllowanceFieldNames.chargeAmount, FieldNoChargeAmount),
      Field(AnnualAllowanceFieldNames.dateNoticeReceivedDay, parsedDate.day, AnnualAllowanceFieldNames.dateNoticeReceived, FieldNoDateNoticeReceived),
      Field(AnnualAllowanceFieldNames.dateNoticeReceivedMonth, parsedDate.month, AnnualAllowanceFieldNames.dateNoticeReceived, FieldNoDateNoticeReceived),
      Field(AnnualAllowanceFieldNames.dateNoticeReceivedYear, parsedDate.year, AnnualAllowanceFieldNames.dateNoticeReceived, FieldNoDateNoticeReceived),
      Field(AnnualAllowanceFieldNames.isPaymentMandatory, stringToBoolean(chargeFields(FieldNoIsPaymentMandatory)),
        AnnualAllowanceFieldNames.isPaymentMandatory, FieldNoIsPaymentMandatory)

    )

    val chargeDetailsForm: Form[ChargeEDetails] = chargeDetailsFormProvider(
      minimumChargeValueAllowed = minChargeValueAllowed,
      minimumDate = config.earliestDateOfNotice,
      maximumDate = getQuarter(startDate).endDate
    )
    chargeDetailsForm.bind(
      Field.seqToMap(fields)
    ).fold(
      formWithErrors => errors(index, formWithErrors, fields, taxYearsErrors),
      value => checkIfTaxYearHasAnErrorsOrReturnChargeEDetails(value, taxYearsErrors)
    )
  }


  private def errors[A](fieldIndex: Int, formWithErrors: Form[A], fields: Seq[Field], taxYearsErrors: Seq[ParserValidationError]) =
    Left(errorsFromForm(formWithErrors, fields, fieldIndex) ++ taxYearsErrors)

  private def checkIfTaxYearHasAnErrorsOrReturnChargeEDetails(chargeEDetails: ChargeEDetails, taxYearsErrors: Seq[ParserValidationError])
                                         :Either[Seq[ParserValidationError], ChargeEDetails] =
    if (taxYearsErrors.nonEmpty) {
      Left(taxYearsErrors)
    } else {
      Right(chargeEDetails)
    }
  private def chargeDetailsValidation(startDate: LocalDate, index: Int, chargeFields: Seq[String]): Either[Seq[ParserValidationError], ChargeEDetails] =
    processChargeDetailsValidation(
      index,
      startDate,
      chargeFields,
      splitDayMonthYear(chargeFields(FieldNoDateNoticeReceived)),
      validateTaxYear(startDate, index, chargeFields(3))
    )

  private def validateTaxYear(startDate: LocalDate, index: Int, fieldValue: String): Seq[ParserValidationError] = {
    val minYearDefaultValue = 2011
    year(
      minYear = minYearDefaultValue,
      maxYear = startDate.getYear,
      requiredKey = TaxYearErrorKeys.requiredKey,
      invalidKey = TaxYearErrorKeys.invalidKey,
      minKey = TaxYearErrorKeys.minKey,
      maxKey = TaxYearErrorKeys.maxKey
    ).apply(fieldValue) match {
      case Valid => Nil
      case Invalid(errors) => errors.map(error => ParserValidationError(index, 3, error.message, AnnualAllowanceFieldNames.taxYear))
    }
  }

  override protected def validateFields(startDate: LocalDate,
                                        index: Int,
                                        chargeFields: Seq[String])(implicit messages: Messages): Either[Seq[ParserValidationError], Seq[CommitItem]] = {
    val validationResults = combineValidationResults[MemberDetails, ChargeEDetails](
      memberDetailsValidation(index, chargeFields, memberDetailsFormProvider()),
      chargeDetailsValidation(startDate, index, chargeFields),
      MemberDetailsPage(index - 1).path,
      Json.toJson(_),
      ChargeDetailsPage(index - 1).path,
      Json.toJson(_)
    )

    addToValidationResults[String](Right(chargeFields(FieldNoTaxYear)), validationResults, AnnualAllowanceYearPage(index - 1).path, Json.toJson(_))
  }
}