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

import com.google.inject.Inject
import config.FrontendAppConfig
import forms.chargeE.ChargeDetailsFormProvider
import forms.mappings.Constraints
import forms.{MemberDetailsFormProvider, YesNoFormProvider}
import models.{ChargeType, CommonQuarters}
import pages.mccloud.IsChargeInAdditionReportedPage
import play.api.data.Form
import play.api.i18n.Messages

import java.time.LocalDate

class AnnualAllowanceMcCloudParser @Inject()(
                                              override val memberDetailsFormProvider: MemberDetailsFormProvider,
                                              override val chargeDetailsFormProvider: ChargeDetailsFormProvider,
                                              override val config: FrontendAppConfig,
                                              val yesNoFormProvider: YesNoFormProvider
                                            ) extends AnnualAllowanceParser with Constraints with CommonQuarters {
  override protected val totalFields: Int = 7 // TODO This will vary

  override protected def validHeader: String = config.validAnnualAllowanceMcCloudHeader

  protected final val FieldNoIsChargeInAdditionReported: Int = 7

  object McCloudFieldNames {
    val isChargeInAdditionReported = "value"
  }

  private def chargeTypeDescription(implicit messages: Messages) =
    Messages(s"chargeType.description.${ChargeType.ChargeTypeAnnualAllowance.toString}")

  private def isChargeInAdditionReportedValidation(index: Int, columns: Seq[String],
                                                   yesNoForm: Form[Boolean]): Either[Seq[ParserValidationError], Boolean] = {
    val fields = Seq(
      Field(McCloudFieldNames.isChargeInAdditionReported, stringToBoolean(fieldValue(columns, FieldNoIsChargeInAdditionReported)),
        McCloudFieldNames.isChargeInAdditionReported, FieldNoIsChargeInAdditionReported)
    )
    val toMap = Field.seqToMap(fields)

    val bind = yesNoForm.bind(toMap)
    bind.fold(
      formWithErrors => Left(errorsFromForm(formWithErrors, fields, index)),
      value => Right(value)
    )
  }

  override protected def validateFields(startDate: LocalDate,
                                        index: Int,
                                        columns: Seq[String])(implicit messages: Messages): Either[Seq[ParserValidationError], Seq[CommitItem]] = {
    val minimalFieldsResult: Either[Seq[ParserValidationError], Seq[CommitItem]] = validateMinimumFields(startDate, index, columns)
    val additionalResult: Result[Boolean] =
      Result(
        isChargeInAdditionReportedValidation(index, columns,
          yesNoFormProvider(messages("isChargeInAdditionReported.error.required", chargeTypeDescription))),
        createCommitItem(index, IsChargeInAdditionReportedPage.apply(ChargeType.ChargeTypeAnnualAllowance, _: Int))
      )
    addToValidationResults(additionalResult, minimalFieldsResult)
  }
}
