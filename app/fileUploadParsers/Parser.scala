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

import models.UserAnswers
import play.api.libs.json.{Format, Json}

trait Parser {
  protected val totalFields:Int

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

  protected def validateFields(ua:UserAnswers, index: Int, chargeFields: Array[String]) : Either[ParserValidationErrors, UserAnswers]
}

case class ParserValidationErrors(row: Int, errors: Seq[String])

object ParserValidationErrors {
  implicit lazy val formats: Format[ParserValidationErrors] =
    Json.format[ParserValidationErrors]
}

case class ValidationResult(ua:UserAnswers, errors: List[ParserValidationErrors])