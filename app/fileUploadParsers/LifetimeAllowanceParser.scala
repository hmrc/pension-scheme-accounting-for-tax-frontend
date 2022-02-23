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
import forms.chargeD.ChargeDetailsFormProvider
import models.chargeD.ChargeDDetails
import models.{MemberDetails, Quarters}
import pages.chargeD.{ChargeDetailsPage, MemberDetailsPage}
import play.api.data.Form
import play.api.i18n.Messages
import play.api.libs.json.Json

import java.time.LocalDate
import controllers.fileUpload.FileUploadHeaders.LifetimeAllowanceFieldNames._

class LifetimeAllowanceParser @Inject()(
                                         memberDetailsFormProvider: MemberDetailsFormProvider,
                                         chargeDetailsFormProvider: ChargeDetailsFormProvider,
                                         config: FrontendAppConfig
                                       ) extends Parser {
  override protected def validHeader: String = config.validLifeTimeAllowanceHeader

  override protected val totalFields: Int = 6

  private final val FieldNoDateOfEvent = 3
  private final val FieldNoTaxAt25Percent = 4
  private final val FieldNoTaxAt55Percent = 5

  private def chargeDetailsValidation(startDate: LocalDate,
                                      index: Int,
                                      chargeFields: Seq[String])(implicit messages: Messages): Either[Seq[ParserValidationError], ChargeDDetails] = {

    val parsedDate = splitDayMonthYear(chargeFields(FieldNoDateOfEvent))
    val fields = Seq(
      Field(dateOfEventDay, parsedDate.day, dateOfEvent, FieldNoDateOfEvent),
      Field(dateOfEventMonth, parsedDate.month, dateOfEvent, FieldNoDateOfEvent),
      Field(dateOfEventYear, parsedDate.year, dateOfEvent, FieldNoDateOfEvent),
      Field(taxAt25Percent, chargeFields(FieldNoTaxAt25Percent), taxAt25Percent, FieldNoTaxAt25Percent),
      Field(taxAt55Percent, chargeFields(FieldNoTaxAt55Percent), taxAt55Percent, FieldNoTaxAt55Percent)
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

  override protected def validateFields(startDate: LocalDate,
                                        index: Int,
                                        chargeFields: Seq[String])(implicit messages: Messages): Either[Seq[ParserValidationError], Seq[CommitItem]] = {
    combineValidationResults[MemberDetails, ChargeDDetails](
      memberDetailsValidation(index, chargeFields, memberDetailsFormProvider()),
      chargeDetailsValidation(startDate, index, chargeFields),
      MemberDetailsPage(index - 1).path,
      Json.toJson(_),
      ChargeDetailsPage(index - 1).path,
      Json.toJson(_)
    )
  }
}