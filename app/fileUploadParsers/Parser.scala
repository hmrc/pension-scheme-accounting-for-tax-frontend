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
import models.{MemberDetails, UserAnswers}
import play.api.data.Form
import play.api.i18n.Messages
import play.api.libs.json.{Format, JsPath, JsValue, Json}

import java.time.LocalDate

object Parser {
  val FileLevelParserValidationErrorTypeHeaderInvalid: ParserValidationError = ParserValidationError(0, 0, "Header invalid")
  val FileLevelParserValidationErrorTypeFileEmpty: ParserValidationError = ParserValidationError(0, 0, "File is empty")
}

trait Parser {
  protected final val FieldNoFirstName = 0
  protected final val FieldNoLastName = 1
  protected final val FieldNoNino = 2

  protected def validHeader: String

  protected val totalFields: Int

  def parse(startDate: LocalDate, rows: Seq[String], userAnswers: UserAnswers)(implicit messages: Messages): Either[Seq[ParserValidationError], UserAnswers] = {
    rows.headOption match {
      case Some(row) if row.equalsIgnoreCase(validHeader) =>
        parseDataRows(startDate, rows).map{ commitItems =>
          commitItems.foldLeft(userAnswers)((acc, ci) => acc.setOrException(ci.jsPath, ci.value))
        }
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
      Field(MemberDetailsFieldNames.firstName, chargeFields(FieldNoFirstName), MemberDetailsFieldNames.firstName, 0),
      Field(MemberDetailsFieldNames.lastName, chargeFields(FieldNoLastName), MemberDetailsFieldNames.lastName, 1),
      Field(MemberDetailsFieldNames.nino, chargeFields(FieldNoNino), MemberDetailsFieldNames.nino, 2)
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

  protected final def addToValidationResults[A](
                                                 resultA: Either[Seq[ParserValidationError], A],
                                                 resultB: Either[Seq[ParserValidationError], Seq[CommitItem]],
                                                 resultAJsPath: => JsPath,
                                                 resultAJsValue: A => JsValue
                                               ): Either[Seq[ParserValidationError], Seq[CommitItem]] = {
    resultA match {
      case Left(resultAErrors) =>
        resultB match {
          case Left(existingErrors) => Left(existingErrors ++ resultAErrors)
          case Right(_) => Left(resultAErrors)
        }
      case Right(resultAObject) =>
        resultB match {
          case Left(existingErrors) => Left(existingErrors)
          case Right(existingCommits) =>
            Right(
              existingCommits ++ Seq(CommitItem(resultAJsPath, resultAJsValue(resultAObject)))
            )
        }
    }
  }

  protected final def combineValidationResults[A, B](
                                                      resultA: Either[Seq[ParserValidationError], A],
                                                      resultB: Either[Seq[ParserValidationError], B],
                                                      resultAJsPath: => JsPath,
                                                      resultAJsValue: A => JsValue,
                                                      resultBJsPath: => JsPath,
                                                      resultBJsValue: => B => JsValue
                                                    ): Either[Seq[ParserValidationError], Seq[CommitItem]] =
    addToValidationResults(
      resultB,
      addToValidationResults(
        resultA,
        Right(Nil),
        resultAJsPath,
        resultAJsValue
      ),
      resultBJsPath,
      resultBJsValue
    )

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
