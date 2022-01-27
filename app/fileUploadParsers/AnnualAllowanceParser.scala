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

  private def memberDetailsValidation(index: Int, chargeFields: Array[String]): Either[ParserValidationErrors, MemberDetails] = {
    val memberDetailsForm = memberDetailsFormProvider()
    memberDetailsForm.bind(
      Map(
        "firstName" -> firstNameField(chargeFields),
        "lastName" -> lastNameField(chargeFields),
        "nino" -> ninoField(chargeFields)
      )
    ).fold(
      formWithErrors => Left(ParserValidationErrors(index, formWithErrors.errors.map(_.message))),
      value => Right(value)
    )
  }

  private def chargeDetailsValidation(startDate: LocalDate, index: Int, chargeFields: Array[String]): Either[ParserValidationErrors, ChargeEDetails] = {
    val validationResult = year(
      minYear = 2011,
      maxYear = startDate.getYear,
      requiredKey = "annualAllowanceYear.fileUpload.error.required",
      invalidKey = "annualAllowanceYear.fileUpload.error.invalid",
      minKey = "annualAllowanceYear.fileUpload.error.past",
      maxKey = "annualAllowanceYear.fileUpload.error.future"
    ).apply(chargeFields(3))

    val taxYearErrors = validationResult match {
      case Valid => Nil
      case Invalid(errors) => errors.map(_.message)
    }

    splitDayMonthYear(chargeFields(5)) match {
      case Tuple3(day, month, year) =>
        val chargeDetailsForm: Form[ChargeEDetails] = chargeDetailsFormProvider(
          minimumChargeValueAllowed = BigDecimal("0.01"),
          minimumDate = config.earliestDateOfNotice
        )
        chargeDetailsForm.bind(
          Map(
            "chargeAmount" -> chargeFields(4),
            "dateNoticeReceived.day" -> day,
            "dateNoticeReceived.month" -> month,
            "dateNoticeReceived.year" -> year,
            "isPaymentMandatory" -> stringToBoolean(chargeFields(6))
          )
        ).fold(
          formWithErrors => Left(ParserValidationErrors(index, formWithErrors.errors.map(_.message) ++ taxYearErrors)),
          value =>
            if (taxYearErrors.nonEmpty) {
              Left(ParserValidationErrors(index, taxYearErrors))
            }  else {
              Right(value)
            }
        )
    }
  }

  override protected def validateFields(startDate: LocalDate,
                                        index: Int,
                                        chargeFields: Array[String])(implicit messages: Messages): Either[ParserValidationErrors, Seq[CommitItem]] = {
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
