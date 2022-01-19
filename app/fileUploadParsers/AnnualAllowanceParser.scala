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
import forms.MemberDetailsFormProvider
import models.UserAnswers
import pages.chargeE.MemberDetailsPage

class AnnualAllowanceParser @Inject()(
                                       memberDetailsFormProvider: MemberDetailsFormProvider
                                     ) extends Parser {

  override protected val totalFields: Int = 7

  override protected def validateFields(ua: UserAnswers, index: Int, chargeFields: Array[String]): Either[ParserValidationErrors, UserAnswers] = {
    val m = Map(
      "firstName" -> firstNameField(chargeFields),
      "lastName" -> lastNameField(chargeFields),
      "nino" -> ninoField(chargeFields)
    )
    val form = memberDetailsFormProvider.apply()
    form.bind(m).fold(
      formWithErrors => Left(ParserValidationErrors(index, formWithErrors.errors.map(_.message))),
      value => Right(ua.setOrException(MemberDetailsPage(index), value))
    )
  }
}