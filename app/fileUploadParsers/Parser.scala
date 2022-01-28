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

import play.api.data.Form
import play.api.i18n.Messages
import play.api.libs.json.{Format, JsPath, JsValue, Json}

import java.time.LocalDate

trait Parser {
  protected val totalFields:Int
  protected val minChargeValueAllowed = BigDecimal("0.01")
  def parse(startDate: LocalDate, rows: Seq[String])(implicit messages: Messages): Either[Seq[ParserValidationError], Seq[CommitItem]] = {
    rows.zipWithIndex.foldLeft[Either[Seq[ParserValidationError], Seq[CommitItem]]](Right(Nil)){
      case (acc, Tuple2(row, index)) =>
        val cells = row.split(",")
        cells.length match {
          case this.totalFields =>
            (acc, validateFields(startDate, index, cells)) match {
              case (Left(currentErrors), Left(newErrors)) => Left(currentErrors ++ newErrors)
              case (Right(_), Left(newErrors)) => Left(newErrors)
              case (currentErrors@Left(_), Right(_)) => currentErrors
              case (currentCommitItems@Right(_), Right(newCommitItems)) => currentCommitItems.map(_ ++ newCommitItems)
            }
          case _ =>
            Left(acc.left.getOrElse(Nil) ++ Seq(ParserValidationError(index, -1, "Not enough fields")))
        }
    }
  }

  protected def errorsFromForm[A](formWithErrors:Form[A], fields: Seq[Field], index:Int): Seq[ParserValidationError] = {
    formWithErrors
      .errors
      .map { formError =>
        val col = fields.find(_.columnName == formError.key) match {
          case Some(f) => f.columnNo
          case _ => -1
        }
        ParserValidationError(index, col, formError.message)
      }
  }

  protected def combineValidationResults[A, B](
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

  protected def validateFields(startDate: LocalDate,
                               index: Int,
                               chargeFields: Array[String])(implicit messages: Messages) : Either[Seq[ParserValidationError], Seq[CommitItem]]

  protected def firstNameField(fields: Array[String]):String =fields(0)
  protected def lastNameField(fields: Array[String]):String =fields(1)
  protected def ninoField(fields: Array[String]):String =fields(2)

  protected object MemberDetailsFieldNames {
    val firstName = "firstName"
    val lastName = "lastName"
    val nino = "nino"
  }

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

case class ParserValidationError(row: Int, col:Int, errors: String)

object ParserValidationError {
  implicit lazy val formats: Format[ParserValidationError] =
    Json.format[ParserValidationError]
}

case class CommitItem(jsPath: JsPath, value: JsValue)

case class Field(fieldName:String, fieldValue: String, columnName: String, columnNo:Int)

object Field {
  def seqToMap(s:Seq[Field]):Map[String,String] = {
    s.map{ f =>
      f.fieldName -> f.fieldValue
    }.toMap
  }
}
