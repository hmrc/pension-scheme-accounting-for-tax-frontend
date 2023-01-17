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
import forms.MemberDetailsFormProvider
import forms.chargeE.ChargeDetailsFormProvider
import forms.mappings.Constraints
import models.CommonQuarters
import play.api.i18n.Messages

import java.time.LocalDate

class AnnualAllowanceMcCloudParser @Inject()(
                                                 override val memberDetailsFormProvider: MemberDetailsFormProvider,
                                                 override val chargeDetailsFormProvider: ChargeDetailsFormProvider,
                                                 override val config: FrontendAppConfig
                                               ) extends AnnualAllowanceParser with Constraints with CommonQuarters {
  override protected val totalFields: Int = 7 // TODO This will vary

  override protected def validHeader: String = config.validAnnualAllowanceMcCloudHeader

  override protected def validateFields(startDate: LocalDate,
                                        index: Int,
                                        columns: Seq[String])(implicit messages: Messages): Either[Seq[ParserValidationError], Seq[CommitItem]] = {
    validateMinimumFields(startDate, index, columns)
  }
}
