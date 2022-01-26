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
import pages.Page
import play.api.libs.json.{Format, JsPath, JsValue, Json}

trait Parser {
  protected val totalFields:Int

  def parse(rows: List[String]): ValidationResult = {
    rows.zipWithIndex.foldLeft[ValidationResult](ValidationResult(Nil, Nil)){
      case (acc, Tuple2(row, index)) =>
        val cells = row.split(",")
        cells.length match {
          case this.totalFields =>
            validateFields(index, cells) match {
              case Left(validationErrors) => ValidationResult(acc.commitItems, acc.errors ++ List(validationErrors))
              case Right(commitItems) => ValidationResult(acc.commitItems ++ commitItems, acc.errors)
            }
          case _ =>
            ValidationResult(acc.commitItems, acc.errors ++ List(ParserValidationErrors(index, List("Not enough fields"))))
        }
    }
  }

  protected def validateFields(index: Int, chargeFields: Array[String]) : Either[ParserValidationErrors, Seq[CommitItem]]

  protected def firstNameField(fields: Array[String]):String =fields(0)
  protected def lastNameField(fields: Array[String]):String =fields(1)
  protected def ninoField(fields: Array[String]):String =fields(2)
}

case class ParserValidationErrors(row: Int, errors: Seq[String])

object ParserValidationErrors {
  implicit lazy val formats: Format[ParserValidationErrors] =
    Json.format[ParserValidationErrors]
}

case class CommitItem(jsPath: JsPath, value: JsValue)

case class ValidationResult(commitItems:Seq[CommitItem], errors: List[ParserValidationErrors])