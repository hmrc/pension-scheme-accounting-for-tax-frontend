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

import config.FrontendAppConfig
import controllers.fileUpload.FileUploadHeaders.LifetimeAllowanceFieldNames
import controllers.fileUpload.FileUploadHeaders.LifetimeAllowanceFieldNames._
import fileUploadParsers.Parser.Result
import forms.MemberDetailsFormProvider
import forms.chargeD.ChargeDetailsFormProvider
import models.chargeD.ChargeDDetails
import models.{MemberDetails, Quarters}
import pages.chargeD.{ChargeDetailsPage, MemberDetailsPage}
import play.api.data.Form
import play.api.i18n.Messages
import fileUploadParsers.Parser.resultMonoid
import java.time.LocalDate

trait LifetimeAllowanceParser extends Parser {

  protected val memberDetailsFormProvider: MemberDetailsFormProvider

  protected val chargeDetailsFormProvider: ChargeDetailsFormProvider

  protected val config: FrontendAppConfig

  private val fieldNoDateOfEvent = 3
  private val fieldNoTaxAt25Percent = 4
  private val fieldNoTaxAt55Percent = 5

  private def chargeDetailsValidation(startDate: LocalDate,
                                      index: Int,
                                      chargeFields: Seq[String])(implicit messages: Messages): Either[Seq[ParserValidationError], ChargeDDetails] = {

    val parsedDate = splitDayMonthYear(chargeFields(fieldNoDateOfEvent))
    val fields = Seq(
      Field(dateOfEventDay, parsedDate.day, dateOfEvent, fieldNoDateOfEvent, Some(LifetimeAllowanceFieldNames.dateOfEvent)),
      Field(dateOfEventMonth, parsedDate.month, dateOfEvent, fieldNoDateOfEvent, Some(LifetimeAllowanceFieldNames.dateOfEvent)),
      Field(dateOfEventYear, parsedDate.year, dateOfEvent, fieldNoDateOfEvent, Some(LifetimeAllowanceFieldNames.dateOfEvent)),
      Field(taxAt25Percent, chargeFields(fieldNoTaxAt25Percent), taxAt25Percent, fieldNoTaxAt25Percent),
      Field(taxAt55Percent, chargeFields(fieldNoTaxAt55Percent), taxAt55Percent, fieldNoTaxAt55Percent)
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
      value => {
        val updatedChargeDetails: ChargeDDetails = value.copy(
          taxAt25Percent = Option(value.taxAt25Percent.getOrElse(BigDecimal(0.00))),
          taxAt55Percent = Option(value.taxAt55Percent.getOrElse(BigDecimal(0.00)))
        )
        Right(updatedChargeDetails)
      }
    )

  }

  protected def validateMinimumFields(startDate: LocalDate,
                                      index: Int,
                                      columns: Seq[String])(implicit messages: Messages): Result = {
    val a = resultFromFormValidationResult[MemberDetails](
      memberDetailsValidation(index, columns, memberDetailsFormProvider()), createCommitItem(index, MemberDetailsPage.apply)
    )
    val b = resultFromFormValidationResult[ChargeDDetails](
      chargeDetailsValidation(startDate, index, columns), createCommitItem(index, ChargeDetailsPage.apply)
    )
    combineResults(a, b)
  }
}
