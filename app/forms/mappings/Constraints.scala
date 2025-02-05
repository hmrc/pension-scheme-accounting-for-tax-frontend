/*
 * Copyright 2024 HM Revenue & Customs
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

package forms.mappings

import play.api.data.validation.{Constraint, Invalid, Valid}
import uk.gov.hmrc.domain.Nino
import utils.DateHelper

import java.time.LocalDate

trait Constraints {
  private val regexPostcode = """^[A-Z]{1,2}[0-9][0-9A-Z]?\s?[0-9][A-Z]{2}$"""
  lazy val nameRegex: String = """^[a-zA-Z &`\-\'\.^]*$"""
  private val regexCrn = "^[A-Za-z0-9 -]{8}$"
  val addressLineRegex = """^[A-Za-z0-9 \-,.&'\/]{1,35}$"""
  val psaIdRegex = "^A[0-9]{7}$"
  val pstrRegx = """^[0-9]{8}[Rr][A-Za-z]{1}$"""
  val regexTightText = """^[a-zA-ZàÀ-ÿ '&.^-]{1,160}$"""
  val regexTightTextWithNumber = """^[a-zA-Z0-9àÀ-ÿ '&.^-]{1,160}$"""


  protected def year(minYear: Int,
                     maxYear: Int,
                     requiredKey: String,
                     invalidKey: String,
                     minKey: String,
                     maxKey: String
                    ): Constraint[String] =
    Constraint {
      case year if year.isEmpty => Invalid(requiredKey)
      case year if year.toLowerCase.contains("to") =>
        year.split("to").map(_.trim) match {
          case Array(year1, year2) =>
            if (year1.toInt > maxYear)
              Invalid(maxKey)
            else if (year1.toInt < minYear)
              Invalid(minKey)
            else if (year2.toInt - year1.toInt == 1)
              Valid
            else {
              Invalid(invalidKey)
            }
          case _ => Invalid(invalidKey)
        }
      case year if year.length == 4 =>
        if (year.toInt > maxYear)
          Invalid(maxKey)
        else if (year.toInt < minYear)
          Invalid(minKey)
        else
          Invalid(invalidKey)
      case _ => Invalid(invalidKey)
    }

  protected def firstError[A](constraints: Constraint[A]*): Constraint[A] =
    Constraint {
      input =>
        constraints
          .map(_.apply(input))
          .find(_ != Valid)
          .getOrElse(Valid)
    }

  protected def isEqual(expectedValue: Option[String], errorKey: String): Constraint[String] =
    Constraint {
      case _ if expectedValue.isEmpty => Valid
      case s if expectedValue.contains(s) => Valid
      case _ => Invalid(errorKey)
    }

  protected def postCode(errorKey: String): Constraint[String] = regexp(regexPostcode, errorKey)

  protected def minimumValue[A](minimum: A, errorKey: String)(implicit ev: Ordering[A]): Constraint[A] =
    Constraint {
      input =>

        import ev._

        if (input >= minimum) {
          Valid
        } else {
          Invalid(errorKey, minimum)
        }
    }

  protected def minimumValueOption[A](minimum: A, errorKey: String)(implicit ev: Ordering[A]): Constraint[Option[A]] =
    Constraint {
      case Some(input) =>
        import ev._

        if (input >= minimum) {
          Valid
        } else {
          Invalid(errorKey, minimum)
        }
      case None =>
        Valid
    }

  protected def maximumValue[A](maximum: A, errorKey: String)(implicit ev: Ordering[A]): Constraint[A] =
    Constraint {
      input =>

        import ev._

        if (input <= maximum) {
          Valid
        } else {
          Invalid(errorKey, maximum)
        }
    }

  protected def maximumValueOption[A](minimum: A, errorKey: String)(implicit ev: Ordering[A]): Constraint[Option[A]] =
    Constraint {
      case Some(input) =>
        import ev._

        if (input <= minimum) {
          Valid
        } else {
          Invalid(errorKey, minimum)
        }
      case None =>
        Valid
    }

  protected def inRange[A](minimum: A, maximum: A, errorKey: String)(implicit ev: Ordering[A]): Constraint[A] =
    Constraint {
      input =>

        import ev._

        if (input >= minimum && input <= maximum) {
          Valid
        } else {
          Invalid(errorKey, minimum, maximum)
        }
    }

  protected def regexp(regex: String, errorKey: String): Constraint[String] =
    Constraint {
      case str if str.matches(regex) =>
        Valid
      case _ =>
        Invalid(errorKey, regex)
    }

  protected def maxLength(maximum: Int, errorKey: String): Constraint[String] =
    Constraint {
      case str if str.length <= maximum =>
        Valid
      case _ =>
        Invalid(errorKey, maximum)
    }

  protected def optionalMaxLength(maximum: Int, errorKey: String): Constraint[Option[String]] =
    Constraint {
      case Some(str) if str.length <= maximum =>
        Valid
      case None => Valid
      case _ =>
        Invalid(errorKey, maximum)
    }

  protected def exactLength(length: Int, errorKey: String): Constraint[String] =
    Constraint {
      case str if str.length == length =>
        Valid
      case _ =>
        Invalid(errorKey, length)
    }

  protected def maxDate(maximum: LocalDate, errorKey: String, args: Any*): Constraint[LocalDate] =
    Constraint {
      case date if date.isAfter(maximum) =>
        Invalid(errorKey, args: _*)
      case _ =>
        Valid
    }

  protected def minDate(minimum: LocalDate, errorKey: String, args: Any*): Constraint[LocalDate] =
    Constraint {
      case date if date.isBefore(minimum) =>
        Invalid(errorKey, args: _*)
      case _ =>
        Valid
    }

  protected def futureDate(invalidKey: String): Constraint[LocalDate] =
    Constraint {
      case date if date.isAfter(DateHelper.today) =>
        Invalid(invalidKey)
      case _ => Valid
    }

  protected def yearHas4Digits(errorKey: String): Constraint[LocalDate] =
    Constraint {
      case date if date.getYear >= 1000 => Valid
      case _ => Invalid(errorKey)
    }

  protected def nonEmptySet(errorKey: String): Constraint[Set[_]] =
    Constraint {
      case set if set.nonEmpty =>
        Valid
      case _ =>
        Invalid(errorKey)
    }

  protected def validNino(invalidKey: String): Constraint[String] =
    Constraint {
      case nino if Nino.isValid(nino) => Valid
      case _ => Invalid(invalidKey)
    }

  protected def validCrn(invalidKey: String): Constraint[String] =
    Constraint {
      case crn if crn.matches(regexCrn) => Valid
      case _ => Invalid(invalidKey)
    }

  protected def validAddressLine(invalidKey: String): Constraint[String] = regexp(addressLineRegex, invalidKey)

  protected def optionalValidAddressLine(invalidKey: String): Constraint[Option[String]] = Constraint {
    case Some(str) if str.matches(addressLineRegex) =>
      Valid
    case None => Valid
    case _ =>
      Invalid(invalidKey, addressLineRegex)
  }

  protected def tightText(errorKey: String): Constraint[String] = regexp(regexTightText, errorKey)

  protected def tightTextWithNumber(errorKey: String): Constraint[String] = regexp(regexTightTextWithNumber, errorKey)
}
