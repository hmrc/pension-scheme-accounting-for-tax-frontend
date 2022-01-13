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
import play.api.libs.json.{Format, Json}

class AnnualAllowanceParser @Inject()(
                                       memberDetailsFormProvider: MemberDetailsFormProvider
                                     ) {

  private val totalFields:Int = 7

  def parse(ua: UserAnswers, rows: List[String]): ValidationResult = {
    rows.zipWithIndex.foldLeft[ValidationResult](ValidationResult(ua, Nil)){
      case (acc, Tuple2(row, index)) =>
        val cells = row.split(",")
        cells.length match {
          case this.totalFields =>
            validateFields(acc.ua, index, cells) match {
              case Left(validationErrors) =>
                ValidationResult(acc.ua, acc.errors ++ List(validationErrors))
              case Right(updatedUA) => ValidationResult(updatedUA, acc.errors)
            }
          case _ =>
            ValidationResult(acc.ua, acc.errors ++ List(ParserValidationErrors(index, List("Not enough fields"))))
        }
    }
  }

  private def validateFields(ua:UserAnswers, index: Int, chargeFields: Array[String]) : Either[ParserValidationErrors, UserAnswers] = {
    val m =
      Map(
        "firstName" -> chargeFields(0),
        "lastName" -> chargeFields(1),
        "nino" -> chargeFields(2)
      )
    val form = memberDetailsFormProvider.apply()
    form.bind(m).fold(
      formWithErrors => Left(ParserValidationErrors(index, formWithErrors.errors.map(_.message))),
      value => Right(ua.setOrException(MemberDetailsPage(index), value))
    )
  }
}

case class ParserValidationErrors(row: Int, errors: Seq[String])

case class ValidationResult(ua:UserAnswers, errors: List[ParserValidationErrors])

object ParserValidationErrors {
  implicit lazy val formats: Format[ParserValidationErrors] =
    Json.format[ParserValidationErrors]
}
