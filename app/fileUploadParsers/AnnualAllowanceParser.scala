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

import models.{MemberDetails, UserAnswers, YearRange}
import models.chargeE.ChargeEDetails
import pages.chargeE.{AddMembersPage, AnnualAllowanceYearPage, ChargeDetailsPage, MemberDetailsPage, TotalChargeAmountPage}
import play.api.data.FormError
import play.api.libs.json.{Format, Json}
import uk.gov.hmrc.domain.Nino

import java.time.LocalDate
import scala.util.{Failure, Success, Try}

object AnnualAllowanceParser {

  def parse(request: UserAnswers, lines: List[String]): Either[List[ParserValidationErrors], UserAnswers] = {

    validate(lines).fold(
      x => Left(x),
      y => {
        val memberDetails = y.map { paa =>
          val a = MemberDetails(paa.firstName, paa.lastName, paa.nino)
          val b = ChargeEDetails(paa.amount, paa.date, paa.mandatory)
          val c = YearRange(paa.year)
          (a, b, c)
        }
        Right(add(request, memberDetails))
    })
  }

  def validate(lines: List[String]): Either[List[ParserValidationErrors], List[ParsedAnnualAllowance]] = {

    val parsedLines: List[Either[ParserValidationErrors, ParsedAnnualAllowance]] =
      lines.zipWithIndex.map { case (line, index) =>

      val items = line.split(",")

      items.length match {
        case 7 => validateFields(index, items)
        case _ => Left(ParserValidationErrors(index, List("Not enough fields")))
      }
    }

    if (parsedLines.exists(_.isLeft)) {
      Left(parsedLines.filter(lefts).map(a => a.left.get))
    }
    else {
      Right(parsedLines.filter(rights).map(x => x.right.get))
    }
  }

  def validateFields(index: Int, items: Array[String]) : Either[ParserValidationErrors, ParsedAnnualAllowance] = {

    val numericRegexp = """^-?(\-?)(\d*)(\.?)(\d*)$"""
    val decimal2DPRegexp = """^-?(\d*\.\d{2})$"""
    val intRegexp = """^-?(\d*)$"""

    val list = List[Option[String]](
      if (items(0).isEmpty) Some("firstName.is.empty") else if (items(0).length > 35) Some("firstName.is.too.long") else None,
      if (items(1).isEmpty) Some("lastName.is.empty") else if (items(1).length > 35) Some("lastName.is.too.long") else None,
      if (items(2).isEmpty) Some("nino.is.empty") else if (!Nino.isValid(items(2))) Some("nino.is.not.valid") else None,
      // TODO: Year
      if (items(4).isEmpty) Some("chargeAmount.is.empty") else if (!items(4).matches(numericRegexp)) Some("chargeAmount.is.not.valid") else if (!items(4).matches(decimal2DPRegexp) && !items(4).matches(intRegexp) ) Some("chargeAmount.has.to.many.decimal.places") else None
    )

    if (list.exists(x => x.isDefined))
      Left(ParserValidationErrors(index, list.filter(x => x.isDefined).map(x => x.get)))
    else
      Right(ParsedAnnualAllowance(
        items(0),
        items(1),
        items(2),
        items(3),
        BigDecimal(items(4)),
        LocalDate.parse(items(5)),
        items(6).trim.toBoolean))
  }

  def rights[A,B](x: Either[A, B]) = x match {
    case Left(_) => false
    case Right(_) => true
  }

  def lefts[A,B](x: Either[A, B]) = x match {
    case Left(_) => true
    case Right(_) => false
  }

  def add(userAnswers: UserAnswers, memberDetails: List[(MemberDetails, ChargeEDetails, YearRange)], index: Int = 0):
    UserAnswers = {
      memberDetails.length match {
        case 0 => userAnswers
        case _ => add(userAnswers
          .setOrException(AddMembersPage, true)
          .setOrException(MemberDetailsPage(index), memberDetails.head._1)
          .setOrException(ChargeDetailsPage(index), memberDetails.head._2)
          .setOrException(AnnualAllowanceYearPage(index), memberDetails.head._3),
          memberDetails.tail,
          index + 1)
    }
  }
}

case class ParserValidationErrors(row: Int, errors: List[String])

object ParserValidationErrors {
  implicit lazy val formats: Format[ParserValidationErrors] =
    Json.format[ParserValidationErrors]
}

case class ParsedAnnualAllowance(firstName: String, lastName: String, nino: String, year: String,
  amount: BigDecimal, date: LocalDate,mandatory: Boolean)