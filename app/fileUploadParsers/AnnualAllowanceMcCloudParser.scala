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
import forms.mccloud.{ChargeAmountReportedFormProvider, EnterPstrFormProvider}
import forms.{MemberDetailsFormProvider, YesNoFormProvider}
import models.{ChargeType, CommonQuarters}
import pages.IsPublicServicePensionsRemedyPage
import pages.mccloud._
import play.api.i18n.Messages
import play.api.libs.json.{JsBoolean, Json}
import utils.DateHelper.dateFormatterDMYSlashes

import java.time.LocalDate
import scala.util.Try

class AnnualAllowanceMcCloudParser @Inject()(
                                              override val memberDetailsFormProvider: MemberDetailsFormProvider,
                                              override val chargeDetailsFormProvider: ChargeDetailsFormProvider,
                                              override val config: FrontendAppConfig,
                                              val yesNoFormProvider: YesNoFormProvider,
                                              enterPstrFormProvider: EnterPstrFormProvider,
                                              chargeAmountReportedFormProvider: ChargeAmountReportedFormProvider
                                            ) extends AnnualAllowanceParser with Constraints with CommonQuarters with McCloudParser {
  override protected def validHeader: String = config.validAnnualAllowanceMcCloudHeader

  protected final val FieldNoIsChargeInAdditionReported: Int = 7
  protected final val FieldNoWasAnotherPensionScheme: Int = 8
  protected final val FieldNoEnterPstr1: Int = 9
  protected final val FieldNoTaxQuarterReportedAndPaid1: Int = 10
  protected final val FieldNoChargeAmountReported1: Int = 11

  object McCloudFieldNames {
    val formFieldNameForSingleFields = "value"

    val isInAdditionToPrevious: String = "isInAdditionToPrevious"
    val wasPaidByAnotherScheme: String = "wasPaidByAnotherScheme"
    val pstr: String = "pstr"
    val dateReportedAndPaid: String = "dateReportedAndPaid"
    val chargeAmountReported: String = "chargeAmountReported"
  }

  private def validateTaxQuarterReportedAndPaid(index: Int, columns: Seq[String], schemeIndex: => Option[Int], offset: Int): Result = {
    val fieldNo = FieldNoTaxQuarterReportedAndPaid1 + offset
    fieldValue(columns, fieldNo) match {
      case a if a.isEmpty =>
        Left(Seq(ParserValidationError(index, fieldNo,
          "taxQuarterReportedAndPaid.error.required", McCloudFieldNames.dateReportedAndPaid)))
      case a =>
        Try(LocalDate.parse(a, dateFormatterDMYSlashes)).toOption match {
          case None => // TODO: Need proper date validation and content - future ticket for this
            Left(Seq(ParserValidationError(index, fieldNo,
              "Invalid tax quarter reported and paid", McCloudFieldNames.dateReportedAndPaid)))
          case Some(ld) =>
            val qtr = getQuarter(ld)
            Right(Seq(CommitItem(TaxQuarterReportedAndPaidPage(ChargeType.ChargeTypeAnnualAllowance, index - 1, schemeIndex).path, Json.toJson(qtr))))

        }
    }
  }
// TODO: Take out is mccloud remedy boolean as this will be in ua
  private def otherMcCloud(index: Int,
                           columns: Seq[String])(implicit messages: Messages): Seq[Result] = {
    val wasAnotherPensionSchemeResult =
      validateField(
        index = index,
        columns = columns,
        page = WasAnotherPensionSchemePage.apply(ChargeType.ChargeTypeAnnualAllowance, _: Int),
        formFieldName = McCloudFieldNames.formFieldNameForSingleFields,
        columnName = McCloudFieldNames.wasPaidByAnotherScheme,
        fieldNo = FieldNoWasAnotherPensionScheme,
        formProvider = yesNoFormProvider(messages("wasAnotherPensionScheme.error.required", chargeTypeDescription(ChargeType.ChargeTypeAnnualAllowance))),
        convertValue = stringToBoolean
      )

    def schemeFields(schemeIndex: Option[Int], offset: Int): Seq[Result] = {
      Seq(
        validateTaxQuarterReportedAndPaid(index, columns, schemeIndex, offset),
        validateField(
          index = index,
          columns = columns,
          page = ChargeAmountReportedPage.apply(ChargeType.ChargeTypeAnnualAllowance, _: Int, schemeIndex),
          formFieldName = McCloudFieldNames.formFieldNameForSingleFields,
          columnName = McCloudFieldNames.chargeAmountReported,
          fieldNo = FieldNoChargeAmountReported1 + offset,
          formProvider = chargeAmountReportedFormProvider(BigDecimal(0))
        )
      )
    }

    val wasAnotherPensionScheme = getOrElse[Boolean](wasAnotherPensionSchemeResult, false)
    val taxQuarter = if (wasAnotherPensionScheme) {
      val max = countNoOfSchemes(columns, FieldNoEnterPstr1)
      (0 until max).foldLeft[Seq[Result]](Nil){ (acc, schemeIndex) =>
        val offset = (schemeIndex * 3)
        acc ++ Seq(validateField(
          index = index,
          columns = columns,
          page = EnterPstrPage(ChargeType.ChargeTypeAnnualAllowance, _: Int, schemeIndex),
          formFieldName = McCloudFieldNames.formFieldNameForSingleFields,
          columnName = McCloudFieldNames.pstr,
          fieldNo = FieldNoEnterPstr1 + offset,
          formProvider = enterPstrFormProvider()
        )) ++ schemeFields(Some(schemeIndex), offset)
      }
    } else {
      schemeFields(None, 0)
    }

    Seq(wasAnotherPensionSchemeResult) ++ taxQuarter
  }


  override protected def validateFields(startDate: LocalDate,
                                        index: Int,
                                        columns: Seq[String])(implicit messages: Messages): Result = {
    val minimalFieldsResult = validateMinimumFields(startDate, index, columns)
    val isPublicServicePensionsRemedyResult = Right(Seq(
      CommitItem(IsPublicServicePensionsRemedyPage(ChargeType.ChargeTypeAnnualAllowance, Some(index - 1)).path, JsBoolean(true))))
    val isChargeInAdditionReportedResult = validateField(
      index = index,
      columns = columns,
      page = IsChargeInAdditionReportedPage.apply(ChargeType.ChargeTypeAnnualAllowance, _: Int),
      formFieldName = McCloudFieldNames.formFieldNameForSingleFields,
      columnName = McCloudFieldNames.isInAdditionToPrevious,
      fieldNo = FieldNoIsChargeInAdditionReported,
      formProvider = yesNoFormProvider(messages("isChargeInAdditionReported.error.required", chargeTypeDescription(ChargeType.ChargeTypeAnnualAllowance))),
      convertValue = stringToBoolean
    )

    val isMcCloud =
      getOrElse[Boolean](isPublicServicePensionsRemedyResult, false) &&
        getOrElse[Boolean](isChargeInAdditionReportedResult, false)

    val otherResults = if (isMcCloud) {
      otherMcCloud(index, columns)
    } else {
      Nil
    }

    val finalResults = Seq(
      minimalFieldsResult, isPublicServicePensionsRemedyResult, isChargeInAdditionReportedResult
    ) ++ otherResults
    combineResults(finalResults: _*)
  }
}
