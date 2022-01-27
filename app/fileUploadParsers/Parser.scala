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

import play.api.i18n.Messages
import play.api.libs.json.{Format, JsPath, JsValue, Json}

import java.time.LocalDate

trait Parser {
  protected val totalFields:Int
  protected val minChargeValueAllowed = BigDecimal("0.01")

  def parse(startDate: LocalDate, rows: List[String])(implicit messages: Messages): ValidationResult = {
    rows.zipWithIndex.foldLeft[ValidationResult](ValidationResult(Nil, Nil)){
      case (acc, Tuple2(row, index)) =>
        val cells = row.split(",")
        cells.length match {
          case this.totalFields =>
            validateFields(startDate, index, cells) match {
              case Left(validationErrors) => ValidationResult(Nil, acc.errors ++ List(validationErrors))
              case Right(_) if acc.errors.nonEmpty => ValidationResult(Nil, acc.errors)
              case Right(commitItems) => ValidationResult(acc.commitItems ++ commitItems, acc.errors)
            }
          case _ =>
            ValidationResult(Nil, acc.errors ++ List(ParserValidationErrors(index, List("Not enough fields"))))
        }
    }
  }

  protected def combineValidationResults[A, B](
                                                resultA: Either[ParserValidationErrors, A],
                                                resultB: Either[ParserValidationErrors, B],
                                                resultAJsPath: => JsPath,
                                                resultAJsValue: A => JsValue,
                                                resultBJsPath: => JsPath,
                                                resultBJsValue: => B => JsValue
                                              ): Either[ParserValidationErrors, Seq[CommitItem]] = {
    resultA match {
      case Left(resultAErrors) =>
        resultB match {
          case Left(resultBErrors) =>
            Left(ParserValidationErrors(resultAErrors.row, resultAErrors.errors ++ resultBErrors.errors))
          case _ => Left(resultAErrors)
        }

      case Right(resultAObject) =>
        resultB match {
          case Left(resultBErrors) => Left(resultBErrors)
          case Right(resultBObject) =>
            Right(
              Seq(
                CommitItem(resultAJsPath, resultAJsValue(resultAObject)),
                CommitItem(resultBJsPath, resultBJsValue(resultBObject))
              )
            )
        }
    }
  }

  protected def validateFields(startDate: LocalDate,
                               index: Int,
                               chargeFields: Array[String])(implicit messages: Messages) : Either[ParserValidationErrors, Seq[CommitItem]]

  protected def firstNameField(fields: Array[String]):String =fields(0)
  protected def lastNameField(fields: Array[String]):String =fields(1)
  protected def ninoField(fields: Array[String]):String =fields(2)

  protected def splitDayMonthYear(date:String):(String, String, String) = {
    date.split("/").toSeq match {
      case Seq(d,m,y) => Tuple3(d,m,y)
      case Seq(d,m) => Tuple3(d,m,"")
      case Seq(d) => Tuple3(d, "", "")
      case _ => Tuple3("", "", "")
    }
  }

  protected def stringToBoolean(s:String): String =
    s.toLowerCase match {
      case "yes" => "true"
      case "no" => "false"
      case l => l
    }
}

case class ParserValidationErrors(row: Int, errors: Seq[String])

object ParserValidationErrors {
  implicit lazy val formats: Format[ParserValidationErrors] =
    Json.format[ParserValidationErrors]
}

case class CommitItem(jsPath: JsPath, value: JsValue)

case class ValidationResult(commitItems:Seq[CommitItem], errors: Seq[ParserValidationErrors])