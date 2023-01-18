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
import pages.IsPublicServicePensionsRemedyPage
import pages.mccloud.IsChargeInAdditionReportedPage
import play.api.i18n.Messages
import play.api.libs.json.JsBoolean
import queries.Gettable

import java.time.LocalDate

class AnnualAllowanceMcCloudParser @Inject()(
                                              override val memberDetailsFormProvider: MemberDetailsFormProvider,
                                              override val chargeDetailsFormProvider: ChargeDetailsFormProvider,
                                              override val config: FrontendAppConfig,
                                              val yesNoFormProvider: YesNoFormProvider
                                            ) extends AnnualAllowanceParser with Constraints with CommonQuarters {
  override protected val totalFields: Int = 7 // TODO This can be removed

  override protected def validHeader: String = config.validAnnualAllowanceMcCloudHeader

  protected final val FieldNoIsChargeInAdditionReported: Int = 7

  object McCloudFieldNames {
    val isChargeInAdditionReported = "value"
  }

  private def chargeTypeDescription(chargeType: ChargeType)(implicit messages: Messages) =
    Messages(s"chargeType.description.${chargeType.toString}")

  private def validateBooleanField(
                                    index: Int,
                                    columns: Seq[String],
                                    page: Int => Gettable[_],
                                    errorMessage: String,
                                    formFieldName: String,
                                    fieldNo: Int
                                  )(implicit messages: Messages): Either[Seq[ParserValidationError], Seq[CommitItem]] = {

    def validation(index: Int, columns: Seq[String],
                   fieldName: String, fieldNo: Int, formMessageKey: String)(implicit messages: Messages)
    : Either[Seq[ParserValidationError], Boolean] = {
      val form = yesNoFormProvider(messages(formMessageKey, chargeTypeDescription(ChargeType.ChargeTypeAnnualAllowance)))
      val fields = Seq(Field(fieldName, stringToBoolean(fieldValue(columns, fieldNo)), fieldName, fieldNo))
      val toMap = Field.seqToMap(fields)
      val bind = form.bind(toMap)
      bind.fold(
        formWithErrors => Left(errorsFromForm(formWithErrors, fields, index)),
        value => Right(value)
      )
    }

    val formValidationResult = validation(index, columns, formFieldName, fieldNo, errorMessage)
    resultFromFormValidationResult[Boolean](
      formValidationResult,
      createCommitItem(index, page)
    )
  }

  override protected def validateFields(startDate: LocalDate,
                                        index: Int,
                                        columns: Seq[String])(implicit messages: Messages): Either[Seq[ParserValidationError], Seq[CommitItem]] = {
    val minimalFieldsResult: Either[Seq[ParserValidationError], Seq[CommitItem]] = validateMinimumFields(startDate, index, columns)
    val isPublicServicePensionsRemedyResult: Either[Seq[ParserValidationError], Seq[CommitItem]] =
      Right(Seq(CommitItem(IsPublicServicePensionsRemedyPage(ChargeType.ChargeTypeAnnualAllowance, Some(index - 1)).path, JsBoolean(true))))
    val isChargeInAdditionReportedResult: Either[Seq[ParserValidationError], Seq[CommitItem]] =
      validateBooleanField(
        index, columns, IsChargeInAdditionReportedPage.apply(ChargeType.ChargeTypeAnnualAllowance, _: Int),
        "isChargeInAdditionReported.error.required", McCloudFieldNames.isChargeInAdditionReported, FieldNoIsChargeInAdditionReported
      )

    combineResults(minimalFieldsResult, isPublicServicePensionsRemedyResult, isChargeInAdditionReportedResult)
  }
}
