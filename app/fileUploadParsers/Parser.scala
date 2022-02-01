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

import fileUploadParsers.Parser.{FileLevelParserValidationErrorTypeFileEmpty, FileLevelParserValidationErrorTypeHeaderInvalid}
import models.MemberDetails
import play.api.data.Form
import play.api.i18n.Messages
import play.api.libs.json.{Format, JsPath, JsValue, Json}

import java.time.LocalDate

object Parser {
  val FileLevelParserValidationErrorTypeHeaderInvalid:ParserValidationError = ParserValidationError(0, 0, "Header invalid")
  val FileLevelParserValidationErrorTypeFileEmpty:ParserValidationError = ParserValidationError(0, 0, "File is empty")
}

trait Parser {
  protected def validHeader: String

  protected val totalFields: Int

  def parse(startDate: LocalDate, rows: Seq[String])(implicit messages: Messages): Either[Seq[ParserValidationError], Seq[CommitItem]] = {
    rows.headOption match {
      case Some(row) if row.equalsIgnoreCase(validHeader) => parseDataRows(startDate, rows)
      case Some(_) => Left(Seq(FileLevelParserValidationErrorTypeHeaderInvalid))
      case None => Left(Seq(FileLevelParserValidationErrorTypeFileEmpty))
    }
  }

  private def parseDataRows(startDate: LocalDate, rows: Seq[String])(implicit messages: Messages): Either[Seq[ParserValidationError], Seq[CommitItem]] = {
    rows.zipWithIndex.foldLeft[Either[Seq[ParserValidationError], Seq[CommitItem]]](Right(Nil)) {
      case (acc, Tuple2(_, 0)) => acc
      case (acc, Tuple2(row, index)) =>
        val cells = row.split(",")
        cells.length match {
          case this.totalFields =>
            (acc, validateFields(startDate, index, cells)) match {
              case (Left(currentErrors), Left(newErrors)) => Left(currentErrors ++ newErrors)
              case (Right(_), newErrors@Left(_)) => newErrors
              case (currentErrors@Left(_), Right(_)) => currentErrors
              case (currentCommitItems@Right(_), Right(newCommitItems)) => currentCommitItems.map(_ ++ newCommitItems)
            }
          case _ =>
            Left(acc.left.getOrElse(Nil) ++ Seq(ParserValidationError(index, 0, "Not enough fields")))
        }
    }
  }

  protected def validateFields(startDate: LocalDate,
                               index: Int,
                               chargeFields: Array[String])(implicit messages: Messages): Either[Seq[ParserValidationError], Seq[CommitItem]]

  protected def memberDetailsValidation(index: Int, chargeFields: Array[String],
                                        memberDetailsForm: Form[MemberDetails]): Either[Seq[ParserValidationError], MemberDetails] = {
    val fields = Seq(
      Field(MemberDetailsFieldNames.firstName, firstNameField(chargeFields), MemberDetailsFieldNames.firstName, 0),
      Field(MemberDetailsFieldNames.lastName, lastNameField(chargeFields), MemberDetailsFieldNames.lastName, 1),
      Field(MemberDetailsFieldNames.nino, ninoField(chargeFields), MemberDetailsFieldNames.nino, 2)
    )
    memberDetailsForm
      .bind(Field.seqToMap(fields))
      .fold(
        formWithErrors => Left(errorsFromForm(formWithErrors, fields, index)),
        value => Right(value)
      )
  }

  protected final def errorsFromForm[A](formWithErrors: Form[A], fields: Seq[Field], index: Int): Seq[ParserValidationError] = {
    formWithErrors
      .errors
      .flatMap { formError =>
        fields.find(_.columnName == formError.key)
          .map(f => ParserValidationError(index, f.columnNo, formError.message))
          .toSeq
      }
  }

  protected final def combineValidationResults[A, B](
                                                      resultA: Either[Seq[ParserValidationError], A],
                                                      resultB: Either[Seq[ParserValidationError], B],
                                                      resultAJsPath: => JsPath,
                                                      resultAJsValue: A => JsValue,
                                                      resultBJsPath: => JsPath,
                                                      resultBJsValue: => B => JsValue
                                                    ): Either[Seq[ParserValidationError], Seq[CommitItem]] = {
    resultA match {
      case Left(resultAErrors) =>
        resultB match {
          case Left(resultBErrors) =>
            Left(resultAErrors ++ resultBErrors)
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

  protected final def firstNameField(fields: Array[String]): String = fields(0)

  protected final def lastNameField(fields: Array[String]): String = fields(1)

  protected final def ninoField(fields: Array[String]): String = fields(2)

  protected final val minChargeValueAllowed = BigDecimal("0.01")

  protected final object MemberDetailsFieldNames {
    val firstName = "firstName"
    val lastName = "lastName"
    val nino = "nino"
  }

  protected final def splitDayMonthYear(date: String): ParsedDate = {
    date.split("/").toSeq match {
      case Seq(d, m, y) => ParsedDate(d, m, y)
      case Seq(d, m) => ParsedDate(d, m, "")
      case Seq(d) => ParsedDate(d, "", "")
      case _ => ParsedDate("", "", "")
    }
  }

  protected final def stringToBoolean(s: String): String =
    s.toLowerCase match {
      case "yes" => "true"
      case "no" => "false"
      case l => l
    }
}

case class ParserValidationError(row: Int, col: Int, error: String)

object ParserValidationError {
  implicit lazy val formats: Format[ParserValidationError] =
    Json.format[ParserValidationError]
}

case class CommitItem(jsPath: JsPath, value: JsValue)

case class Field(formValidationFieldName: String, fieldValue: String, columnName: String, columnNo: Int)

case class ParsedDate(day: String, month: String, year: String)

object Field {
  def seqToMap(s: Seq[Field]): Map[String, String] = {
    s.map { f =>
      f.formValidationFieldName -> f.fieldValue
    }.toMap
  }
}
