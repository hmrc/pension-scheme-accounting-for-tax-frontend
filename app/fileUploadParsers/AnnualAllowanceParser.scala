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

import cats.data.Validated
import cats.data.Validated.{Invalid, Valid}
import cats.implicits.toFoldableOps
import config.FrontendAppConfig
import controllers.fileUpload.FileUploadHeaders.AnnualAllowanceFieldNames
import fileUploadParsers.Parser.Result
import forms.MemberDetailsFormProvider
import forms.chargeE.ChargeDetailsFormProvider
import forms.mappings.Constraints
import models.chargeE.ChargeEDetails
import models.{ChargeType, CommonQuarters, MemberDetails}
import pages.chargeE.{AnnualAllowanceYearPage, ChargeDetailsPage, MemberDetailsPage}
import play.api.data.Form
import play.api.i18n.Messages
import play.api.libs.json.{JsString, Json}

import java.time.LocalDate

trait AnnualAllowanceParser extends Parser with Constraints with CommonQuarters {
  override val chargeType: ChargeType = ChargeType.ChargeTypeAnnualAllowance
  protected val memberDetailsFormProvider: MemberDetailsFormProvider

  protected val chargeDetailsFormProvider: ChargeDetailsFormProvider

  protected val config: FrontendAppConfig

  private val fieldNoTaxYear = 3
  private val fieldNoChargeAmount = 4
  private val fieldNoDateNoticeReceived = 5
  private val fieldNoIsPaymentMandatory = 6

  private final object TaxYearErrorKeys {
    val requiredKey = "annualAllowanceYear.fileUpload.error.required"
    val invalidKey = "annualAllowanceYear.fileUpload.error.invalid"
    val minKey = "annualAllowanceYear.fileUpload.error.past"
    val maxKey = "annualAllowanceYear.fileUpload.error.future"
  }

  private def processChargeDetailsValidation(index: Int,
                                             startDate: LocalDate,
                                             chargeFields: Seq[String],
                                             parsedDate: ParsedDate): Validated[Seq[ParserValidationError], ChargeEDetails] = {
    val fields = Seq(
      Field(AnnualAllowanceFieldNames.chargeAmount, chargeFields(fieldNoChargeAmount), AnnualAllowanceFieldNames.chargeAmount, fieldNoChargeAmount),
      Field(AnnualAllowanceFieldNames.dateNoticeReceivedDay, parsedDate.day,
        AnnualAllowanceFieldNames.dateNoticeReceived, fieldNoDateNoticeReceived, Some(AnnualAllowanceFieldNames.dateNoticeReceived)),
      Field(AnnualAllowanceFieldNames.dateNoticeReceivedMonth, parsedDate.month,
        AnnualAllowanceFieldNames.dateNoticeReceived, fieldNoDateNoticeReceived, Some(AnnualAllowanceFieldNames.dateNoticeReceived)),
      Field(AnnualAllowanceFieldNames.dateNoticeReceivedYear, parsedDate.year,
        AnnualAllowanceFieldNames.dateNoticeReceived, fieldNoDateNoticeReceived, Some(AnnualAllowanceFieldNames.dateNoticeReceived)),
      Field(AnnualAllowanceFieldNames.isPaymentMandatory, stringToBoolean(chargeFields(fieldNoIsPaymentMandatory)),
        AnnualAllowanceFieldNames.isPaymentMandatory, fieldNoIsPaymentMandatory)

    )

    val chargeDetailsForm: Form[ChargeEDetails] = chargeDetailsFormProvider(
      minimumChargeValueAllowed = minChargeValueAllowed,
      minimumDate = config.earliestDateOfNotice,
      maximumDate = getQuarter(startDate).endDate
    )

    chargeDetailsForm.bind(
      Field.seqToMap(fields)
    ).fold(
      formWithErrors => errors(index, formWithErrors, fields),
      value => Valid(value)
    )
  }


  private def errors[A](fieldIndex: Int,
                        formWithErrors: Form[A],
                        fields: Seq[Field]): Validated[Seq[ParserValidationError], A] =
    Invalid(errorsFromForm(formWithErrors, fields, fieldIndex))

  private def chargeDetailsValidation(startDate: LocalDate, index: Int, chargeFields: Seq[String]): Validated[Seq[ParserValidationError], ChargeEDetails] = {
    processChargeDetailsValidation(
      index,
      startDate,
      chargeFields,
      splitDayMonthYear(chargeFields(fieldNoDateNoticeReceived))
    )
  }

  private def validateTaxYear(startDate: LocalDate, index: Int,
                              columns: Seq[String], fieldValue: String): Validated[Seq[ParserValidationError], String] = {
    val minYearDefaultValue = 2011
    year(
      minYear = minYearDefaultValue,
      maxYear = startDate.getYear,
      requiredKey = TaxYearErrorKeys.requiredKey,
      invalidKey = TaxYearErrorKeys.invalidKey,
      minKey = TaxYearErrorKeys.minKey,
      maxKey = TaxYearErrorKeys.maxKey
    ).apply(fieldValue) match {
      case play.api.data.validation.Valid =>
        Valid(this.fieldValue(columns, fieldNoTaxYear))
      case play.api.data.validation.Invalid(errors) => Invalid(errors.map(
        error => ParserValidationError(index, fieldNoTaxYear, error.message, AnnualAllowanceFieldNames.taxYear)
      ))
    }
  }

  protected def validateMinimumFields(startDate: LocalDate,
                                      index: Int,
                                      columns: Seq[String])(implicit messages: Messages): Result = {
    val a = resultFromFormValidationResult[MemberDetails](
      memberDetailsValidation(index, columns, memberDetailsFormProvider()),
      createCommitItem(index, MemberDetailsPage.apply)
    )
    val b = resultFromFormValidationResult[ChargeEDetails](
      chargeDetailsValidation(startDate, index, columns),
      createCommitItem(index, ChargeDetailsPage.apply)
    )

    val createCommitItemForYear: String => CommitItem =
      year => CommitItem(AnnualAllowanceYearPage(index - 1).path, Json.toJson(year.take(YearLength)))

    val c = resultFromFormValidationResult[String](
      validateTaxYear(startDate, index, columns, fieldValue(columns, fieldNoTaxYear)),
      createCommitItemForYear
    )
    Seq(a, b, c).combineAll
  }
}
