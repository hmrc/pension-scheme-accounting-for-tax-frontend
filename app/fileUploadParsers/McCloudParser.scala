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

import fileUploadParsers.McCloudParser.countNoOfSchemes
import forms.YesNoFormProvider
import forms.mccloud.{ChargeAmountReportedFormProvider, EnterPstrFormProvider}
import models.ChargeType
import models.Quarters.getQuarter
import pages.IsPublicServicePensionsRemedyPage
import pages.mccloud.{ChargeAmountReportedPage, EnterPstrPage, IsChargeInAdditionReportedPage, TaxQuarterReportedAndPaidPage, WasAnotherPensionSchemePage}
import play.api.i18n.Messages
import play.api.libs.json.{JsBoolean, Json}
import utils.DateHelper.dateFormatterDMYSlashes

import java.time.LocalDate
import scala.util.Try

trait McCloudParser  extends Parser {
  protected val yesNoFormProvider: YesNoFormProvider
  protected val chargeAmountReportedFormProvider: ChargeAmountReportedFormProvider
  protected val enterPstrFormProvider: EnterPstrFormProvider
  protected val chargeType: ChargeType

  protected val fieldNoIsChargeInAdditionReported: Int
  protected val fieldNoWasAnotherPensionScheme: Int
  protected val fieldNoEnterPstr1: Int
  protected val fieldNoTaxQuarterReportedAndPaid1: Int
  protected val fieldNoChargeAmountReported1: Int

  object McCloudFieldNames {
    val formFieldNameForSingleFields = "value"

    val isInAdditionToPrevious: String = "isInAdditionToPrevious"
    val wasPaidByAnotherScheme: String = "wasPaidByAnotherScheme"
    val pstr: String = "pstr"
    val dateReportedAndPaid: String = "dateReportedAndPaid"
    val chargeAmountReported: String = "chargeAmountReported"
  }

  private def validateTaxQuarterReportedAndPaid(index: Int, columns: Seq[String], schemeIndex: => Option[Int], offset: Int): Result = {
    val fieldNo = fieldNoTaxQuarterReportedAndPaid1 + offset
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
            Right(Seq(CommitItem(TaxQuarterReportedAndPaidPage(chargeType, index - 1, schemeIndex).path, Json.toJson(qtr))))

        }
    }
  }

  protected def isChargeInAdditionReportedResult(index: Int,
                                       columns: Seq[String])(implicit messages: Messages): Result = validateField(
    index = index,
    columns = columns,
    page = IsChargeInAdditionReportedPage.apply(chargeType, _: Int),
    formFieldName = McCloudFieldNames.formFieldNameForSingleFields,
    columnName = McCloudFieldNames.isInAdditionToPrevious,
    fieldNo = fieldNoIsChargeInAdditionReported,
    formProvider = yesNoFormProvider(messages("isChargeInAdditionReported.error.required", chargeTypeDescription(chargeType))),
    convertValue = stringToBoolean
  )

  protected def schemeFields(index: Int,
                             columns: Seq[String])(implicit messages: Messages): Seq[Result] = {
    val wasAnotherPensionSchemeResult =
      validateField(
        index = index,
        columns = columns,
        page = WasAnotherPensionSchemePage.apply(chargeType, _: Int),
        formFieldName = McCloudFieldNames.formFieldNameForSingleFields,
        columnName = McCloudFieldNames.wasPaidByAnotherScheme,
        fieldNo = fieldNoWasAnotherPensionScheme,
        formProvider = yesNoFormProvider(messages("wasAnotherPensionScheme.error.required", chargeTypeDescription(chargeType))),
        convertValue = stringToBoolean
      )

    def otherSchemeFields(schemeIndex: Option[Int], offset: Int): Seq[Result] = {
      Seq(
        validateTaxQuarterReportedAndPaid(index, columns, schemeIndex, offset),
        validateField(
          index = index,
          columns = columns,
          page = ChargeAmountReportedPage.apply(chargeType, _: Int, schemeIndex),
          formFieldName = McCloudFieldNames.formFieldNameForSingleFields,
          columnName = McCloudFieldNames.chargeAmountReported,
          fieldNo = fieldNoChargeAmountReported1 + offset,
          formProvider = chargeAmountReportedFormProvider(BigDecimal(0))
        )
      )
    }

    val wasAnotherPensionScheme = getOrElse[Boolean](wasAnotherPensionSchemeResult, false)
    val taxQuarter = if (wasAnotherPensionScheme) {
      val max = countNoOfSchemes(columns, fieldNoEnterPstr1)
      (0 until max).foldLeft[Seq[Result]](Nil) { (acc, schemeIndex) =>
        val offset = (schemeIndex * 3)
        acc ++ Seq(validateField(
          index = index,
          columns = columns,
          page = EnterPstrPage(chargeType, _: Int, schemeIndex),
          formFieldName = McCloudFieldNames.formFieldNameForSingleFields,
          columnName = McCloudFieldNames.pstr,
          fieldNo = fieldNoEnterPstr1 + offset,
          formProvider = enterPstrFormProvider()
        )) ++ otherSchemeFields(Some(schemeIndex), offset)
      }
    } else {
      otherSchemeFields(None, 0)
    }
    Seq(wasAnotherPensionSchemeResult) ++ taxQuarter
  }

  protected def chargeTypeDescription(chargeType: ChargeType)(implicit messages: Messages): String =
    Messages(s"chargeType.description.${chargeType.toString}")

  protected def validateMinimumFields(startDate: LocalDate,
                                      index: Int,
                                      columns: Seq[String])(implicit messages: Messages): Result

  override protected def validateFields(startDate: LocalDate,
                                        index: Int,
                                        columns: Seq[String])(implicit messages: Messages): Result = {
    val minimalFieldsResult = validateMinimumFields(startDate, index, columns)
    val isPublicServicePensionsRemedyResult = Right(Seq(
      CommitItem(IsPublicServicePensionsRemedyPage(chargeType, Some(index - 1)).path, JsBoolean(true))))

    val isInAdditionResult = isChargeInAdditionReportedResult(index, columns)

    val isInAddition = getOrElse[Boolean](isInAdditionResult, false)

    val schemeResults = if (isInAddition) {
      schemeFields(index, columns)
    } else {
      Nil
    }

    val finalResults =
      Seq(minimalFieldsResult, isPublicServicePensionsRemedyResult, isInAdditionResult) ++ schemeResults
    combineResults(finalResults: _*)
  }
}

object McCloudParser {
  def countNoOfSchemes(columns: Seq[String], startFrom: Int): Int = {
    val default: Int => String = _ => ""
    val processedSeq = (startFrom until columns.size by 3).takeWhile { w =>
      columns.applyOrElse(w, default).nonEmpty ||
        columns.applyOrElse(w + 1, default).nonEmpty ||
        columns.applyOrElse(w + 2, default).nonEmpty
    }
    processedSeq.size
  }
}
