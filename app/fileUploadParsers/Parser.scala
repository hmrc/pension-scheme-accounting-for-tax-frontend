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

import cats.data.Validated
import cats.data.Validated.{Invalid, Valid}
import cats.implicits._
import controllers.fileUpload.FileUploadHeaders.MemberDetailsFieldNames
import fileUploadParsers.ParserErrorMessages.HeaderInvalidOrFileIsEmpty
import models.{ChargeType, MemberDetails, UserAnswers}
import org.apache.commons.lang3.StringUtils.EMPTY
import play.api.data.Form
import play.api.i18n.Messages
import play.api.libs.json._
import queries.Gettable

import java.time.LocalDate

object ParserErrorMessages {
  val HeaderInvalidOrFileIsEmpty = "Header invalid or File is empty"
}

object Parser {
  type Result = Validated[Seq[ParserValidationError], Seq[CommitItem]]
  val FileLevelParserValidationErrorTypeHeaderInvalidOrFileEmpty: ParserValidationError = ParserValidationError(0, 0, HeaderInvalidOrFileIsEmpty, EMPTY)
}

trait Parser {

  import Parser._

  protected val chargeType: ChargeType

  protected val fieldNoFirstName = 0
  protected val fieldNoLastName = 1
  protected val fieldNoNino = 2

  protected final val YearLength = 4

  protected def validHeader: String

  // scalastyle:off parameter.number
  protected def validateField[A](
                                  index: Int,
                                  columns: Seq[String],
                                  page: Int => Gettable[_],
                                  formFieldName: String,
                                  columnName: String,
                                  fieldNo: Int,
                                  formProvider: => Form[A],
                                  convertValue: String => String = identity
                                )(implicit writes: Writes[A]): Result = {
    def bindForm(index: Int, columns: Seq[String],
                 fieldName: String, fieldNo: Int)
    : Validated[Seq[ParserValidationError], A] = {
      val form: Form[A] = formProvider
      val fields = Seq(Field(fieldName, convertValue(fieldValue(columns, fieldNo)), columnName, fieldNo))
      val toMap = Field.seqToMap(fields)
      val bind = form.bind(toMap)
      bind.fold(
        formWithErrors => Invalid(errorsFromForm(formWithErrors, fields, index)),
        value => Valid(value)
      )
    }

    resultFromFormValidationResult[A](
      bindForm(index, columns, formFieldName, fieldNo),
      createCommitItem(index, page)
    )
  }

  protected def fieldValue(columns: Seq[String], fieldNo: Int): String =
    if (columns.isDefinedAt(fieldNo)) {
      columns(fieldNo)
    } else {
      ""
    }

  def parse(startDate: LocalDate, rows: Seq[Array[String]], userAnswers: UserAnswers)
           (implicit messages: Messages): Validated[Seq[ParserValidationError], UserAnswers] = {
    rows.headOption match {
      case Some(row) if row.mkString(",").equalsIgnoreCase(validHeader) =>
        rows.size match {
          case n if n >= 2 => parseDataRows(startDate, rows)
            .map(_.foldLeft(userAnswers)((acc, ci) => acc.setOrException(ci.jsPath, ci.value)))
          case _ => Invalid(Seq(FileLevelParserValidationErrorTypeHeaderInvalidOrFileEmpty))
        }
      case _ =>
        Invalid(Seq(FileLevelParserValidationErrorTypeHeaderInvalidOrFileEmpty))
    }
  }

  private def parseDataRows(startDate: LocalDate, rows: Seq[Array[String]])
                           (implicit messages: Messages): Result = {
    rows.zipWithIndex.foldLeft[Result](Valid(Nil)) {
      case (acc, Tuple2(_, 0)) => acc
      case (acc, Tuple2(row, index)) => Seq(acc, validateFields(startDate, index, row.toIndexedSeq)).combineAll
    }
  }

  protected def validateFields(startDate: LocalDate,
                               index: Int,
                               columns: Seq[String])(implicit messages: Messages): Result

  protected def memberDetailsValidation(index: Int, columns: Seq[String],
                                        memberDetailsForm: Form[MemberDetails]): Validated[Seq[ParserValidationError], MemberDetails] = {
    val fields = Seq(
      Field(MemberDetailsFieldNames.firstName, fieldValue(columns, fieldNoFirstName), MemberDetailsFieldNames.firstName, 0),
      Field(MemberDetailsFieldNames.lastName, fieldValue(columns, fieldNoLastName), MemberDetailsFieldNames.lastName, 1),
      Field(MemberDetailsFieldNames.nino, fieldValue(columns, fieldNoNino), MemberDetailsFieldNames.nino, 2)
    )
    val toMap = Field.seqToMap(fields)

    val bind = memberDetailsForm.bind(toMap)
    bind.fold(
      formWithErrors => Invalid(errorsFromForm(formWithErrors, fields, index)),
      value => Valid(value)
    )
  }

  protected final def errorsFromForm[A](formWithErrors: Form[A], fields: Seq[Field], index: Int): Seq[ParserValidationError] = {
    for {
      formError <- formWithErrors.errors
      field <- fields.find(_.getFormValidationFullFieldName == formError.key)
    }
    yield {
      ParserValidationError(index, field.columnNo, formError.message, field.columnName, formError.args)
    }
  }

  protected final def createCommitItem[A](index: Int, page: Int => Gettable[_])(implicit writes: Writes[A]): A => CommitItem =
    a => CommitItem(page(index - 1).path, Json.toJson(a))

  protected def resultFromFormValidationResult[A](formValidationResult: Validated[Seq[ParserValidationError], A],
                                                  generateCommitItem: A => CommitItem): Result = {
    formValidationResult match {
      case Invalid(resultAErrors) => Invalid(resultAErrors)
      case Valid(resultAObject) => Valid(Seq(generateCommitItem(resultAObject)))
    }
  }

  protected def get[A](r: Result)(implicit reads: Reads[A]): Option[A] =
    r.toOption.flatMap(_.headOption.flatMap(_.value.asOpt[A]))

  protected def getOrElse[A](r: Result, default: A)(implicit reads: Reads[A]): A =
    r.toOption.flatMap(_.headOption.flatMap(_.value.asOpt[A])) match {
      case None => default
      case Some(a) => a
    }

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

case class ParserValidationError(row: Int, col: Int, error: String, columnName: String = EMPTY, args: Seq[Any] = Nil)

protected case class CommitItem(jsPath: JsPath, value: JsValue)

protected case class Field(formValidationFieldName: String,
                           fieldValue: String,
                           columnName: String,
                           columnNo: Int,
                           private val formValidationFullFieldName: Option[String] = None
                          ) {
  def getFormValidationFullFieldName: String = formValidationFullFieldName match {
    case Some(v) => v
    case _ => formValidationFieldName
  }
}

protected case class ParsedDate(day: String, month: String, year: String)

protected object Field {
  def seqToMap(s: Seq[Field]): Map[String, String] = {
    s.map { f =>
      f.formValidationFieldName -> f.fieldValue
    }.toMap
  }
}