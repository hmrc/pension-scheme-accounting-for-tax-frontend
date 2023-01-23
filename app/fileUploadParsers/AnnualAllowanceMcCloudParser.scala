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
import play.api.i18n.Messages
import play.api.libs.json.JsBoolean

import java.time.LocalDate

class AnnualAllowanceMcCloudParser @Inject()(
                                              override val memberDetailsFormProvider: MemberDetailsFormProvider,
                                              override val chargeDetailsFormProvider: ChargeDetailsFormProvider,
                                              override val config: FrontendAppConfig,
                                              val yesNoFormProvider: YesNoFormProvider,
                                              val enterPstrFormProvider: EnterPstrFormProvider,
                                              val chargeAmountReportedFormProvider: ChargeAmountReportedFormProvider
                                            ) extends AnnualAllowanceParser with Constraints with CommonQuarters with McCloudParser {

  override protected val chargeType: ChargeType = ChargeType.ChargeTypeAnnualAllowance

  override protected def validHeader: String = config.validAnnualAllowanceMcCloudHeader

  override protected def validateFields(startDate: LocalDate,
                                        index: Int,
                                        columns: Seq[String])(implicit messages: Messages): Result = {
    val minimalFieldsResult = validateMinimumFields(startDate, index, columns)
    val isPublicServicePensionsRemedyResult = Right(Seq(
      CommitItem(IsPublicServicePensionsRemedyPage(ChargeType.ChargeTypeAnnualAllowance, Some(index - 1)).path, JsBoolean(true))))

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
