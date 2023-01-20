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
    val allSingleFields = "value"
  }

  private def validateTaxQuarterReportedAndPaid(index: Int, columns: Seq[String], schemeIndex: => Option[Int], offset: Int): Result = {
    val fieldNo = FieldNoTaxQuarterReportedAndPaid1 + offset
    fieldValue(columns, fieldNo) match {
      case a if a.isEmpty =>
        Left(Seq(ParserValidationError(index, fieldNo,
          "taxQuarterReportedAndPaid.error.required", McCloudFieldNames.allSingleFields)))
      case a =>
        Try(LocalDate.parse(a, dateFormatterDMYSlashes)).toOption match {
          case None => // TODO: Need proper date validation and content - future ticket for this
            Left(Seq(ParserValidationError(index, fieldNo,
              "Invalid tax quarter reported and paid", McCloudFieldNames.allSingleFields)))
          case Some(ld) =>
            val qtr = getQuarter(ld)
            Right(Seq(CommitItem(TaxQuarterReportedAndPaidPage(ChargeType.ChargeTypeAnnualAllowance, index - 1, schemeIndex).path, Json.toJson(qtr))))

        }
    }
  }

  private def otherMcCloud(index: Int,
                           columns: Seq[String])(implicit messages: Messages): Seq[Result] = {
    val wasAnotherPensionSchemeResult =
      validateField(
        index, columns, WasAnotherPensionSchemePage.apply(ChargeType.ChargeTypeAnnualAllowance, _: Int),
        McCloudFieldNames.allSingleFields, FieldNoWasAnotherPensionScheme,
        yesNoFormProvider(messages("wasAnotherPensionScheme.error.required", chargeTypeDescription(ChargeType.ChargeTypeAnnualAllowance))),
        stringToBoolean
      )

    def schemeFields(schemeIndex: Option[Int], offset: Int): Seq[Result] = {
      Seq(
        validateTaxQuarterReportedAndPaid(index, columns, schemeIndex, offset),
        validateField(
          index, columns, ChargeAmountReportedPage.apply(ChargeType.ChargeTypeAnnualAllowance, _: Int, schemeIndex),
          McCloudFieldNames.allSingleFields, FieldNoChargeAmountReported1 + offset,
          chargeAmountReportedFormProvider(BigDecimal(0))
        )
      )
    }

    val wasAnotherPensionScheme = getOrElse[Boolean](wasAnotherPensionSchemeResult, false)
    val taxQuarter = if (wasAnotherPensionScheme) {
      val max = countNoOfSchemes(columns, FieldNoEnterPstr1)
      (0 until max).foldLeft[Seq[Result]](Nil){ (acc, schemeIndex) =>
        val offset = (schemeIndex * 3)
        acc ++ Seq(validateField(
          index, columns, EnterPstrPage(ChargeType.ChargeTypeAnnualAllowance, _: Int, schemeIndex),
          McCloudFieldNames.allSingleFields, FieldNoEnterPstr1 + offset,
          enterPstrFormProvider()
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
      index, columns, IsChargeInAdditionReportedPage.apply(ChargeType.ChargeTypeAnnualAllowance, _: Int),
      McCloudFieldNames.allSingleFields, FieldNoIsChargeInAdditionReported,
      yesNoFormProvider(messages("isChargeInAdditionReported.error.required", chargeTypeDescription(ChargeType.ChargeTypeAnnualAllowance))),
      stringToBoolean
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
