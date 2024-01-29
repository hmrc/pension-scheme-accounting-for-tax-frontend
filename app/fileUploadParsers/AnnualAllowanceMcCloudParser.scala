/*
 * Copyright 2024 HM Revenue & Customs
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
import models.CommonQuarters

class AnnualAllowanceMcCloudParser @Inject()(
                                              override val memberDetailsFormProvider: MemberDetailsFormProvider,
                                              override val chargeDetailsFormProvider: ChargeDetailsFormProvider,
                                              override val config: FrontendAppConfig,
                                              val yesNoFormProvider: YesNoFormProvider,
                                              val enterPstrFormProvider: EnterPstrFormProvider,
                                              val chargeAmountReportedFormProvider: ChargeAmountReportedFormProvider
                                            ) extends AnnualAllowanceParser with Constraints with CommonQuarters with McCloudParser {

  override protected val fieldNoIsChargeInAdditionReported: Int = 7
  override protected val fieldNoWasAnotherPensionScheme: Int = 8
  override protected val fieldNoEnterPstr1: Int = 9
  override protected val fieldNoTaxQuarterReportedAndPaid1: Int = 10
  override protected val fieldNoChargeAmountReported1: Int = 11

  override protected def validHeader: String = config.validAnnualAllowanceMcCloudHeader
}
