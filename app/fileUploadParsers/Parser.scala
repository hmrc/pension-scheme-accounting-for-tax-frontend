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

import controllers.fileUpload.FileUploadHeaders.MemberDetailsFieldNames
import fileUploadParsers.Parser.FileLevelParserValidationErrorTypeHeaderInvalidOrFileEmpty
import models.{MemberDetails, UserAnswers}
import org.apache.commons.lang3.StringUtils.EMPTY
import play.api.data.Form
import play.api.i18n.Messages
import play.api.libs.json.{JsPath, JsValue}
import utils.StringHelper

import java.time.LocalDate

object Parser {
  val FileLevelParserValidationErrorTypeHeaderInvalidOrFileEmpty:ParserValidationError = ParserValidationError(0, 0, "Header invalid or File is empty", EMPTY)
}

trait Parser {
  protected final val FieldNoFirstName = 0
  protected final val FieldNoLastName = 1
  protected final val FieldNoNino = 2

  protected def validHeader: String

  protected val totalFields: Int

  def parse(startDate: LocalDate, rows: Seq[Array[String]], userAnswers: UserAnswers)(implicit messages: Messages): Either[Seq[ParserValidationError], UserAnswers] = {
    rows.headOption match {
      case Some(row) if row.mkString(",").equalsIgnoreCase(validHeader) =>
        rows.size match {
          case n if n >= 2 => parseDataRows(startDate, rows).map{ commitItems =>
            commitItems.foldLeft(userAnswers)((acc, ci) => acc.setOrException(ci.jsPath, ci.value))
          }
          case _ => Left(Seq(FileLevelParserValidationErrorTypeHeaderInvalidOrFileEmpty))
        }
      case _ => Left(Seq(FileLevelParserValidationErrorTypeHeaderInvalidOrFileEmpty))
    }
  }

  private def parseDataRows(startDate: LocalDate, rows: Seq[Array[String]])(implicit messages: Messages): Either[Seq[ParserValidationError], Seq[CommitItem]] = {
    rows.zipWithIndex.foldLeft[Either[Seq[ParserValidationError], Seq[CommitItem]]](Right(Nil)) {
      case (acc, Tuple2(_, 0)) => acc
      case (acc, Tuple2(row, index)) =>
      // val cells = StringHelper.split(row)
        row.length match {
          case this.totalFields =>
            (acc, validateFields(startDate, index, row)) match {
              case (Left(currentErrors), Left(newErrors)) => Left(currentErrors ++ newErrors)
              case (Right(_), newErrors@Left(_)) => newErrors
              case (currentErrors@Left(_), Right(_)) => currentErrors
              case (currentCommitItems@Right(_), Right(newCommitItems)) => currentCommitItems.map(_ ++ newCommitItems)
            }
          case _ =>
            Left(acc.left.getOrElse(Nil) :+ ParserValidationError(index, 0, "Not enough fields", EMPTY))
        }
    }
  }

  protected def validateFields(startDate: LocalDate,
                               index: Int,
                               columns: Seq[String])(implicit messages: Messages): Either[Seq[ParserValidationError], Seq[CommitItem]]

  protected def memberDetailsValidation(index: Int, columns: Seq[String],
                                        memberDetailsForm: Form[MemberDetails]): Either[Seq[ParserValidationError], MemberDetails] = {
    val fields = Seq(
      Field(MemberDetailsFieldNames.firstName, columns(FieldNoFirstName), MemberDetailsFieldNames.firstName, 0),
      Field(MemberDetailsFieldNames.lastName, columns(FieldNoLastName), MemberDetailsFieldNames.lastName, 1),
      Field(MemberDetailsFieldNames.nino, columns(FieldNoNino), MemberDetailsFieldNames.nino, 2)
    )
   val toMap = Field.seqToMap(fields)

   val bind =  memberDetailsForm.bind(toMap)
    bind.fold(
        formWithErrors => Left(errorsFromForm(formWithErrors, fields, index)),
        value => Right(value)
      )
  }

  protected final def errorsFromForm[A](formWithErrors: Form[A], fields: Seq[Field], index: Int): Seq[ParserValidationError] = {
    for{
      formError <- formWithErrors.errors
      field <- fields.find(_.columnName == formError.key)
    }
    yield {
      ParserValidationError(index, field.columnNo, formError.message, field.columnName,formError.args)
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



  protected final def splitDayMonthYear(date: String): ParsedDate = {
    date.split("/").toSeq match {
      case Seq(d, m, y) => ParsedDate(d, m, y)
      case Seq(d, m) => ParsedDate(d, m, EMPTY)
      case Seq(d) => ParsedDate(d, EMPTY, EMPTY)
      case _ => ParsedDate(EMPTY, EMPTY, EMPTY)
    }
  }

  protected final def stringToBoolean(s: String): String =
    s.toLowerCase match {
      case "yes" => "true"
      case "no" => "false"
      case l => l
    }
}

case class ParserValidationError(row: Int, col: Int, error: String, columnName: String = EMPTY,args:Seq[Any]=Nil)


protected case class CommitItem(jsPath: JsPath, value: JsValue)

protected case class Field(formValidationFieldName: String, fieldValue: String, columnName: String, columnNo: Int)

protected case class ParsedDate(day: String, month: String, year: String)

protected object Field {
  def seqToMap(s: Seq[Field]): Map[String, String] = {
    s.map { f =>
      f.formValidationFieldName -> f.fieldValue
    }.toMap
  }
}